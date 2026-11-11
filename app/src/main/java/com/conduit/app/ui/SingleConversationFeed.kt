@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.conduit.app.ui

import android.app.Notification
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conduit.app.data.ConduitSettings
import com.conduit.app.data.HubNotification
import com.conduit.app.performHapticClick
import com.conduit.app.performHapticTick

private val FeedSplitChipLeftShape = RoundedCornerShape(
    topStart = 8.dp,
    bottomStart = 8.dp,
    topEnd = 0.dp,
    bottomEnd = 0.dp
)

private val FeedSplitChipRightShape = RoundedCornerShape(
    topStart = 0.dp,
    bottomStart = 0.dp,
    topEnd = 8.dp,
    bottomEnd = 8.dp
)

@Composable
private fun SingleConversationFeedRow(
    context: Context,
    item: SingleConversationFeedItem,
    onOpenConversation: (String, Float) -> Unit,
    onArchiveNotification: (Int, Long) -> Unit,
    onPinNotification: (HubNotification) -> Unit,
    actionsByKey: Map<String, List<Notification.Action>>,
    unifiedView: Boolean,
    isSelectionMode: Boolean,
    selectedIds: Set<Int>,
    onSelectToggleIds: (Set<Int>) -> Unit,
    showActionChips: Boolean,
    settings: ConduitSettings,
    individualToggles: Map<Int, Boolean>,
    onToggleIndividual: (HubNotification) -> Unit,
    onTriggerAction: (HubNotification, List<HubNotification>, Notification.Action) -> Unit,
    onReply: (HubNotification, String, Notification.Action) -> Unit,
    swipeLeftAction: String,
    swipeRightAction: String,
    screenWidthPx: Float,
    onMarkRead: (List<HubNotification>) -> Unit,
    onSnoozeNotification: (HubNotification) -> Unit,
    onBlockNotification: (HubNotification) -> Unit
) {
    when (item) {
        is SingleConversationFeedItem.Conversation -> {
            val rollup = item.rollup
            val notification = rollup.primaryNotification
            val isMultiMessage = rollup.subNotificationCount > 0
            val isSelected = selectedIds.isNotEmpty() && if (isMultiMessage) {
                rollup.allHistoryMessages.any { selectedIds.contains(it.id) }
            } else {
                selectedIds.contains(notification.id)
            }
            var itemTopYPx by remember { mutableFloatStateOf(0f) }

            SwipeableFeedRow(
                context = context,
                notification = notification,
                swipeLeftAction = swipeLeftAction,
                swipeRightAction = swipeRightAction,
                unifiedView = unifiedView,
                isSelectionMode = isSelectionMode,
                screenWidthPx = screenWidthPx,
                isPinned = rollup.isPinned,
                onSwipeAction = { action ->
                    // The row carries the conversation's history and stays in the feed, so it always settles back.
                    when (action) {
                        "ARCHIVE" -> {
                            if (rollup.activeMessages.isNotEmpty()) onMarkRead(rollup.activeMessages)
                            false
                        }
                        "SNOOZE" -> {
                            onSnoozeNotification(notification)
                            false
                        }
                        "PIN" -> {
                            if (rollup.isPinned) {
                                rollup.allHistoryMessages.filter { it.isPinned }.forEach { onPinNotification(it) }
                            } else {
                                onPinNotification(notification)
                            }
                            false
                        }
                        "BLOCK" -> {
                            onBlockNotification(notification)
                            false
                        }
                        else -> false
                    }
                }
            ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coords ->
                        itemTopYPx = coords.positionInRoot().y
                    }
            ) {
                NotificationItem(
                    notification = notification,
                    isArchivedView = false,
                    isUnifiedView = unifiedView,
                    onArchiveNotification = onArchiveNotification,
                    onPinNotification = onPinNotification,
                    isSelected = isSelected,
                    isSelectionMode = isSelectionMode,
                    onSelectToggle = {
                        val allIds = if (isMultiMessage) {
                            rollup.allHistoryMessages.mapTo(HashSet(rollup.allHistoryMessages.size)) { it.id }
                        } else {
                            setOf(notification.id)
                        }
                        onSelectToggleIds(allIds)
                    },
                    showActionChips = showActionChips && rollup.hasActiveUnread,
                    minimizeIcons = settings.minimizeIcons,
                    allActions = actionsByKey[notification.notificationKey],
                    isExpanded = individualToggles[notification.id]
                        ?: if (settings.autoCollapseRead && notification.isArchived) false else settings.masterExpandedState,
                    onExpandToggle = {
                        if (isMultiMessage) {
                            performHapticTick(context)
                            onOpenConversation(rollup.convoKey, itemTopYPx)
                        } else {
                            onToggleIndividual(notification)
                        }
                    },
                    onTriggerAction = { action -> onTriggerAction(notification, rollup.activeMessages, action) },
                    onReply = { text, action -> onReply(notification, text, action) },
                    overrideText = null,
                    subNotificationCount = rollup.subNotificationCount,
                    conversationRollupState = if (isMultiMessage) ConversationRollupState.PARTIALLY_EXPANDED else null
                )
                if (isMultiMessage) {
                    DoubleDivider()
                } else {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                }
            }
            }
        }
        is SingleConversationFeedItem.Standalone -> {
            val notification = item.notification
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
                            onMarkRead(listOf(notification))
                            !unifiedView
                        }
                        "SNOOZE" -> {
                            onSnoozeNotification(notification)
                            false
                        }
                        "PIN" -> {
                            onPinNotification(notification)
                            false
                        }
                        "BLOCK" -> {
                            onBlockNotification(notification)
                            false
                        }
                        else -> false
                    }
                }
            ) {
            Column {
            NotificationItem(
                notification = notification,
                isArchivedView = false,
                isUnifiedView = unifiedView,
                onArchiveNotification = onArchiveNotification,
                onPinNotification = onPinNotification,
                isSelected = selectedIds.isNotEmpty() && selectedIds.contains(notification.id),
                isSelectionMode = isSelectionMode,
                onSelectToggle = { onSelectToggleIds(setOf(notification.id)) },
                showActionChips = showActionChips,
                minimizeIcons = settings.minimizeIcons,
                allActions = actionsByKey[notification.notificationKey],
                isExpanded = individualToggles[notification.id]
                    ?: if (settings.autoCollapseRead && notification.isArchived) false else settings.masterExpandedState,
                onExpandToggle = { onToggleIndividual(notification) },
                onTriggerAction = { action -> onTriggerAction(notification, listOf(notification), action) },
                onReply = { text, action -> onReply(notification, text, action) },
                overrideText = null,
                subNotificationCount = 0,
                conversationRollupState = null
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
            }
            }
        }
    }
}

