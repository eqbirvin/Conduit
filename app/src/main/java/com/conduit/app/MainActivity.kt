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

enum class Screen { HOME, SETTINGS, ARCHIVE, DEV_SETTINGS, MANAGE_VIEWS }

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
    private var intentState by androidx.compose.runtime.mutableStateOf<Intent?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intentState = intent
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
        enableEdgeToEdge()
        intentState = intent
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
            val hubViewModel: HubViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = HubViewModel.Factory(
                    application = context.applicationContext as android.app.Application,
                    repository = com.conduit.app.data.NotificationRepository(context, AppDatabase.getDatabase(context)),
                    viewsRepository = com.conduit.app.data.ViewsRepository(context)
                )
            )
            val settingsViewModel: com.conduit.app.SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = com.conduit.app.SettingsViewModel.Factory(prefs)
            )
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
            
            val latestIntent = intentState
            LaunchedEffect(latestIntent) {
                val targetViewId = latestIntent?.getStringExtra("EXTRA_TARGET_VIEW_ID")
                if (targetViewId != null) {
                    hubViewModel.setActiveViewId(targetViewId)
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
            var showSuccessState by remember { mutableStateOf(false) }

            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        val currentlyEnabled = isNotificationServiceEnabled()
                        if (!isPermissionGranted && currentlyEnabled) {
                            showSuccessState = true
                        } else if (!currentlyEnabled) {
                            isPermissionGranted = false
                        }
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            LaunchedEffect(showSuccessState) {
                if (showSuccessState) {
                    kotlinx.coroutines.delay(1000)
                    isPermissionGranted = true
                    showSuccessState = false
                }
            }

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
                    if (!isPermissionGranted && !showSuccessState) {
                        PermissionScreen(
                            showSuccess = false,
                            onGrantClick = {
                                val intent = Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                context.startActivity(intent)
                            }
                        )
                    } else if (showSuccessState) {
                        PermissionScreen(
                            showSuccess = true,
                            onGrantClick = {}
                        )
                    } else {
                        when (currentScreen) {
                            Screen.HOME -> HubScreen(
                                viewModel = hubViewModel,
                                onNavigateToArchive = { currentScreen = Screen.ARCHIVE },
                                onNavigateToSettings = { currentScreen = Screen.SETTINGS },
                                onNavigateToManageViews = { currentScreen = Screen.MANAGE_VIEWS },
                                onUnifiedViewChanged = { settingsViewModel.updateUnifiedView(it) }
                            )
                            Screen.ARCHIVE -> ArchiveScreen(
                                archivedNotifications = filteredArchivedNotifications,
                                showActionChips = settings.showActionChips,
                                onNavigateBack = { currentScreen = Screen.HOME }
                            )
                            Screen.SETTINGS -> SettingsScreen(
                                settings = settings,
                                callbacks = object : com.conduit.app.ui.SettingsScreenCallbacks {
                                    override fun onThemeChanged(theme: Int) { settingsViewModel.updateTheme(theme) }
                                    override fun onJacobMonochromeChanged(enabled: Boolean) { settingsViewModel.updateJacobMonochrome(enabled) }
                                    override fun onGroupByChannelChanged(enabled: Boolean) { settingsViewModel.updateGroupByChannel(enabled) }
                                    override fun onChannelToggled(prefKey: String, isEnabled: Boolean) { settingsViewModel.updateChannelState(prefKey, isEnabled) }
                                    override fun onSyncDismissalChanged(enabled: Boolean) { settingsViewModel.updateSyncDismissal(enabled) }
                                    override fun onSyncPinnedChanged(enabled: Boolean) { settingsViewModel.updateSyncPinned(enabled) }
                                    override fun onShowActionChipsChanged(enabled: Boolean) { settingsViewModel.updateShowActionChips(enabled) }
                                    override fun onAiBundleChanged(bundle: List<String>) { settingsViewModel.updateAiBundle(bundle) }
                                    override fun onNotesBundleChanged(bundle: List<String>) { settingsViewModel.updateNotesBundle(bundle) }
                                    override fun onRecorderBundleChanged(bundle: List<String>) { settingsViewModel.updateRecorderBundle(bundle) }
                                    override fun onComposeBundleChanged(bundle: List<String>) { settingsViewModel.updateComposeBundle(bundle) }
                                    override fun onDockLongPressLaunchChanged(enabled: Boolean) { settingsViewModel.updateDockLongPressLaunch(enabled) }
                                    override fun onDockScrollIndicatorChanged(indicator: String) { settingsViewModel.updateDockScrollIndicator(indicator) }
                                    override fun onSwipeLeftActionChanged(action: String) { settingsViewModel.updateSwipeLeftAction(action) }
                                    override fun onSwipeRightActionChanged(action: String) { settingsViewModel.updateSwipeRightAction(action) }
                                    override fun onDockSizeChanged(size: Int) { settingsViewModel.updateDockSize(size) }
                                    override fun onEnableBracketChanged(enabled: Boolean) {
                                        settingsViewModel.updateEnableBracket(enabled)
                                        if (enabled && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(this@MainActivity)) {
                                            val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName"))
                                            startActivity(intent)
                                        }
                                    }
                                    override fun onBracketNotificationPopupChanged(enabled: Boolean) { settingsViewModel.updateBracketNotificationPopup(enabled) }
                                    override fun onBracketHangerEnabledChanged(enabled: Boolean) { settingsViewModel.updateBracketHangerEnabled(enabled) }
                                    override fun onBracketVerticalPositionChanged(position: Float) { settingsViewModel.updateBracketVerticalPosition(position) }
                                    override fun onUnifiedViewChanged(enabled: Boolean) { settingsViewModel.updateUnifiedView(enabled) }
                                    override fun onActiveAppIconChanged(icon: String) { 
                                        settingsViewModel.updateActiveAppIcon(icon)
                                        changeAppIcon(context, icon)
                                    }
                                    override fun onSmartMarkReadChanged(enabled: Boolean) { settingsViewModel.updateSmartMarkRead(enabled) }
                                    override fun onSmartMarkReadTargetChanged(target: String) { settingsViewModel.updateSmartMarkReadTarget(target) }
                                    override fun onRetentionDaysChanged(days: Int) { settingsViewModel.updateRetentionDays(days) }
                                    override fun onEnableAppBundlesChanged(enabled: Boolean) { settingsViewModel.updateEnableAppBundles(enabled) }
                                    override fun onMinimizeIconsChanged(enabled: Boolean) {
                                        settingsViewModel.updateMinimizeIcons(enabled)
                                    }
                                    override fun onAutoCollapseReadChanged(enabled: Boolean) {
                                        settingsViewModel.updateAutoCollapseRead(enabled)
                                    }
                                    override fun onAutoDismissDetachedChanged(enabled: Boolean) {
                                        settingsViewModel.updateAutoDismissDetached(enabled)
                                    }
                                    override fun onShowWhatsNew() { showWhatsNewDialog = true }
                                    override fun onNavigateToDevSettings() { currentScreen = Screen.DEV_SETTINGS }
                                },
                                onNavigateBack = { currentScreen = Screen.HOME }
                            )
                            Screen.DEV_SETTINGS -> com.conduit.app.ui.DevSettingsScreen(
                                settings = settings,
                                callbacks = object : com.conduit.app.ui.DevSettingsScreenCallbacks {
                                    override fun onPersistentTrayEnabledChanged(enabled: Boolean) { settingsViewModel.updatePersistentTrayEnabled(enabled) }
                                    override fun onEnableBubblesChanged(enabled: Boolean) { settingsViewModel.updateEnableBubbles(enabled) }
                                },
                                onNavigateBack = { currentScreen = Screen.SETTINGS }
                            )
                            Screen.MANAGE_VIEWS -> com.conduit.app.ui.ManageViewsScreen(
                                hubViewModel = hubViewModel,
                                onNavigateBack = { currentScreen = Screen.HOME }
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

