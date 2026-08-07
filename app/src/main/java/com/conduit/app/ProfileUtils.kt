package com.conduit.app

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.UserHandle
import android.os.UserManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.util.concurrent.ConcurrentHashMap

val appLabelCache = ConcurrentHashMap<String, String>()
val appIconCache = android.util.LruCache<String, ImageBitmap>(50)
val representativePackageCache = ConcurrentHashMap<String, String>()

fun isPackageInstalled(context: Context, packageName: String): Boolean {
    val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
    val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    for (user in userManager.userProfiles) {
        try {
            if (launcherApps.isPackageEnabled(packageName, user)) {
                return true
            }
        } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
            android.util.Log.e("ProfileUtils", "Package not found", e)
        } catch (e: IllegalArgumentException) {
            android.util.Log.e("ProfileUtils", "Invalid argument", e)
        }
    }
    return false
}

fun launchApp(context: Context, packageName: String): Boolean {
    val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
    val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    for (user in userManager.userProfiles) {
        try {
            val activities = launcherApps.getActivityList(packageName, user)
            if (activities.isNotEmpty()) {
                launcherApps.startMainActivity(activities[0].componentName, user, null, null)
                return true
            }
        } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
            android.util.Log.e("ProfileUtils", "Package not found", e)
        } catch (e: SecurityException) {
            android.util.Log.e("ProfileUtils", "Security exception", e)
        } catch (e: android.content.ActivityNotFoundException) {
            android.util.Log.e("ProfileUtils", "Activity not found", e)
        }
    }
    return false
}

fun getAppLabel(context: Context, packageName: String, fallback: String = packageName): String {
    val cached = appLabelCache[packageName]
    if (cached != null) return cached

    val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
    val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    for (user in userManager.userProfiles) {
        try {
            val activities = launcherApps.getActivityList(packageName, user)
            if (activities.isNotEmpty()) {
                val label = activities[0].label?.toString()
                if (!label.isNullOrEmpty()) {
                    appLabelCache[packageName] = label
                    return label
                }
            }
        } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
            android.util.Log.e("ProfileUtils", "Package not found", e)
        } catch (e: SecurityException) {
            android.util.Log.e("ProfileUtils", "Security exception", e)
        }
    }

    // Fallback
    try {
        val pm = context.packageManager
        val ai = pm.getApplicationInfo(packageName, 0)
        val label = pm.getApplicationLabel(ai).toString()
        appLabelCache[packageName] = label
        return label
    } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
        android.util.Log.e("ProfileUtils", "Package not found in fallback", e)
    } catch (e: SecurityException) {
        android.util.Log.e("ProfileUtils", "Security exception in fallback", e)
    }

    return fallback
}

fun getAppIcon(context: Context, packageName: String): Drawable? {
    val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
    val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    for (user in userManager.userProfiles) {
        try {
            val activities = launcherApps.getActivityList(packageName, user)
            if (activities.isNotEmpty()) {
                // Returns the badged icon (with briefcase badge for work profile apps)
                return activities[0].getBadgedIcon(0)
            }
        } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
            android.util.Log.e("ProfileUtils", "Package not found", e)
        } catch (e: SecurityException) {
            android.util.Log.e("ProfileUtils", "Security exception", e)
        }
    }

    // Fallback
    try {
        return context.packageManager.getApplicationIcon(packageName)
    } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
        android.util.Log.e("ProfileUtils", "Package not found in fallback", e)
    } catch (e: SecurityException) {
        android.util.Log.e("ProfileUtils", "Security exception in fallback", e)
    }

    return null
}

fun getInstalledChannels(context: Context): List<Pair<String, String>> {
    val pm = context.packageManager
    val installedChannelKeys = mutableSetOf<String>()
    HubNotificationListenerService.supportedApps.forEach { (pkg, pair) ->
        val prefKey = pair.first
        if (isPackageInstalled(context, pkg)) {
            installedChannelKeys.add(prefKey)
        }
    }

    val channelsToShow = HubNotificationListenerService.supportedApps.values
        .distinctBy { it.first }
        .filter { installedChannelKeys.contains(it.first) }
        
    return channelsToShow
}

fun getInstalledApps(context: Context): List<Pair<String, String>> {
    val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
    val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    val appsMap = mutableMapOf<String, String>()

    for (user in userManager.userProfiles) {
        try {
            val activities = launcherApps.getActivityList(null, user)
            for (activity in activities) {
                val pkg = activity.applicationInfo.packageName
                val label = activity.label?.toString() ?: pkg
                appsMap[pkg] = label
            }
        } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
            android.util.Log.e("ProfileUtils", "Package not found", e)
        } catch (e: SecurityException) {
            android.util.Log.e("ProfileUtils", "Security exception", e)
        }
    }

    return appsMap.toList().sortedBy { it.second }
}

fun getRepresentativePackage(context: Context, packageName: String): String {
    val cached = representativePackageCache[packageName]
    if (cached != null) return cached

    val channelKey = HubNotificationListenerService.supportedApps[packageName]?.first
    val result = if (channelKey != null) {
        val rep = HubNotificationListenerService.supportedApps.entries
            .filter { it.value.first == channelKey }
            .map { it.key }
            .firstOrNull { isPackageInstalled(context, it) }
        rep ?: packageName
    } else {
        packageName
    }
    
    representativePackageCache[packageName] = result
    return result
}

