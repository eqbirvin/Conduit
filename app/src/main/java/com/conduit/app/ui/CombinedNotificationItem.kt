package com.conduit.app.ui

import android.app.Notification
import android.content.Context
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conduit.app.HubNotificationListenerService
import com.conduit.app.data.AppDatabase
import com.conduit.app.data.HubNotification
import com.conduit.app.performHapticClick
import com.conduit.app.performHapticTick
import com.conduit.app.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun CombinedNotificationItem(
    notification: HubNotification,
    displayText: String,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onSelectToggle: () -> Unit,
    showActionChips: Boolean,
    minimizeIcons: Boolean,
    allActions: List<Notification.Action>?,
    isExpanded: Boolean,
    onExpandToggle: () -> Unit,
    onTriggerAction: (Notification.Action) -> Unit,
    onReply: (String, Notification.Action) -> Unit,
    onArchiveNotification: ((Int, Long) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isReplying by remember { mutableStateOf(false) }
    var replyText by remember { mutableStateOf("") }
    val replyAction = remember(allActions) {
        allActions?.firstOrNull { it.remoteInputs != null && it.remoteInputs.isNotEmpty() }
    }

    val generalPrefs = remember { context.getSharedPreferences("conduit_prefs", Context.MODE_PRIVATE) }
    val smartMarkRead = remember { generalPrefs.getBoolean("smart_mark_read", true) }
    val smartMarkReadTarget = remember { generalPrefs.getString("smart_mark_read_target", "widget_and_app") ?: "widget_and_app" }

    val hasNativeMarkRead = remember(allActions) {
        allActions?.any { action ->
            val title = action.title?.toString()?.lowercase() ?: ""
            title.contains("mark as read") || title.contains("mark read") || title.contains("read")
        } ?: false
    }

    val showConduitMarkRead = showActionChips && !notification.isArchived &&
            smartMarkRead && smartMarkReadTarget == "widget_and_app" && !hasNativeMarkRead

    val hasChips = (allActions != null && allActions.isNotEmpty()) || showConduitMarkRead

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
                // Aligns with the 48dp avatar box of the parent card above
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
                // Main content line: Message Text on left, Time + Expand toggle on right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = if (isExpanded) Alignment.Top else Alignment.CenterVertically
                ) {
                    if (!isExpanded) {
                        Text(
                            text = annotatedText,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Text(
                            text = annotatedText,
                            fontSize = 14.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
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

                // Expanded Action Chips & Inline Reply
                if (isExpanded && showActionChips && !notification.isArchived && hasChips && !isReplying) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        allActions?.forEach { action ->
                            val title = action.title?.toString() ?: ""
                            if (title.isBlank()) return@forEach
                            val isReply = action.remoteInputs != null && action.remoteInputs.isNotEmpty()

                            Surface(
                                onClick = {
                                    performHapticTick(context)
                                    if (isReply) {
                                        isReplying = true
                                    } else {
                                        onTriggerAction(action)
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = title,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        if (showConduitMarkRead) {
                            Surface(
                                onClick = {
                                    performHapticTick(context)
                                    if (onArchiveNotification != null) {
                                        onArchiveNotification(notification.id, System.currentTimeMillis())
                                    } else {
                                        val db = AppDatabase.getDatabase(context)
                                        val service = HubNotificationListenerService.instance
                                        val syncDismissal = generalPrefs.getBoolean("sync_dismissal", true)
                                        coroutineScope.launch(Dispatchers.IO) {
                                            db.notificationDao().archiveNotificationByKey(notification.notificationKey, System.currentTimeMillis())
                                            if (syncDismissal) {
                                                service?.cancel(notification.notificationKey)
                                            }
                                            WidgetUpdater.updateAllWidgets(context)
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                            ) {
                                Text(
                                    text = if (notification.kind == "MESSAGE") "Mark Read" else "Dismiss",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                // Inline Reply Field
                if (isExpanded && isReplying) {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        TextField(
                            value = replyText,
                            onValueChange = { replyText = it },
                            placeholder = { Text("Type a reply...") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences
                            ),
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )
                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            TextButton(onClick = {
                                isReplying = false
                                replyText = ""
                            }) {
                                Text("Cancel")
                            }
                            Button(
                                onClick = {
                                    if (replyAction != null) {
                                        onReply(replyText, replyAction)
                                    }
                                    isReplying = false
                                    replyText = ""
                                },
                                enabled = replyText.isNotBlank(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Send")
                            }
                        }
                    }
                }
            }
        }
    }
}
