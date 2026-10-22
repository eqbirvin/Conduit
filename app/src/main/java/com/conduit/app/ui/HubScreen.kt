@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.conduit.app.ui

import com.conduit.app.*
import android.content.Intent
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.*
import androidx.compose.animation.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import com.conduit.app.data.HubNotification
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.geometry.Offset

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
    
    val notificationsState = viewModel.notifications.collectAsStateWithLifecycle()
    val notifications by notificationsState
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
    val showBundleMenuState = remember { mutableStateOf<Pair<String, List<String>>?>(null) }
    val isBundleFlyoutExpandedState = remember { mutableStateOf(false) }
    var isBundleFlyoutExpanded by isBundleFlyoutExpandedState
    val editingBundleTitleState = remember { mutableStateOf<String?>(null) }
    var editingBundleTitle by editingBundleTitleState
    
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
    val showUpdateDialogState = remember { mutableStateOf(false) }
    
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
    
    val selectedDockPackageState = remember { mutableStateOf<String?>(null) }
    var selectedDockPackage by selectedDockPackageState
    
    val views by viewModel.viewsRepository.views.collectAsStateWithLifecycle()
    val activeViewId by viewModel.activeViewId.collectAsStateWithLifecycle()
    val activeView = remember(views, activeViewId) { views.find { it.id == activeViewId } }
    val activeViewDockFilter = activeView?.filterDock == true
    // A hidden strip leaves no way to see or clear the active view, so fall back to All.
    LaunchedEffect(settings.showCustomViewStrip, activeViewId) {
        if (!settings.showCustomViewStrip && activeViewId != null) {
            viewModel.setActiveViewId(null)
        }
    }
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
    val notificationToSnoozeState = remember { mutableStateOf<HubNotification?>(null) }

    val activeIntent = (context as? android.app.Activity)?.intent
    LaunchedEffect(activeIntent) {
        if (activeIntent?.action == "com.conduit.app.OPEN_FILTER") {
            val pkg = activeIntent.getStringExtra("FILTER_PACKAGE")
            if (pkg != null) {
                selectedDockPackage = pkg
            }
        }
    }

    val notificationToBlockState = remember { mutableStateOf<HubNotification?>(null) }
    val selectedIdsState = remember { mutableStateOf(setOf<Int>()) }
    var selectedIds by selectedIdsState
    val isSelectionMode = selectedIds.isNotEmpty()
    val searchQueryState = remember { mutableStateOf("") }
    var searchQuery by searchQueryState
    val isSearchModeState = remember { mutableStateOf(false) }
    var isSearchMode by isSearchModeState
    val searchItems = viewModel.searchResults.collectAsLazyPagingItems()
    val searchCorrection by viewModel.searchCorrectionState.collectAsStateWithLifecycle()

    val activeSingleConversationKeyState = remember { mutableStateOf<String?>(null) }
    var activeSingleConversationKey by activeSingleConversationKeyState
    val activeSingleConversationOriginYState = remember { mutableFloatStateOf(0f) }

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
    val allNotificationsCombined = remember(notifications, archivedNotifications) {
        (notifications + archivedNotifications).sortedByDescending { it.timestamp }
    }
    val displayNotifications = remember(notifications, allNotificationsCombined, selectedDockPackage, unifiedView) {
        val list = if (unifiedView) allNotificationsCombined else notifications
        if (selectedDockPackage != null) {
            list.filter { getRepresentativePackage(context, it.packageName) == selectedDockPackage }
        } else {
            list
        }
    }

    val mainListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val headerHeightPxState = remember { mutableFloatStateOf(0f) }
    var headerHeightPx by headerHeightPxState
    val headerOffsetPxState = remember { mutableFloatStateOf(0f) }
    var headerOffsetPx by headerOffsetPxState

    val nestedScrollConnection = remember(settings.topBarScrollsWithFeed, settings.topBarScrollBehavior, activeSingleConversationKey) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!settings.topBarScrollsWithFeed || isSelectionMode || isSearchMode || activeSingleConversationKey != null) {
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

    LaunchedEffect(isSelectionMode, isSearchMode, settings.topBarScrollsWithFeed) {
        if (isSelectionMode || isSearchMode || !settings.topBarScrollsWithFeed) {
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
        searchQuery, hasUpdateAvailable, masterExpandedState, unifiedView, settings.conversationViewMode
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
            unifiedView = unifiedView,
            conversationViewMode = settings.conversationViewMode
        )
    }

    val topBarCallbacks = rememberHubTopBarCallbacks(
        context = context,
        viewModel = viewModel,
        settingsViewModel = settingsViewModel,
        notificationsState = notificationsState,
        unifiedView = unifiedView,
        showUpdateDialogState = showUpdateDialogState,
        masterExpandedState = masterExpandedState,
        individualToggles = individualToggles,
        conversationRollupStates = conversationRollupStates,
        notificationToSnoozeState = notificationToSnoozeState,
        notificationToBlockState = notificationToBlockState,
        selectedIdsState = selectedIdsState,
        searchQueryState = searchQueryState,
        isSearchModeState = isSearchModeState,
        activeSingleConversationKeyState = activeSingleConversationKeyState,
        onNavigateToSettings = onNavigateToSettings,
        onNavigateToManageViews = onNavigateToManageViews,
        onNavigateToArchive = onNavigateToArchive,
        onUnifiedViewChanged = onUnifiedViewChanged
    )

    Scaffold(
        topBar = {
            if ((isSelectionMode || isSearchMode || (!settings.topBarScrollsWithFeed && !settings.hideTopBar)) && activeSingleConversationKey == null) {
                HubTopBar(state = topBarState, callbacks = topBarCallbacks)
            }
        },
        bottomBar = {
            if (!isSelectionMode && !isSearchMode && activeSingleConversationKey == null) {
                HubDock(
                    context = context,
                    prefs = prefs,
                    settings = settings,
                    notifications = notifications,
                    channelStates = channelStates,
                    aiBundle = aiBundle,
                    notesBundle = notesBundle,
                    recorderBundle = recorderBundle,
                    composeBundle = composeBundle,
                    dockSizeIndex = dockSizeIndex,
                    dockScrollIndicator = dockScrollIndicator,
                    unifiedView = unifiedView,
                    isBundleFlyoutExpandedState = isBundleFlyoutExpandedState,
                    editingBundleTitleState = editingBundleTitleState,
                    selectedDockPackageState = selectedDockPackageState,
                    launchAppOrAction = launchAppOrAction,
                    launchBundleAction = launchBundleAction,
                    activeView = activeView,
                    activeViewDockFilter = activeViewDockFilter,
                    topBarState = topBarState,
                    topBarCallbacks = topBarCallbacks
                )
            }
        },
        floatingActionButton = {}
    ) { padding ->
        HubScreenContent(
            context = context,
            prefs = prefs,
            viewModel = viewModel,
            settingsViewModel = settingsViewModel,
            settings = settings,
            notifications = notifications,
            actionsByKey = actionsByKey,
            showActionChips = showActionChips,
            unifiedView = unifiedView,
            onArchiveNotification = onArchiveNotification,
            onSnoozeNotification = onSnoozeNotification,
            onPinNotification = onPinNotification,
            showBundleMenuState = showBundleMenuState,
            isBundleFlyoutExpandedState = isBundleFlyoutExpandedState,
            editingBundleTitleState = editingBundleTitleState,
            latestVersionAvailable = latestVersionAvailable,
            showUpdateDialogState = showUpdateDialogState,
            masterExpandedState = masterExpandedState,
            individualToggles = individualToggles,
            getRollupState = ::getRollupState,
            cycleRollupState = ::cycleRollupState,
            selectedDockPackageState = selectedDockPackageState,
            views = views,
            activeViewId = activeViewId,
            swipeLeftAction = swipeLeftAction,
            swipeRightAction = swipeRightAction,
            notificationToSnoozeState = notificationToSnoozeState,
            notificationToBlockState = notificationToBlockState,
            selectedIdsState = selectedIdsState,
            isSelectionMode = isSelectionMode,
            searchQueryState = searchQueryState,
            isSearchModeState = isSearchModeState,
            searchItems = searchItems,
            searchCorrection = searchCorrection,
            activeSingleConversationKeyState = activeSingleConversationKeyState,
            activeSingleConversationOriginYState = activeSingleConversationOriginYState,
            allNotificationsCombined = allNotificationsCombined,
            displayNotifications = displayNotifications,
            mainListState = mainListState,
            headerHeightPxState = headerHeightPxState,
            headerOffsetPxState = headerOffsetPxState,
            nestedScrollConnection = nestedScrollConnection,
            topBarState = topBarState,
            topBarCallbacks = topBarCallbacks,
            onNavigateToArchive = onNavigateToArchive,
            padding = padding
        )
    }

}
