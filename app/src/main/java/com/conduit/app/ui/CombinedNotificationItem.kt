package com.conduit.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conduit.app.data.HubNotification
import com.conduit.app.performHapticClick

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CombinedNotificationItem(
    notification: HubNotification,
    displayText: String,
    senderName: String? = null,
    isGroupChat: Boolean = false,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onSelectToggle: () -> Unit,
    minimizeIcons: Boolean,
    isExpanded: Boolean,
    onExpandToggle: () -> Unit
) {
    val context = LocalContext.current
    val replyPrimaryColor = MaterialTheme.colorScheme.primary
    val annotatedText = remember(displayText, replyPrimaryColor) {
        buildAnnotatedString {
            val lines = displayText.split("\n")
            lines.forEachIndexed { index, line ->
                if (index > 0) append("\n")
                if (line.startsWith("\u21aa You:")) {
                    val youPrefix = "\u21aa You:"
                    val messagePart = line.substring(youPrefix.length)
                    withStyle(style = SpanStyle(color = replyPrimaryColor, fontWeight = FontWeight.Bold)) {
                        append(youPrefix)
                    }
                    withStyle(style = SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(messagePart)
                    }
                } else {
                    append(line)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else if (notification.isArchived) MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp).copy(alpha = 0.7f)
                else MaterialTheme.colorScheme.surface
            )
            .animateContentSize()
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) {
                        onSelectToggle()
                    } else {
                        NotificationLaunchHelper.launchNotification(context, notification)
                    }
                },
                onLongClick = {
                    performHapticClick(context)
                    onSelectToggle()
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Top
        ) {
            if (!minimizeIcons) {
                // Aligns with the 48dp avatar box of the parent card above (total 72dp indent)
                val avatarBoxWidth = 48.dp
                Box(
                    modifier = Modifier.width(avatarBoxWidth),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
            } else {
                if (isSelected) {
                    Box(
                        modifier = Modifier.padding(end = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 2.dp)
            ) {
                // Top row: Sender Name (if group chat) or message preview on left, Time + Expand toggle on right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isGroupChat && !senderName.isNullOrBlank()) {
                            Text(
                                text = senderName,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else if (!isExpanded) {
                            Text(
                                text = annotatedText,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = formatTimestamp(notification.timestamp),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onExpandToggle
                                )
                                .background(MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(4.dp))
                        ) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                contentDescription = if (isExpanded) "Collapse" else "Expand",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                // Body text: if group chat, or if expanded, show the full message content
                if (isGroupChat && !senderName.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = annotatedText,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (isExpanded) 12 else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                } else if (isExpanded) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = annotatedText,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 12,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
