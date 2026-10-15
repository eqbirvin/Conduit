@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.conduit.app.ui

import android.app.Notification
import android.content.Context
import com.conduit.app.appLabelCache
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conduit.app.data.ConduitSettings
import com.conduit.app.data.HubNotification
import com.conduit.app.getAppLabel
import com.conduit.app.getRepresentativePackage
import com.conduit.app.performHapticClick
import com.conduit.app.performHapticTick

@Composable
fun SingleConversationStickyHeader(
    rollup: SingleConversationRollup,
    onCollapse: () -> Unit,
    onMarkAllRead: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var appName by remember(rollup.appPackage) {
        mutableStateOf(appLabelCache[rollup.appPackage] ?: rollup.primaryNotification.channel)
    }

    LaunchedEffect(rollup.appPackage) {
        if (!appLabelCache.containsKey(rollup.appPackage)) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val repPkg = getRepresentativePackage(context, rollup.appPackage)
                val label = getAppLabel(context, repPkg, rollup.primaryNotification.channel)
                appName = label
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIcon(packageName = rollup.appPackage, size = 38.dp)

                Spacer(modifier = Modifier.width(10.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Text(
                        text = rollup.conversationTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = appName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val totalCount = rollup.allHistoryMessages.size
                val pendingCount = rollup.activeMessages.size
                val chipText = if (pendingCount > 0) "$totalCount | UNREAD $pendingCount" else "$totalCount"

                Row(
                    modifier = Modifier.height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.fillMaxHeight(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxHeight(),
                            shape = if (pendingCount > 0 && onMarkAllRead != null) {
                                RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 0.dp, bottomEnd = 0.dp)
                            } else {
                                MaterialTheme.shapes.small
                            },
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

                        if (pendingCount > 0 && onMarkAllRead != null) {
                            val rightShape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 8.dp, bottomEnd = 8.dp)
                            Surface(
                                shape = rightShape,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .clip(rightShape)
                                    .clickable {
                                        performHapticClick(context)
                                        onMarkAllRead()
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

                    Spacer(modifier = Modifier.width(10.dp))

                    val collapseShape = RoundedCornerShape(8.dp)
                    Surface(
                        shape = collapseShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(1f, matchHeightConstraintsFirst = true)
                            .clip(collapseShape)
                            .clickable {
                                performHapticTick(context)
                                onCollapse()
                            }
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowUp,
                                contentDescription = "Collapse",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            DoubleDivider()
        }
    }
}

fun LazyListScope.singleConversationDetail(
    rollup: SingleConversationRollup,
    onCollapse: () -> Unit,
    onMarkAllRead: () -> Unit,
    actionsByKey: Map<String, List<Notification.Action>>,
    unifiedView: Boolean,
    isSelectionMode: Boolean,
    selectedIds: Set<Int>,
    onSelectToggle: (Int) -> Unit,
    showActionChips: Boolean,
    settings: ConduitSettings,
    onArchiveNotification: (Int, Long) -> Unit,
    onPinNotification: (HubNotification) -> Unit,
    onTriggerAction: (HubNotification, Notification.Action) -> Unit,
    onReply: (HubNotification, String, Notification.Action) -> Unit
) {
    stickyHeader(key = "single_convo_header_${rollup.convoKey}") {
        SingleConversationStickyHeader(
            rollup = rollup,
            onCollapse = onCollapse,
            onMarkAllRead = onMarkAllRead
        )
    }

    itemsIndexed(
        items = rollup.allHistoryMessages,
        key = { _, msg -> msg.id }
    ) { index, msg ->
        val isLatest = index == 0
        val showChipsForThisItem = showActionChips && isLatest && rollup.hasActiveUnread

        SingleConversationMessageItem(
            notification = msg,
            headerTitle = rollup.conversationTitle,
            isSelected = selectedIds.contains(msg.id),
            isSelectionMode = isSelectionMode,
            onSelectToggle = { onSelectToggle(msg.id) },
            showActionChips = showChipsForThisItem,
            allActions = if (showChipsForThisItem) actionsByKey[msg.notificationKey] else null,
            onTriggerAction = { action -> onTriggerAction(msg, action) },
            onReply = { text, action -> onReply(msg, text, action) }
        )
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            thickness = 0.5.dp
        )
    }
}

fun LazyListScope.singleConversationFeed(
    context: Context,
    pinnedItems: List<SingleConversationFeedItem>,
    groupedUnpinnedItems: Map<String, List<SingleConversationFeedItem>>,
    onOpenConversation: (String) -> Unit,
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
    onToggleIndividual: (Int) -> Unit,
    onTriggerAction: (HubNotification, List<HubNotification>, Notification.Action) -> Unit,
    onReply: (HubNotification, String, Notification.Action) -> Unit,
    activeNotificationIds: Set<Int>,
    onArchiveAllInDate: (List<Int>) -> Unit
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
            key = { it.id }
        ) { item ->
            when (item) {
                is SingleConversationFeedItem.Conversation -> {
                    val rollup = item.rollup
                    val notification = rollup.primaryNotification
                    val isMultiMessage = rollup.subNotificationCount > 0
                    val isSelected = if (isMultiMessage) {
                        rollup.allHistoryMessages.any { selectedIds.contains(it.id) }
                    } else {
                        selectedIds.contains(notification.id)
                    }

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
                                rollup.allHistoryMessages.map { it.id }.toSet()
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
                                onOpenConversation(rollup.convoKey)
                            } else {
                                onToggleIndividual(notification.id)
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
                is SingleConversationFeedItem.Standalone -> {
                    val notification = item.notification
                    NotificationItem(
                        notification = notification,
                        isArchivedView = false,
                        isUnifiedView = unifiedView,
                        onArchiveNotification = onArchiveNotification,
                        onPinNotification = onPinNotification,
                        isSelected = selectedIds.contains(notification.id),
                        isSelectionMode = isSelectionMode,
                        onSelectToggle = { onSelectToggleIds(setOf(notification.id)) },
                        showActionChips = showActionChips,
                        minimizeIcons = settings.minimizeIcons,
                        allActions = actionsByKey[notification.notificationKey],
                        isExpanded = individualToggles[notification.id]
                            ?: if (settings.autoCollapseRead && notification.isArchived) false else settings.masterExpandedState,
                        onExpandToggle = { onToggleIndividual(notification.id) },
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

    groupedUnpinnedItems.forEach { (dateHeader, itemsList) ->
        stickyHeader(key = "single_convo_date_$dateHeader") {
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
                                            onArchiveAllInDate(itemsToArchive.map { it.id })
                                        }
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)) {
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
            when (item) {
                is SingleConversationFeedItem.Conversation -> {
                    val rollup = item.rollup
                    val notification = rollup.primaryNotification
                    val isMultiMessage = rollup.subNotificationCount > 0
                    val isSelected = if (isMultiMessage) {
                        rollup.allHistoryMessages.any { selectedIds.contains(it.id) }
                    } else {
                        selectedIds.contains(notification.id)
                    }

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
                                rollup.allHistoryMessages.map { it.id }.toSet()
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
                                onOpenConversation(rollup.convoKey)
                            } else {
                                onToggleIndividual(notification.id)
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
                is SingleConversationFeedItem.Standalone -> {
                    val notification = item.notification
                    NotificationItem(
                        notification = notification,
                        isArchivedView = false,
                        isUnifiedView = unifiedView,
                        onArchiveNotification = onArchiveNotification,
                        onPinNotification = onPinNotification,
                        isSelected = selectedIds.contains(notification.id),
                        isSelectionMode = isSelectionMode,
                        onSelectToggle = { onSelectToggleIds(setOf(notification.id)) },
                        showActionChips = showActionChips,
                        minimizeIcons = settings.minimizeIcons,
                        allActions = actionsByKey[notification.notificationKey],
                        isExpanded = individualToggles[notification.id]
                            ?: if (settings.autoCollapseRead && notification.isArchived) false else settings.masterExpandedState,
                        onExpandToggle = { onToggleIndividual(notification.id) },
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
