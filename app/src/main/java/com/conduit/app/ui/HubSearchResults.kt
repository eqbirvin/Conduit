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
import androidx.paging.compose.itemKey
import androidx.paging.LoadState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
                            modifier = Modifier.animateItem(
                                placementSpec = tween(durationMillis = 300)
                            )
                        ) {
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
                    }
                }
            }
        }
        }
    }
    }
}
