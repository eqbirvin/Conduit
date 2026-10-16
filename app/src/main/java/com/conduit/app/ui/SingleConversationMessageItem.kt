@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.conduit.app.ui

import android.app.Notification
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
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
import com.conduit.app.data.HubNotification
import com.conduit.app.performHapticClick
import com.conduit.app.performHapticTick

private val ActionChipShape = RoundedCornerShape(8.dp)
private val ReplyInputShape = RoundedCornerShape(12.dp)

@Composable
fun SingleConversationMessageItem(
    notification: HubNotification,
    headerTitle: String,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onSelectToggle: () -> Unit,
    showActionChips: Boolean,
    allActions: List<Notification.Action>?,
    onTriggerAction: (Notification.Action) -> Unit,
    onReply: (String, Notification.Action) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isReplying by remember { mutableStateOf(false) }
    var replyText by remember { mutableStateOf("") }
    val replyAction = remember(allActions) {
        allActions?.find { it.remoteInputs != null && it.remoteInputs.isNotEmpty() }
    }

    val normalizedTitle = remember(notification.title) {
        SingleConversationHelper.normalizeTitle(notification.title)
    }
    val sender = remember(normalizedTitle) {
        ConversationCombineHelper.extractSenderName(normalizedTitle) ?: normalizedTitle
    }
    val isSameAsHeader = remember(sender, headerTitle, normalizedTitle) {
        sender.isBlank() ||
        sender.equals(headerTitle, ignoreCase = true) ||
        normalizedTitle.equals(headerTitle, ignoreCase = true)
    }
    val formattedTimestamp = remember(notification.timestamp) {
        formatTimestamp(notification.timestamp)
    }

    val replyPrimaryColor = MaterialTheme.colorScheme.primary
    val rawText = notification.text ?: ""
    val annotatedText = remember(rawText, replyPrimaryColor) {
        buildAnnotatedString {
            val lines = rawText.split("\n")
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
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.surface
            )
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
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            if (isSelectionMode) {
                Box(
                    modifier = Modifier.padding(end = 12.dp, top = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Selected",
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                if (!isSameAsHeader) {
                    // Group chat with distinct sender: display sender name on top row with timestamp on right
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = sender,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = formattedTimestamp,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = annotatedText,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    // 1-on-1 chat or sender matches header: message text on top row directly beside timestamp
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = annotatedText,
                            fontSize = 14.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = formattedTimestamp,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Action chips on latest message if active unread exist
                if (showActionChips && !isReplying && allActions != null && allActions.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        allActions.forEach { action ->
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
                                shape = ActionChipShape,
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
                    }
                }

                // Inline reply text field
                if (showActionChips && isReplying) {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        TextField(
                            value = replyText,
                            onValueChange = { replyText = it },
                            placeholder = { Text("Type a reply...") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = ReplyInputShape,
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
                                shape = ReplyInputShape
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
