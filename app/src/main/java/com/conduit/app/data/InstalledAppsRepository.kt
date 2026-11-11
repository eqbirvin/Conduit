package com.conduit.app.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import com.conduit.app.AppIconBitmapCache
import com.conduit.app.HubNotificationListenerService
import com.conduit.app.representativePackageCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

/**
 * Immutable view of installed state and representative package for every
 * [HubNotificationListenerService.supportedApps] key, published for composition.
 * [ready] is false until the first off-main-thread resolution has finished.
 */
@Immutable
data class InstalledAppsSnapshot(
    val ready: Boolean = false,
    val installed: Map<String, Boolean> = emptyMap(),
    val representative: Map<String, String> = emptyMap()
) {
    fun isInstalled(packageName: String): Boolean = installed[packageName] == true

    /** Packages outside supportedApps are their own representative, exactly as getRepresentativePackage. */
    fun representativeOf(packageName: String): String = representative[packageName] ?: packageName
}

/**
 * Process-lifetime cache of package installed state (positive AND negative results) and the
 * representative package per supported app. The cache is trusted only while a watcher keeps the
 * package receivers registered (see [keepFresh]); outside that window lookups fall back to the
 * live PackageManager/LauncherApps query, as before the cache existed.
 */
object InstalledAppsRepository {
    private const val TAG = "InstalledAppsRepo"
    private const val REFRESH_DEBOUNCE_MS = 300L

    private val installedCache = ConcurrentHashMap<String, Boolean>()

    private val lock = Any()
    private var watchers = 0
    private var packageReceiver: BroadcastReceiver? = null
    private var profileReceiver: BroadcastReceiver? = null
    private var launcherCallback: LauncherApps.Callback? = null

    @Volatile
    private var trusted = false

    private val _snapshot = MutableStateFlow(InstalledAppsSnapshot())
    val snapshot: StateFlow<InstalledAppsSnapshot> = _snapshot.asStateFlow()

    private val refreshRequests = MutableStateFlow(0L)

    /** Cached installed state; resolves (and caches while trusted) on a miss. Blocking: call off the main thread on a miss. */
    fun isInstalled(context: Context, packageName: String): Boolean {
        if (trusted) {
            installedCache[packageName]?.let { return it }
        }
        val result = queryInstalled(context, packageName)
        if (trusted) installedCache[packageName] = result
        return result
    }

    /**
     * In-memory only: the representative package if already known, null if resolving it would need IPC.
     * Safe to call from composition.
     */
    fun cachedRepresentative(packageName: String): String? =
        representativePackageCache[packageName]
            ?: if (HubNotificationListenerService.supportedApps[packageName] == null) packageName else null

    /**
     * Registers the package receivers, resolves every supportedApps key on [Dispatchers.IO], and re-resolves
     * (debounced) whenever a relevant package or profile changes. Runs until the calling scope is cancelled,
     * then unregisters. Intended to be launched from an owned scope (viewModelScope).
     */
    suspend fun keepFresh(context: Context) {
        val app = context.applicationContext
        withContext(Dispatchers.IO) {
            startWatching(app)
            try {
                val initialRequest = refreshRequests.value
                refreshRequests.collectLatest { request ->
                    if (request != initialRequest) delay(REFRESH_DEBOUNCE_MS)
                    refresh(app)
                }
            } finally {
                withContext(NonCancellable) { stopWatching(app) }
            }
        }
    }

    private suspend fun refresh(context: Context) {
        val supportedApps = HubNotificationListenerService.supportedApps
        val installed = HashMap<String, Boolean>(supportedApps.size)
        for (pkg in supportedApps.keys) {
            coroutineContext.ensureActive()
            installed[pkg] = queryInstalled(context, pkg)
        }

        val pm = context.packageManager
        val launchable = HashMap<String, Boolean>()
        val representative = HashMap<String, String>(supportedApps.size)
        for ((pkg, channel) in supportedApps) {
            coroutineContext.ensureActive()
            val channelKey = channel.first
            representative[pkg] = supportedApps.entries
                .filter { it.value.first == channelKey }
                .map { it.key }
                .firstOrNull {
                    installed[it] == true &&
                        launchable.getOrPut(it) { pm.getLaunchIntentForPackage(it) != null }
                } ?: pkg
        }

        coroutineContext.ensureActive()
        if (!trusted) return
        // Installed state and representativePackageCache are replaced in the same step, so a reader never
        // sees a fresh installed state paired with a stale representative (or a cold cache in between).
        installedCache.putAll(installed)
        representativePackageCache.putAll(representative)
        _snapshot.value = InstalledAppsSnapshot(ready = true, installed = installed, representative = representative)
    }

