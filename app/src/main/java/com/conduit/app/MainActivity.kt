@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.conduit.app.ui.*
import com.conduit.app.ui.theme.ConduitTheme
import com.conduit.app.ui.HubScreen

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
            var unreadReleases by remember { mutableStateOf<List<ChangelogRelease>>(emptyList()) }
            var showWhatsNewDialog by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                val currentVersion = BuildConfig.VERSION_NAME
                val lastRun = prefs.getString("last_run_version", null)
                if (lastRun == null) {
                    prefs.edit().putString("last_run_version", currentVersion).apply()
                } else if (isVersionNewer(currentVersion, lastRun)) {
                    val newerEntries = CHANGELOG.filter { isVersionNewer(it.versionName, lastRun) }
                    if (newerEntries.isNotEmpty()) {
                        unreadReleases = newerEntries
                        showWhatsNewDialog = true
                    } else {
                        prefs.edit().putString("last_run_version", currentVersion).apply()
                    }
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
            val notifications by database.notificationDao().getAllNotifications().collectAsStateWithLifecycle(initialValue = emptyList())
            val archivedNotifications by database.notificationDao().getArchivedNotifications().collectAsStateWithLifecycle(initialValue = emptyList())
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
                    
                    if (showWhatsNewDialog && unreadReleases.isNotEmpty()) {
                        WhatsNewDialog(
                            releases = unreadReleases,
                            onDismiss = {
                                prefs.edit().putString("last_run_version", BuildConfig.VERSION_NAME).apply()
                                showWhatsNewDialog = false
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

