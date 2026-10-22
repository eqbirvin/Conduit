@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.conduit.app.ui

import com.conduit.app.*
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.core.*
import androidx.compose.material.icons.Icons
import androidx.compose.animation.*
import android.app.Notification
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conduit.app.data.HubNotification
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyListScope
import com.conduit.app.data.ConduitSettings

@Composable
fun HubFeedList(
    context: Context,
    viewModel: HubViewModel,
    settings: ConduitSettings,
    notifications: List<HubNotification>,
    actionsByKey: Map<String, List<Notification.Action>>,
    showActionChips: Boolean,
    unifiedView: Boolean,
    onArchiveNotification: (Int, Long) -> Unit,
    onPinNotification: (HubNotification) -> Unit,
    masterExpandedState: Boolean,
    individualToggles: SnapshotStateMap<Int, Boolean>,
    getRollupState: (Int) -> ConversationRollupState,
    cycleRollupState: (Int) -> Unit,
    swipeLeftAction: String,
    swipeRightAction: String,
    notificationToSnoozeState: MutableState<HubNotification?>,
    notificationToBlockState: MutableState<HubNotification?>,
    selectedIdsState: MutableState<Set<Int>>,
    isSelectionMode: Boolean,
    activeSingleConversationKeyState: MutableState<String?>,
    activeSingleConversationOriginYState: MutableFloatState,
    displayNotifications: List<HubNotification>,
    mainListState: LazyListState,
    padding: PaddingValues,
    singleConvoFeedItems: List<SingleConversationFeedItem>
) {
    var selectedIds by selectedIdsState
    var activeSingleConversationKey by activeSingleConversationKeyState
    var activeSingleConversationOriginY by activeSingleConversationOriginYState
    val pinnedNotifications = remember(displayNotifications, settings.singleConversationView) {
        if (settings.singleConversationView) emptyList() else displayNotifications.filter { it.isPinned }
    }
    val unpinnedNotifications = remember(displayNotifications, settings.singleConversationView) {
        if (settings.singleConversationView) emptyList() else displayNotifications.filter { !it.isPinned }
    }
    val activeNotificationIds = remember(notifications) { notifications.map { it.id }.toSet() }
    val groupedNotifications = remember(unpinnedNotifications, settings.combineConversations, settings.singleConversationView) {
        if (settings.singleConversationView) {
            emptyMap()
        } else {
            unpinnedNotifications
                .groupBy { formatDateHeader(it.timestamp) }
                .mapValues { (_, itemsList) ->
                    ConversationCombineHelper.groupConversationItems(context, itemsList, settings.combineConversations)
                }
        }
    }
    val groupedPinned = remember(pinnedNotifications, settings.combineConversations, settings.singleConversationView) {
        if (settings.singleConversationView) {
            emptyList()
        } else {
            ConversationCombineHelper.groupConversationItems(context, pinnedNotifications, settings.combineConversations)
        }
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
        if (settings.singleConversationView) {
            singleConversationFeed(
                context = context,
                pinnedItems = singleConvoPinned,
                groupedUnpinnedItems = singleConvoGroupedUnpinned,
                onOpenConversation = { convoKey, originY ->
                    activeSingleConversationOriginY = originY
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
                onToggleIndividual = { notif ->
                    val currentState = individualToggles[notif.id]
                        ?: if (settings.autoCollapseRead && notif.isArchived) false else settings.masterExpandedState
                    individualToggles[notif.id] = !currentState
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
                onMarkDateRead = { notifs ->
                    viewModel.markNotificationsAsRead(notifs)
                }
            )
        } else {
        // Pinned section
        hubPinnedSection(
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
            getRollupState = getRollupState,
            cycleRollupState = cycleRollupState,
            selectedIdsState = selectedIdsState,
            isSelectionMode = isSelectionMode,
            pinnedNotifications = pinnedNotifications,
            visiblePinned = visiblePinned
        )

        // Unpinned date-grouped section
        hubDateGroupedSection(
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
            getRollupState = getRollupState,
            cycleRollupState = cycleRollupState,
            swipeLeftAction = swipeLeftAction,
            swipeRightAction = swipeRightAction,
            notificationToSnoozeState = notificationToSnoozeState,
            notificationToBlockState = notificationToBlockState,
            selectedIdsState = selectedIdsState,
            isSelectionMode = isSelectionMode,
            activeNotificationIds = activeNotificationIds,
            visibleGroupedNotifications = visibleGroupedNotifications,
            screenWidthPx = screenWidthPx
        )
        }
        }
    }
}

fun LazyListScope.hubPinnedSection(
    context: Context,
    viewModel: HubViewModel,
    settings: ConduitSettings,
    actionsByKey: Map<String, List<Notification.Action>>,
    showActionChips: Boolean,
    unifiedView: Boolean,
    onArchiveNotification: (Int, Long) -> Unit,
    onPinNotification: (HubNotification) -> Unit,
    masterExpandedState: Boolean,
    individualToggles: SnapshotStateMap<Int, Boolean>,
    getRollupState: (Int) -> ConversationRollupState,
    cycleRollupState: (Int) -> Unit,
    selectedIdsState: MutableState<Set<Int>>,
    isSelectionMode: Boolean,
    pinnedNotifications: List<HubNotification>,
    visiblePinned: List<GroupedConversationItem>
) {
    var selectedIds by selectedIdsState
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
}
