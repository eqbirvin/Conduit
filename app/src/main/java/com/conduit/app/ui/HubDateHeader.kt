@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.conduit.app.ui

import com.conduit.app.*
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.animation.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HubDateHeader(
    context: Context,
    viewModel: HubViewModel,
    unifiedView: Boolean,
    activeNotificationIds: Set<Int>,
    dateHeader: String,
    itemsList: List<GroupedConversationItem>,
    showMarkAllRead: Boolean
) {
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
                    shape = if (pendingCount > 0 && showMarkAllRead) androidx.compose.foundation.shape.RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 0.dp, bottomEnd = 0.dp) else MaterialTheme.shapes.small,
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

                if (pendingCount > 0 && showMarkAllRead) {
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
