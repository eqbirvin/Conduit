package com.conduit.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.conduit.app.data.ConversationViewMode
import com.conduit.app.performHapticTick

private data class ConversationViewOption(
    val mode: ConversationViewMode,
    val icon: ImageVector,
    val label: String,
    val description: String
)

// Built once per process, so the menu allocates nothing per recomposition.
private val conversationViewOptions = listOf(
    ConversationViewOption(
        ConversationViewMode.STOCK,
        Icons.Filled.ViewStream,
        "Stock View",
        "Every notification as its own card"
    ),
    ConversationViewOption(
        ConversationViewMode.COMBINE_CONVERSATIONS,
        Icons.Filled.Forum,
        "Combine Conversations",
        "Groups back-to-back messages into a thread"
    ),
    ConversationViewOption(
        ConversationViewMode.SINGLE_CONVERSATION,
        Icons.AutoMirrored.Filled.Chat,
        "Single Conversation View",
        "One entry per conversation; expand for history"
    )
)

/**
 * Top bar button for the conversation view. Tap opens a picker menu; long-press
 * cycles to the next view without opening it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ConversationViewButton(
    mode: ConversationViewMode,
    onSelect: (ConversationViewMode) -> Unit,
    onCycle: () -> Unit
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val current = conversationViewOptions.first { it.mode == mode }

    Box {
        // IconButton has no long-press, so this mirrors its 40dp ripple inside a 48dp touch target.
        Box(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .size(40.dp)
                .clip(CircleShape)
                .combinedClickable(
                    onClickLabel = "Choose conversation view",
                    onLongClickLabel = "Cycle to next conversation view",
                    onClick = {
                        performHapticTick(context)
                        expanded = true
                    },
                    onLongClick = {
                        performHapticTick(context)
                        onCycle()
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = current.icon,
                contentDescription = "Conversation View: ${current.label}",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            conversationViewOptions.forEach { option ->
                ConversationViewMenuItem(
                    option = option,
                    isSelected = option.mode == mode,
                    onClick = {
                        expanded = false
                        onSelect(option.mode)
                    }
                )
            }
        }
    }
}

@Composable
private fun ConversationViewMenuItem(
    option: ConversationViewOption,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val contentColor = if (isSelected) colors.onPrimaryContainer else colors.onSurface
    val descriptionColor = if (isSelected) colors.onPrimaryContainer.copy(alpha = 0.8f) else colors.onSurfaceVariant

    DropdownMenuItem(
        text = {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                Text(option.label, style = MaterialTheme.typography.bodyLarge)
                Text(option.description, style = MaterialTheme.typography.bodySmall, color = descriptionColor)
            }
        },
        leadingIcon = { Icon(option.icon, contentDescription = null) },
        onClick = onClick,
        colors = MenuDefaults.itemColors(textColor = contentColor, leadingIconColor = contentColor),
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(if (isSelected) colors.primaryContainer else Color.Transparent)
            .semantics { selected = isSelected }
    )
}
