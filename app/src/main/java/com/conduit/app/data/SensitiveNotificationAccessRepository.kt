package com.conduit.app.data

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import com.conduit.app.shizuku.ShizukuRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class SensitiveAccessState {
    /** Android 14 and below never redact notification content for listeners. */
    NOT_REQUIRED,
    GRANTED,
    NOT_GRANTED,
    /** The app-op could not be read on this build; treated as not granted in the UI. */
    UNKNOWN
}

sealed interface SensitiveGrantResult {
    data object Granted : SensitiveGrantResult
    data object ServiceUnavailable : SensitiveGrantResult
    data class CommandFailed(val exitCode: Int) : SensitiveGrantResult
}

/**
 * Reads and grants Android 15+'s RECEIVE_SENSITIVE_NOTIFICATIONS app-op, which stops the
 * system from redacting OTPs and other sensitive content before it reaches Conduit's listener.
 */
class SensitiveNotificationAccessRepository(
    context: Context,
    private val shizuku: ShizukuRepository
) {
    private val appContext = context.applicationContext

    suspend fun accessState(): SensitiveAccessState = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < ANDROID_15) return@withContext SensitiveAccessState.NOT_REQUIRED
        val appOps = appContext.getSystemService(AppOpsManager::class.java)
        try {
            val mode = appOps.unsafeCheckOpNoThrow(OPSTR_RECEIVE_SENSITIVE_NOTIFICATIONS, Process.myUid(), appContext.packageName)
            if (mode == AppOpsManager.MODE_ALLOWED) SensitiveAccessState.GRANTED else SensitiveAccessState.NOT_GRANTED
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Sensitive notification app-op is unknown on this build", e)
            SensitiveAccessState.UNKNOWN
        }
    }

    suspend fun grantWithShizuku(): SensitiveGrantResult {
        val userId = Process.myUid() / PER_USER_RANGE
        val exitCode = shizuku.exec(
            "appops", "set", "--user", userId.toString(), appContext.packageName, APPOP_NAME, "allow"
        ) ?: return SensitiveGrantResult.ServiceUnavailable
        return if (exitCode == 0) SensitiveGrantResult.Granted else SensitiveGrantResult.CommandFailed(exitCode)
    }

    private companion object {
        const val TAG = "SensitiveNotifAccess"
        const val ANDROID_15 = 35
        const val PER_USER_RANGE = 100_000
        const val APPOP_NAME = "RECEIVE_SENSITIVE_NOTIFICATIONS"
        const val OPSTR_RECEIVE_SENSITIVE_NOTIFICATIONS = "android:receive_sensitive_notifications"
    }
}
