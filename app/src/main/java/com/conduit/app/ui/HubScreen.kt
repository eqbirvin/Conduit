@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.conduit.app.ui



import com.conduit.app.*
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun HubScreen(
    viewModel: com.conduit.app.HubViewModel,
    onNavigateToArchive: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToManageViews: () -> Unit,
    onUnifiedViewChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("conduit_prefs", android.content.Context.MODE_PRIVATE) }
    
    val settingsViewModel: com.conduit.app.SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = com.conduit.app.SettingsViewModel.Factory(prefs)
    )
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val archivedNotifications by viewModel.archivedNotifications.collectAsStateWithLifecycle()
    val actionsByKey by viewModel.actionsByKey.collectAsStateWithLifecycle()

    val channelStates = settings.channelStates
    val fabConfigs = settings.fabConfigs
    val onSaveFabConfigs: (List<com.conduit.app.FabAction>) -> Unit = { configs -> settingsViewModel.updateFabConfigs(configs) }
    val aiBundle = settings.aiBundle
    val notesBundle = settings.notesBundle
    val recorderBundle = settings.recorderBundle
    val composeBundle = settings.composeBundle
    val showActionChips = settings.showActionChips
    val dockSizeIndex = settings.dockSizeIndex
    val unifiedView = settings.unifiedView

    val onArchiveNotification: (Int, Long) -> Unit = { id, ts -> viewModel.archiveNotification(id, ts) }
    val onSnoozeNotification: (Int, Long) -> Unit = { id, ts ->  }
    val onPinNotification: (HubNotification) -> Unit = { notif ->  }
    var showBundleMenu by remember { mutableStateOf<Pair<String, List<String>>?>(null) }
    var showCustomizeFab by remember { mutableStateOf<FabAction?>(null) }
    
    var isFabExpanded by remember { mutableStateOf(prefs.getBoolean("fab_expanded", true)) }
    val masterExpandedState = settings.masterExpandedState
    val individualToggles = remember { androidx.compose.runtime.mutableStateMapOf<Int, Boolean>() }
    
    var selectedDockPackage by remember { mutableStateOf<String?>(null) }
    
    val views by viewModel.viewsRepository.views.collectAsStateWithLifecycle()
    val activeViewId by viewModel.activeViewId.collectAsStateWithLifecycle()
    val activeView = remember(views, activeViewId) { views.find { it.id == activeViewId } }
    val activeViewDockFilter = activeView?.filterDock == true
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
                            val idsToPin = selectedIds.toList()
                            val anyUnpinned = notifications.filter { it.id in selectedIds }.any { !it.isPinned }
                            kotlinx.coroutines.GlobalScope.launch {
                                val database = AppDatabase.getDatabase(context)
                                database.notificationDao().pinNotifications(idsToPin, anyUnpinned)
                                com.conduit.app.widget.WidgetUpdater.updateAllWidgets(context)
                            }
                            performHapticClick(context)
                            selectedIds = emptySet()
                        }) {
                            Icon(Icons.Filled.PushPin, contentDescription = "Pin/Unpin selected")
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
                                com.conduit.app.widget.WidgetUpdater.updateAllWidgets(context)
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
                    navigationIcon = {
                        Row {
                            IconButton(onClick = {
                                performHapticTick(context)
                                onNavigateToSettings()
                            }) {
                                Icon(Icons.Filled.SettingsIcon, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurface)
                            }
                            IconButton(onClick = {
                                performHapticTick(context)
                                onNavigateToManageViews()
                            }) {
                                Icon(Icons.Filled.FilterList, contentDescription = "Manage Views", tint = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            val newState = !masterExpandedState
                            settingsViewModel.updateMasterExpandedState(newState)
                            individualToggles.clear()
                            performHapticClick(context)
                        }) {
                            Icon(
                                imageVector = if (masterExpandedState) Icons.Filled.Compress else Icons.Filled.Expand,
                                contentDescription = "Toggle Master Expand",
                                tint = if (masterExpandedState) MaterialTheme.colorScheme.secondary else LocalContentColor.current
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
                        
                        IconButton(onClick = {
                            performHapticTick(context)
                            isSearchMode = true
                        }) {
                            Icon(Icons.Filled.Search, contentDescription = "Search")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        },
        bottomBar = {
            if (!isSelectionMode && !isSearchMode) {
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
                if (notifications.isNotEmpty() || (unifiedView && enabledApps.isNotEmpty()) || (activeViewDockFilter && activeView != null && activeView.packageNames.isNotEmpty())) {
                    val grouped = remember(notifications) {
                        notifications.groupBy { getRepresentativePackage(context, it.packageName) }
                    }
                    val dockPackagesList = remember(grouped, enabledApps, unifiedView, activeView, activeViewDockFilter) {
                        if (activeViewDockFilter && activeView != null) {
                            val viewApps = activeView.packageNames.filter { enabledApps.contains(it) }
                            val unread = viewApps.filter { grouped.containsKey(it) }
                            val read = viewApps.filter { !grouped.containsKey(it) }
                            (unread + read).distinct()
                        } else if (unifiedView) {
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
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier.wrapContentWidth(),
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shadowElevation = 8.dp
                        ) {
                            LazyRow(
                                modifier = Modifier.padding(vertical = 1.dp).wrapContentWidth(),
                                contentPadding = PaddingValues(horizontal = dockPaddingHorizontal, vertical = dockPaddingVertical), 
                                horizontalArrangement = Arrangement.spacedBy(dockSpacing, Alignment.CenterHorizontally),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                items(dockPackagesList.size) { index ->
                                    val pkg = dockPackagesList[index]
                                    val groupNotifs = grouped[pkg] ?: emptyList()
                                    
                                    if ((unifiedView || activeViewDockFilter) && index > 0) {
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
                                            .padding(dockBoxPadding)
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
            }
        },
        floatingActionButton = {
            if (!isSelectionMode && !isSearchMode && settings.enableAppBundles) {
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
                                                                com.conduit.app.widget.WidgetUpdater.updateAllWidgets(context)
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
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize()) {
            if (views.isNotEmpty()) {
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    views.forEach { view ->
                        val isSelected = activeViewId == view.id
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                performHapticTick(context)
                                if (isSelected) {
                                    viewModel.setActiveViewId(null)
                                } else {
                                    viewModel.setActiveViewId(view.id)
                                }
                            },
                            label = { Text(view.name) },
                            leadingIcon = if (isSelected) {
                                { Icon(Icons.Filled.Check, contentDescription = "Selected", modifier = Modifier.size(18.dp)) }
                            } else null
                        )
                    }
                }
            }

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
                    val screenWidthPx = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 16.dp)
                    ) {
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
                            Box(modifier = Modifier.animateItem(
                                placementSpec = tween(durationMillis = 300)
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

                                    showActionChips = showActionChips,
                                    minimizeIcons = settings.minimizeIcons,
                                    allActions = actionsByKey[notification.notificationKey],
                                    isExpanded = individualToggles[notification.id] 
                                        ?: if (settings.autoCollapseRead && notification.isArchived) false else masterExpandedState,
                                    onExpandToggle = {
                                        val currentState = individualToggles[notification.id] 
                                            ?: if (settings.autoCollapseRead && notification.isArchived) false else masterExpandedState
                                        individualToggles[notification.id] = !currentState
                                        performHapticTick(context)
                                    },
                                    onTriggerAction = { action -> viewModel.triggerAction(notification, action) },
                                    onReply = { text, action -> viewModel.sendReply(notification, text, action) }
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
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = dateHeader,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (unifiedView) {
                                        val pendingCount = itemsList.count { notifications.contains(it) }
                                        val chipText = if (pendingCount > 0) "${itemsList.size} | UNREAD $pendingCount" else "${itemsList.size}"
                                        
                                        Row(
                                            modifier = Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(1.dp)
                                        ) {
                                            Surface(
                                                modifier = Modifier.fillMaxHeight(),
                                                shape = if (pendingCount > 0) androidx.compose.foundation.shape.RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 0.dp, bottomEnd = 0.dp) else MaterialTheme.shapes.small,
                                                color = MaterialTheme.colorScheme.secondaryContainer
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = chipText,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                                    )
                                                }
                                            }
                                            
                                            if (pendingCount > 0) {
                                                val rightShape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 8.dp, bottomEnd = 8.dp)
                                                Surface(
                                                    shape = rightShape,
                                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                                    modifier = Modifier
                                                        .fillMaxHeight()
                                                        .clip(rightShape)
                                                        .clickable {
                                                            performHapticClick(context)
                                                            val now = System.currentTimeMillis()
                                                            val idsToArchive = itemsList.filter { notifications.contains(it) }.map { it.id }
                                                            if (idsToArchive.isNotEmpty()) {
                                                                viewModel.archiveMany(idsToArchive)
                                                            }
                                                        }
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = Icons.Filled.DoneAll,
                                                            contentDescription = "Mark all as read",
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp).size(14.dp),
                                                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        items(itemsList, key = { it.id }) { notification ->
                            class StateHolder(var state: SwipeToDismissBoxState? = null)
                            val stateHolder = remember { StateHolder() }
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    if (value == SwipeToDismissBoxValue.Settled) {
                                        return@rememberSwipeToDismissBoxState true
                                    }
                                    
                                    val currentState = stateHolder.state
                                    if (currentState != null) {
                                        val offset = kotlin.math.abs(currentState.requireOffset())
                                        if (offset < screenWidthPx * 0.3f) {
                                            return@rememberSwipeToDismissBoxState false
                                        }
                                    }
                                    val action = if (value == SwipeToDismissBoxValue.StartToEnd) swipeRightAction else swipeLeftAction
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
                                positionalThreshold = { distance -> distance * 0.4f }
                            )
                            stateHolder.state = dismissState
                            
                            LaunchedEffect(dismissState.targetValue) {
                                if (dismissState.targetValue != SwipeToDismissBoxValue.Settled) {
                                    performHapticTick(context)
                                }
                            }
                            
                            val currentViewConfiguration = LocalViewConfiguration.current
                            val customViewConfiguration = remember(currentViewConfiguration) {
                                object : ViewConfiguration by currentViewConfiguration {
                                    override val touchSlop: Float
                                        get() = currentViewConfiguration.touchSlop * 2.5f
                                }
                            }
                            
                            CompositionLocalProvider(LocalViewConfiguration provides customViewConfiguration) {
                                SwipeToDismissBox(
                                state = dismissState,
                                modifier = Modifier.animateItem(
                                    placementSpec = tween(durationMillis = 300)
                                ),
                                backgroundContent = {
                                    val direction = dismissState.dismissDirection
                                    if (direction == SwipeToDismissBoxValue.Settled) return@SwipeToDismissBox
                                    val action = if (direction == SwipeToDismissBoxValue.StartToEnd) swipeRightAction else swipeLeftAction
                                    
                                    val color = when (action) {
                                        "ARCHIVE" -> MaterialTheme.colorScheme.primaryContainer
                                        "SNOOZE" -> Color(0xFFFF9800)
                                        "PIN" -> MaterialTheme.colorScheme.secondaryContainer
                                        "BLOCK" -> MaterialTheme.colorScheme.errorContainer
                                        else -> Color.Gray
                                    }
                                    
                                    val alignment = if (direction == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
                                    
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
                                        if (dismissState.targetValue == SwipeToDismissBoxValue.Settled) 0.75f else 1.25f
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
                                content = {
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

                                            showActionChips = showActionChips,
                                            minimizeIcons = settings.minimizeIcons,
                                            allActions = actionsByKey[notification.notificationKey],
                                            isExpanded = individualToggles[notification.id] 
                                                ?: if (settings.autoCollapseRead && notification.isArchived) false else masterExpandedState,
                                            onExpandToggle = {
                                                val currentState = individualToggles[notification.id] 
                                                    ?: if (settings.autoCollapseRead && notification.isArchived) false else masterExpandedState
                                                individualToggles[notification.id] = !currentState
                                                performHapticTick(context)
                                            },
                                            onTriggerAction = { action -> viewModel.triggerAction(notification, action) },
                                            onReply = { text, action -> viewModel.sendReply(notification, text, action) }
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
            }
            
            // The Floating Dock
            // The Floating Dock (moved to bottomBar)
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
                                        com.conduit.app.widget.WidgetUpdater.updateAllWidgets(context)
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


