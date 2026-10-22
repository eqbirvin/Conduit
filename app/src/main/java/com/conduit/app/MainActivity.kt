@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.conduit.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.conduit.app.data.AppDatabase
import com.conduit.app.ui.*
import com.conduit.app.ui.settings.SettingsNavHost
import com.conduit.app.ui.theme.ConduitTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val ACTION_OPEN_SETTINGS = "com.conduit.app.OPEN_SETTINGS"

enum class Screen {
    HOME,
    SETTINGS,
    ARCHIVE,
    DEV_SETTINGS,
    MANAGE_VIEWS,
    WHATS_NEW,
    CHANNELS,
    SETTINGS_APPEARANCE,
    SETTINGS_DOCK,
    SETTINGS_NOTIFICATIONS,
    SETTINGS_DATA,
    SETTINGS_BRACKET,
    SETTINGS_ABOUT
}

class MainActivity : ComponentActivity() {
    private var intentState by mutableStateOf<Intent?>(null)
    // Set by the "Settings" launcher shortcut; consumed once so recreation doesn't re-open Settings.
    private var pendingOpenSettings by mutableStateOf(false)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intentState = intent
        if (intent.action == ACTION_OPEN_SETTINGS) pendingOpenSettings = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
        enableEdgeToEdge()
        intentState = intent
        if (savedInstanceState == null && intent?.action == ACTION_OPEN_SETTINGS) pendingOpenSettings = true
        setContent {
            val context = LocalContext.current
            val prefs = remember { context.getSharedPreferences("conduit_prefs", Context.MODE_PRIVATE) }
            val settingsRepo = remember { com.conduit.app.data.SettingsRepository(prefs) }
            var unreadReleases by remember { mutableStateOf<List<ChangelogRelease>>(emptyList()) }
            var showWhatsNewDialog by remember { mutableStateOf(false) }
            var showImportPrompt by remember { mutableStateOf(false) }
            var showImportLoading by remember { mutableStateOf(false) }

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
                
                val currentInterval = prefs.getString("update_interval", "DAILY") ?: "DAILY"
                com.conduit.app.updater.UpdateManager.setupWorker(context, currentInterval)
                
                if (settingsRepo.settings.value.enableAppBundles) {
                    settingsRepo.seedDefaultAppBundlesIfFirstTime(context.applicationContext)
                }
                
                if (!prefs.getBoolean("defaults_seeded", false)) {
                    val currentRules = prefs.getStringSet("blocked_rules", emptySet()) ?: emptySet()
                    val defaultRules = setOf(
                        "com.snapchat.android|TITLE|Running...",
                        "com.snapchat.android|TITLE|Updating messages...",
                        "com.snapchat.android|TEXT|is typing...",
                        "com.google.android.apps.messaging|TITLE|Getting things ready...",
                        "com.google.android.apps.messaging|TEXT|Your messages are available on the device you've paired",
                        "com.textra|TEXT|Sent"
                    )
                    val updatedRules = currentRules + defaultRules
                    prefs.edit()
                        .putStringSet("blocked_rules", updatedRules)
                        .putBoolean("defaults_seeded", true)
                        .apply()
                        
                    withContext(Dispatchers.IO) {
                        val db = AppDatabase.getDatabase(context)
                        val allNotifs = db.notificationDao().getAllNotificationsSync()
                        val idsToDelete = mutableListOf<Int>()
                        val keysToCancel = mutableListOf<String>()
                        
                        defaultRules.forEach { rule ->
                            val parts = rule.split("|", limit = 3)
                            if (parts.size == 3) {
                                val pkg = parts[0]
                                val type = parts[1]
                                val pattern = parts[2]
                                
                                allNotifs.forEach { item ->
                                    if (item.packageName == pkg) {
                                        val match = if (type == "TITLE") {
                                            item.title?.contains(pattern, ignoreCase = true) == true
                                        } else {
                                            item.text?.contains(pattern, ignoreCase = true) == true
                                        }
                                        if (match) {
                                            idsToDelete.add(item.id)
                                            keysToCancel.add(item.notificationKey)
                                        }
                                    }
                                }
                            }
                        }
                        
                        if (idsToDelete.isNotEmpty()) {
                            keysToCancel.distinct().forEach { key ->
                                HubNotificationListenerService.instance?.cancel(key)
                            }
                            db.notificationDao().deleteNotifications(idsToDelete.distinct())
                            com.conduit.app.widget.WidgetUpdater.updateAllWidgets(context)
                        }
                    }
                }
            }
            val hubViewModel: HubViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = HubViewModel.Factory(
                    application = context.applicationContext as android.app.Application,
                    repository = com.conduit.app.data.NotificationRepository(context, AppDatabase.getDatabase(context), settingsRepo),
                    viewsRepository = com.conduit.app.data.ViewsRepository(context)
                )
            )
            val settingsViewModel: SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = SettingsViewModel.Factory(settingsRepo)
            )
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

            LaunchedEffect(Unit) {
                if (prefs.getBoolean("default_to_todo_mode", false)) {
                    settingsViewModel.updateUnifiedView(false)
                }
            }
            
            val latestIntent = intentState
            LaunchedEffect(latestIntent) {
                val targetViewId = latestIntent?.getStringExtra("EXTRA_TARGET_VIEW_ID")
                if (targetViewId != null) {
                    hubViewModel.setActiveViewId(targetViewId)
                }
            }

            var currentScreen by remember { mutableStateOf(Screen.HOME) }
            var whatsNewOriginScreen by remember { mutableStateOf(Screen.SETTINGS_ABOUT) }
            LaunchedEffect(pendingOpenSettings) {
                if (pendingOpenSettings) {
                    currentScreen = Screen.SETTINGS
                    pendingOpenSettings = false
                }
            }
            var isPermissionGranted by remember { mutableStateOf(isNotificationServiceEnabled()) }
            var isRestricted by remember { mutableStateOf(isRestrictedSettingsGuarded(context)) }
            var showSuccessState by remember { mutableStateOf(false) }

            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        val currentlyEnabled = isNotificationServiceEnabled()
                        isRestricted = isRestrictedSettingsGuarded(context)
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
                    delay(1000)
                    isPermissionGranted = true
                    showSuccessState = false
                }
            }

            LaunchedEffect(isPermissionGranted, currentScreen, showSuccessState) {
                if (isPermissionGranted && !showSuccessState && currentScreen == Screen.HOME) {
                    val hasPrompted = prefs.getBoolean("has_prompted_import", false)
                    if (!hasPrompted) {
                        showImportPrompt = true
                    }
                }
            }

            if (currentScreen != Screen.HOME) {
                BackHandler {
                    currentScreen = when (currentScreen) {
                        Screen.DEV_SETTINGS -> Screen.SETTINGS_ABOUT
                        Screen.WHATS_NEW -> whatsNewOriginScreen
                        Screen.CHANNELS,
                        Screen.SETTINGS_APPEARANCE,
                        Screen.SETTINGS_DOCK,
                        Screen.SETTINGS_NOTIFICATIONS,
                        Screen.SETTINGS_DATA,
                        Screen.SETTINGS_BRACKET,
                        Screen.SETTINGS_ABOUT -> Screen.SETTINGS
                        else -> Screen.HOME
                    }
                }
            }

            // Observe notifications from Room
            val database = remember { AppDatabase.getDatabase(context) }
            val archivedNotifications by database.notificationDao().getArchivedNotifications(settings.demoModeEnabled).collectAsStateWithLifecycle(initialValue = emptyList())
            val coroutineScope = rememberCoroutineScope()

            ConduitTheme(
                themePreference = settings.themePreference,
                jacobMonochrome = settings.jacobMonochrome,
                jacobFollowSystemDark = settings.jacobFollowSystemDark
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (!isPermissionGranted && !showSuccessState) {
                        PermissionScreen(
                            showSuccess = false,
                            isRestricted = isRestricted,
                            onOpenAppSettings = {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = android.net.Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            },
                            onGrantClick = {
                                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
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
                                archivedNotifications = archivedNotifications,
                                showActionChips = settings.showActionChips,
                                onNavigateBack = { currentScreen = Screen.HOME }
                            )
                            Screen.MANAGE_VIEWS -> ManageViewsScreen(
                                hubViewModel = hubViewModel,
                                onNavigateBack = { currentScreen = Screen.HOME }
                            )
                            Screen.WHATS_NEW -> WhatsNewScreen(
                                onNavigateBack = { currentScreen = whatsNewOriginScreen }
                            )
                            else -> SettingsNavHost(
                                currentScreen = currentScreen,
                                settings = settings,
                                settingsViewModel = settingsViewModel,
                                onNavigateToScreen = {
                                    if (it == Screen.WHATS_NEW) {
                                        whatsNewOriginScreen = currentScreen
                                    }
                                    currentScreen = it
                                },
                                onNavigateBackToSettings = { currentScreen = Screen.SETTINGS },
                                onNavigateHome = { currentScreen = Screen.HOME }
                            )
                        }
                    }
                    
                    if (showImportPrompt) {
                        AlertDialog(
                            onDismissRequest = {
                                showImportPrompt = false
                                prefs.edit().putBoolean("has_prompted_import", true).apply()
                            },
                            title = { Text("Import Current Notifications?", fontWeight = FontWeight.Bold) },
                            text = { Text("Would you like to import your current notifications? Conduit can safely grab any pending messages for supported channels from your notification tray.\n\nThis is a one-time prompt since Conduit was not capturing notifications that posted prior to installation and setup.") },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        showImportPrompt = false
                                        showImportLoading = true
                                        prefs.edit().putBoolean("has_prompted_import", true).apply()
                                        coroutineScope.launch {
                                            var retries = 0
                                            while (HubNotificationListenerService.instance == null && retries < 20) {
                                                delay(100)
                                                retries++
                                            }
                                            HubNotificationListenerService.instance?.importActiveNotifications()
                                            delay(1500)
                                            showImportLoading = false
                                        }
                                    }
                                ) {
                                    Text("Import")
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = {
                                        showImportPrompt = false
                                        prefs.edit().putBoolean("has_prompted_import", true).apply()
                                    }
                                ) {
                                    Text("Skip")
                                }
                            }
                        )
                    }

                    if (showImportLoading) {
                        AlertDialog(
                            onDismissRequest = { },
                            title = { Text("Importing...", fontWeight = FontWeight.Bold) },
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                                ) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                }
                            },
                            confirmButton = {}
                        )
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

    private fun isRestrictedSettingsGuarded(context: Context = this): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? android.app.AppOpsManager
                ?: return false
            val mode = appOps.unsafeCheckOpNoThrow(
                "android:access_restricted_settings",
                context.applicationInfo.uid,
                context.packageName
            )
            mode != android.app.AppOpsManager.MODE_ALLOWED
        } catch (e: SecurityException) {
            android.util.Log.w("MainActivity", "SecurityException checking restricted settings op", e)
            false
        } catch (e: IllegalArgumentException) {
            android.util.Log.w("MainActivity", "Op not recognized on device", e)
            false
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "Failed to check restricted settings op", e)
            false
        }
    }
}