fun LazyListScope.singleConversationFeed(
    context: Context,
    pinnedItems: List<SingleConversationFeedItem>,
    groupedUnpinnedItems: Map<String, List<SingleConversationFeedItem>>,
    onOpenConversation: (String, Float) -> Unit,
    onArchiveNotification: (Int, Long) -> Unit,
    onPinNotification: (HubNotification) -> Unit,
    actionsByKey: Map<String, List<Notification.Action>>,
    unifiedView: Boolean,
    isSelectionMode: Boolean,
    selectedIds: Set<Int>,
    onSelectToggleIds: (Set<Int>) -> Unit,
    showActionChips: Boolean,
    settings: ConduitSettings,
    individualToggles: Map<Int, Boolean>,
    onToggleIndividual: (HubNotification) -> Unit,
    onTriggerAction: (HubNotification, List<HubNotification>, Notification.Action) -> Unit,
    onReply: (HubNotification, String, Notification.Action) -> Unit,
    activeNotificationIds: Set<Int>,
    onMarkDateRead: (List<HubNotification>) -> Unit,
    swipeLeftAction: String,
    swipeRightAction: String,
    screenWidthPx: Float,
    onSnoozeNotification: (HubNotification) -> Unit,
    onBlockNotification: (HubNotification) -> Unit,
    onDateHeaderClick: () -> Unit
) {
    if (pinnedItems.isNotEmpty()) {
        stickyHeader(key = "single_convo_pinned_header") {
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
                        text = "PINNED",
                        style = MaterialTheme.typography.labelMedium,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = "${pinnedItems.size}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }

        items(
            items = pinnedItems,
            key = { "pinned_${it.id}" }
        ) { item ->
            SingleConversationFeedRow(
                context = context,
                item = item,
                onOpenConversation = onOpenConversation,
                onArchiveNotification = onArchiveNotification,
                onPinNotification = onPinNotification,
                actionsByKey = actionsByKey,
                unifiedView = unifiedView,
                isSelectionMode = isSelectionMode,
                selectedIds = selectedIds,
                onSelectToggleIds = onSelectToggleIds,
                showActionChips = showActionChips,
                settings = settings,
                individualToggles = individualToggles,
                onToggleIndividual = onToggleIndividual,
                onTriggerAction = onTriggerAction,
                onReply = onReply,
                swipeLeftAction = swipeLeftAction,
                swipeRightAction = swipeRightAction,
                screenWidthPx = screenWidthPx,
                onMarkRead = onMarkDateRead,
                onSnoozeNotification = onSnoozeNotification,
                onBlockNotification = onBlockNotification
            )
        }
    }

    groupedUnpinnedItems.forEach { (dateHeader, itemsList) ->
        stickyHeader(key = "single_convo_date_$dateHeader") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .clickable(onClick = onDateHeaderClick)
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

                    val allNotifsInDate = remember(itemsList) {
                        itemsList.flatMap { item ->
                            when (item) {
                                is SingleConversationFeedItem.Conversation -> item.rollup.allHistoryMessages
                                is SingleConversationFeedItem.Standalone -> listOf(item.notification)
                            }
                        }.distinctBy { it.id }
                    }
                    val pendingCount = remember(allNotifsInDate, activeNotificationIds) {
                        allNotifsInDate.count { activeNotificationIds.contains(it.id) }
                    }
                    val chipText = remember(unifiedView, pendingCount, itemsList.size) {
                        if (unifiedView) {
                            if (pendingCount > 0) "${itemsList.size} | UNREAD $pendingCount" else "${itemsList.size}"
                        } else {
                            "${itemsList.size}"
                        }
                    }

                    Row(
                        modifier = Modifier.height(IntrinsicSize.Min),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxHeight(),
                            shape = if (pendingCount > 0 && settings.showDateMarkAllRead) FeedSplitChipLeftShape else MaterialTheme.shapes.small,
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

                        if (pendingCount > 0 && settings.showDateMarkAllRead) {
                            Surface(
                                shape = FeedSplitChipRightShape,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .clip(FeedSplitChipRightShape)
                                    .clickable {
                                        performHapticClick(context)
                                        val itemsToArchive = allNotifsInDate.filter { activeNotificationIds.contains(it.id) }
                                        if (itemsToArchive.isNotEmpty()) {
                                            onMarkDateRead(itemsToArchive)
                                        }
                                    }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.DoneAll,
                                        contentDescription = "Mark all read",
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        items(
            items = itemsList,
            key = { it.id }
        ) { item ->
            SingleConversationFeedRow(
                context = context,
                item = item,
                onOpenConversation = onOpenConversation,
                onArchiveNotification = onArchiveNotification,
                onPinNotification = onPinNotification,
                actionsByKey = actionsByKey,
                unifiedView = unifiedView,
                isSelectionMode = isSelectionMode,
                selectedIds = selectedIds,
                onSelectToggleIds = onSelectToggleIds,
                showActionChips = showActionChips,
                settings = settings,
                individualToggles = individualToggles,
                onToggleIndividual = onToggleIndividual,
                onTriggerAction = onTriggerAction,
                onReply = onReply,
                swipeLeftAction = swipeLeftAction,
                swipeRightAction = swipeRightAction,
                screenWidthPx = screenWidthPx,
                onMarkRead = onMarkDateRead,
                onSnoozeNotification = onSnoozeNotification,
                onBlockNotification = onBlockNotification
            )
        }
    }
}
