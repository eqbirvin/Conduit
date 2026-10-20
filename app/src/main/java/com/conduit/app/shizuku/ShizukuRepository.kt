package com.conduit.app.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import com.conduit.app.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume

enum class ShizukuStatus {
    /** Neither the Shizuku app nor Sui is present. */
    NOT_INSTALLED,
    /** Shizuku is installed but its service has not been started. */
    NOT_RUNNING,
    /** Shizuku is running but is older than v11 (no user service support). */
    OUTDATED,
    /** Running; Conduit has not been authorized yet and Shizuku can prompt for it. */
    PERMISSION_REQUIRED,
    /** Running; the user denied Conduit permanently, so it must be allowed from the Shizuku app. */
    PERMISSION_DENIED,
    /** Running and Conduit is authorized. */
    READY
}

/**
 * Single entry point for Shizuku. Exposes Shizuku's status and runs privileged commands
 * through [PrivilegedUserService]. All Shizuku binder calls happen off the main thread.
 */
class ShizukuRepository(context: Context) {

    private val appContext = context.applicationContext

    /** Emits whenever Shizuku's binder arrives or dies, or a permission request is answered. */
    fun statusChanges(): Flow<Unit> = callbackFlow {
        val binderReceived = Shizuku.OnBinderReceivedListener { trySend(Unit) }
        val binderDead = Shizuku.OnBinderDeadListener { trySend(Unit) }
        val permissionResult = Shizuku.OnRequestPermissionResultListener { _, _ -> trySend(Unit) }
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)
        awaitClose {
            Shizuku.removeBinderReceivedListener(binderReceived)
            Shizuku.removeBinderDeadListener(binderDead)
            Shizuku.removeRequestPermissionResultListener(permissionResult)
        }
    }.conflate()

    suspend fun currentStatus(): ShizukuStatus = withContext(Dispatchers.IO) {
        if (!Shizuku.pingBinder()) {
            return@withContext if (managerLaunchIntent() != null) ShizukuStatus.NOT_RUNNING else ShizukuStatus.NOT_INSTALLED
        }
        // Shizuku wraps binder failures (e.g. the service dying mid-call) in RuntimeException.
        try {
            when {
                Shizuku.isPreV11() -> ShizukuStatus.OUTDATED
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> ShizukuStatus.READY
                Shizuku.shouldShowRequestPermissionRationale() -> ShizukuStatus.PERMISSION_DENIED
                else -> ShizukuStatus.PERMISSION_REQUIRED
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: RuntimeException) {
            Log.e(TAG, "Shizuku status check failed", e)
            ShizukuStatus.NOT_RUNNING
        }
    }

    /** Shows Shizuku's authorization prompt; the answer arrives through [statusChanges]. */
    suspend fun requestPermission() = withContext(Dispatchers.IO) {
        try {
            Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: RuntimeException) {
            Log.e(TAG, "Shizuku permission request failed", e)
        }
    }

    /** Launch intent for the Shizuku manager app, or null when it is not installed. */
    suspend fun managerLaunchIntent(): Intent? = withContext(Dispatchers.IO) {
        appContext.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /**
     * Runs [command] (no shell) with Shizuku's identity. Returns the exit code, or null when
     * the privileged service could not be reached. Requires [ShizukuStatus.READY].
     */
    suspend fun exec(vararg command: String): Int? = withContext(Dispatchers.IO) {
        val args = userServiceArgs()
        var connection: ServiceConnection? = null
        try {
            val binder = withTimeoutOrNull(BIND_TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    val conn = object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                            if (cont.isActive) cont.resume(service?.takeIf { it.pingBinder() })
                        }

                        override fun onServiceDisconnected(name: ComponentName?) = Unit
                    }
                    connection = conn
                    Shizuku.bindUserService(args, conn)
                }
            }
            if (binder == null) {
                Log.e(TAG, "Privileged service did not connect")
                return@withContext null
            }
            IPrivilegedService.Stub.asInterface(binder).exec(arrayOf(*command))
        } catch (e: CancellationException) {
            throw e
        } catch (e: RemoteException) {
            Log.e(TAG, "Privileged service call failed", e)
            null
        } catch (e: RuntimeException) {
            Log.e(TAG, "Shizuku user service bind failed", e)
            null
        } finally {
            connection?.let { conn ->
                try {
                    Shizuku.unbindUserService(args, conn, true)
                } catch (e: RuntimeException) {
                    Log.e(TAG, "Shizuku user service unbind failed", e)
                }
            }
        }
    }

    private fun userServiceArgs() = Shizuku.UserServiceArgs(
        ComponentName(appContext.packageName, PrivilegedUserService::class.java.name)
    )
        .daemon(false)
        .processNameSuffix("privileged")
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)

    private companion object {
        const val TAG = "ShizukuRepository"
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        const val PERMISSION_REQUEST_CODE = 4210
        const val BIND_TIMEOUT_MS = 10_000L
    }
}
