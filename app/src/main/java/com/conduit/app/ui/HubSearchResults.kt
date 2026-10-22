@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.conduit.app.ui

import com.conduit.app.*
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.scale
import androidx.compose.material.icons.Icons
import androidx.compose.animation.*
import android.app.Notification
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.paging.compose.itemKey
import androidx.paging.LoadState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conduit.app.data.HubNotification
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.paging.compose.LazyPagingItems
import com.conduit.app.data.ConduitSettings

@Composable
fun HubSearchResults(
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
    swipeLeftAction: String,
    swipeRightAction: String,
    notificationToSnoozeState: MutableState<HubNotification?>,
    notificationToBlockState: MutableState<HubNotification?>,
    selectedIdsState: MutableState<Set<Int>>,
    isSelectionMode: Boolean,
    searchQueryState: MutableState<String>,
    searchItems: LazyPagingItems<HubNotification>,
    searchCorrection: SearchCorrection?,
    padding: PaddingValues
) {
    var notificationToSnooze by notificationToSnoozeState
    var notificationToBlock by notificationToBlockState
    var selectedIds by selectedIdsState
    val searchQuery by searchQueryState
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
}
