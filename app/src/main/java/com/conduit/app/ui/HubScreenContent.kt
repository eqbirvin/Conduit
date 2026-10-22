@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.conduit.app.ui

import com.conduit.app.*
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.IntOffset
import androidx.compose.material.icons.Icons
import androidx.compose.animation.*
import android.app.Notification
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.conduit.app.data.HubNotification
import android.os.Build
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.layout.onSizeChanged
import kotlin.math.roundToInt
import android.content.SharedPreferences
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.foundation.lazy.LazyListState
import androidx.paging.compose.LazyPagingItems
import com.conduit.app.data.ConduitSettings
import com.conduit.app.data.CustomView

@Composable
fun HubScreenContent(
    context: Context,
    prefs: SharedPreferences,
    viewModel: HubViewModel,
    settingsViewModel: SettingsViewModel,
    settings: ConduitSettings,
    notifications: List<HubNotification>,
    actionsByKey: Map<String, List<Notification.Action>>,
    showActionChips: Boolean,
    unifiedView: Boolean,
    onArchiveNotification: (Int, Long) -> Unit,
    onSnoozeNotification: (Int, Long) -> Unit,
    onPinNotification: (HubNotification) -> Unit,
    showBundleMenuState: MutableState<Pair<String, List<String>>?>,
    isBundleFlyoutExpandedState: MutableState<Boolean>,
    editingBundleTitleState: MutableState<String?>,
    latestVersionAvailable: String,
    showUpdateDialogState: MutableState<Boolean>,
    masterExpandedState: Boolean,
    individualToggles: SnapshotStateMap<Int, Boolean>,
    getRollupState: (Int) -> ConversationRollupState,
    cycleRollupState: (Int) -> Unit,
    selectedDockPackageState: MutableState<String?>,
    views: List<CustomView>,
    activeViewId: String?,
    swipeLeftAction: String,
    swipeRightAction: String,
    notificationToSnoozeState: MutableState<HubNotification?>,
    notificationToBlockState: MutableState<HubNotification?>,
    selectedIdsState: MutableState<Set<Int>>,
    isSelectionMode: Boolean,
    searchQueryState: MutableState<String>,
    isSearchModeState: MutableState<Boolean>,
    searchItems: LazyPagingItems<HubNotification>,
    searchCorrection: SearchCorrection?,
    activeSingleConversationKeyState: MutableState<String?>,
    activeSingleConversationOriginYState: MutableFloatState,
    allNotificationsCombined: List<HubNotification>,
    displayNotifications: List<HubNotification>,
    mainListState: LazyListState,
    headerHeightPxState: MutableFloatState,
    headerOffsetPxState: MutableFloatState,
    nestedScrollConnection: NestedScrollConnection,
    topBarState: HubTopBarState,
    topBarCallbacks: HubTopBarCallbacks,
    onNavigateToArchive: () -> Unit,
    padding: PaddingValues
) {
    var isBundleFlyoutExpanded by isBundleFlyoutExpandedState
    var selectedDockPackage by selectedDockPackageState
    val isSearchMode by isSearchModeState
    val activeSingleConversationKey by activeSingleConversationKeyState
    var headerHeightPx by headerHeightPxState
    val headerOffsetPx by headerOffsetPxState
    HubUpdateDialog(
        context = context,
        latestVersionAvailable = latestVersionAvailable,
        showUpdateDialogState = showUpdateDialogState
    )
    val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme() || settings.themePreference == 2
    val blurModifier = if (isBundleFlyoutExpanded && settings.enableAppBundles && settings.bundleMenuBlur && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        Modifier.blur(20.dp)
    } else {
        Modifier
    }

    val singleConvoFeedItems = remember(displayNotifications, allNotificationsCombined, settings.singleConversationView) {
        if (settings.singleConversationView) {
            SingleConversationHelper.groupFeedForSingleConversation(context, displayNotifications, allNotificationsCombined)
        } else {
            emptyList()
        }
    }
    var lastActiveConversation by remember { mutableStateOf<SingleConversationRollup?>(null) }
    val activeSingleConversation = remember(activeSingleConversationKey, singleConvoFeedItems, allNotificationsCombined) {
        val key = activeSingleConversationKey
        if (key == null) {
            null
        } else {
            val fromFeed = singleConvoFeedItems.firstOrNull {
                it is SingleConversationFeedItem.Conversation && it.rollup.convoKey == key
            } as? SingleConversationFeedItem.Conversation
            fromFeed?.rollup ?: SingleConversationHelper.buildRollupForKey(context, key, allNotificationsCombined)
        }
    }
    if (activeSingleConversation != null) {
        lastActiveConversation = activeSingleConversation
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
        val rawTopPaddingDp = with(density) {
            if (settings.scrollTopBarWithFeed && !isSelectionMode && !isSearchMode) {
                maxOf(statusBarHeightPx, visibleHeaderPx).toDp()
            } else {
                padding.calculateTopPadding()
            }
        }
        var savedTopPaddingDp by remember { mutableStateOf(0.dp) }
        if (activeSingleConversationKey == null) {
            savedTopPaddingDp = rawTopPaddingDp
        }
        val contentTopPaddingDp = if (activeSingleConversationKey != null) savedTopPaddingDp else rawTopPaddingDp

        if ((settings.scrollTopBarWithFeed && !isSelectionMode && !isSearchMode) || activeSingleConversationKey != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(MaterialTheme.colorScheme.background)
                    .zIndex(1f)
            )
        }

        if (settings.scrollTopBarWithFeed && !isSelectionMode && !isSearchMode) {
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
        if (!settings.scrollTopBarWithFeed || isSelectionMode || isSearchMode) {
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
            HubSearchResults(
                context = context,
                viewModel = viewModel,
                settings = settings,
                actionsByKey = actionsByKey,
                showActionChips = showActionChips,
                unifiedView = unifiedView,
                onArchiveNotification = onArchiveNotification,
                onPinNotification = onPinNotification,
                masterExpandedState = masterExpandedState,
                individualToggles = individualToggles,
                swipeLeftAction = swipeLeftAction,
                swipeRightAction = swipeRightAction,
                notificationToSnoozeState = notificationToSnoozeState,
                notificationToBlockState = notificationToBlockState,
                selectedIdsState = selectedIdsState,
                isSelectionMode = isSelectionMode,
                searchQueryState = searchQueryState,
                searchItems = searchItems,
                searchCorrection = searchCorrection,
                padding = padding
            )
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
            HubFeedList(
                context = context,
                viewModel = viewModel,
                settings = settings,
                notifications = notifications,
                actionsByKey = actionsByKey,
                showActionChips = showActionChips,
                unifiedView = unifiedView,
                onArchiveNotification = onArchiveNotification,
                onPinNotification = onPinNotification,
                masterExpandedState = masterExpandedState,
                individualToggles = individualToggles,
                getRollupState = getRollupState,
                cycleRollupState = cycleRollupState,
                swipeLeftAction = swipeLeftAction,
                swipeRightAction = swipeRightAction,
                notificationToSnoozeState = notificationToSnoozeState,
                notificationToBlockState = notificationToBlockState,
                selectedIdsState = selectedIdsState,
                isSelectionMode = isSelectionMode,
                activeSingleConversationKeyState = activeSingleConversationKeyState,
                activeSingleConversationOriginYState = activeSingleConversationOriginYState,
                displayNotifications = displayNotifications,
                mainListState = mainListState,
                padding = padding,
                singleConvoFeedItems = singleConvoFeedItems
            )
        }

        // The Floating Dock (moved to bottomBar)
        }
        }

        HubSingleConversationOverlay(
            context = context,
            viewModel = viewModel,
            actionsByKey = actionsByKey,
            showActionChips = showActionChips,
            selectedIdsState = selectedIdsState,
            isSelectionMode = isSelectionMode,
            activeSingleConversationKeyState = activeSingleConversationKeyState,
            activeSingleConversationOriginYState = activeSingleConversationOriginYState,
            activeSingleConversation = activeSingleConversation,
            lastActiveConversation = lastActiveConversation,
            density = density,
            statusBarHeightPx = statusBarHeightPx
        )

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

    HubBlockDialog(
        context = context,
        prefs = prefs,
        notificationToBlockState = notificationToBlockState,
        selectedIdsState = selectedIdsState
    )

    HubSnoozeSheet(
        onSnoozeNotification = onSnoozeNotification,
        notificationToSnoozeState = notificationToSnoozeState,
        selectedIdsState = selectedIdsState
    )

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

    HubBundleSheets(
        context = context,
        settingsViewModel = settingsViewModel,
        settings = settings,
        showBundleMenuState = showBundleMenuState,
        editingBundleTitleState = editingBundleTitleState
    )
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
