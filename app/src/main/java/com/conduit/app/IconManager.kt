package com.conduit.app

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

private const val TAG = "IconManager"

fun changeAppIcon(context: Context, iconName: String) {
    val pm = context.packageManager
    val packageName = context.packageName

    val aliases = mapOf(
        "LEGACY" to "$packageName.MainActivityLegacy",
        "MANILA" to "$packageName.MainActivityManila",
        "DARK" to "$packageName.MainActivityDark",
        "BLUE" to "$packageName.MainActivityBlue"
    )

    val activeAlias = aliases[iconName.uppercase(java.util.Locale.ROOT)] ?: return
    
    // Enable target alias first
    try {
        pm.setComponentEnabledSetting(
            ComponentName(context, activeAlias),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
    } catch (e: Exception) {
        Log.e(TAG, "Failed to enable active app icon alias $activeAlias", e)
    }

    // Disable all other aliases
    aliases.values.forEach { aliasName ->
        if (aliasName != activeAlias) {
            try {
                pm.setComponentEnabledSetting(
                    ComponentName(context, aliasName),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to disable app icon alias $aliasName", e)
            }
        }
    }
}
