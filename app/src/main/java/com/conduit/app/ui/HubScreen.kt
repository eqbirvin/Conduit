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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.IntOffset
import androidx.compose.material.icons.Icons
import androidx.compose.animation.*
import android.app.Notification
import android.app.RemoteInput
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.Settings as SettingsIcon
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.paging.LoadState
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
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlin.math.roundToInt

import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Density

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
        factory = com.conduit.app.SettingsViewModel.Factory(com.conduit.app.data.SettingsRepository(prefs))
    )
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val archivedNotifications by viewModel.archivedNotifications.collectAsStateWithLifecycle()
    val actionsByKey by viewModel.actionsByKey.collectAsStateWithLifecycle()

    val channelStates = settings.channelStates
    val aiBundle = settings.aiBundle
    val notesBundle = settings.notesBundle
    val recorderBundle = settings.recorderBundle
    val composeBundle = settings.composeBundle
    val showActionChips = settings.showActionChips
    val dockSizeIndex = settings.dockSizeIndex
    val dockScrollIndicator = settings.dockScrollIndicator
    val unifiedView = settings.unifiedView

    val onArchiveNotification: (Int, Long) -> Unit = { id, ts -> viewModel.archiveNotification(id, ts) }
    val onSnoozeNotification: (Int, Long) -> Unit = { id, ts ->  }
    val onPinNotification: (HubNotification) -> Unit = { notif -> viewModel.togglePin(notif, settings.syncPinned) }
    var showBundleMenu by remember { mutableStateOf<Pair<String, List<String>>?>(null) }
    var isBundleFlyoutExpanded by remember { mutableStateOf(false) }
    var editingBundleTitle by remember { mutableStateOf<String?>(null) }
    
    val launchAppOrAction: (String) -> Unit = { pkg ->
        performHapticTick(context)
        isBundleFlyoutExpanded = false
        if (pkg.startsWith("conduit.action.")) {
            try {
                val intent = ComposeActionLauncher.createIntent(context, pkg)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                } else {
                    android.widget.Toast.makeText(context, "Unknown action: $pkg", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("HubScreen", "Failed to launch intent action: $pkg", e)
                android.widget.Toast.makeText(context, "No app available to handle this action.", android.widget.Toast.LENGTH_SHORT).show()
            }
        } else {
            val launched = launchApp(context, pkg)
            if (!launched) {
                val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    if (intent != null) context.startActivity(intent)
                } catch (e: Exception) {
                    android.util.Log.e("HubScreen", "Failed to launch app: $pkg", e)
                }
            }
        }
    }

    val launchBundleAction: (String, List<String>) -> Unit = { title, bundle ->
        performHapticTick(context)
        isBundleFlyoutExpanded = false
        if (bundle.isNotEmpty()) {
            launchAppOrAction(bundle.first())
        } else {
            editingBundleTitle = title
        }
    }
    
    val hasUpdateAvailable = settings.hasUpdateAvailable
    val latestVersionAvailable = settings.latestVersionAvailable
    var showUpdateDialog by remember { mutableStateOf(false) }
    
    val masterExpandedState = settings.masterExpandedState
    val individualToggles = remember { androidx.compose.runtime.mutableStateMapOf<Int, Boolean>() }
    val conversationRollupStates = remember { androidx.compose.runtime.mutableStateMapOf<Int, ConversationRollupState>() }

    fun getRollupState(clusterId: Int): ConversationRollupState {
        return conversationRollupStates[clusterId] ?: if (!masterExpandedState) {
            ConversationRollupState.FULLY_COLLAPSED
        } else {
            ConversationRollupState.PARTIALLY_EXPANDED
        }
    }

    fun cycleRollupState(clusterId: Int) {
        val currentState = getRollupState(clusterId)
        val nextState = when (currentState) {
            ConversationRollupState.PARTIALLY_EXPANDED -> ConversationRollupState.ALL_EXPANDED
            ConversationRollupState.ALL_EXPANDED -> ConversationRollupState.FULLY_COLLAPSED
            ConversationRollupState.FULLY_COLLAPSED -> ConversationRollupState.PARTIALLY_EXPANDED
        }
        conversationRollupStates[clusterId] = nextState
    }
    
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
    val searchItems = viewModel.searchResults.collectAsLazyPagingItems()
    val searchCorrection by viewModel.searchCorrectionState.collectAsStateWithLifecycle()

    var activeSingleConversationKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedDockPackage, activeViewId, isSearchMode) {
        activeSingleConversationKey = null
    }

    // Clear selection, conversation view, or exit search on back press
    BackHandler(enabled = isSelectionMode || isSearchMode || activeSingleConversationKey != null) {
        if (isSelectionMode) {
            selectedIds = emptySet()
        } else if (activeSingleConversationKey != null) {
            activeSingleConversationKey = null
        } else {
            isSearchMode = false
            searchQuery = ""
            viewModel.setSearchQuery("")
        }
    }
    val displayNotifications = remember(notifications, archivedNotifications, selectedDockPackage, unifiedView) {
        if (selectedDockPackage != null) {
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

    val mainListState = androidx.compose.foundation.lazy.rememberLazyListState()
    var headerHeightPx by remember { mutableFloatStateOf(0f) }
    var headerOffsetPx by remember { mutableFloatStateOf(0f) }

    val nestedScrollConnection = remember(settings.scrollTopBarWithFeed, settings.topBarScrollBehavior, activeSingleConversationKey) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!settings.scrollTopBarWithFeed || isSelectionMode || isSearchMode || activeSingleConversationKey != null) {
                    return Offset.Zero
                }
                val delta = available.y
                if (delta < 0f) {
                    val newOffset = (headerOffsetPx + delta).coerceIn(-headerHeightPx, 0f)
                    val consumed = newOffset - headerOffsetPx
                    headerOffsetPx = newOffset
                    return Offset(0f, consumed)
                } else if (delta > 0f) {
                    if (settings.topBarScrollBehavior == "QUICK_RETURN") {
                        val newOffset = (headerOffsetPx + delta).coerceIn(-headerHeightPx, 0f)
                        val consumed = newOffset - headerOffsetPx
                        headerOffsetPx = newOffset
                        return Offset(0f, consumed)
                    } else {
                        if (mainListState.firstVisibleItemIndex == 0) {
                            val newOffset = (headerOffsetPx + delta).coerceIn(-headerHeightPx, 0f)
                            val consumed = newOffset - headerOffsetPx
                            headerOffsetPx = newOffset
                            return Offset(0f, consumed)
                        }
                    }
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(isSelectionMode, isSearchMode, settings.scrollTopBarWithFeed) {
        if (isSelectionMode || isSearchMode || !settings.scrollTopBarWithFeed) {
            headerOffsetPx = 0f
        }
    }

    LaunchedEffect(mainListState.firstVisibleItemIndex, mainListState.firstVisibleItemScrollOffset) {
        if (mainListState.firstVisibleItemIndex == 0 && mainListState.firstVisibleItemScrollOffset == 0) {
            headerOffsetPx = 0f
        }
    }

    val topBarState = remember(
        isSelectionMode, isSearchMode, selectedIds, notifications, archivedNotifications,
        searchQuery, hasUpdateAvailable, masterExpandedState, unifiedView
    ) {
        val singleNotif = if (selectedIds.size == 1) {
            val notifId = selectedIds.first()
            notifications.find { it.id == notifId } ?: archivedNotifications.find { it.id == notifId }
        } else null
        HubTopBarState(
            isSelectionMode = isSelectionMode,
            isSearchMode = isSearchMode,
            selectedCount = selectedIds.size,
            selectedNotification = singleNotif,
            searchQuery = searchQuery,
            hasUpdateAvailable = hasUpdateAvailable,
            masterExpandedState = masterExpandedState,
            unifiedView = unifiedView
        )
    }

    val topBarCallbacks = remember(
        context, viewModel, settingsViewModel, onNavigateToSettings, onNavigateToManageViews,
        onNavigateToArchive, onUnifiedViewChanged
    ) {
        object : HubTopBarCallbacks {
            override fun onClearSelection() {
                selectedIds = emptySet()
            }
            override fun onBlockNotification(notification: HubNotification) {
                notificationToBlock = notification
            }
            override fun onSnoozeNotification(notification: HubNotification) {
                notificationToSnooze = notification
            }
            override fun onPinSelected() {
                val idsToPin = selectedIds.toList()
                val anyUnpinned = notifications.filter { it.id in selectedIds }.any { !it.isPinned }
                viewModel.viewModelScope.launch(Dispatchers.IO) {
                    val database = AppDatabase.getDatabase(context)
                    database.notificationDao().pinNotifications(idsToPin, anyUnpinned)
                    com.conduit.app.widget.WidgetUpdater.updateAllWidgets(context)
                }
                performHapticClick(context)
                selectedIds = emptySet()
            }
            override fun onMarkSelectedAsRead() {
                val selectedNotifs = notifications.filter { it.id in selectedIds }
                if (selectedNotifs.isNotEmpty()) {
                    viewModel.markNotificationsAsRead(selectedNotifs)
                }
                performHapticClick(context)
                selectedIds = emptySet()
            }
            override fun onSearchQueryChanged(query: String) {
                searchQuery = query
                viewModel.setSearchQuery(query)
            }
            override fun onExitSearch() {
                isSearchMode = false
                searchQuery = ""
                viewModel.setSearchQuery("")
            }
            override fun onShowUpdateDialog() {
                showUpdateDialog = true
            }
            override fun onNavigateToSettings() {
                onNavigateToSettings()
            }
            override fun onNavigateToManageViews() {
                onNavigateToManageViews()
            }
            override fun onToggleMasterExpand() {
                val newState = !masterExpandedState
                settingsViewModel.updateMasterExpandedState(newState)
                individualToggles.clear()
                conversationRollupStates.clear()
            }
            override fun onNavigateToArchive() {
                onNavigateToArchive()
            }
            override fun onToggleUnifiedView() {
                onUnifiedViewChanged(!unifiedView)
            }
            override fun onEnterSearch() {
                isSearchMode = true
                searchQuery = ""
                viewModel.setSearchQuery("")
            }
        }
    }

    Scaffold(
        topBar = {
            if ((isSelectionMode || isSearchMode || !settings.scrollTopBarWithFeed) && activeSingleConversationKey == null) {
                HubTopBar(state = topBarState, callbacks = topBarCallbacks)
            }
        },
        bottomBar = {
            if (!isSelectionMode && !isSearchMode && activeSingleConversationKey == null) {
                val pm = context.packageManager
                val enabledApps = remember(unifiedView, channelStates.toMap()) {
                    if (!unifiedView) emptyList<String>()
                    else {
                        HubNotificationListenerService.supportedApps.keys
                            .filter { pkg ->
                                val prefKey = HubNotificationListenerService.supportedApps[pkg]?.first
                                if (prefKey != null && channelStates[prefKey] == true) {
                                    isPackageInstalled(context, pkg) || settings.demoModeEnabled
                                } else false
                            }
                            .map { getRepresentativePackage(context, it) }
                            .distinct()
                    }
                }
                val hasDockApps = notifications.isNotEmpty() || (unifiedView && enabledApps.isNotEmpty()) || (activeViewDockFilter && activeView != null && activeView.packageNames.isNotEmpty())
                if (hasDockApps || settings.enableAppBundles) {
                    val grouped = remember(notifications) {
                        notifications.groupBy { getRepresentativePackage(context, it.packageName) }
                    }
                    val dockPackagesList = remember(grouped, enabledApps, unifiedView, activeView, activeViewDockFilter, hasDockApps) {
                        if (!hasDockApps) {
                            emptyList()
                        } else if (activeViewDockFilter && activeView != null) {
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
                        0 -> 6.dp
                        2 -> 7.dp
                        else -> 7.dp
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
                    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                    LaunchedEffect(dockPackagesList.size, dockScrollIndicator) {
                        if (dockScrollIndicator == "BOUNCE" && dockPackagesList.size > 0) {
                            kotlinx.coroutines.delay(500)
                            if (listState.layoutInfo.totalItemsCount > listState.layoutInfo.visibleItemsInfo.size) {
                                listState.animateScrollBy(80f, androidx.compose.animation.core.tween(300))
                                listState.animateScrollBy(-80f, androidx.compose.animation.core.tween(300))
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .then(
                                if (isBundleFlyoutExpanded) {
                                    Modifier.pointerInput(Unit) {
                                        detectTapGestures { isBundleFlyoutExpanded = false }
                                    }
                                } else Modifier
                            ),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            BundleFabMenu(
                                expanded = isBundleFlyoutExpanded && settings.enableAppBundles,
                                recorderBundle = recorderBundle,
                                aiBundle = aiBundle,
                                notesBundle = notesBundle,
                                composeBundle = composeBundle,
                                bundleMenuSizing = settings.bundleMenuSizing,
                                dockIconSize = dockIconSize,
                                dockExpandPosition = settings.dockExpandPosition,
                                transparentBundlePills = settings.transparentBundlePills,
                                bundleMenuBlur = settings.bundleMenuBlur,
                                onAppClick = { pkg ->
                                    launchAppOrAction(pkg)
                                },
                                onBundleClick = { title, bundle ->
                                    launchBundleAction(title, bundle)
                                },
                                onBundleLongClick = { title ->
                                    isBundleFlyoutExpanded = false
                                    editingBundleTitle = title
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                            )

                            val dockHeight = when (dockSizeIndex) {
                                0 -> 64.dp
                                2 -> 80.dp
                                else -> 72.dp
                            }
                            val dockCapRadius = dockHeight / 2

                            val isExpandLeft = settings.dockExpandPosition == "LEFT"
                            val rightSplitShape = rememberRightSplitButtonShape(dockCapRadius)
                            val leftSplitShape = rememberLeftSplitButtonShape(dockCapRadius)

                            val dockPackagesShape = if (settings.enableAppBundles) {
                                if (dockPackagesList.isNotEmpty()) {
                                    if (isExpandLeft) rightSplitShape else leftSplitShape
                                } else {
                                    androidx.compose.foundation.shape.CircleShape
                                }
                            } else {
                                androidx.compose.foundation.shape.CircleShape
                            }

                            val expandButtonShape = if (dockPackagesList.isNotEmpty()) {
                                if (isExpandLeft) leftSplitShape else rightSplitShape
                            } else {
                                androidx.compose.foundation.shape.CircleShape
                            }

                            val isFading = dockScrollIndicator == "FADING_EDGES"
                            val fadeWidth = 32.dp
                            val fadeWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { fadeWidth.toPx() }

                            val dockPackagesContent: @Composable RowScope.() -> Unit = {
                                Surface(
                                    modifier = if (settings.enableAppBundles) {
                                        Modifier.height(dockHeight).weight(1f, fill = false).wrapContentWidth()
                                    } else {
                                        Modifier.height(dockHeight).wrapContentWidth()
                                    },
                                    shape = dockPackagesShape,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shadowElevation = 8.dp
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxHeight().wrapContentWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        LazyRow(
                                            state = listState,
                                            modifier = Modifier
                                                .padding(vertical = 1.dp)
                                                .wrapContentWidth()
                                                .then(
                                                    if (isFading) {
                                                        Modifier
                                                            .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                                                            .drawWithContent {
                                                                drawContent()
                                                                val canScrollBackward = listState.canScrollBackward
                                                                val canScrollForward = listState.canScrollForward
                                                                if (canScrollBackward) {
                                                                    drawRect(
                                                                        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                                                            colors = listOf(Color.Transparent, Color.Black),
                                                                            startX = 0f,
                                                                            endX = fadeWidthPx
                                                                        ),
                                                                        blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
                                                                    )
                                                                }
                                                                if (canScrollForward) {
                                                                    drawRect(
                                                                        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                                                            colors = listOf(Color.Black, Color.Transparent),
                                                                            startX = this.size.width - fadeWidthPx,
                                                                            endX = this.size.width
                                                                        ),
                                                                        blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
                                                                    )
                                                                }
                                                            }
                                                    } else Modifier
                                                ),
                                            contentPadding = PaddingValues(
                                                start = if (settings.enableAppBundles && isExpandLeft) (dockPaddingHorizontal * 0.65f).coerceAtLeast(8.dp) else dockPaddingHorizontal,
                                                end = if (settings.enableAppBundles && !isExpandLeft) (dockPaddingHorizontal * 0.65f).coerceAtLeast(8.dp) else dockPaddingHorizontal,
                                                top = dockPaddingVertical,
                                                bottom = dockPaddingVertical
                                            ), 
                                            horizontalArrangement = Arrangement.spacedBy(dockSpacing, Alignment.CenterHorizontally),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            items(
                                                count = dockPackagesList.size,
                                                key = { index -> dockPackagesList[index] }
                                            ) { index ->
                                                val pkg = dockPackagesList[index]
                                                val groupNotifs = grouped[pkg] ?: emptyList()
                                                
                                                if ((unifiedView || activeViewDockFilter) && index > 0) {
                                                    val prevPkg = dockPackagesList[index - 1]
                                                    val isPrevUnread = grouped.containsKey(prevPkg)
                                                    val isCurrentRead = !grouped.containsKey(pkg)
                                                    if (isPrevUnread && isCurrentRead) {
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Box(
                                                            modifier = Modifier
                                                                .height(24.dp)
                                                                .width(1.dp)
                                                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
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
                                                                        android.util.Log.e("HubScreen", "Failed to launch application for $pkg", e)
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

                                        if (dockScrollIndicator == "SCROLLBAR" && dockPackagesList.isNotEmpty()) {
                                            val layoutInfo = listState.layoutInfo
                                            val totalItems = layoutInfo.totalItemsCount
                                            val visibleItems = layoutInfo.visibleItemsInfo.size
                                            if (totalItems > 0 && visibleItems < totalItems) {
                                                val scrollRange = totalItems - visibleItems
                                                val scrollFraction = if (scrollRange > 0) {
                                                    val firstIndex = listState.firstVisibleItemIndex
                                                    val firstOffset = listState.firstVisibleItemScrollOffset
                                                    val itemSize = layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 1
                                                    ((firstIndex.toFloat() + (firstOffset.toFloat() / itemSize)) / scrollRange).coerceIn(0f, 1f)
                                                } else 0f
                                                
                                                val trackWidth = 40.dp
                                                val thumbWidth = 12.dp
                                                val maxThumbOffset = trackWidth - thumbWidth
                                                
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.BottomCenter)
                                                        .padding(bottom = 2.dp)
                                                        .width(trackWidth)
                                                        .height(3.dp)
                                                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f), androidx.compose.foundation.shape.CircleShape)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .offset(x = maxThumbOffset * scrollFraction)
                                                            .width(thumbWidth)
                                                            .fillMaxHeight()
                                                            .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            val expandButtonContent: @Composable () -> Unit = {
                                Surface(
                                    modifier = Modifier
                                        .height(dockHeight)
                                        .wrapContentWidth(),
                                    shape = expandButtonShape,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shadowElevation = 8.dp
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .padding(vertical = 1.dp)
                                            .clip(expandButtonShape)
                                            .clickable {
                                                performHapticTick(context)
                                                isBundleFlyoutExpanded = !isBundleFlyoutExpanded
                                            }
                                            .padding(
                                                start = if (dockPackagesList.isNotEmpty()) {
                                                    if (isExpandLeft) (dockPaddingHorizontal * 0.55f).coerceAtLeast(6.dp)
                                                    else (dockPaddingHorizontal * 0.35f).coerceAtLeast(4.dp)
                                                } else dockPaddingHorizontal,
                                                end = if (dockPackagesList.isNotEmpty()) {
                                                    if (isExpandLeft) (dockPaddingHorizontal * 0.35f).coerceAtLeast(4.dp)
                                                    else (dockPaddingHorizontal * 0.55f).coerceAtLeast(6.dp)
                                                } else dockPaddingHorizontal,
                                                top = dockPaddingVertical,
                                                bottom = dockPaddingVertical
                                            )
                                            .padding(
                                                horizontal = if (dockPackagesList.isNotEmpty()) (dockBoxPadding * 0.5f).coerceAtLeast(3.dp) else dockBoxPadding,
                                                vertical = dockBoxPadding
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val caretIconSize = (dockIconSize * 0.675f).coerceIn(24.dp, 30.dp)
                                        Box(
                                            modifier = Modifier
                                                .height(dockIconSize)
                                                .width(if (dockPackagesList.isNotEmpty()) caretIconSize else dockIconSize),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (isBundleFlyoutExpanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                                                contentDescription = if (isBundleFlyoutExpanded) "Collapse App Bundles" else "Expand App Bundles",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(caretIconSize)
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.wrapContentWidth()
                            ) {
                                if (isExpandLeft) {
                                    if (settings.enableAppBundles) {
                                        expandButtonContent()
                                    }
                                    if (dockPackagesList.isNotEmpty() && settings.enableAppBundles) {
                                        Spacer(modifier = Modifier.width(3.dp))
                                    }
                                    if (dockPackagesList.isNotEmpty()) {
                                        dockPackagesContent()
                                    }
                                } else {
                                    if (dockPackagesList.isNotEmpty()) {
                                        dockPackagesContent()
                                    }
                                    if (dockPackagesList.isNotEmpty() && settings.enableAppBundles) {
                                        Spacer(modifier = Modifier.width(3.dp))
                                    }
                                    if (settings.enableAppBundles) {
                                        expandButtonContent()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {}
    ) { padding ->
        if (showUpdateDialog) {
            AlertDialog(
                onDismissRequest = { showUpdateDialog = false },
                title = { Text("Update Available", fontWeight = FontWeight.Bold) },
                text = { Text("A new version of Conduit is available!\n\nCurrent Version: ${BuildConfig.VERSION_NAME}\nNew Version: $latestVersionAvailable") },
                confirmButton = {
                    Button(onClick = {
                        showUpdateDialog = false
                        kotlinx.coroutines.GlobalScope.launch {
                            val updateInfo = com.conduit.app.updater.UpdateManager.checkForUpdates()
                            if (updateInfo != null) {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    android.widget.Toast.makeText(context, "Starting download...", android.widget.Toast.LENGTH_SHORT).show()
                                    com.conduit.app.updater.UpdateManager.downloadAndInstall(context, updateInfo.second, updateInfo.first)
                                }
                            }
                        }
                    }) {
                        Text("Update")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showUpdateDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
        val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme() || settings.themePreference == 2
        val blurModifier = if (isBundleFlyoutExpanded && settings.enableAppBundles && settings.bundleMenuBlur && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            Modifier.blur(20.dp)
        } else {
            Modifier
        }
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (settings.scrollTopBarWithFeed && !isSelectionMode && !isSearchMode)
                        Modifier.nestedScroll(nestedScrollConnection)
                    else Modifier
                )
        ) {
            val density = LocalDensity.current
            val visibleHeaderPx = (headerHeightPx + headerOffsetPx).coerceAtLeast(0f)
            val statusBarHeightPx = WindowInsets.statusBars.getTop(density).toFloat()
            val contentTopPaddingDp = with(density) {
                if (activeSingleConversationKey != null) {
                    statusBarHeightPx.toDp()
                } else if (settings.scrollTopBarWithFeed && !isSelectionMode && !isSearchMode) {
                    maxOf(statusBarHeightPx, visibleHeaderPx).toDp()
                } else {
                    padding.calculateTopPadding()
                }
            }

            if ((settings.scrollTopBarWithFeed && !isSelectionMode && !isSearchMode) || activeSingleConversationKey != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsTopHeight(WindowInsets.statusBars)
                        .background(MaterialTheme.colorScheme.background)
                        .zIndex(1f)
                )
            }

            if (settings.scrollTopBarWithFeed && !isSelectionMode && !isSearchMode && activeSingleConversationKey == null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(0, headerOffsetPx.roundToInt()) }
                        .onSizeChanged { headerHeightPx = it.height.toFloat() }
                        .background(MaterialTheme.colorScheme.background)
                        .statusBarsPadding()
                        .zIndex(2f)
                ) {
                    HubTopBar(state = topBarState, callbacks = topBarCallbacks)
                    if (views.isNotEmpty()) {
                        CustomViewChipsBar(
                            views = views,
                            activeViewId = activeViewId,
                            onSelectView = { newId ->
                                viewModel.setActiveViewId(newId)
                                if (newId != null) {
                                    selectedDockPackage = null
                                }
                            },
                            isExpanded = settings.customViewsExpanded,
                            onToggleExpanded = {
                                settingsViewModel.updateCustomViewsExpanded(!settings.customViewsExpanded)
                            }
                        )
                    }
                    androidx.compose.animation.AnimatedVisibility(visible = !unifiedView) {
                        TodoModeBanner()
                    }
                }
            }

            Column(
                modifier = Modifier
                    .padding(top = contentTopPaddingDp)
                    .fillMaxSize()
                    .then(blurModifier)
            ) {
            if ((!settings.scrollTopBarWithFeed || isSelectionMode || isSearchMode) && activeSingleConversationKey == null) {
                if (views.isNotEmpty() && !isSearchMode) {
                    CustomViewChipsBar(
                        views = views,
                        activeViewId = activeViewId,
                        onSelectView = { newId ->
                            viewModel.setActiveViewId(newId)
                            if (newId != null) {
                                selectedDockPackage = null
                            }
                        },
                        isExpanded = settings.customViewsExpanded,
                        onToggleExpanded = {
                            settingsViewModel.updateCustomViewsExpanded(!settings.customViewsExpanded)
                        }
                    )
                }

                androidx.compose.animation.AnimatedVisibility(visible = !unifiedView) {
                    TodoModeBanner()
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (isSearchMode) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (searchCorrection != null) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "No exact matches for \"${searchCorrection?.originalQuery}\"",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                    )
                                    Text(
                                        text = "Showing results for \"${searchCorrection?.correctedQuery}\"",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        searchCorrection?.let { correction ->
                                            viewModel.forceOriginalQuery(correction.originalQuery)
                                        }
                                    }
                                ) {
                                    Text(
                                        text = "Search original",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        if (searchQuery.isBlank()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Type to search notifications...", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else if (searchItems.itemCount == 0 && searchItems.loadState.refresh !is LoadState.Loading) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No notifications found", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                    val currentViewConfig = LocalViewConfiguration.current
                    val customViewConfig = remember(currentViewConfig) {
                        object : ViewConfiguration by currentViewConfig {
                            override val longPressTimeoutMillis: Long
                                get() = 700L
                        }
                    }
                    CompositionLocalProvider(LocalViewConfiguration provides customViewConfig) {
                        val screenWidthPx = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 16.dp)
                        ) {
                            items(
                                count = searchItems.itemCount,
                                key = searchItems.itemKey { it.id }
                            ) { index ->
                                val notification = searchItems[index]
                                if (notification != null) {
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
                                                    viewModel.markNotificationsAsRead(listOf(notification))
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
                                                    "ARCHIVE" -> if (notification.kind == "MESSAGE") "Mark Read" else "Dismiss"
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
                                                        Icon(icon, contentDescription = null, tint = textColor, modifier = Modifier.scale(scale).size(28.dp))
                                                        Text(label, color = textColor, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.scale(scale))
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
                                                            selectedIds = if (becomingSelected) selectedIds + notification.id else selectedIds - notification.id
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
                                                        onTriggerAction = { action -> viewModel.triggerAction(context, notification, action) },
                                                        onReply = { text, action -> viewModel.sendReply(context, notification, text, action) }
                                                    )
                                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    }
                }
                }
            } else if (displayNotifications.isEmpty() && selectedDockPackage == null) {
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
                val pinnedNotifications = remember(displayNotifications) {
                    displayNotifications.filter { it.isPinned }
                }
                val unpinnedNotifications = remember(displayNotifications) {
                    displayNotifications.filter { !it.isPinned }
                }
                val activeNotificationIds = remember(notifications) { notifications.map { it.id }.toSet() }
                val groupedNotifications = remember(unpinnedNotifications, settings.combineConversations) {
                    unpinnedNotifications
                        .groupBy { formatDateHeader(it.timestamp) }
                        .mapValues { (_, itemsList) ->
                            ConversationCombineHelper.groupConversationItems(context, itemsList, settings.combineConversations)
                        }
                }
                val groupedPinned = remember(pinnedNotifications, settings.combineConversations) {
                    ConversationCombineHelper.groupConversationItems(context, pinnedNotifications, settings.combineConversations)
                }
                val visiblePinned by remember(groupedPinned, masterExpandedState) {
                    derivedStateOf {
                        groupedPinned.filter { item ->
                            !item.isChildInConversation || getRollupState(item.conversationClusterId) == ConversationRollupState.ALL_EXPANDED
                        }
                    }
                }
                val visibleGroupedNotifications by remember(groupedNotifications, masterExpandedState) {
                    derivedStateOf {
                        groupedNotifications.mapValues { (_, itemsList) ->
                            itemsList.filter { item ->
                                !item.isChildInConversation || getRollupState(item.conversationClusterId) == ConversationRollupState.ALL_EXPANDED
                            }
                        }.filterValues { it.isNotEmpty() }
                    }
                }

                val allNotificationsCombined = remember(notifications, archivedNotifications) {
                    (notifications + archivedNotifications).sortedByDescending { it.timestamp }
                }
                val activeSingleConversation = remember(activeSingleConversationKey, allNotificationsCombined) {
                    if (activeSingleConversationKey == null) null
                    else {
                        val messages = allNotificationsCombined.filter {
                            SingleConversationHelper.isMessagingNotification(it, context) &&
                            SingleConversationHelper.getConversationKey(context, it) == activeSingleConversationKey
                        }
                        if (messages.isEmpty()) null
                        else {
                            val primary = messages.first()
                            val active = messages.filter { !it.isArchived }
                            val isGroup = messages.any { ConversationCombineHelper.isGroupChat(it.title) }
                            val title = if (isGroup) {
                                ConversationCombineHelper.extractConversationName(primary.title) ?: primary.title.orEmpty()
                            } else {
                                SingleConversationHelper.normalizeTitle(primary.title)
                            }
                            SingleConversationRollup(
                                convoKey = activeSingleConversationKey!!,
                                primaryNotification = primary,
                                allHistoryMessages = messages,
                                activeMessages = active,
                                subNotificationCount = if (messages.size > 1) messages.size - 1 else 0,
                                isGroupChat = isGroup,
                                conversationTitle = if (title.isBlank()) primary.title.orEmpty() else title,
                                appPackage = getRepresentativePackage(context, primary.packageName),
                                hasActiveUnread = active.isNotEmpty(),
                                isPinned = messages.any { it.isPinned }
                            )
                        }
                    }
                }
                val singleConvoFeedItems = remember(displayNotifications, allNotificationsCombined, settings.singleConversationView) {
                    if (settings.singleConversationView) {
                        SingleConversationHelper.groupFeedForSingleConversation(context, displayNotifications, allNotificationsCombined)
                    } else {
                        emptyList()
                    }
                }
                val singleConvoPinned = remember(singleConvoFeedItems) {
                    singleConvoFeedItems.filter { it.isPinned }
                }
                val singleConvoUnpinned = remember(singleConvoFeedItems) {
                    singleConvoFeedItems.filter { !it.isPinned }
                }
                val singleConvoGroupedUnpinned = remember(singleConvoUnpinned) {
                    singleConvoUnpinned.groupBy { formatDateHeader(it.timestamp) }
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
                        state = mainListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 16.dp)
                    ) {
                    if (activeSingleConversation != null) {
                        singleConversationDetail(
                            rollup = activeSingleConversation,
                            onCollapse = { activeSingleConversationKey = null },
                            onMarkAllRead = {
                                viewModel.markNotificationsAsRead(activeSingleConversation.activeMessages)
                            },
                            actionsByKey = actionsByKey,
                            unifiedView = unifiedView,
                            isSelectionMode = isSelectionMode,
                            selectedIds = selectedIds,
                            onSelectToggle = { id ->
                                val becomingSelected = !selectedIds.contains(id)
                                if (becomingSelected) performHapticClick(context) else performHapticTick(context)
                                selectedIds = if (becomingSelected) selectedIds + id else selectedIds - id
                            },
                            showActionChips = showActionChips,
                            settings = settings,
                            onArchiveNotification = onArchiveNotification,
                            onPinNotification = onPinNotification,
                            onTriggerAction = { notif, action ->
                                val actionTitle = action.title?.toString()?.lowercase() ?: ""
                                if (actionTitle.contains("read") || actionTitle.contains("done") || actionTitle.contains("seen")) {
                                    viewModel.markNotificationsAsRead(activeSingleConversation.activeMessages)
                                } else {
                                    viewModel.triggerAction(context, notif, action)
                                }
                            },
                            onReply = { notif, text, action ->
                                viewModel.sendReply(context, notif, text, action)
                            }
                        )
                    } else if (settings.singleConversationView) {
                        singleConversationFeed(
                            context = context,
                            pinnedItems = singleConvoPinned,
                            groupedUnpinnedItems = singleConvoGroupedUnpinned,
                            onOpenConversation = { convoKey ->
                                activeSingleConversationKey = convoKey
                            },
                            onArchiveNotification = onArchiveNotification,
                            onPinNotification = onPinNotification,
                            actionsByKey = actionsByKey,
                            unifiedView = unifiedView,
                            isSelectionMode = isSelectionMode,
                            selectedIds = selectedIds,
                            onSelectToggleIds = { ids ->
                                val anySelected = ids.any { selectedIds.contains(it) }
                                val becomingSelected = !anySelected
                                if (becomingSelected) performHapticClick(context) else performHapticTick(context)
                                selectedIds = if (becomingSelected) selectedIds + ids else selectedIds - ids
                            },
                            showActionChips = showActionChips,
                            settings = settings,
                            individualToggles = individualToggles,
                            onToggleIndividual = { id ->
                                val currentState = individualToggles[id]
                                    ?: if (settings.autoCollapseRead) false else settings.masterExpandedState
                                individualToggles[id] = !currentState
                                performHapticTick(context)
                            },
                            onTriggerAction = { notif, activeMsgs, action ->
                                val actionTitle = action.title?.toString()?.lowercase() ?: ""
                                if (actionTitle.contains("read") || actionTitle.contains("done") || actionTitle.contains("seen")) {
                                    viewModel.markNotificationsAsRead(activeMsgs)
                                } else {
                                    viewModel.triggerAction(context, notif, action)
                                }
                            },
                            onReply = { notif, text, action ->
                                viewModel.sendReply(context, notif, text, action)
                            },
                            activeNotificationIds = activeNotificationIds,
                            onArchiveAllInDate = { ids ->
                                viewModel.archiveMany(ids)
                            }
                        )
                    } else {
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
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        items(visiblePinned, key = { "pinned_${it.primaryNotification.id}" }) { item ->
                            val notification = item.primaryNotification
                            val rollupState = getRollupState(item.conversationClusterId)
                            Box(modifier = Modifier.animateItem(
                                placementSpec = tween(durationMillis = 300)
                            )) {
                                if (item.isChildInConversation) {
                                    CombinedNotificationItem(
                                        notification = notification,
                                        displayText = item.combinedText,
                                        senderName = item.senderName,
                                        isGroupChat = item.isGroupChat,
                                        isSelected = item.allNotifications.any { selectedIds.contains(it.id) },
                                        isSelectionMode = isSelectionMode,
                                        onSelectToggle = {
                                            val allIds = item.allNotifications.map { it.id }.toSet()
                                            val anySelected = allIds.any { selectedIds.contains(it) }
                                            val becomingSelected = !anySelected
                                            if (becomingSelected) performHapticClick(context) else performHapticTick(context)
                                            selectedIds = if (becomingSelected) selectedIds + allIds else selectedIds - allIds
                                        },
                                        minimizeIcons = settings.minimizeIcons,
                                        isExpanded = individualToggles[notification.id]
                                            ?: if (settings.autoCollapseRead && notification.isArchived) false else true,
                                        onExpandToggle = {
                                            val currentState = individualToggles[notification.id]
                                                ?: if (settings.autoCollapseRead && notification.isArchived) false else true
                                            individualToggles[notification.id] = !currentState
                                            performHapticTick(context)
                                        }
                                    )
                                } else {
                                    NotificationItem(
                                        notification = notification,
                                        isArchivedView = false,
                                        isUnifiedView = unifiedView,
                                        onArchiveNotification = onArchiveNotification,
                                        onPinNotification = onPinNotification,
                                        isSelected = item.allNotifications.any { selectedIds.contains(it.id) },
                                        isSelectionMode = isSelectionMode,
                                        onSelectToggle = {
                                            val allIds = item.allNotifications.map { it.id }.toSet()
                                            val anySelected = allIds.any { selectedIds.contains(it) }
                                            val becomingSelected = !anySelected
                                            if (becomingSelected) performHapticClick(context) else performHapticTick(context)
                                            selectedIds = if (becomingSelected) selectedIds + allIds else selectedIds - allIds
                                        },
                                        showActionChips = showActionChips,
                                        minimizeIcons = settings.minimizeIcons,
                                        allActions = actionsByKey[notification.notificationKey],
                                        isExpanded = if (settings.combineConversations && item.subNotificationCount > 0) {
                                            rollupState != ConversationRollupState.FULLY_COLLAPSED
                                        } else {
                                            individualToggles[notification.id] 
                                                ?: if (settings.autoCollapseRead && notification.isArchived) false else masterExpandedState
                                        },
                                        onExpandToggle = {
                                            if (settings.combineConversations && item.subNotificationCount > 0) {
                                                cycleRollupState(item.conversationClusterId)
                                            } else {
                                                val currentState = individualToggles[notification.id] 
                                                    ?: if (settings.autoCollapseRead && notification.isArchived) false else masterExpandedState
                                                individualToggles[notification.id] = !currentState
                                            }
                                            performHapticTick(context)
                                        },
                                        onTriggerAction = { action -> viewModel.triggerAction(context, notification, action) },
                                        onReply = { text, action -> viewModel.sendReply(context, notification, text, action) },
                                        overrideText = if (settings.combineConversations) item.combinedText else null,
                                        subNotificationCount = if (settings.combineConversations) item.subNotificationCount else 0,
                                        conversationRollupState = if (settings.combineConversations && item.subNotificationCount > 0) rollupState else null
                                    )
                                }
                                if (settings.combineConversations && item.isChildInConversation) {
                                    if (item.isLastInConversation) {
                                        DoubleDivider()
                                    } else {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                                    }
                                } else if (settings.combineConversations && item.subNotificationCount > 0 && (rollupState == ConversationRollupState.PARTIALLY_EXPANDED || rollupState == ConversationRollupState.FULLY_COLLAPSED)) {
                                    DoubleDivider()
                                } else {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                                }
                            }
                        }
                    }
                    
                    // Unpinned date-grouped section
                    visibleGroupedNotifications.forEach { (dateHeader, itemsList) ->
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
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    val allNotifsInDate = remember(itemsList) { itemsList.flatMap { it.allNotifications }.distinctBy { it.id } }
                                    val pendingCount = remember(allNotifsInDate, activeNotificationIds) { allNotifsInDate.count { activeNotificationIds.contains(it.id) } }
                                    val chipText = remember(unifiedView, pendingCount, itemsList.size) {
                                        if (unifiedView) {
                                            if (pendingCount > 0) "${itemsList.size} | UNREAD $pendingCount" else "${itemsList.size}"
                                        } else {
                                            "${itemsList.size}"
                                        }
                                    }
                                    
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
                                                        val itemsToArchive = allNotifsInDate.filter { activeNotificationIds.contains(it.id) }
                                                        if (itemsToArchive.isNotEmpty()) {
                                                            viewModel.markNotificationsAsRead(itemsToArchive)
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
                        items(itemsList, key = { it.primaryNotification.id }) { item ->
                            val notification = item.primaryNotification
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
                                            viewModel.markNotificationsAsRead(item.allNotifications)
                                            !unifiedView
                                        }
                                        "SNOOZE" -> {
                                            notificationToSnooze = notification
                                            false
                                        }
                                        "PIN" -> {
                                            item.allNotifications.forEach { onPinNotification(it) }
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
                                        "ARCHIVE" -> if (notification.kind == "MESSAGE") "Mark Read" else "Dismiss"
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
                                        val rollupState = getRollupState(item.conversationClusterId)
                                        if (item.isChildInConversation) {
                                            CombinedNotificationItem(
                                                notification = notification,
                                                displayText = item.combinedText,
                                                senderName = item.senderName,
                                                isGroupChat = item.isGroupChat,
                                                isSelected = item.allNotifications.any { selectedIds.contains(it.id) },
                                                isSelectionMode = isSelectionMode,
                                                onSelectToggle = {
                                                    val allIds = item.allNotifications.map { it.id }.toSet()
                                                    val anySelected = allIds.any { selectedIds.contains(it) }
                                                    val becomingSelected = !anySelected
                                                    if (becomingSelected) performHapticClick(context) else performHapticTick(context)
                                                    selectedIds = if (becomingSelected) selectedIds + allIds else selectedIds - allIds
                                                },
                                                minimizeIcons = settings.minimizeIcons,
                                                isExpanded = individualToggles[notification.id]
                                                    ?: if (settings.autoCollapseRead && notification.isArchived) false else true,
                                                onExpandToggle = {
                                                    val currentState = individualToggles[notification.id]
                                                        ?: if (settings.autoCollapseRead && notification.isArchived) false else true
                                                    individualToggles[notification.id] = !currentState
                                                    performHapticTick(context)
                                                }
                                            )
                                        } else {
                                            NotificationItem(
                                                notification = notification,
                                                isArchivedView = false,
                                                isUnifiedView = unifiedView,
                                                onArchiveNotification = onArchiveNotification,
                                                onPinNotification = onPinNotification,
                                                isSelected = item.allNotifications.any { selectedIds.contains(it.id) },
                                                isSelectionMode = isSelectionMode,
                                                onSelectToggle = {
                                                    val allIds = item.allNotifications.map { it.id }.toSet()
                                                    val anySelected = allIds.any { selectedIds.contains(it) }
                                                    val becomingSelected = !anySelected
                                                    if (becomingSelected) performHapticClick(context) else performHapticTick(context)
                                                    selectedIds = if (becomingSelected) selectedIds + allIds else selectedIds - allIds
                                                },
                                                showActionChips = showActionChips,
                                                minimizeIcons = settings.minimizeIcons,
                                                allActions = actionsByKey[notification.notificationKey],
                                                isExpanded = if (settings.combineConversations && item.subNotificationCount > 0) {
                                                    rollupState != ConversationRollupState.FULLY_COLLAPSED
                                                } else {
                                                    individualToggles[notification.id] 
                                                        ?: if (settings.autoCollapseRead && notification.isArchived) false else masterExpandedState
                                                },
                                                onExpandToggle = {
                                                    if (settings.combineConversations && item.subNotificationCount > 0) {
                                                        cycleRollupState(item.conversationClusterId)
                                                    } else {
                                                        val currentState = individualToggles[notification.id] 
                                                            ?: if (settings.autoCollapseRead && notification.isArchived) false else masterExpandedState
                                                        individualToggles[notification.id] = !currentState
                                                    }
                                                    performHapticTick(context)
                                                },
                                                onTriggerAction = { action -> viewModel.triggerAction(context, notification, action) },
                                                onReply = { text, action -> viewModel.sendReply(context, notification, text, action) },
                                                overrideText = if (settings.combineConversations) item.combinedText else null,
                                                subNotificationCount = if (settings.combineConversations) item.subNotificationCount else 0,
                                                conversationRollupState = if (settings.combineConversations && item.subNotificationCount > 0) rollupState else null
                                            )
                                        }
                                        if (settings.combineConversations && item.isChildInConversation) {
                                            if (item.isLastInConversation) {
                                                DoubleDivider()
                                            } else {
                                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                                            }
                                        } else if (settings.combineConversations && item.subNotificationCount > 0 && (rollupState == ConversationRollupState.PARTIALLY_EXPANDED || rollupState == ConversationRollupState.FULLY_COLLAPSED)) {
                                            DoubleDivider()
                                        } else {
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                                        }
                                    }
                                }
                            )
                            }
                        }
                    }
                    }
                    }
                }
            }
            
            // The Floating Dock (moved to bottomBar)
            }
        }

        if (isBundleFlyoutExpanded) {
            BackHandler {
                isBundleFlyoutExpanded = false
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures {
                            isBundleFlyoutExpanded = false
                        }
                    }
            )
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
                                
                                kotlinx.coroutines.GlobalScope.launch {
                                    val database = AppDatabase.getDatabase(context)
                                    val allNotifs = database.notificationDao().getAllNotificationsSync()
                                    val idsToDelete = mutableListOf<Int>()
                                    val keysToCancel = mutableListOf<String>()
                                    allNotifs.forEach { item ->
                                        if (item.packageName == notif.packageName) {
                                            val match = if (blockType == "TITLE") {
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
                                    if (idsToDelete.isNotEmpty()) {
                                        keysToCancel.forEach { key ->
                                            HubNotificationListenerService.instance?.cancel(key)
                                        }
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
                                selectedIds = emptySet()
                            }
                        )
                    }
                }
            }
        }

            androidx.compose.animation.AnimatedVisibility(
                visible = isBundleFlyoutExpanded && settings.enableAppBundles,
                enter = androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(200)),
                exit = androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(150))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (isDarkTheme) Color.Black.copy(alpha = 0.40f)
                            else MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp).copy(alpha = 0.65f)
                        )
                        .pointerInput(Unit) {
                            detectTapGestures { isBundleFlyoutExpanded = false }
                        }
                )
            }
        }

        val editingTitle = editingBundleTitle
        if (editingTitle != null) {
            val currentApps = when (editingTitle) {
                "AI Assistants" -> settings.aiBundle
                "Notes" -> settings.notesBundle
                "Recorders", "Recorder" -> settings.recorderBundle
                "Compose" -> settings.composeBundle
                else -> emptyList()
            }
            val onUpdate: (List<String>) -> Unit = when (editingTitle) {
                "AI Assistants" -> settingsViewModel::updateAiBundle
                "Notes" -> settingsViewModel::updateNotesBundle
                "Recorders", "Recorder" -> settingsViewModel::updateRecorderBundle
                "Compose" -> settingsViewModel::updateComposeBundle
                else -> { _ -> }
            }
            BundleManagementBottomSheet(
                bundleTitle = editingTitle,
                currentApps = currentApps,
                onUpdateApps = onUpdate,
                onDismiss = { editingBundleTitle = null }
            )
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


    }

}

@Composable
private fun TodoModeBanner() {
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


