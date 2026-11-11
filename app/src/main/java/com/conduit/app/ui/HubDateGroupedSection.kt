@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.conduit.app.ui

import com.conduit.app.*
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import com.conduit.app.data.HubNotification
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.foundation.lazy.LazyListScope
import com.conduit.app.data.ConduitSettings

fun LazyListScope.hubDateGroupedSection(
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
    swipeLeftAction: String,
    swipeRightAction: String,
    notificationToSnoozeState: MutableState<HubNotification?>,
    notificationToBlockState: MutableState<HubNotification?>,
    selectedIdsState: MutableState<Set<Int>>,
    isSelectionMode: Boolean,
    activeNotificationIds: Set<Int>,
    visibleGroupedNotifications: Map<String, List<GroupedConversationItem>>,
    screenWidthPx: Float,
    onDateHeaderClick: () -> Unit
) {
    var notificationToSnooze by notificationToSnoozeState
    var notificationToBlock by notificationToBlockState
    var selectedIds by selectedIdsState
    visibleGroupedNotifications.forEach { (dateHeader, itemsList) ->
        stickyHeader {
            HubDateHeader(
                context = context,
                viewModel = viewModel,
                unifiedView = unifiedView,
                activeNotificationIds = activeNotificationIds,
                dateHeader = dateHeader,
                itemsList = itemsList,
                showMarkAllRead = settings.showDateMarkAllRead,
                onClick = onDateHeaderClick
            )
        }
        items(itemsList, key = { it.primaryNotification.id }) { item ->
            val notification = item.primaryNotification
            SwipeableFeedRow(
                context = context,
                notification = notification,
                swipeLeftAction = swipeLeftAction,
                swipeRightAction = swipeRightAction,
                unifiedView = unifiedView,
                isSelectionMode = isSelectionMode,
                screenWidthPx = screenWidthPx,
                onSwipeAction = { action ->
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
                modifier = Modifier.animateItem(
                    placementSpec = tween(durationMillis = 300)
                )
            ) {
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
        }
    }
}