    private fun startWatching(app: Context) {
        synchronized(lock) {
            watchers++
            if (watchers > 1) return
            installedCache.clear()
            trusted = true
            AppIconBitmapCache.setEnabled(true)

            val pkgReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action == Intent.ACTION_PACKAGE_REMOVED &&
                        intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                    ) return // an ACTION_PACKAGE_ADDED for the same package follows
                    onPackageEvent(intent.data?.schemeSpecificPart)
                }
            }
            val pkgFilter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            }
            ContextCompat.registerReceiver(app, pkgReceiver, pkgFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
            packageReceiver = pkgReceiver

            // Work/managed profiles appearing, disappearing or being paused change LauncherApps results.
            val profReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    onPackageEvent(null)
                }
            }
            val profFilter = IntentFilter().apply {
                addAction(Intent.ACTION_MANAGED_PROFILE_ADDED)
                addAction(Intent.ACTION_MANAGED_PROFILE_REMOVED)
                addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
                addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
            }
            ContextCompat.registerReceiver(app, profReceiver, profFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
            profileReceiver = profReceiver

            // Package broadcasts only cover this user; LauncherApps reports changes in work profiles too.
            val callback = object : LauncherApps.Callback() {
                override fun onPackageRemoved(packageName: String, user: UserHandle) = onPackageEvent(packageName)
                override fun onPackageAdded(packageName: String, user: UserHandle) = onPackageEvent(packageName)
                override fun onPackageChanged(packageName: String, user: UserHandle) = onPackageEvent(packageName)
                override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
                    packageNames.forEach { onPackageEvent(it) }
                }
                override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
                    packageNames.forEach { onPackageEvent(it) }
                }
            }
            val launcherApps = app.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
            launcherCallback = callback
        }
    }

    private fun stopWatching(app: Context) {
        synchronized(lock) {
            if (watchers == 0) return
            watchers--
            if (watchers > 0) return

            trusted = false
            installedCache.clear()
            AppIconBitmapCache.setEnabled(false)
            _snapshot.value = InstalledAppsSnapshot()

            listOfNotNull(packageReceiver, profileReceiver).forEach { receiver ->
                try {
                    app.unregisterReceiver(receiver)
                } catch (e: IllegalArgumentException) {
                    Log.e(TAG, "Package receiver was not registered", e)
                }
            }
            packageReceiver = null
            profileReceiver = null
            launcherCallback?.let { callback ->
                val launcherApps = app.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                launcherApps.unregisterCallback(callback)
            }
            launcherCallback = null
        }
    }

    /** Cheap (map ops only); the expensive re-resolution is debounced in [keepFresh]. Null = everything may have changed. */
    private fun onPackageEvent(packageName: String?) {
        if (packageName == null) {
            installedCache.clear()
            AppIconBitmapCache.clear()
            refreshRequests.update { it + 1 }
            return
        }
        installedCache.remove(packageName)
        AppIconBitmapCache.invalidate(packageName)
        if (HubNotificationListenerService.supportedApps.containsKey(packageName)) {
            refreshRequests.update { it + 1 }
        }
    }

    /** The original uncached ProfileUtils.isPackageInstalled check, unchanged. */
    private fun queryInstalled(context: Context, packageName: String): Boolean {
        val pm = context.packageManager
        try {
            val appInfo = pm.getApplicationInfo(packageName, 0)
            if (appInfo.enabled) {
                return true
            }
        } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
            // Ignored
        } catch (e: Exception) {
            // Ignored
        }

        val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        for (user in userManager.userProfiles) {
            try {
                if (launcherApps.isPackageEnabled(packageName, user)) {
                    return true
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
        return false
    }
}
