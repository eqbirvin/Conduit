package com.conduit.app.ui

import android.app.Notification
import com.conduit.app.appLabelCache
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
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
import com.conduit.app.data.HubNotification
import com.conduit.app.getAppLabel
import com.conduit.app.getRepresentativePackage
import com.conduit.app.performHapticClick
import com.conduit.app.performHapticTick

private val HeaderSplitChipLeftShape = RoundedCornerShape(
    topStart = 8.dp,
    bottomStart = 8.dp,
    topEnd = 0.dp,
    bottomEnd = 0.dp
)

private val HeaderSplitChipRightShape = RoundedCornerShape(
    topStart = 0.dp,
    bottomStart = 0.dp,
    topEnd = 8.dp,
    bottomEnd = 8.dp
)

private val HeaderCollapseButtonShape = RoundedCornerShape(8.dp)

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
                                HeaderSplitChipLeftShape
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
                            Surface(
                                shape = HeaderSplitChipRightShape,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .clip(HeaderSplitChipRightShape)
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

                    Surface(
                        shape = HeaderCollapseButtonShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(1f, matchHeightConstraintsFirst = true)
                            .clip(HeaderCollapseButtonShape)
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
    actionsByKey: Map<String, List<Notification.Action>>,
    isSelectionMode: Boolean,
    selectedIds: Set<Int>,
    onSelectToggle: (Int) -> Unit,
    showActionChips: Boolean,
    onTriggerAction: (HubNotification, Notification.Action) -> Unit,
    onReply: (HubNotification, String, Notification.Action) -> Unit
) {
    itemsIndexed(
        items = rollup.allHistoryMessages,
        key = { _, msg -> msg.id }
    ) { index, msg ->
        val isLatest = index == 0
        val showChipsForThisItem = showActionChips && isLatest && rollup.hasActiveUnread

        SingleConversationMessageItem(
            notification = msg,
            headerTitle = rollup.conversationTitle,
            isSelected = selectedIds.isNotEmpty() && selectedIds.contains(msg.id),
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
