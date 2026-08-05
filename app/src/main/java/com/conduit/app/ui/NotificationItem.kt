package com.conduit.app.ui

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.conduit.app.*
import com.conduit.app.data.HubNotification

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun NotificationItem(
    notification: HubNotification,
    isArchivedView: Boolean = false,
    isUnifiedView: Boolean = false,
    onArchiveNotification: ((Int, Long) -> Unit)? = null,
    onPinNotification: ((HubNotification) -> Unit)? = null,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onSelectToggle: (() -> Unit)? = null,
    isCompactMode: Boolean = false,
    showActionChips: Boolean = true,
    allActions: List<android.app.Notification.Action>? = null
) {
    val context = LocalContext.current
    var replyText by remember { mutableStateOf("") }
    var activeReplyAction by remember { mutableStateOf<android.app.Notification.Action?>(null) }

    val verticalPadding = if (isCompactMode) 6.dp else 12.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else Color.Transparent
            )
            .combinedClickable(
                onClick = {
                    if (isSelectionMode && onSelectToggle != null) {
                        onSelectToggle()
                    } else {
                        val intent = context.packageManager.getLaunchIntentForPackage(notification.packageName)
                        if (intent != null) {
                            try {
                                performHapticClick(context)
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                },
                onLongClick = {
                    if (onSelectToggle != null) {
                        onSelectToggle()
                    }
                }
            )
            .padding(vertical = verticalPadding, horizontal = 16.dp),
        verticalAlignment = Alignment.Top
    ) {
        val avatarSize = if (isCompactMode) 36.dp else 50.dp
        val avatarBoxWidth = if (isCompactMode) 44.dp else 60.dp

        // Selection / Icon area
        Box(
            modifier = Modifier
                .width(avatarBoxWidth)
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = androidx.compose.material3.ripple(bounded = false, radius = 32.dp),
                    enabled = true
                ) {
                    if (onSelectToggle != null) onSelectToggle()
                },
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(avatarSize)
                )
            } else {
                AppIcon(packageName = notification.packageName, size = avatarSize)
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                var appName by remember(notification.packageName) { mutableStateOf<String>(appLabelCache[notification.packageName] ?: notification.channel) }
                
                LaunchedEffect(notification.packageName) {
                    if (!appLabelCache.containsKey(notification.packageName)) {
                        val resolved = getAppLabel(context, notification.packageName, notification.channel)
                        appName = resolved
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = appName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )

                    if (isWorkProfilePackage(context, notification.packageName)) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Filled.Work,
                            contentDescription = "Work Profile",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                Text(
                    text = if (isArchivedView) formatTimestampWithDate(notification.archivedTimestamp ?: notification.timestamp) else formatTimestamp(notification.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            if (!notification.title.isNullOrEmpty()) {
                Text(
                    text = notification.title!!,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!notification.text.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = notification.text!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (isCompactMode) 1 else 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (showActionChips && !allActions.isNullOrEmpty() && !isSelectionMode) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    allActions.forEach { act ->
                        val isReplyAction = act.remoteInputs?.isNotEmpty() == true
                        val titleStr = act.title?.toString() ?: "Action"

                        SuggestionChip(
                            onClick = {
                                if (isReplyAction) {
                                    activeReplyAction = if (activeReplyAction == act) null else act
                                } else {
                                    triggerNotificationAction(context, notification, act)
                                }
                            },
                            label = {
                                Text(
                                    text = titleStr,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            icon = {
                                Icon(
                                    imageVector = if (isReplyAction) Icons.Default.Reply else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = if (isReplyAction) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            border = SuggestionChipDefaults.suggestionChipBorder(
                                enabled = true,
                                borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            )
                        )
                    }
                }

                if (activeReplyAction != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = replyText,
                            onValueChange = { replyText = it },
                            placeholder = { Text("Direct reply...", fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                if (replyText.isNotBlank()) {
                                    sendReply(context, notification, replyText, activeReplyAction!!)
                                    replyText = ""
                                    activeReplyAction = null
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.Send,
                                contentDescription = "Send Reply",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        if (notification.isPinned && !isSelectionMode) {
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                Icons.Filled.PushPin,
                contentDescription = "Pinned",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun AppIcon(packageName: String, size: Dp = 50.dp) {
    val context = LocalContext.current
    var iconBitmap by remember(packageName) { mutableStateOf<ImageBitmap?>(appIconCache[packageName]) }
    var isWorkProfile by remember(packageName) { mutableStateOf<Boolean>(isWorkProfilePackage(context, packageName)) }

    LaunchedEffect(packageName) {
        if (iconBitmap == null) {
            val loadedDrawable = try {
                val repPkg = getRepresentativePackage(context, packageName)
                getAppIcon(context, repPkg)
            } catch (e: Exception) {
                try {
                    getAppIcon(context, packageName)
                } catch (e2: Exception) {
                    null
                }
            }
            if (loadedDrawable != null) {
                val bmp = loadedDrawable.toBitmap().asImageBitmap()
                appIconCache[packageName] = bmp
                iconBitmap = bmp
            }
        }
    }

    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        if (iconBitmap != null) {
            Image(
                bitmap = iconBitmap!!,
                contentDescription = null,
                modifier = Modifier.size(size)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(size * 0.6f)
                )
            }
        }

        if (isWorkProfile) {
            Box(
                modifier = Modifier
                    .size(size * 0.45f)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(1.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Work,
                        contentDescription = "Work Profile",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(size * 0.3f)
                    )
                }
            }
        }
    }
}
