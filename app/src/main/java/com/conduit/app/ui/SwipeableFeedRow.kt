@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.conduit.app.ui

import com.conduit.app.*
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.scale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conduit.app.data.HubNotification

/**
 * Shared swipe-to-action wrapper for feed rows. [onSwipeAction] receives the configured action
 * ("ARCHIVE", "SNOOZE", "PIN", "BLOCK") and returns true to let the row stay dismissed.
 * [notification] and [isPinned] only drive the background labels.
 */
@Composable
fun SwipeableFeedRow(
    context: Context,
    notification: HubNotification,
    swipeLeftAction: String,
    swipeRightAction: String,
    unifiedView: Boolean,
    isSelectionMode: Boolean,
    screenWidthPx: Float,
    onSwipeAction: (String) -> Boolean,
    modifier: Modifier = Modifier,
    isPinned: Boolean = notification.isPinned,
    content: @Composable RowScope.() -> Unit
) {
    val currentLeftAction by rememberUpdatedState(swipeLeftAction)
    val currentRightAction by rememberUpdatedState(swipeRightAction)
    val currentOnSwipeAction by rememberUpdatedState(onSwipeAction)
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
            val action = if (value == SwipeToDismissBoxValue.StartToEnd) currentRightAction else currentLeftAction
            currentOnSwipeAction(action)
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
            modifier = modifier,
            gesturesEnabled = !isSelectionMode,
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
                    "PIN" -> if (isPinned) "Unpin" else "Pin"
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
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = textColor,
                            modifier = Modifier.scale(scale).size(28.dp)
                        )
                        Text(
                            label,
                            color = textColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.scale(scale)
                        )
                    }
                }
            },
            content = content
        )
    }
}
