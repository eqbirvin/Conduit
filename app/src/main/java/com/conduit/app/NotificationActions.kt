package com.conduit.app

import android.app.NotificationManager
import android.app.RemoteInput
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.toBitmap
import com.conduit.app.data.AppDatabase
import com.conduit.app.data.HubNotification
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun performHapticTick(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        manager.defaultVibrator
    } else {
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
    } else {
        vibrator.vibrate(10)
    }
}

fun performHapticClick(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        manager.defaultVibrator
    } else {
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
    } else {
        vibrator.vibrate(20)
    }
}

fun sendReply(context: Context, notification: HubNotification, text: String, action: android.app.Notification.Action) {
    val remoteInput = action.remoteInputs?.firstOrNull() ?: return
    val results = Bundle().apply {
        putString(remoteInput.resultKey, text)
    }
    val fillInIntent = Intent().apply {
        addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
    }
    RemoteInput.addResultsToIntent(action.remoteInputs, fillInIntent, results)
    
    try {
        action.actionIntent.send(context, 0, fillInIntent)
        performHapticClick(context)
        // Auto-archive
        GlobalScope.launch {
            val database = AppDatabase.getDatabase(context)
            database.notificationDao().archiveNotification(notification.id, System.currentTimeMillis())
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun triggerNotificationAction(context: Context, notification: HubNotification, action: android.app.Notification.Action) {
    try {
        action.actionIntent.send()
        performHapticClick(context)
        
        val title = action.title?.toString()?.lowercase() ?: ""
        val autoArchiveKeywords = listOf("read", "archive", "delete", "dismiss", "seen", "done", "clear")
        if (autoArchiveKeywords.any { title.contains(it) }) {
            GlobalScope.launch {
                val database = AppDatabase.getDatabase(context)
                database.notificationDao().archiveNotification(notification.id, System.currentTimeMillis())
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

data class FabAction(
    val id: String,
    val label: String,
    val iconName: String,
    val type: String, // "APP", "MENU", "SYSTEM"
    val target: String
)

val DEFAULT_FABS = listOf(
    FabAction("1", "Recorder", "PlayArrow", "MENU", "RECORDER"),
    FabAction("2", "AI", "Star", "MENU", "AI"),
    FabAction("3", "Notes", "List", "MENU", "NOTES"),
    FabAction("4", "Compose", "Edit", "MENU", "COMPOSE")
)

fun getFabIcon(name: String): ImageVector {
    return when (name) {
        "PlayArrow" -> Icons.Filled.PlayArrow
        "Star" -> Icons.Filled.Star
        "List" -> Icons.Filled.List
        "Edit" -> Icons.Filled.Edit
        "DoneAll" -> Icons.Filled.DoneAll
        "Archive" -> Icons.Filled.Archive
        "Search" -> Icons.Filled.Search
        "Email" -> Icons.Filled.Email
        "PushPin" -> Icons.Filled.PushPin
        else -> Icons.Filled.Star
    }
}

// Rule 12: Hoist SimpleDateFormat instances to top-level vals
private val dateHeaderFormatter = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
private val timeFormatter = SimpleDateFormat("h:mm a", Locale.getDefault())
private val timestampWithDateFormatter = SimpleDateFormat("M/d · h:mm a", Locale.getDefault())

fun formatDateHeader(timestamp: Long): String {
    return dateHeaderFormatter.format(Date(timestamp)).uppercase()
}

fun formatTimestamp(timestamp: Long): String {
    return timeFormatter.format(Date(timestamp))
}

fun formatTimestampWithDate(timestamp: Long): String {
    return timestampWithDateFormatter.format(Date(timestamp))
}

fun postPinnedNotification(context: Context, id: Int, title: String, text: String, pkg: String) {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channelId = "conduit_pinned"
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = android.app.NotificationChannel(
            channelId,
            "Pinned Notifications",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Persistent notifications pinned from Conduit"
        }
        notificationManager.createNotificationChannel(channel)
    }

    val builder = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(title)
        .setContentText(text)
        .setOngoing(true)
        .setAutoCancel(false)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        
    try {
        val drawable = context.packageManager.getApplicationIcon(pkg)
        builder.setLargeIcon(drawable.toBitmap())
    } catch (e: Exception) {
        // Fallback to no large icon
    }
    
    notificationManager.notify(id, builder.build())
}

fun changeAppIcon(context: Context, iconName: String) {
    val pm = context.packageManager
    val packageName = context.packageName

    val aliases = mapOf(
        "LEGACY" to "$packageName.MainActivityLegacy",
        "MANILA" to "$packageName.MainActivityManila",
        "DARK" to "$packageName.MainActivityDark",
        "BLUE" to "$packageName.MainActivityBlue"
    )

    val activeAlias = aliases[iconName.uppercase(Locale.ROOT)] ?: return
    
    // Enable target alias first
    try {
        pm.setComponentEnabledSetting(
            ComponentName(context, activeAlias),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
    } catch (e: Exception) {
        e.printStackTrace()
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
                e.printStackTrace()
            }
        }
    }
}

fun compareVersions(v1: String, v2: String): Int {
    val parts1 = v1.split(".").mapNotNull { it.toIntOrNull() }
    val parts2 = v2.split(".").mapNotNull { it.toIntOrNull() }
    val maxLen = maxOf(parts1.size, parts2.size)
    for (i in 0 until maxLen) {
        val p1 = parts1.getOrElse(i) { 0 }
        val p2 = parts2.getOrElse(i) { 0 }
        if (p1 != p2) {
            return p1.compareTo(p2)
        }
    }
    return 0
}

fun isVersionNewer(newVersion: String, oldVersion: String): Boolean {
    return compareVersions(newVersion, oldVersion) > 0
}
