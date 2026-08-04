package com.conduit.app

import android.content.ComponentName
import android.content.Intent
import android.content.Context
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.IntOffset
import androidx.compose.material.icons.Icons
import androidx.compose.animation.*
import android.app.Notification
import android.app.RemoteInput
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Settings as SettingsIcon
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.conduit.app.data.AppDatabase
import com.conduit.app.data.HubNotification
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.os.Build
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import android.os.Vibrator
import android.os.VibratorManager
import android.os.VibrationEffect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

import androidx.activity.enableEdgeToEdge

enum class Screen { HOME, SETTINGS, ARCHIVE, DEV_SETTINGS }

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
        kotlinx.coroutines.GlobalScope.launch {
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
            kotlinx.coroutines.GlobalScope.launch {
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

fun getFabIcon(name: String): androidx.compose.ui.graphics.vector.ImageVector {
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

// Caches are now imported from ProfileUtils.kt

class MainActivity : ComponentActivity() {
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val prefs = remember { context.getSharedPreferences("conduit_prefs", Context.MODE_PRIVATE) }
            var showWhatsNewDialog by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                val currentVersion = "2.02.06"
                val lastRun = prefs.getString("last_run_version", "") ?: ""
                if (lastRun != currentVersion) {
                    showWhatsNewDialog = true
                }
            }
            var themePreference by remember { mutableIntStateOf(prefs.getInt("theme", 0)) }
            var jacobMonochrome by remember { mutableStateOf(prefs.getBoolean("jacob_monochrome", false)) }
            var groupByChannel by remember { mutableStateOf(prefs.getBoolean("group_by_channel", false)) }
            var persistentTrayEnabled by remember { mutableStateOf(prefs.getBoolean("persistent_tray_enabled", false)) }
            val channelStates = remember { mutableStateMapOf<String, Boolean>() }
            LaunchedEffect(Unit) {
                HubNotificationListenerService.supportedApps.values.forEach { (prefKey, _) ->
                    channelStates[prefKey] = prefs.getBoolean(prefKey, true)
                }
            }
            var syncDismissal by remember { mutableStateOf(prefs.getBoolean("sync_dismissal", true)) }
            var showActionChips by remember { mutableStateOf(prefs.getBoolean("show_action_chips", true)) }
            var syncPinned by remember { mutableStateOf(prefs.getBoolean("sync_pinned", false)) }
            var dockLongPressLaunch by remember { mutableStateOf(prefs.getBoolean("dock_long_press_launch", true)) }
            var swipeLeftAction by remember { mutableStateOf(prefs.getString("swipe_left_action", "SNOOZE") ?: "SNOOZE") }
            var swipeRightAction by remember { mutableStateOf(prefs.getString("swipe_right_action", "ARCHIVE") ?: "ARCHIVE") }
            var dockSizeIndex by remember { mutableIntStateOf(prefs.getInt("dock_size", 1)) }
            var enableBubbles by remember { mutableStateOf(prefs.getBoolean("enable_bubbles", false)) }
            var enableBracket by remember { mutableStateOf(prefs.getBoolean("enable_bracket", false)) }
            var bracketNotificationPopup by remember { mutableStateOf(prefs.getBoolean("bracket_notification_popup", true)) }
            var bracketHangerEnabled by remember { mutableStateOf(prefs.getBoolean("bracket_hanger_enabled", true)) }
            var bracketVerticalPosition by remember { mutableFloatStateOf(prefs.getFloat("bracket_vertical_position", 0.5f)) }
            var unifiedView by remember { mutableStateOf(prefs.getBoolean("unified_view", true)) }
            var activeAppIcon by remember { mutableStateOf(prefs.getString("active_app_icon", "MANILA") ?: "MANILA") }
            var smartMarkRead by remember { mutableStateOf(prefs.getBoolean("smart_mark_read", true)) }
            var smartMarkReadTarget by remember { mutableStateOf(prefs.getString("smart_mark_read_target", "widget_only") ?: "widget_only") }

            
            var fabConfigsJson = prefs.getString("fab_configs", null)
            var fabConfigs by remember { 
                mutableStateOf(
                    if (fabConfigsJson != null) {
                        try {
                            fabConfigsJson.split("|").map { 
                                val parts = it.split(",")
                                FabAction(parts[0], parts[1], parts[2], parts[3], parts[4])
                            }
                        } catch (e: Exception) { DEFAULT_FABS }
                    } else { DEFAULT_FABS }
                )
            }
            
            var aiBundle by remember { mutableStateOf(prefs.getString("ai_bundle", "com.anthropic.claude,com.google.android.apps.bard")?.split(",")?.filter { it.isNotEmpty() } ?: listOf("com.anthropic.claude", "com.google.android.apps.bard")) }
            var notesBundle by remember { mutableStateOf(prefs.getString("notes_bundle", "com.google.android.keep,com.notion.id")?.split(",")?.filter { it.isNotEmpty() } ?: listOf("com.google.android.keep", "com.notion.id")) }
            var recorderBundle by remember { mutableStateOf(prefs.getString("recorder_bundle", "com.google.android.apps.recorder")?.split(",")?.filter { it.isNotEmpty() } ?: listOf("com.google.android.apps.recorder")) }
            var composeBundle by remember { mutableStateOf(prefs.getString("compose_bundle", "com.google.android.apps.messaging,com.google.android.gm")?.split(",")?.filter { it.isNotEmpty() } ?: listOf("com.google.android.apps.messaging", "com.google.android.gm")) }

            fun saveBundle(key: String, list: List<String>) {
                prefs.edit().putString(key, list.joinToString(",")).apply()
                when (key) {
                    "ai_bundle" -> aiBundle = list
                    "notes_bundle" -> notesBundle = list
                    "recorder_bundle" -> recorderBundle = list
                    "compose_bundle" -> composeBundle = list
                }
            }
            
            fun saveFabConfigs(configs: List<FabAction>) {
                val json = configs.joinToString("|") { "${it.id},${it.label},${it.iconName},${it.type},${it.target}" }
                prefs.edit().putString("fab_configs", json).apply()
                fabConfigs = configs
            }
            
            var currentScreen by remember { mutableStateOf(Screen.HOME) }
            var isPermissionGranted by remember { mutableStateOf(isNotificationServiceEnabled()) }

            if (currentScreen != Screen.HOME) {
                BackHandler {
                    currentScreen = if (currentScreen == Screen.DEV_SETTINGS) Screen.SETTINGS else Screen.HOME
                }
            }

            // Observe notifications from Room
            val database = remember { AppDatabase.getDatabase(context) }
            val notifications by database.notificationDao().getAllNotifications().collectAsState(initial = emptyList())
            val archivedNotifications by database.notificationDao().getArchivedNotifications().collectAsState(initial = emptyList())
            val coroutineScope = rememberCoroutineScope()
            
            val filteredNotifications = remember(notifications, channelStates.toMap()) {
                notifications.filter {
                    val prefKey = HubNotificationListenerService.supportedApps.values.find { appInfo -> appInfo.second == it.channel }?.first
                    if (prefKey != null) {
                        channelStates[prefKey] ?: true
                    } else {
                        true
                    }
                }
            }

            val filteredArchivedNotifications = remember(archivedNotifications, channelStates.toMap()) {
                archivedNotifications.filter {
                    val prefKey = HubNotificationListenerService.supportedApps.values.find { appInfo -> appInfo.second == it.channel }?.first
                    if (prefKey != null) {
                        channelStates[prefKey] ?: true
                    } else {
                        true
                    }
                }
            }

            ConduitTheme(themePreference = themePreference, jacobMonochrome = jacobMonochrome) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (!isPermissionGranted) {
                        PermissionScreen(
                            onGrantClick = {
                                val intent = Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                context.startActivity(intent)
                            },
                            onCheckAgainClick = {
                                isPermissionGranted = isNotificationServiceEnabled()
                            }
                        )
                    } else {
                        when (currentScreen) {
                            Screen.HOME -> HubScreen(
                                channelStates = channelStates.toMap(),
                                notifications = filteredNotifications,
                                archivedNotifications = filteredArchivedNotifications,
                                fabConfigs = fabConfigs,
                                onSaveFabConfigs = { saveFabConfigs(it) },
                                aiBundle = aiBundle,
                                notesBundle = notesBundle,
                                recorderBundle = recorderBundle,
                                composeBundle = composeBundle,
                                unifiedView = unifiedView,
                                onUnifiedViewChanged = {
                                    unifiedView = it
                                    prefs.edit().putBoolean("unified_view", it).apply()
                                },
                                onNavigateToSettings = { currentScreen = Screen.SETTINGS },
                                onNavigateToArchive = { currentScreen = Screen.ARCHIVE },
                                onArchiveNotification = { id, timestamp ->
                                    kotlinx.coroutines.GlobalScope.launch {
                                        database.notificationDao().archiveNotification(id, timestamp)
                                        com.conduit.app.widget.ConduitWidgetProvider.updateAllWidgets(context)
                                    }
                                },
                                onSnoozeNotification = { id, timestamp ->
                                    kotlinx.coroutines.GlobalScope.launch {
                                        database.notificationDao().snoozeNotification(id, timestamp)
                                        com.conduit.app.widget.ConduitWidgetProvider.updateAllWidgets(context)
                                    }
                                },
                                onPinNotification = { notification ->
                                    kotlinx.coroutines.GlobalScope.launch {
                                        val isCurrentlyPinned = notification.isPinned
                                        database.notificationDao().togglePin(notification.id)
                                        com.conduit.app.widget.ConduitWidgetProvider.updateAllWidgets(context)
                                        if (prefs.getBoolean("sync_pinned", false)) {
                                            if (!isCurrentlyPinned) {
                                                HubNotificationListenerService.instance?.cancelNotification(notification.notificationKey)
                                                postPinnedNotification(context, notification.id, notification.title ?: "", notification.text ?: "", notification.packageName)
                                            } else {
                                                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                                                notificationManager.cancel(notification.id)
                                            }
                                        }
                                    }
                                },
                                showActionChips = showActionChips,
                                dockSizeIndex = dockSizeIndex
                            )
                            Screen.ARCHIVE -> ArchiveScreen(
                                archivedNotifications = filteredArchivedNotifications,
                                showActionChips = showActionChips,
                                onNavigateBack = { currentScreen = Screen.HOME }
                            )
                            Screen.SETTINGS -> SettingsScreen(
                                themePreference = themePreference,
                                onThemeChanged = { newTheme ->
                                    themePreference = newTheme
                                    prefs.edit().putInt("theme", newTheme).apply()
                                },
                                jacobMonochrome = jacobMonochrome,
                                onJacobMonochromeChanged = { 
                                    jacobMonochrome = it
                                    prefs.edit().putBoolean("jacob_monochrome", it).apply()
                                },
                                groupByChannel = groupByChannel,
                                onGroupByChannelChanged = {
                                    groupByChannel = it
                                    prefs.edit().putBoolean("group_by_channel", it).apply()
                                },
                                channelStates = channelStates,
                                onChannelToggled = { prefKey, isEnabled ->
                                    channelStates[prefKey] = isEnabled
                                    prefs.edit().putBoolean(prefKey, isEnabled).apply()
                                },
                                syncDismissal = syncDismissal,
                                onSyncDismissalChanged = { syncDismissal = it; prefs.edit().putBoolean("sync_dismissal", it).apply() },
                                syncPinned = syncPinned,
                                onSyncPinnedChanged = { syncPinned = it; prefs.edit().putBoolean("sync_pinned", it).apply() },
                                showActionChips = showActionChips,
                                onShowActionChipsChanged = { showActionChips = it; prefs.edit().putBoolean("show_action_chips", it).apply() },
                                aiBundle = aiBundle,
                                onAiBundleChanged = { saveBundle("ai_bundle", it) },
                                notesBundle = notesBundle,
                                onNotesBundleChanged = { saveBundle("notes_bundle", it) },
                                recorderBundle = recorderBundle,
                                onRecorderBundleChanged = { saveBundle("recorder_bundle", it) },
                                composeBundle = composeBundle,
                                onComposeBundleChanged = { saveBundle("compose_bundle", it) },
                                dockLongPressLaunch = dockLongPressLaunch,
                                onDockLongPressLaunchChanged = { 
                                    dockLongPressLaunch = it
                                    prefs.edit().putBoolean("dock_long_press_launch", it).apply()
                                },
                                swipeLeftAction = swipeLeftAction,
                                onSwipeLeftActionChanged = {
                                    swipeLeftAction = it
                                    prefs.edit().putString("swipe_left_action", it).apply()
                                },
                                swipeRightAction = swipeRightAction,
                                onSwipeRightActionChanged = {
                                    swipeRightAction = it
                                    prefs.edit().putString("swipe_right_action", it).apply()
                                },
                                dockSizeIndex = dockSizeIndex,
                                onDockSizeChanged = { newSize ->
                                    dockSizeIndex = newSize
                                    prefs.edit().putInt("dock_size", newSize).apply()
                                },
                                enableBracket = enableBracket,
                                onEnableBracketChanged = {
                                    enableBracket = it
                                    prefs.edit().putBoolean("enable_bracket", it).apply()
                                    if (it && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(this@MainActivity)) {
                                        val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName"))
                                        startActivity(intent)
                                    }
                                },
                                bracketNotificationPopup = bracketNotificationPopup,
                                onBracketNotificationPopupChanged = {
                                    bracketNotificationPopup = it
                                    prefs.edit().putBoolean("bracket_notification_popup", it).apply()
                                },
                                bracketHangerEnabled = bracketHangerEnabled,
                                onBracketHangerEnabledChanged = {
                                    bracketHangerEnabled = it
                                    prefs.edit().putBoolean("bracket_hanger_enabled", it).apply()
                                },
                                bracketVerticalPosition = bracketVerticalPosition,
                                onBracketVerticalPositionChanged = {
                                    bracketVerticalPosition = it
                                    prefs.edit().putFloat("bracket_vertical_position", it).apply()
                                },
                                unifiedView = unifiedView,
                                onUnifiedViewChanged = {
                                    unifiedView = it
                                    prefs.edit().putBoolean("unified_view", it).apply()
                                },
                                activeAppIcon = activeAppIcon,
                                onActiveAppIconChanged = { newIcon ->
                                    activeAppIcon = newIcon
                                    prefs.edit().putString("active_app_icon", newIcon).apply()
                                    changeAppIcon(context, newIcon)
                                },
                                smartMarkRead = smartMarkRead,
                                onSmartMarkReadChanged = {
                                    smartMarkRead = it
                                    prefs.edit().putBoolean("smart_mark_read", it).apply()
                                    com.conduit.app.widget.ConduitWidgetProvider.updateAllWidgets(context)
                                },
                                smartMarkReadTarget = smartMarkReadTarget,
                                onSmartMarkReadTargetChanged = {
                                    smartMarkReadTarget = it
                                    prefs.edit().putString("smart_mark_read_target", it).apply()
                                    com.conduit.app.widget.ConduitWidgetProvider.updateAllWidgets(context)
                                },
                                onShowWhatsNew = { showWhatsNewDialog = true },
                                onNavigateToDevSettings = { currentScreen = Screen.DEV_SETTINGS },
                                onNavigateBack = { currentScreen = Screen.HOME }
                            )
                            Screen.DEV_SETTINGS -> DevSettingsScreen(
                                persistentTrayEnabled = persistentTrayEnabled,
                                onPersistentTrayEnabledChanged = {
                                    persistentTrayEnabled = it
                                    prefs.edit().putBoolean("persistent_tray_enabled", it).apply()
                                    HubNotificationListenerService.instance?.updatePersistentNotification()
                                },
                                enableBubbles = enableBubbles,
                                onEnableBubblesChanged = {
                                    enableBubbles = it
                                    prefs.edit().putBoolean("enable_bubbles", it).apply()
                                },
                                onNavigateBack = { currentScreen = Screen.SETTINGS }
                            )
                        }
                    }
                    
                    if (showWhatsNewDialog) {
                        AlertDialog(
                            onDismissRequest = {
                                prefs.edit().putString("last_run_version", "2.02.06").apply()
                                showWhatsNewDialog = false
                            },
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("What's New in Conduit", fontWeight = FontWeight.Bold)
                                }
                            },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Text("Version 2.02.06 (Beta)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)
                                    
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Row(verticalAlignment = Alignment.Top) {
                                            Icon(
                                                imageVector = Icons.Default.Storage,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp).padding(top = 2.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text("Database Indices & Retention Policy", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                                Text("Added SQL database indices for notification key, package name, and archived status. Migration v6 to v7 preserves user history, plus automatic 90-day retention cleanup.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.Top) {
                                            Icon(
                                                imageVector = Icons.Default.Work,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp).padding(top = 2.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text("Work Profile Support", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                                Text("Conduit now dynamically scans and launches apps from your Android Work Profile. Managed apps display with briefcase badge overlays.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.Top) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp).padding(top = 2.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text("Dynamic System Tray Sync", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                                Text("Rebuilt status bar notification tray checks to dynamically filter against enabled channels, automatically supporting all new networks (Facebook, Teams, Messenger, Twitter).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        prefs.edit().putString("last_run_version", "2.02.06").apply()
                                        showWhatsNewDialog = false
                                    }
                                ) {
                                    Text("Got It")
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun isRunningInBubble(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (isLaunchedFromBubble) {
                return true
            }
        }
        return intent?.getBooleanExtra("from_bubble", false) == true
    }

    override fun onResume() {
        super.onResume()
        if (isRunningInBubble()) {
            return
        }
        val prefs = getSharedPreferences("conduit_prefs", Context.MODE_PRIVATE)
        val enableBubbles = prefs.getBoolean("enable_bubbles", false)
        if (enableBubbles) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.cancel(9999)
            val packages = HubNotificationListenerService.supportedApps.keys
            for (pkg in packages) {
                val shortcutId = "conduit_shortcut_$pkg"
                notificationManager.cancel(shortcutId.hashCode())
            }
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val cn = ComponentName(this, HubNotificationListenerService::class.java)
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(cn.flattenToString())
    }
}

@Composable
fun ConduitTheme(themePreference: Int, jacobMonochrome: Boolean = false, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val darkTheme = when (themePreference) {
        1 -> false // Light
        2 -> true  // Dark
        3 -> true  // Jacob Mode (AMOLED)
        else -> isSystemDark // System (0)
    }
    
    val dynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && themePreference != 3
    val colorScheme = when {
        themePreference == 3 -> {
            if (jacobMonochrome) {
                androidx.compose.material3.darkColorScheme(
                    primary = Color.White,
                    onPrimary = Color.Black,
                    primaryContainer = Color(0xFF1C1C1E),
                    onPrimaryContainer = Color.White,
                    secondary = Color.LightGray,
                    onSecondary = Color.Black,
                    secondaryContainer = Color(0xFF1C1C1E),
                    onSecondaryContainer = Color.White,
                    background = Color.Black,
                    onBackground = Color.White,
                    surface = Color.Black,
                    onSurface = Color.White,
                    surfaceVariant = Color.Black,
                    onSurfaceVariant = Color.White.copy(alpha = 0.7f),
                    outline = Color.White.copy(alpha = 0.5f),
                    outlineVariant = Color.White.copy(alpha = 0.2f)
                )
            } else {
                val base = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    androidx.compose.material3.dynamicDarkColorScheme(context)
                } else {
                    androidx.compose.material3.darkColorScheme()
                }
                base.copy(
                    background = Color.Black,
                    surface = Color.Black,
                    surfaceVariant = Color.Black
                )
            }
        }
        dynamicColor && darkTheme -> androidx.compose.material3.dynamicDarkColorScheme(context)
        dynamicColor && !darkTheme -> androidx.compose.material3.dynamicLightColorScheme(context)
        darkTheme -> androidx.compose.material3.darkColorScheme()
        else -> androidx.compose.material3.lightColorScheme()
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

@Composable
fun PermissionScreen(onGrantClick: () -> Unit, onCheckAgainClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Notification Access Required", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Conduit needs access to read your notifications to aggregate them here.", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onGrantClick) {
            Text("Grant Permission")
        }
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onCheckAgainClick) {
            Text("I've granted it, continue")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HubScreen(
    channelStates: Map<String, Boolean>,
    notifications: List<HubNotification>,
    archivedNotifications: List<HubNotification>,
    fabConfigs: List<FabAction>,
    onSaveFabConfigs: (List<FabAction>) -> Unit,
    aiBundle: List<String>,
    notesBundle: List<String>,
    recorderBundle: List<String>,
    composeBundle: List<String>,
    onNavigateToSettings: () -> Unit,
    onNavigateToArchive: () -> Unit,
    onArchiveNotification: (Int, Long) -> Unit,
    onSnoozeNotification: (Int, Long) -> Unit,
    onPinNotification: (HubNotification) -> Unit,
    showActionChips: Boolean,
    dockSizeIndex: Int,
    unifiedView: Boolean,
    onUnifiedViewChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var showBundleMenu by remember { mutableStateOf<Pair<String, List<String>>?>(null) }
    var showCustomizeFab by remember { mutableStateOf<FabAction?>(null) }
    
    val prefs = remember { context.getSharedPreferences("conduit_prefs", android.content.Context.MODE_PRIVATE) }
    var isFabExpanded by remember { mutableStateOf(prefs.getBoolean("fab_expanded", true)) }
    var isCompactMode by remember { mutableStateOf(prefs.getBoolean("compact_mode", false)) }
    
    var selectedDockPackage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(notifications, unifiedView) {
        if (!unifiedView && selectedDockPackage != null) {
            val hasActive = notifications.any { getRepresentativePackage(context, it.packageName) == selectedDockPackage }
            if (!hasActive) {
                selectedDockPackage = notifications.map { getRepresentativePackage(context, it.packageName) }.distinct().firstOrNull()
            }
        }
    }
    val swipeLeftAction = prefs.getString("swipe_left_action", "SNOOZE") ?: "SNOOZE"
    val swipeRightAction = prefs.getString("swipe_right_action", "ARCHIVE") ?: "ARCHIVE"
    var notificationToSnooze by remember { mutableStateOf<HubNotification?>(null) }

    val activeIntent = (context as? android.app.Activity)?.intent
    LaunchedEffect(activeIntent) {
        if (activeIntent?.action == "com.conduit.app.OPEN_FILTER") {
            val pkg = activeIntent.getStringExtra("FILTER_PACKAGE")
            if (pkg != null) {
                selectedDockPackage = pkg
            }
        }
    }

    var notificationToBlock by remember { mutableStateOf<HubNotification?>(null) }
    var selectedIds by remember { mutableStateOf(setOf<Int>()) }
    val isSelectionMode = selectedIds.isNotEmpty()
    var searchQuery by remember { mutableStateOf("") }
    var isSearchMode by remember { mutableStateOf(false) }

    // Clear selection or exit search on back press
    BackHandler(enabled = isSelectionMode || isSearchMode) {
        if (isSelectionMode) {
            selectedIds = emptySet()
        } else {
            isSearchMode = false
            searchQuery = ""
        }
    }
    val displayNotifications = remember(notifications, archivedNotifications, searchQuery, isSearchMode, selectedDockPackage, unifiedView) {
        if (isSearchMode && searchQuery.isNotEmpty()) {
            val combined = notifications + archivedNotifications
            combined.filter { notif ->
                notif.title.toString().contains(searchQuery, ignoreCase = true) ||
                notif.text.toString().contains(searchQuery, ignoreCase = true) ||
                notif.packageName.contains(searchQuery, ignoreCase = true)
            }.sortedByDescending { it.timestamp }
        } else if (selectedDockPackage != null) {
            val list = if (unifiedView) {
                (notifications + archivedNotifications).sortedByDescending { it.timestamp }
            } else {
                notifications
            }
            list.filter { getRepresentativePackage(context, it.packageName) == selectedDockPackage }
        } else {
            if (unifiedView) {
                (notifications + archivedNotifications).sortedByDescending { it.timestamp }
            } else {
                notifications
            }
        }
    }

    val actionsByKey = remember(displayNotifications, showActionChips) {
        if (!showActionChips) emptyMap()
        else displayNotifications.associate {
            it.notificationKey to HubNotificationListenerService.instance?.getNotificationActions(it.notificationKey)
        }
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text("${selectedIds.size} selected", fontSize = 20.sp, fontWeight = FontWeight.Medium) },
                    navigationIcon = {
                        IconButton(onClick = { selectedIds = emptySet() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            val now = System.currentTimeMillis()
                            val idsToArchive = selectedIds.toList()
                            kotlinx.coroutines.GlobalScope.launch {
                                val database = AppDatabase.getDatabase(context)
                                database.notificationDao().archiveNotifications(idsToArchive, now)
                                com.conduit.app.widget.ConduitWidgetProvider.updateAllWidgets(context)
                            }
                            performHapticClick(context)
                            selectedIds = emptySet()
                        }) {
                            Icon(Icons.Filled.Email, contentDescription = "Archive selected")
                        }
                        IconButton(onClick = {
                            val idsToPin = selectedIds.toList()
                            val anyUnpinned = notifications.filter { it.id in selectedIds }.any { !it.isPinned }
                            kotlinx.coroutines.GlobalScope.launch {
                                val database = AppDatabase.getDatabase(context)
                                database.notificationDao().pinNotifications(idsToPin, anyUnpinned)
                                com.conduit.app.widget.ConduitWidgetProvider.updateAllWidgets(context)
                            }
                            performHapticClick(context)
                            selectedIds = emptySet()
                        }) {
                            Icon(Icons.Filled.PushPin, contentDescription = "Pin/Unpin selected")
                        }
                        IconButton(onClick = {
                            val allAvailable = notifications + archivedNotifications
                            val selectedNotifs = allAvailable.filter { it.id in selectedIds }
                            if (selectedNotifs.isNotEmpty()) {
                                notificationToBlock = selectedNotifs.first()
                            }
                        }) {
                            Icon(Icons.Filled.Block, contentDescription = "Block similar notifications")
                        }
                        IconButton(onClick = {
                            val idsToProcess = selectedIds.toList()
                            val service = HubNotificationListenerService.instance
                            kotlinx.coroutines.GlobalScope.launch {
                                val database = AppDatabase.getDatabase(context)
                                idsToProcess.forEach { id ->
                                    val notif = notifications.find { it.id == id }
                                    if (notif != null && service != null) {
                                        val sbn = service.activeNotifications.find { it.key == notif.notificationKey }
                                        if (sbn != null) {
                                            val actions = sbn.notification.actions
                                            if (actions != null) {
                                                val readAction = actions.find { 
                                                    it.title.toString().contains("read", ignoreCase = true) ||
                                                    it.title.toString().contains("done", ignoreCase = true) ||
                                                    it.title.toString().contains("seen", ignoreCase = true)
                                                }
                                                if (readAction != null) {
                                                    try {
                                                        readAction.actionIntent.send()
                                                    } catch (e: Exception) {
                                                        e.printStackTrace()
                                                    }
                                                }
                                            }
                                        }
                                        database.notificationDao().archiveNotification(id, System.currentTimeMillis())
                                    }
                                }
                                com.conduit.app.widget.ConduitWidgetProvider.updateAllWidgets(context)
                            }
                            performHapticClick(context)
                            selectedIds = emptySet()
                        }) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = "Mark selected as read")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                    )
                )
            } else if (isSearchMode) {
                TopAppBar(
                    title = {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search notifications...") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            singleLine = true,
                            leadingIcon = {
                                IconButton(onClick = { 
                                    performHapticTick(context)
                                    isSearchMode = false
                                    searchQuery = ""
                                }) {
                                    Icon(Icons.Filled.ArrowBack, contentDescription = "Exit search")
                                }
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Filled.Close, contentDescription = "Clear search")
                                    }
                                }
                            }
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                    )
                )
            } else {
                CenterAlignedTopAppBar(
                    title = { Text("Conduit", fontSize = 22.sp, fontWeight = FontWeight.SemiBold) },
                    actions = {
                        IconButton(onClick = {
                            isCompactMode = !isCompactMode
                            prefs.edit().putBoolean("compact_mode", isCompactMode).apply()
                            performHapticClick(context)
                        }) {
                            Icon(
                                imageVector = if (isCompactMode) Icons.Filled.Expand else Icons.Filled.Compress,
                                contentDescription = "Toggle Compact Mode",
                                tint = if (isCompactMode) MaterialTheme.colorScheme.secondary else LocalContentColor.current
                            )
                        }

                        if (!unifiedView) {
                            IconButton(onClick = onNavigateToArchive) {
                                Icon(Icons.Filled.History, contentDescription = "Actioned")
                            }
                        }

                        IconButton(onClick = { 
                            onUnifiedViewChanged(!unifiedView)
                            performHapticTick(context)
                        }) {
                            Icon(
                                if (unifiedView) Icons.Filled.Checklist else Icons.Filled.DynamicFeed,
                                contentDescription = if (unifiedView) "Switch to Todo Mode" else "Switch to Unified View"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                    )
                )
            }
        },
        floatingActionButton = {
            if (!isSelectionMode && !isSearchMode) {
                val fabColumnBottomPadding = when (dockSizeIndex) {
                    0 -> 2.dp
                    2 -> 10.dp
                    else -> 5.dp
                }
                Column(
                    modifier = Modifier.padding(bottom = fabColumnBottomPadding),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    AnimatedVisibility(
                        visible = isFabExpanded,
                        enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
                        exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            fabConfigs.forEach { fab ->
                                Surface(
                                    modifier = Modifier.size(56.dp).clip(MaterialTheme.shapes.large).combinedClickable(
                                        onClick = {
                                            performHapticTick(context)
                                            when (fab.type) {
                                                "APP" -> {
                                                    val launched = launchApp(context, fab.target)
                                                    if (!launched) {
                                                        val intent = context.packageManager.getLaunchIntentForPackage(fab.target)
                                                        intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                        try { intent?.let { context.startActivity(it) } } catch (e: Exception) { e.printStackTrace() }
                                                    }
                                                }
                                                "MENU" -> {
                                                    val bundle = when (fab.target) {
                                                        "AI" -> aiBundle
                                                        "NOTES" -> notesBundle
                                                        "COMPOSE" -> composeBundle
                                                        "RECORDER" -> recorderBundle
                                                        else -> emptyList()
                                                    }
                                                    if (bundle.size == 1) {
                                                        val pkg = bundle[0]
                                                        if (pkg.startsWith("conduit.action.")) {
                                                            val uri = if (pkg == "conduit.action.SMS") "smsto:" else "mailto:"
                                                            val intent = Intent(Intent.ACTION_SENDTO, android.net.Uri.parse(uri))
                                                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                            try { context.startActivity(intent) } catch (e: Exception) { e.printStackTrace() }
                                                        } else {
                                                            val launched = launchApp(context, pkg)
                                                            if (!launched) {
                                                                val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                                                                intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                                try { intent?.let { context.startActivity(it) } } catch (e: Exception) { e.printStackTrace() }
                                                            }
                                                        }
                                                    } else if (bundle.size > 1) {
                                                        showBundleMenu = when (fab.target) {
                                                            "AI" -> "AI Assistants" to aiBundle
                                                            "NOTES" -> "Notes" to notesBundle
                                                            "COMPOSE" -> "Compose" to composeBundle
                                                            "RECORDER" -> "Recorder" to recorderBundle
                                                            else -> null
                                                        }
                                                    }
                                                }
                                                "SYSTEM" -> {
                                                    when (fab.target) {
                                                        "ARCHIVE_ALL" -> {
                                                            performHapticClick(context)
                                                            val now = System.currentTimeMillis()
                                                            kotlinx.coroutines.GlobalScope.launch {
                                                                val database = AppDatabase.getDatabase(context)
                                                                notifications.forEach { database.notificationDao().archiveNotification(it.id, now) }
                                                                com.conduit.app.widget.ConduitWidgetProvider.updateAllWidgets(context)
                                                            }
                                                        }
                                                        "SEARCH" -> {
                                                            isSearchMode = true
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                        onLongClick = {
                                            performHapticClick(context)
                                            showCustomizeFab = fab
                                        }
                                    ),
                                    shape = MaterialTheme.shapes.large,
                                    color = if (showCustomizeFab?.id == fab.id) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
                                    tonalElevation = 6.dp,
                                    shadowElevation = 6.dp
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(getFabIcon(fab.iconName), contentDescription = fab.label)
                                    }
                                }
                            }

                            Surface(
                                modifier = Modifier.size(56.dp).clip(MaterialTheme.shapes.large).clickable {
                                    performHapticTick(context)
                                    onNavigateToSettings()
                                },
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                tonalElevation = 6.dp,
                                shadowElevation = 6.dp
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.SettingsIcon, contentDescription = "Settings")
                                }
                            }
                        }
                    }
                    
                    // Master Toggle
                    Surface(
                        modifier = Modifier.size(56.dp).clip(MaterialTheme.shapes.large).clickable {
                            performHapticTick(context)
                            isFabExpanded = !isFabExpanded
                            prefs.edit().putBoolean("fab_expanded", isFabExpanded).apply()
                        },
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        tonalElevation = 6.dp,
                        shadowElevation = 6.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (isFabExpanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                                contentDescription = if (isFabExpanded) "Collapse" else "Expand"
                            )
                        }
                    }
                    
                    // Search Button
                    Surface(
                        modifier = Modifier.size(56.dp).clip(MaterialTheme.shapes.large).clickable {
                            performHapticTick(context)
                            isSearchMode = true
                        },
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        tonalElevation = 6.dp,
                        shadowElevation = 6.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Search, contentDescription = "Search")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            androidx.compose.animation.AnimatedVisibility(visible = !unifiedView) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Todo Mode (showing pending items only)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (displayNotifications.isEmpty() && selectedDockPackage == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No notifications to show", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!unifiedView) {
                            Spacer(modifier = Modifier.height(16.dp))
                            AssistChip(
                                onClick = onNavigateToArchive,
                                label = { Text("View Actioned") }
                            )
                        }
                    }
                }
            } else {
                val pinnedNotifications = remember(displayNotifications, isSearchMode) {
                    if (isSearchMode) emptyList() else displayNotifications.filter { it.isPinned }
                }
                val unpinnedNotifications = remember(displayNotifications, isSearchMode) {
                    if (isSearchMode) displayNotifications else displayNotifications.filter { !it.isPinned }
                }
                val groupedNotifications = remember(unpinnedNotifications) {
                    unpinnedNotifications.groupBy { formatDateHeader(it.timestamp) }
                }
                
                val currentViewConfig = LocalViewConfiguration.current
                val customViewConfig = remember(currentViewConfig) {
                    object : ViewConfiguration by currentViewConfig {
                        override val longPressTimeoutMillis: Long
                            get() = 700L // Increased from default 400ms
                    }
                }
                
                CompositionLocalProvider(LocalViewConfiguration provides customViewConfig) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                    // Pinned section
                    if (pinnedNotifications.isNotEmpty()) {
                        stickyHeader {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.background)
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.PushPin,
                                        contentDescription = "Pinned",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "PINNED",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        items(pinnedNotifications, key = { "pinned_${it.id}" }) { notification ->
                            Box(modifier = Modifier.animateItemPlacement(
                                animationSpec = tween(durationMillis = 300)
                            )) {
                                NotificationItem(
                                    notification = notification,
                                    isArchivedView = false,
                                    isUnifiedView = unifiedView,

                                    onArchiveNotification = onArchiveNotification,
                                    onPinNotification = onPinNotification,
                                    isSelected = selectedIds.contains(notification.id),
                                    isSelectionMode = isSelectionMode,
                                    onSelectToggle = {
                                        val becomingSelected = !selectedIds.contains(notification.id)
                                        if (becomingSelected) performHapticClick(context) else performHapticTick(context)
                                        
                                        selectedIds = if (becomingSelected) {
                                            selectedIds + notification.id
                                        } else {
                                            selectedIds - notification.id
                                        }
                                    },
                                    isCompactMode = isCompactMode,
                                    showActionChips = showActionChips,
                                    allActions = actionsByKey[notification.notificationKey]
                                )
                                Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                            }
                        }
                    }
                    
                    // Unpinned date-grouped section
                    groupedNotifications.forEach { (dateHeader, itemsList) ->
                        stickyHeader {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.background)
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = dateHeader,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (unifiedView) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        val pendingCount = itemsList.count { notifications.contains(it) }
                                        val chipText = if (pendingCount > 0) "${itemsList.size} | UNREAD $pendingCount" else "${itemsList.size}"
                                        Surface(
                                            shape = MaterialTheme.shapes.small,
                                            color = MaterialTheme.colorScheme.secondaryContainer
                                        ) {
                                            Text(
                                                text = chipText,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        items(itemsList, key = { it.id }) { notification ->
                            val dismissState = rememberDismissState(
                                confirmValueChange = {
                                    val action = if (it == DismissValue.DismissedToEnd) swipeRightAction else swipeLeftAction
                                    when (action) {
                                        "ARCHIVE" -> {
                                            if (prefs.getBoolean("sync_dismissal", true)) {
                                                HubNotificationListenerService.instance?.cancel(notification.notificationKey)
                                            }
                                            onArchiveNotification(notification.id, System.currentTimeMillis())
                                            !unifiedView
                                        }
                                        "SNOOZE" -> {
                                            notificationToSnooze = notification
                                            false
                                        }
                                        "PIN" -> {
                                            onPinNotification(notification)
                                            false
                                        }
                                        "BLOCK" -> {
                                            notificationToBlock = notification
                                            false
                                        }
                                        else -> false
                                    }
                                },
                                positionalThreshold = { distance -> distance * 0.6f }
                            )
                            
                            LaunchedEffect(dismissState.targetValue) {
                                if (dismissState.targetValue != DismissValue.Default) {
                                    performHapticTick(context)
                                }
                            }
                            
                            SwipeToDismiss(
                                state = dismissState,
                                modifier = Modifier.animateItemPlacement(
                                    animationSpec = tween(durationMillis = 300)
                                ),
                                directions = setOf(DismissDirection.StartToEnd, DismissDirection.EndToStart),
                                background = {
                                    val direction = dismissState.dismissDirection ?: return@SwipeToDismiss
                                    val action = if (direction == DismissDirection.StartToEnd) swipeRightAction else swipeLeftAction
                                    
                                    val color = when (action) {
                                        "ARCHIVE" -> MaterialTheme.colorScheme.primaryContainer
                                        "SNOOZE" -> Color(0xFFFF9800)
                                        "PIN" -> MaterialTheme.colorScheme.secondaryContainer
                                        "BLOCK" -> MaterialTheme.colorScheme.errorContainer
                                        else -> Color.Gray
                                    }
                                    
                                    val alignment = if (direction == DismissDirection.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
                                    
                                    val icon = when (action) {
                                        "ARCHIVE" -> if (unifiedView) Icons.Filled.Check else Icons.Filled.Archive
                                        "SNOOZE" -> Icons.Filled.Schedule
                                        "PIN" -> Icons.Filled.PushPin
                                        "BLOCK" -> Icons.Filled.Block
                                        else -> Icons.Filled.Delete
                                    }
                                    
                                    val label = when (action) {
                                        "ARCHIVE" -> if (unifiedView) "Mark Read" else "Clear"
                                        "SNOOZE" -> "Snooze"
                                        "PIN" -> if (notification.isPinned) "Unpin" else "Pin"
                                        "BLOCK" -> "Block"
                                        else -> ""
                                    }
                                    
                                    val textColor = when (action) {
                                        "ARCHIVE" -> MaterialTheme.colorScheme.onPrimaryContainer
                                        "SNOOZE" -> Color.White
                                        "PIN" -> MaterialTheme.colorScheme.onSecondaryContainer
                                        "BLOCK" -> MaterialTheme.colorScheme.onErrorContainer
                                        else -> Color.White
                                    }
                                    
                                    val scale by animateFloatAsState(
                                        if (dismissState.targetValue == DismissValue.Default) 0.75f else 1.25f
                                    )
                                    
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(color)
                                            .padding(horizontal = 24.dp),
                                        contentAlignment = alignment
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                icon,
                                                contentDescription = null,
                                                tint = textColor,
                                                modifier = Modifier.scale(scale).size(28.dp)
                                            )
                                            Text(
                                                label,
                                                color = textColor,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                modifier = Modifier.scale(scale)
                                            )
                                        }
                                    }
                                },
                                dismissContent = {
                                    Column {
                                        NotificationItem(
                                            notification = notification,
                                            isArchivedView = false,
                                            isUnifiedView = unifiedView,

                                            onArchiveNotification = onArchiveNotification,
                                            onPinNotification = onPinNotification,
                                            isSelected = selectedIds.contains(notification.id),
                                            isSelectionMode = isSelectionMode,
                                            onSelectToggle = {
                                                val becomingSelected = !selectedIds.contains(notification.id)
                                                if (becomingSelected) performHapticClick(context) else performHapticTick(context)

                                                selectedIds = if (becomingSelected) {
                                                    selectedIds + notification.id
                                                } else {
                                                    selectedIds - notification.id
                                                }
                                            },
                                            isCompactMode = isCompactMode,
                                            showActionChips = showActionChips,
                                            allActions = actionsByKey[notification.notificationKey]
                                        )
                                        Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                                    }
                                }
                            )
                        }
                    }
                    }
                }
            }
            
            // The Floating Dock
            val pm = context.packageManager
            val enabledApps = remember(unifiedView, channelStates.toMap()) {
                if (!unifiedView) emptyList<String>()
                else {
                    HubNotificationListenerService.supportedApps.keys
                        .filter { pkg ->
                            val prefKey = HubNotificationListenerService.supportedApps[pkg]?.first
                            if (prefKey != null && channelStates[prefKey] == true) {
                                isPackageInstalled(context, pkg)
                            } else false
                        }
                        .map { getRepresentativePackage(context, it) }
                        .distinct()
                }
            }
            if (notifications.isNotEmpty() || (unifiedView && enabledApps.isNotEmpty())) {
                val grouped = remember(notifications) {
                    notifications.groupBy { getRepresentativePackage(context, it.packageName) }
                }
                val dockPackagesList = remember(grouped, enabledApps, unifiedView) {
                    if (unifiedView) {
                        val unread = enabledApps.filter { grouped.containsKey(it) }
                        val read = enabledApps.filter { !grouped.containsKey(it) }
                        val otherUnread = grouped.keys.filter { !enabledApps.contains(it) }
                        (unread + otherUnread + read).distinct()
                    } else {
                        grouped.keys.toList()
                    }
                }
                val dockIconSize = when (dockSizeIndex) {
                    0 -> 36.dp
                    2 -> 44.dp
                    else -> 40.dp
                }
                val dockSpacing = when (dockSizeIndex) {
                    0 -> 10.dp
                    2 -> 12.dp
                    else -> 12.dp
                }
                val dockBoxPadding = when (dockSizeIndex) {
                    0 -> 6.dp
                    2 -> 8.dp
                    else -> 6.dp
                }
                val dockPaddingHorizontal = when (dockSizeIndex) {
                    0 -> 12.dp
                    2 -> 16.dp
                    else -> 14.dp
                }
                val dockPaddingVertical = when (dockSizeIndex) {
                    0 -> 6.dp
                    2 -> 8.dp
                    else -> 7.dp
                }
                val dockBadgeOffsetY = when (dockSizeIndex) {
                    0 -> 2.dp
                    2 -> 4.dp
                    else -> 3.dp
                }
                val dockBadgeOffsetX = when (dockSizeIndex) {
                    0 -> (-2).dp
                    2 -> (-4).dp
                    else -> (-3).dp
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(start = 16.dp, end = if (unifiedView) 88.dp else 16.dp, bottom = 16.dp), // Fixed padding parameters
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shadowElevation = 8.dp
                    ) {
                        LazyRow(
                            modifier = Modifier.padding(vertical = 1.dp),
                            contentPadding = PaddingValues(horizontal = dockPaddingHorizontal, vertical = dockPaddingVertical), 
                            horizontalArrangement = Arrangement.spacedBy(dockSpacing),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            items(dockPackagesList.size) { index ->
                                val pkg = dockPackagesList[index]
                                val groupNotifs = grouped[pkg] ?: emptyList()
                                
                                if (unifiedView && index > 0) {
                                    val prevPkg = dockPackagesList[index - 1]
                                    val isPrevUnread = grouped.containsKey(prevPkg)
                                    val isCurrentRead = !grouped.containsKey(pkg)
                                    if (isPrevUnread && isCurrentRead) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Divider(
                                            modifier = Modifier.height(24.dp).width(1.dp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                }
                                
                                val isSelected = selectedDockPackage == pkg
                                Box(
                                    modifier = Modifier
                                        .combinedClickable(
                                            onClick = {
                                                if (isSelected) selectedDockPackage = null else selectedDockPackage = pkg
                                            },
                                            onLongClick = {
                                                val shouldLaunch = prefs.getBoolean("dock_long_press_launch", true)
                                                if (shouldLaunch) {
                                                    try {
                                                        val launched = launchApp(context, pkg)
                                                        if (launched) {
                                                            performHapticClick(context)
                                                        } else {
                                                            val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
                                                            if (launchIntent != null) {
                                                                context.startActivity(launchIntent)
                                                                performHapticClick(context)
                                                            } else {
                                                                android.widget.Toast.makeText(context, "Cannot open this application.", android.widget.Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    } catch (e: Exception) {
                                                        android.widget.Toast.makeText(context, "Failed to launch application.", android.widget.Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        )
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent,
                                            shape = androidx.compose.foundation.shape.CircleShape
                                        )
                                        .padding(dockBoxPadding) // Adjusted padding dynamically
                                ) {
                                    androidx.compose.material3.BadgedBox(
                                        badge = {
                                            if (groupNotifs.isNotEmpty()) {
                                                androidx.compose.material3.Badge(
                                                    modifier = Modifier.offset(y = dockBadgeOffsetY, x = dockBadgeOffsetX)
                                                ) {
                                                    Text(groupNotifs.size.toString())
                                                }
                                            }
                                        }
                                    ) {
                                        AppIcon(
                                            packageName = pkg,
                                            size = dockIconSize
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Old Search Button removed from BottomStart
            }
        }

        if (notificationToBlock != null) {
            val notif = notificationToBlock!!
            val appName = remember(notif.packageName) { appLabelCache[notif.packageName] ?: notif.channel }
            var blockType by remember { mutableStateOf("TITLE") }
            
            val titleText = notif.title ?: ""
            val bodyText = notif.text ?: ""
            val scope = rememberCoroutineScope()
            
            AlertDialog(
                onDismissRequest = { notificationToBlock = null },
                title = { Text("Block similar notifications?") },
                text = {
                    Column {
                        Text(
                            text = "Conduit will automatically ignore future notifications from $appName matching the rule below.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { blockType = "TITLE" }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(selected = blockType == "TITLE", onClick = { blockType = "TITLE" })
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Match by Title", fontWeight = FontWeight.SemiBold)
                                Text("\"$titleText\"", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        
                        if (bodyText.isNotBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { blockType = "TEXT" }
                                    .padding(vertical = 4.dp)
                            ) {
                                RadioButton(selected = blockType == "TEXT", onClick = { blockType = "TEXT" })
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Match by Content Text", fontWeight = FontWeight.SemiBold)
                                    Text("\"$bodyText\"", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val pattern = if (blockType == "TITLE") titleText else bodyText
                            if (pattern.isNotBlank()) {
                                val newRule = "${notif.packageName}|$blockType|$pattern"
                                val currentSet = prefs.getStringSet("blocked_rules", emptySet<String>()) ?: emptySet<String>()
                                val updatedSet = currentSet + newRule
                                prefs.edit().putStringSet("blocked_rules", updatedSet).apply()
                                
                                scope.launch {
                                    val database = AppDatabase.getDatabase(context)
                                    val allNotifs = database.notificationDao().getAllNotificationsSync()
                                    val idsToDelete = mutableListOf<Int>()
                                    allNotifs.forEach { item ->
                                        if (item.packageName == notif.packageName) {
                                            val match = if (blockType == "TITLE") {
                                                item.title?.contains(pattern, ignoreCase = true) == true
                                            } else {
                                                item.text?.contains(pattern, ignoreCase = true) == true
                                            }
                                            if (match) {
                                                idsToDelete.add(item.id)
                                            }
                                        }
                                    }
                                    if (idsToDelete.isNotEmpty()) {
                                        database.notificationDao().deleteNotifications(idsToDelete)
                                        com.conduit.app.widget.ConduitWidgetProvider.updateAllWidgets(context)
                                    }
                                }
                            }
                            performHapticClick(context)
                            notificationToBlock = null
                            selectedIds = emptySet()
                        }
                    ) {
                        Text("Block")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { notificationToBlock = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        notificationToSnooze?.let { notif ->
            ModalBottomSheet(onDismissRequest = { notificationToSnooze = null }) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Snooze Notification", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 16.dp))
                    
                    val snoozeOptions = listOf(
                        "15 Minutes" to 15 * 60 * 1000L,
                        "1 Hour" to 60 * 60 * 1000L,
                        "2 Hours" to 2 * 60 * 60 * 1000L
                    )
                    
                    snoozeOptions.forEach { (label, durationMs) ->
                        ListItem(
                            headlineContent = { Text(label) },
                            modifier = Modifier.clickable {
                                HubNotificationListenerService.instance?.snooze(notif.notificationKey, durationMs)
                                onSnoozeNotification(notif.id, System.currentTimeMillis())
                                notificationToSnooze = null
                            }
                        )
                    }
                }
            }
        }

        if (showBundleMenu != null) {
            val (title, packages) = showBundleMenu!!
            ModalBottomSheet(onDismissRequest = { showBundleMenu = null }) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 16.dp))
                    
                    packages.forEach { pkg ->
                        val pm = context.packageManager
                        val label = remember(pkg) {
                            if (isPackageInstalled(context, pkg)) {
                                getAppLabel(context, pkg)
                            } else {
                                null
                            }
                        }
                        if (label != null) {
                            ListItem(
                                headlineContent = { Text(label) },
                                leadingContent = { AppIcon(pkg) },
                                modifier = Modifier.clickable {
                                    showBundleMenu = null
                                    var launchIntent: Intent? = null
                                    
                                    // Try Email Compose
                                    val emailIntent = Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:")).apply { setPackage(pkg) }
                                    if (emailIntent.resolveActivity(pm) != null) {
                                        launchIntent = emailIntent
                                    } else {
                                        // Try SMS Compose
                                        val smsIntent = Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:")).apply { setPackage(pkg) }
                                        if (smsIntent.resolveActivity(pm) != null) {
                                            launchIntent = smsIntent
                                        }
                                    }
                                    
                                    if (launchIntent != null) {
                                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        try { context.startActivity(launchIntent) } catch (e: Exception) { e.printStackTrace() }
                                    } else {
                                        launchApp(context, pkg)
                                    }
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }

        if (showCustomizeFab != null) {
            ModalBottomSheet(onDismissRequest = { showCustomizeFab = null }) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Customize Button", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 16.dp))
                    
                    val availableActions = listOf(
                        FabAction("", "Recorder Menu", "PlayArrow", "MENU", "RECORDER"),
                        FabAction("", "AI Menu", "Star", "MENU", "AI"),
                        FabAction("", "Notes Menu", "List", "MENU", "NOTES"),
                        FabAction("", "Compose Menu", "Edit", "MENU", "COMPOSE"),
                        FabAction("", "Archive All", "Archive", "SYSTEM", "ARCHIVE_ALL"),
                        FabAction("", "Search", "Search", "SYSTEM", "SEARCH")
                    )
                    
                    availableActions.forEach { action ->
                        ListItem(
                            headlineContent = { Text(action.label) },
                            leadingContent = { Icon(getFabIcon(action.iconName), contentDescription = null) },
                            modifier = Modifier.clickable {
                                val currentFab = showCustomizeFab!!
                                val newConfigs = fabConfigs.map { 
                                    if (it.id == currentFab.id) action.copy(id = it.id) else it 
                                }
                                onSaveFabConfigs(newConfigs)
                                showCustomizeFab = null
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun NotificationItem(
    notification: HubNotification,
    isArchivedView: Boolean = false,
    onArchiveNotification: ((Int, Long) -> Unit)? = null,
    onPinNotification: ((HubNotification) -> Unit)? = null,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onSelectToggle: () -> Unit = {},
    isCompactMode: Boolean = false,
    showActionChips: Boolean = true,
    isUnifiedView: Boolean = false,
    allActions: List<Notification.Action>? = null
) {
    val context = LocalContext.current
    var isReplying by remember { mutableStateOf(false) }
    var replyText by remember { mutableStateOf("") }
    val replyAction = remember(allActions) {
        allActions?.find { it.remoteInputs != null && it.remoteInputs.isNotEmpty() }
    }

    val generalPrefs = remember(context) { context.getSharedPreferences("conduit_prefs", Context.MODE_PRIVATE) }
    val smartMarkRead = remember(generalPrefs) { generalPrefs.getBoolean("smart_mark_read", true) }
    val smartMarkReadTarget = remember(generalPrefs) { generalPrefs.getString("smart_mark_read_target", "widget_only") ?: "widget_only" }

    val hasNativeMarkRead = remember(allActions) {
        allActions?.any { action ->
            val title = action.title?.toString()?.lowercase() ?: ""
            title.contains("read") || title.contains("done") || title.contains("clear") || title.contains("dismiss") || title.contains("archive")
        } ?: false
    }

    val showConduitMarkRead = showActionChips && !isCompactMode && !isArchivedView && !notification.isArchived &&
            smartMarkRead && smartMarkReadTarget == "widget_and_app" && !hasNativeMarkRead

    val hasChips = (allActions != null && allActions.isNotEmpty()) || showConduitMarkRead

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else if (notification.isArchived) MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp).copy(alpha = 0.7f)
                else MaterialTheme.colorScheme.surface
            )
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) {
                        onSelectToggle()
                    } else {
                        var launched = false
                    val service = HubNotificationListenerService.instance
                    if (service != null) {
                        try {
                            val activeNotifs = service.activeNotifications
                            for (sbn in activeNotifs) {
                                if (sbn.key == notification.notificationKey) {
                                    val intent = sbn.notification.contentIntent
                                    if (intent != null) {
                                        val options = android.app.ActivityOptions.makeBasic()
                                        if (android.os.Build.VERSION.SDK_INT >= 34) {
                                            options.pendingIntentBackgroundActivityStartMode = android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                                        }
                                        intent.send(context, 0, null, null, null, null, options.toBundle())
                                        launched = true
                                    }
                                    break
                                }
                            }
                            
                            // Fallback to in-memory cache if not found in active live notifications
                            if (!launched) {
                                val cachedIntent = service.getCachedContentIntent(notification.notificationKey)
                                if (cachedIntent != null) {
                                    val options = android.app.ActivityOptions.makeBasic()
                                    if (android.os.Build.VERSION.SDK_INT >= 34) {
                                        options.pendingIntentBackgroundActivityStartMode = android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                                    }
                                    cachedIntent.send(context, 0, null, null, null, null, options.toBundle())
                                    launched = true
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    if (!launched) {
                        try {
                            val launchedCrossProfile = launchApp(context, notification.packageName)
                            if (!launchedCrossProfile) {
                                val launchIntent = context.packageManager.getLaunchIntentForPackage(notification.packageName)
                                if (launchIntent != null) {
                                    launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(launchIntent)
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            },
                onLongClick = {
                    if (!isSelectionMode) {
                        performHapticClick(context)
                        onPinNotification?.invoke(notification)
                    }
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val avatarSize = if (isCompactMode) 36.dp else 50.dp
            val avatarBoxWidth = if (isCompactMode) 44.dp else 60.dp

            // Selection / Icon area
            Box(
                modifier = Modifier
                    .width(avatarBoxWidth)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = androidx.compose.material.ripple.rememberRipple(bounded = false, radius = 32.dp),
                        enabled = true
                    ) { onSelectToggle() },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(avatarSize)
                    )
                } else {
                    val channelUpper = notification.channel.uppercase(java.util.Locale.ROOT)
                    if (channelUpper == "SMS" || channelUpper == "GOOGLE MESSAGES" ||
                        channelUpper == "EMAIL" || channelUpper == "GMAIL" || channelUpper == "SPARK" || channelUpper == "OUTLOOK" ||
                        channelUpper == "SNAPCHAT" ||
                        channelUpper == "LINKEDIN" ||
                        channelUpper == "INSTAGRAM" ||
                        channelUpper == "PHONE" || channelUpper == "SYSTEM PHONE" || channelUpper == "PHONE (GOOGLE DIALER)" || channelUpper == "TRUECALLER" ||
                        channelUpper == "TELEGRAM" || channelUpper == "TELEGRAM X" ||
                        channelUpper == "REDDIT" ||
                        channelUpper == "STEAM" ||
                        channelUpper == "FACEBOOK" || channelUpper == "MESSENGER" ||
                        channelUpper == "TWITTER (X)" || channelUpper == "MICROSOFT TEAMS"
                    ) {
                        AppIcon(notification.packageName, size = avatarSize)
                    } else {
                        // Avatar
                        Box(
                            modifier = Modifier
                                .size(avatarSize)
                                .background(MaterialTheme.colorScheme.primaryContainer, shape = androidx.compose.foundation.shape.CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            val initial = notification.title?.firstOrNull()?.uppercase() ?: "M"
                            Text(initial, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Medium, fontSize = if (isCompactMode) 14.sp else 20.sp)
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = if (isCompactMode) 4.dp else 8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        if (notification.isPinned) {
                            Icon(
                                Icons.Filled.PushPin,
                                contentDescription = "Pinned",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        if (notification.isArchived && !isArchivedView) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                                modifier = Modifier.padding(end = 6.dp)
                            ) {
                                Text(
                                    if (isUnifiedView) "READ" else "ARCHIVED",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                        Text(
                            text = notification.title ?: "Unknown",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = if (notification.isPinned) formatTimestampWithDate(notification.timestamp) else formatTimestamp(notification.timestamp),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                if (isArchivedView && notification.archivedTimestamp != null) {
                    val label = if (notification.isSnoozed) "Snoozed: " else "Read: "
                    val color = if (notification.isSnoozed) Color(0xFFFF9800) else MaterialTheme.colorScheme.onSurfaceVariant
                    Text(
                        text = label + formatTimestamp(notification.archivedTimestamp),
                        fontSize = 11.sp,
                        color = color
                    )
                }
                
                var appName by remember(notification.packageName) { mutableStateOf(appLabelCache[notification.packageName] ?: notification.channel) }
                
                LaunchedEffect(notification.packageName) {
                    if (!appLabelCache.containsKey(notification.packageName)) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            val label = getAppLabel(context, notification.packageName, notification.channel)
                            appName = label
                        }
                    }
                }
                
                Text(
                    text = appName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(2.dp))

                val replyPrimaryColor = MaterialTheme.colorScheme.primary
                val annotatedText = remember(notification.text, replyPrimaryColor) {
                    val fullText = notification.text ?: ""
                    buildAnnotatedString {
                        val lines = fullText.split("\n")
                        lines.forEachIndexed { index, line ->
                            if (index > 0) {
                                append("\n")
                            }
                            if (line.startsWith("\u21aa You:")) {
                                val youPrefix = "\u21aa You:"
                                val messagePart = line.substring(youPrefix.length)
                                withStyle(style = SpanStyle(
                                    color = replyPrimaryColor,
                                    fontWeight = FontWeight.Bold
                                )) {
                                    append(youPrefix)
                                }
                                withStyle(style = SpanStyle(
                                    fontStyle = FontStyle.Italic
                                )) {
                                    append(messagePart)
                                }
                            } else {
                                append(line)
                            }
                        }
                    }
                }

                Text(
                    text = annotatedText,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (isCompactMode) 2 else 6,
                    overflow = TextOverflow.Ellipsis
                )
                
                if (showActionChips && !isCompactMode && !isArchivedView && !notification.isArchived && hasChips && !isReplying) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        allActions?.forEach { action ->
                            val title = action.title?.toString() ?: ""
                            if (title.isBlank()) return@forEach
                            
                            val isReply = action.remoteInputs != null && action.remoteInputs.isNotEmpty()
                            
                            Surface(
                                onClick = { 
                                    performHapticTick(context)
                                    if (isReply) {
                                        isReplying = true
                                    } else {
                                        triggerNotificationAction(context, notification, action)
                                    }
                                },
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                                color = Color.Transparent
                            ) {
                                Text(
                                    text = title, 
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        if (showConduitMarkRead) {
                            Surface(
                                onClick = {
                                    performHapticTick(context)
                                    val db = com.conduit.app.data.AppDatabase.getDatabase(context)
                                    val service = HubNotificationListenerService.instance
                                    val syncDismissal = generalPrefs.getBoolean("sync_dismissal", true)
                                    
                                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                        db.notificationDao().archiveNotificationByKey(notification.notificationKey, System.currentTimeMillis())
                                        if (syncDismissal) {
                                            service?.cancel(notification.notificationKey)
                                        }
                                        com.conduit.app.widget.ConduitWidgetProvider.updateAllWidgets(context)
                                    }
                                },
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                            ) {
                                Text(
                                    text = "Mark Read",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                if (isReplying) {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        TextField(
                            value = replyText,
                            onValueChange = { replyText = it },
                            placeholder = { Text("Type a reply...") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )
                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            TextButton(onClick = { 
                                isReplying = false
                                replyText = ""
                            }) {
                                Text("Cancel")
                            }
                            Button(
                                onClick = {
                                    if (replyAction != null) {
                                        sendReply(context, notification, replyText, replyAction)
                                    }
                                    isReplying = false
                                    replyText = ""
                                },
                                enabled = replyText.isNotBlank(),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                            ) {
                                Text("Send")
                            }
                        }
                    }
                }
            }
        }
    }
}

fun formatDateHeader(timestamp: Long): String {
    val formatter = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
    return formatter.format(Date(timestamp)).uppercase()
}

fun formatTimestamp(timestamp: Long): String {
    val formatter = SimpleDateFormat("h:mm a", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

fun formatTimestampWithDate(timestamp: Long): String {
    val formatter = SimpleDateFormat("M/d · h:mm a", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

@Composable
fun AppIcon(packageName: String, size: androidx.compose.ui.unit.Dp = 50.dp) {
    val context = LocalContext.current
    var iconBitmap by remember(packageName) { mutableStateOf(appIconCache[packageName]) }

    LaunchedEffect(packageName) {
        if (iconBitmap == null) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val drawable = getAppIcon(context, packageName)
                    if (drawable != null) {
                        val bmp = drawable.toBitmap().asImageBitmap()
                        appIconCache[packageName] = bmp
                        iconBitmap = bmp
                    }
                } catch (e: Exception) {
                    // Ignore missing icon
                }
            }
        }
    }

    val currentIcon = iconBitmap
    if (currentIcon != null) {
        Image(
            bitmap = currentIcon,
            contentDescription = null,
            modifier = Modifier.size(size)
        )
    } else {
        // Fallback to circular initial
        Box(
            modifier = Modifier
                .size(size)
                .background(MaterialTheme.colorScheme.primaryContainer, shape = androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center
        ) {
            val initial = packageName.split(".").lastOrNull()?.firstOrNull()?.uppercase() ?: "A"
            Text(initial, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Medium, fontSize = (size.value * 0.4).sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themePreference: Int,
    onThemeChanged: (Int) -> Unit,
    jacobMonochrome: Boolean,
    onJacobMonochromeChanged: (Boolean) -> Unit,
    groupByChannel: Boolean,
    onGroupByChannelChanged: (Boolean) -> Unit,
    channelStates: Map<String, Boolean>,
    onChannelToggled: (String, Boolean) -> Unit,
    syncDismissal: Boolean,
    onSyncDismissalChanged: (Boolean) -> Unit,
    syncPinned: Boolean,
    onSyncPinnedChanged: (Boolean) -> Unit,
    showActionChips: Boolean,
    onShowActionChipsChanged: (Boolean) -> Unit,
    aiBundle: List<String>,
    onAiBundleChanged: (List<String>) -> Unit,
    notesBundle: List<String>,
    onNotesBundleChanged: (List<String>) -> Unit,
    recorderBundle: List<String>,
    onRecorderBundleChanged: (List<String>) -> Unit,
    composeBundle: List<String>,
    onComposeBundleChanged: (List<String>) -> Unit,
    dockLongPressLaunch: Boolean,
    onDockLongPressLaunchChanged: (Boolean) -> Unit,
    swipeLeftAction: String,
    onSwipeLeftActionChanged: (String) -> Unit,
    swipeRightAction: String,
    onSwipeRightActionChanged: (String) -> Unit,
    dockSizeIndex: Int,
    onDockSizeChanged: (Int) -> Unit,
    enableBracket: Boolean,
    onEnableBracketChanged: (Boolean) -> Unit,
    bracketNotificationPopup: Boolean,
    onBracketNotificationPopupChanged: (Boolean) -> Unit,
    bracketHangerEnabled: Boolean,
    onBracketHangerEnabledChanged: (Boolean) -> Unit,
    bracketVerticalPosition: Float,
    onBracketVerticalPositionChanged: (Float) -> Unit,
    unifiedView: Boolean,
    onUnifiedViewChanged: (Boolean) -> Unit,
    activeAppIcon: String,
    onActiveAppIconChanged: (String) -> Unit,
    smartMarkRead: Boolean,
    onSmartMarkReadChanged: (Boolean) -> Unit,
    smartMarkReadTarget: String,
    onSmartMarkReadTargetChanged: (String) -> Unit,
    onShowWhatsNew: () -> Unit,
    onNavigateToDevSettings: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager
    val installedChannelKeys = remember {
        val installed = mutableSetOf<String>()
        HubNotificationListenerService.supportedApps.forEach { (pkg, pair) ->
            val prefKey = pair.first
            if (isPackageInstalled(context, pkg)) {
                installed.add(prefKey)
            }
        }
        installed
    }
    var showSupportedAppsDialog by remember { mutableStateOf(false) }
    val options = listOf("System Default", "Light Theme", "Dark Theme", "Jacob Mode (AMOLED)")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("App Info & Updates", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { 
                        performHapticClick(context)
                        onShowWhatsNew() 
                    }
            ) {
                ListItem(
                    headlineContent = { Text("What's New") },
                    supportingContent = { Text("View the update details and feature changelog for version 2.02.06.") },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("Theme", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
            Column(Modifier.selectableGroup()) {
                options.forEachIndexed { index, text ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .selectable(
                                selected = (themePreference == index),
                                onClick = { onThemeChanged(index) },
                                role = androidx.compose.ui.semantics.Role.RadioButton
                            )
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (themePreference == index),
                            onClick = null 
                        )
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }
            
            if (themePreference == 3) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Monochrome Accents", style = MaterialTheme.typography.bodyLarge)
                        Text("Forces text and icons to pure white/light gray instead of device blue", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = jacobMonochrome,
                        onCheckedChange = onJacobMonochromeChanged
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))

            Text("Layout", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
            
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Show Action Chips", style = MaterialTheme.typography.bodyLarge)
                    Text("Displays quick action chips (like Reply, Mark as Read) directly on the notification card", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = showActionChips, onCheckedChange = onShowActionChipsChanged)
            }

            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Smart Mark as Read Chips", style = MaterialTheme.typography.bodyLarge)
                    Text("Automatically inserts a \"Mark Read\" action chip if an app notification doesn't natively support it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = smartMarkRead, onCheckedChange = onSmartMarkReadChanged)
            }

            if (smartMarkRead) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                    Text("Smart Chips Target", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val targets = listOf("widget_only" to "Widget Only", "widget_and_app" to "Widget and App")
                        targets.forEach { (value, label) ->
                            val isSelected = smartMarkReadTarget == value
                            Surface(
                                onClick = { onSmartMarkReadTargetChanged(value) },
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                ),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                modifier = Modifier.weight(1f).height(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Text(
                                        text = label,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("Swipe Gestures", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
            
            SwipeActionSelector(
                label = "Swipe Right",
                currentValue = swipeRightAction,
                isUnifiedView = unifiedView,
                onValueSelected = onSwipeRightActionChanged
            )
            
            SwipeActionSelector(
                label = "Swipe Left",
                currentValue = swipeLeftAction,
                isUnifiedView = unifiedView,
                onValueSelected = onSwipeLeftActionChanged
            )
            
            Spacer(modifier = Modifier.height(24.dp))

            Text("Unread App Dock Settings", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
            
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Launch App on Long Press", style = MaterialTheme.typography.bodyLarge)
                    Text("When enabled, long-pressing an app icon in the bottom unread dock will immediately open that application", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = dockLongPressLaunch, onCheckedChange = onDockLongPressLaunchChanged)
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text("Dock Size", style = MaterialTheme.typography.bodyLarge)
                Text("Adjust the size and spacing of app icons inside the unread dock", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp))
                
                val sizeOptions = listOf("Small", "Medium", "Large")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sizeOptions.forEachIndexed { index, label ->
                        val isSelected = dockSizeIndex == index
                        OutlinedCard(
                            onClick = { 
                                onDockSizeChanged(index)
                                performHapticClick(context)
                            },
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp, 
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))

            Text("Sync", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
            
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Sync Dismissal with System", style = MaterialTheme.typography.bodyLarge)
                    Text("When enabled, archiving a notification in Conduit will also dismiss it from the Android system tray", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = syncDismissal, onCheckedChange = onSyncDismissalChanged)
            }
            
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Sync Pinned Notifications", style = MaterialTheme.typography.bodyLarge)
                    Text("When enabled, pinned notifications in Conduit will replace original notifications and remain pinned permanently in the Android system tray until unpinned", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = syncPinned, onCheckedChange = onSyncPinnedChanged)
            }
            
            Spacer(modifier = Modifier.height(24.dp))

            Text("Channels", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
            Text(
                text = "Channels represent external applications linked to Conduit. When enabled, notification alerts from these apps will be integrated and managed directly within your Conduit workspace feed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            val channelsToShow = remember(installedChannelKeys) {
                HubNotificationListenerService.supportedApps.values
                    .distinctBy { it.first }
                    .filter { installedChannelKeys.contains(it.first) }
            }
            
            channelsToShow.forEach { (prefKey, name) ->
                val pkgName = remember(prefKey) {
                    HubNotificationListenerService.supportedApps.entries
                        .firstOrNull { it.value.first == prefKey && try { pm.getApplicationInfo(it.key, 0).enabled } catch(e: Exception) { false } }?.key
                        ?: HubNotificationListenerService.supportedApps.entries.firstOrNull { it.value.first == prefKey }?.key
                        ?: ""
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (pkgName.isNotEmpty()) {
                            AppIcon(packageName = pkgName, size = 28.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        Text(name, style = MaterialTheme.typography.bodyLarge)
                    }
                    Switch(
                        checked = channelStates[prefKey] ?: true,
                        onCheckedChange = { onChannelToggled(prefKey, it) }
                    )
                }
            }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showSupportedAppsDialog = true }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 16.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text("View Supported Apps", style = MaterialTheme.typography.bodyLarge)
                    Text("See all channels and package names Conduit supports", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (showSupportedAppsDialog) {
                AlertDialog(
                    onDismissRequest = { showSupportedAppsDialog = false },
                    title = { Text("Supported Apps & Channels") },
                    text = {
                        val groupedApps = remember {
                            HubNotificationListenerService.supportedApps.entries
                                .groupBy { it.value.second }
                        }
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            groupedApps.forEach { (channelName, entries) ->
                                item {
                                    Column {
                                        Text(channelName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                        entries.forEach { entry ->
                                            val pkg = entry.key
                                            val isInstalled = try {
                                                val appInfo = pm.getApplicationInfo(pkg, 0)
                                                appInfo.enabled
                                            } catch (e: Exception) {
                                                false
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 2.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                                if (isInstalled) {
                                                    Text("Installed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp))
                                                } else {
                                                    Text("Not Installed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.padding(start = 8.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showSupportedAppsDialog = false }) {
                            Text("Close")
                        }
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))

            Text("Ignored Notifications", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
            
            val prefs = remember { context.getSharedPreferences("conduit_prefs", android.content.Context.MODE_PRIVATE) }
            val blockedRules = remember(prefs) {
                prefs.getStringSet("blocked_rules", emptySet<String>()) ?: emptySet<String>()
            }
            var blockedRulesState by remember { mutableStateOf<Set<String>>(blockedRules) }
            
            if (blockedRulesState.isEmpty()) {
                Text(
                    text = "No blocked notifications configured. You can block specific types of notifications by selecting them in the Hub list and tapping the Block icon.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                OutlinedCard(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Column {
                        val rulesList = blockedRulesState.toList()
                        for (index in rulesList.indices) {
                            val ruleStr = rulesList[index]
                            val parts = ruleStr.split("|", limit = 3)
                            if (parts.size >= 3) {
                                val pkg = parts[0]
                                val type = parts[1]
                                val pattern = parts[2]
                                val appLabel = remember(pkg) {
                                    getAppLabel(context, pkg)
                                }
                                ListItem(
                                    headlineContent = { Text(appLabel, fontWeight = FontWeight.SemiBold) },
                                    supportingContent = {
                                        Text("Ignore if $type contains: \"$pattern\"", style = MaterialTheme.typography.bodySmall)
                                    },
                                    trailingContent = {
                                        IconButton(onClick = {
                                            val updated = blockedRulesState - ruleStr
                                            prefs.edit().putStringSet("blocked_rules", updated).apply()
                                            blockedRulesState = updated
                                        }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                )
                                if (index < rulesList.size - 1) {
                                    Divider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                                }
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))

            Text("App Bundles", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
            Text("Customize which apps appear in your FAB menus", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
            
            var editingBundleTitle by remember { mutableStateOf<String?>(null) }
            
            val currentApps = when (editingBundleTitle) {
                "AI Assistants" -> aiBundle
                "Notes" -> notesBundle
                "Recorder" -> recorderBundle
                "Compose" -> composeBundle
                else -> emptyList()
            }
            val onUpdate: (List<String>) -> Unit = when (editingBundleTitle) {
                "AI Assistants" -> onAiBundleChanged
                "Notes" -> onNotesBundleChanged
                "Recorder" -> onRecorderBundleChanged
                "Compose" -> onComposeBundleChanged
                else -> { _ -> }
            }

            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large
            ) {
                Column {
                    ListItem(
                        headlineContent = { Text("Recorder") },
                        supportingContent = { Text("${recorderBundle.size} apps configured") },
                        leadingContent = { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = { Icon(Icons.Default.ArrowForward, contentDescription = null) },
                        modifier = Modifier.clickable { editingBundleTitle = "Recorder" }
                    )
                    Divider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    ListItem(
                        headlineContent = { Text("AI Assistants") },
                        supportingContent = { Text("${aiBundle.size} apps configured") },
                        leadingContent = { Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = { Icon(Icons.Default.ArrowForward, contentDescription = null) },
                        modifier = Modifier.clickable { editingBundleTitle = "AI Assistants" }
                    )
                    Divider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    ListItem(
                        headlineContent = { Text("Notes") },
                        supportingContent = { Text("${notesBundle.size} apps configured") },
                        leadingContent = { Icon(Icons.Default.List, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = { Icon(Icons.Default.ArrowForward, contentDescription = null) },
                        modifier = Modifier.clickable { editingBundleTitle = "Notes" }
                    )
                    Divider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    ListItem(
                        headlineContent = { Text("Compose") },
                        supportingContent = { Text("${composeBundle.size} apps configured") },
                        leadingContent = { Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = { Icon(Icons.Default.ArrowForward, contentDescription = null) },
                        modifier = Modifier.clickable { editingBundleTitle = "Compose" }
                    )
                }
            }

            if (editingBundleTitle != null) {
                val title = editingBundleTitle!!
                var showAppPicker by remember { mutableStateOf(false) }

                ModalBottomSheet(onDismissRequest = { editingBundleTitle = null }) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(title, style = MaterialTheme.typography.headlineSmall)
                            FilledTonalButton(onClick = { showAppPicker = true }) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Add App")
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        if (currentApps.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text("No apps in this bundle", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                                Column {
                                    currentApps.forEachIndexed { index, pkg ->
                                        val label = remember(pkg) {
                                            getAppLabel(context, pkg)
                                        }
                                        ListItem(
                                            headlineContent = { Text(text = label, fontWeight = FontWeight.Medium) },
                                            leadingContent = { AppIcon(pkg) },
                                            trailingContent = {
                                                IconButton(onClick = { onUpdate(currentApps.filter { it != pkg }) }) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                        )
                                        if (index < currentApps.size - 1) {
                                            Divider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(48.dp))
                    }
                    
                    if (showAppPicker) {
                        ModalBottomSheet(onDismissRequest = { showAppPicker = false }) {
                            var searchQuery by remember { mutableStateOf("") }
                            val installedApps = remember {
                                getInstalledApps(context)
                            }
                            
                            val filteredApps by remember {
                                derivedStateOf {
                                    if (searchQuery.isEmpty()) installedApps
                                    else installedApps.filter { it.second.contains(searchQuery, ignoreCase = true) || it.first.contains(searchQuery, ignoreCase = true) }
                                }
                            }
                            
                            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                Text("Select App", style = MaterialTheme.typography.titleLarge)
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text("Search apps...") },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                    trailingIcon = {
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { searchQuery = "" }) {
                                                Icon(Icons.Default.Close, contentDescription = "Clear")
                                            }
                                        }
                                    },
                                    shape = MaterialTheme.shapes.medium
                                )
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                LazyColumn(modifier = Modifier.heightIn(max = 500.dp)) {
                                    items(filteredApps) { pair ->
                                        val pkg = pair.first
                                        val label = pair.second
                                        val isAdded = currentApps.contains(pkg)
                                        
                                        ListItem(
                                            headlineContent = { Text(text = label, color = if (isAdded) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface) },
                                            leadingContent = { AppIcon(pkg) },
                                            trailingContent = {
                                                if (isAdded) {
                                                    Icon(Icons.Default.Check, contentDescription = "Added", tint = MaterialTheme.colorScheme.primary)
                                                }
                                            },
                                            modifier = Modifier.clickable(enabled = !isAdded) {
                                                onUpdate(currentApps + pkg)
                                                showAppPicker = false
                                            }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(32.dp))
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))

            Text("App Launcher Icon", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
            Text("Select the app launcher icon for Conduit. Changes might take a moment to reflect on your home screen launcher.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp))
            
            val iconOptions = listOf(
                Triple("LEGACY", "Legacy", MaterialTheme.colorScheme.primary),
                Triple("MANILA", "Manila", Color(0xFFD9A53C)),
                Triple("DARK", "Dark", Color(0xFF15171C)),
                Triple("BLUE", "Blue", Color(0xFF2E6FE0))
            )
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                iconOptions.forEach { (id, label, color) ->
                    val isSelected = activeAppIcon.uppercase(java.util.Locale.ROOT) == id
                    OutlinedCard(
                        onClick = { 
                            onActiveAppIconChanged(id)
                            performHapticClick(context)
                        },
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, 
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(color, shape = androidx.compose.foundation.shape.CircleShape)
                                    .border(1.5.dp, MaterialTheme.colorScheme.outline, shape = androidx.compose.foundation.shape.CircleShape)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))

            Text("Bracket (Floating Overlay) [BETA]", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 4.dp))
            Text("Note: This feature is currently in beta testing.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(bottom = 12.dp))
            
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Enable Bracket", style = MaterialTheme.typography.bodyLarge)
                    Text("Shows a persistent floating handle on the edge of your screen. Long-press to quickly launch Conduit from anywhere.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = enableBracket, onCheckedChange = onEnableBracketChanged)
            }

            if (enableBracket) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Group by Channel (Hangar)", style = MaterialTheme.typography.bodyLarge)
                        Text("When enabled, notifications inside the Hangar (the swipe-in panel on the Bracket) will be grouped by their channel type rather than listed individually.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = groupByChannel, onCheckedChange = onGroupByChannelChanged)
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Animate Icon on Notification", style = MaterialTheme.typography.bodyLarge)
                        Text("Briefly show the incoming notification's app icon next to the bracket. Tap the icon or bracket to open the notification.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = bracketNotificationPopup, onCheckedChange = onBracketNotificationPopupChanged)
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Bracket Hanger", style = MaterialTheme.typography.bodyLarge)
                        Text("Swipe inward on the bracket to pull open a quick view of pending notifications.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = bracketHangerEnabled, onCheckedChange = onBracketHangerEnabledChanged)
                }

                Spacer(modifier = Modifier.height(12.dp))
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text("Bracket Vertical Position", style = MaterialTheme.typography.bodyLarge)
                    Text("Adjust the vertical placement of the bracket along the right edge.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    androidx.compose.material3.Slider(
                        value = bracketVerticalPosition,
                        onValueChange = onBracketVerticalPositionChanged,
                        steps = 9,
                        valueRange = 0f..1f,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("Advanced Security & OTPs", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
            
            val adbCommandText = "adb shell appops set --user 0 com.conduit.app RECEIVE_SENSITIVE_NOTIFICATIONS allow"
            
            OutlinedCard(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Read Sensitive Notifications",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Android 15+ redacts verification codes (OTPs), bank alerts, and other sensitive notifications by default. To allow Conduit to read and log these messages, connect your device to a PC with ADB enabled and run the command below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = adbCommandText,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                                modifier = Modifier.weight(1f),
                                maxLines = 3
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("Conduit ADB Command", adbCommandText)
                                    clipboard.setPrimaryClip(clip)
                                    
                                    performHapticClick(context)
                                    android.widget.Toast.makeText(context, "Command copied to clipboard!", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(
                                    Icons.Filled.ContentCopy,
                                    contentDescription = "Copy command",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))

            Text("Testing & Developer Settings", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
            val devScope = rememberCoroutineScope()
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                val job = devScope.launch {
                                    kotlinx.coroutines.delay(4000)
                                    performHapticClick(context)
                                    onNavigateToDevSettings()
                                }
                                try {
                                    awaitRelease()
                                } finally {
                                    job.cancel()
                                }
                            },
                            onTap = {
                                performHapticClick(context)
                                android.widget.Toast.makeText(context, "Long press for 4 seconds to enter Developer Settings", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
            ) {
                ListItem(
                    headlineContent = { Text("Testing & Developer Settings") },
                    supportingContent = { Text("Advanced tools and options for debugging and previewing beta features.") },
                    leadingContent = { Icon(Icons.Default.Build, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = { Icon(Icons.Default.Lock, contentDescription = "Locked") }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevSettingsScreen(
    persistentTrayEnabled: Boolean,
    onPersistentTrayEnabledChanged: (Boolean) -> Unit,
    enableBubbles: Boolean,
    onEnableBubblesChanged: (Boolean) -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Testing & Developer Settings", fontSize = 20.sp, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Persistent Notification Tray", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Persistent Notification Tray", style = MaterialTheme.typography.bodyLarge)
                    Text("Keep a running summary in the system status bar at all times.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = persistentTrayEnabled, onCheckedChange = onPersistentTrayEnabledChanged)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("Conversation Bubbles", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
            
            OutlinedCard(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Bubble Mode", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text("When enabled, Conduit will post Android Bubbles for new incoming messages.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = enableBubbles, onCheckedChange = onEnableBubblesChanged)
                    }

                    AnimatedVisibility(visible = enableBubbles) {
                        Column {
                            Spacer(modifier = Modifier.height(16.dp))
                            Divider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text(
                                text = "Floating Overlay Setup",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Conduit supports Android's native conversation bubbles, allowing notification threads from active chats to float dynamically above other applications.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Text("First-Time Setup Instructions:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("• Step 1: Tap \"Open Bubble Settings\" below and verify Bubbles are allowed (set to \"All conversations\" or \"Selected\").", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("• Step 2: Tap \"Send Test Bubble\" to trigger a dynamic conversation notification thread.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("• Step 3: Swipe down your system notification tray and tap the small conversation bubble expander icon in the bottom-right corner of the Conduit notification card.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("• Step 4: Once active, the floating overlay floats on your screen and will automatically reappear for any future messages!", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        performHapticClick(context)
                                        val intent = Intent().apply {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                action = android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
                                                putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                            } else {
                                                action = "android.settings.APP_NOTIFICATION_SETTINGS"
                                                putExtra("app_package", context.packageName)
                                                putExtra("app_uid", context.applicationInfo.uid)
                                            }
                                        }
                                        try {
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            android.widget.Toast.makeText(context, "Could not open system settings.", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                                ) {
                                    Text("Open Bubble Settings", fontSize = 11.sp, maxLines = 1)
                                }
                                
                                Button(
                                    onClick = {
                                        val service = HubNotificationListenerService.instance
                                        if (service != null) {
                                            service.postBubbleNotification(
                                                context = context,
                                                packageName = "com.conduit.app",
                                                title = "Conduit Test Bubble",
                                                text = "Tap the bubble icon in the bottom-right of this notification to launch the floating overlay!"
                                            )
                                            performHapticClick(context)
                                            android.widget.Toast.makeText(context, "Test bubble notification sent!", android.widget.Toast.LENGTH_SHORT).show()
                                        } else {
                                            android.widget.Toast.makeText(context, "Notification service not running! Enable access first.", android.widget.Toast.LENGTH_LONG).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                                ) {
                                    Text("Send Test Bubble", fontSize = 11.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("Developer", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
            
            OutlinedButton(
                onClick = {
                    val database = AppDatabase.getDatabase(context)
                    val now = System.currentTimeMillis()
                    val testNotifications = listOf(
                        HubNotification(packageName = "com.google.android.apps.messaging", notificationKey = "test_sms_1", title = "Mom", text = "Hey, are you coming over for dinner tonight? Let me know!", timestamp = now - 60_000, channel = "SMS"),
                        HubNotification(packageName = "com.google.android.apps.messaging", notificationKey = "test_sms_2", title = "Alex", text = "Can you pick up groceries on the way home?", timestamp = now - 300_000, channel = "SMS"),
                        HubNotification(packageName = "com.google.android.apps.messaging", notificationKey = "test_sms_3", title = "Work Group", text = "Meeting moved to 3pm. Please update your calendars.", timestamp = now - 3_600_000, channel = "SMS"),
                        HubNotification(packageName = "com.readdle.spark", notificationKey = "test_email_1", title = "GitHub", text = "Your pull request #142 has been approved and merged into main.", timestamp = now - 120_000, channel = "EMAIL"),
                        HubNotification(packageName = "com.readdle.spark", notificationKey = "test_email_2", title = "Amazon", text = "Your order has shipped! Expected delivery: Wednesday.", timestamp = now - 7_200_000, channel = "EMAIL"),
                        HubNotification(packageName = "com.snapchat.android", notificationKey = "test_snap_1", title = "Jordan", text = "Sent you a snap! \uD83D\uDC40", timestamp = now - 180_000, channel = "SNAPCHAT"),
                        HubNotification(packageName = "com.snapchat.android", notificationKey = "test_snap_2", title = "Sarah", text = "New story available", timestamp = now - 5_400_000, channel = "SNAPCHAT"),
                        HubNotification(packageName = "com.linkedin.android", notificationKey = "test_linkedin_1", title = "LinkedIn", text = "John Smith viewed your profile. See their details.", timestamp = now - 600_000, channel = "LINKEDIN"),
                        HubNotification(packageName = "com.linkedin.android", notificationKey = "test_linkedin_2", title = "LinkedIn", text = "You have 3 new job recommendations matching your skills.", timestamp = now - 86_400_000, channel = "LINKEDIN"),
                        HubNotification(packageName = "com.instagram.android", notificationKey = "test_insta_1", title = "Instagram", text = "photography_daily liked your photo.", timestamp = now - 240_000, channel = "INSTAGRAM"),
                        HubNotification(packageName = "com.instagram.android", notificationKey = "test_insta_2", title = "Instagram", text = "travel.vibes started following you.", timestamp = now - 10_800_000, channel = "INSTAGRAM"),
                        HubNotification(packageName = "com.google.android.dialer", notificationKey = "test_phone_1", title = "Missed call", text = "Missed call from (555) 123-4567", timestamp = now - 900_000, channel = "PHONE"),
                        HubNotification(packageName = "com.google.android.dialer", notificationKey = "test_phone_2", title = "Voicemail", text = "New voicemail from Dr. Johnson's office (2:34)", timestamp = now - 43_200_000, channel = "PHONE")
                    )
                    scope.launch {
                        testNotifications.forEach { database.notificationDao().insert(it) }
                        com.conduit.app.widget.ConduitWidgetProvider.updateAllWidgets(context)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Generate Test Notifications")
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            OutlinedButton(
                onClick = {
                    val database = AppDatabase.getDatabase(context)
                    scope.launch {
                        database.notificationDao().deleteAll()
                        com.conduit.app.widget.ConduitWidgetProvider.updateAllWidgets(context)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Clear All Notifications")
            }
        }
    }
}

@Composable
fun SwipeActionSelector(
    label: String,
    currentValue: String,
    isUnifiedView: Boolean,
    onValueSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val displayNames = mapOf(
        "ARCHIVE" to if (isUnifiedView) "Mark Read" else "Clear",
        "SNOOZE" to "Snooze",
        "PIN" to "Pin / Unpin",
        "BLOCK" to "Block Notification Type"
    )
    val options = listOf("ARCHIVE", "SNOOZE", "PIN", "BLOCK")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text("Action triggered when swiping this direction", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        
        Box {
            TextButton(onClick = { expanded = true }) {
                Text(displayNames[currentValue] ?: currentValue)
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(displayNames[opt] ?: opt) },
                        onClick = {
                            onValueSelected(opt)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ArchiveScreen(
    archivedNotifications: List<HubNotification>,
    showActionChips: Boolean,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Actioned", fontSize = 22.sp, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },

                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        }
    ) { padding ->
        if (archivedNotifications.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No actioned messages.")
            }
        } else {
            val groupedNotifications = remember(archivedNotifications) {
                archivedNotifications.groupBy { formatDateHeader(it.timestamp) }
            }
            LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
                groupedNotifications.forEach { (dateHeader, itemsList) ->
                    stickyHeader {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.background)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = dateHeader,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        text = "${itemsList.size}",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }
                    items(itemsList, key = { it.id }) { notification ->
                        Box(modifier = Modifier.animateItemPlacement(
                            animationSpec = tween(durationMillis = 300)
                        )) {
                            NotificationItem(
                                notification = notification,
                                isArchivedView = true,
                                showActionChips = showActionChips
                            )
                            Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }
}

fun postPinnedNotification(context: Context, id: Int, title: String, text: String, pkg: String) {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
    val channelId = "conduit_pinned"
    
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        val channel = android.app.NotificationChannel(
            channelId,
            "Pinned Notifications",
            android.app.NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Persistent notifications pinned from Conduit"
        }
        notificationManager.createNotificationChannel(channel)
    }

    val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(title)
        .setContentText(text)
        .setOngoing(true)
        .setAutoCancel(false)
        .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
        
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

    val activeAlias = aliases[iconName.uppercase(java.util.Locale.ROOT)] ?: return
    
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
