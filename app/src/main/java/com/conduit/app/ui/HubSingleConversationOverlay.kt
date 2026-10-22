@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.conduit.app.ui

import com.conduit.app.*
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.*
import android.app.Notification
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density

@Composable
fun BoxScope.HubSingleConversationOverlay(
    context: Context,
    viewModel: HubViewModel,
    actionsByKey: Map<String, List<Notification.Action>>,
    showActionChips: Boolean,
    selectedIdsState: MutableState<Set<Int>>,
    isSelectionMode: Boolean,
    activeSingleConversationKeyState: MutableState<String?>,
    activeSingleConversationOriginYState: MutableFloatState,
    activeSingleConversation: SingleConversationRollup?,
    lastActiveConversation: SingleConversationRollup?,
    density: Density,
    statusBarHeightPx: Float
) {
    var selectedIds by selectedIdsState
    var activeSingleConversationKey by activeSingleConversationKeyState
    val activeSingleConversationOriginY by activeSingleConversationOriginYState
    val singleConvoTransition = updateTransition(
        targetState = activeSingleConversation != null,
        label = "SingleConvoTransition"
    )
    if (singleConvoTransition.currentState || singleConvoTransition.targetState) {
        val expandProgress by singleConvoTransition.animateFloat(
            transitionSpec = {
                if (targetState) {
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                } else {
                    tween(durationMillis = 240, easing = FastOutSlowInEasing)
                }
            },
            label = "SingleConvoExpandProgress"
        ) { isExpanded ->
            if (isExpanded) 1f else 0f
        }
        val overlayAlpha by singleConvoTransition.animateFloat(
            transitionSpec = {
                if (targetState) {
                    tween(durationMillis = 120, easing = LinearOutSlowInEasing)
                } else {
                    tween(durationMillis = 220, easing = FastOutLinearInEasing)
                }
            },
            label = "SingleConvoOverlayAlpha"
        ) { isExpanded ->
            if (isExpanded) 1f else 0f
        }

        val convoToDisplay = activeSingleConversation ?: lastActiveConversation
        if (convoToDisplay != null) {
            val singleConvoListState = androidx.compose.foundation.lazy.rememberLazyListState()
            LaunchedEffect(activeSingleConversationKey) {
                if (activeSingleConversationKey != null) {
                    singleConvoListState.scrollToItem(0)
                }
            }

            val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
            val initialHeaderOffsetPx = (activeSingleConversationOriginY - statusBarHeightPx)
                .coerceIn(0f, (screenHeightPx * 0.85f).coerceAtLeast(0f))
            val unfurlSlidePx = with(density) { 28.dp.toPx() }
            val bgColor = MaterialTheme.colorScheme.background

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.TopCenter)
                    .zIndex(10f)
                    .drawBehind {
                        drawRect(color = bgColor.copy(alpha = expandProgress.coerceIn(0f, 1f)))
                    }
                    .statusBarsPadding()
                    .pointerInput(Unit) {
                        detectTapGestures { }
                    }
            ) {
                SingleConversationStickyHeader(
                    rollup = convoToDisplay,
                    onCollapse = { activeSingleConversationKey = null },
                    onMarkAllRead = {
                        viewModel.markNotificationsAsRead(convoToDisplay.activeMessages)
                    },
                    modifier = Modifier
                        .zIndex(2f)
                        .graphicsLayer {
                            val p = expandProgress.coerceIn(0f, 1f)
                            translationY = initialHeaderOffsetPx * (1f - p)
                            alpha = overlayAlpha.coerceIn(0f, 1f)
                        }
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .zIndex(1f)
                        .graphicsLayer {
                            val p = expandProgress.coerceIn(0f, 1f)
                            translationY = initialHeaderOffsetPx * (1f - p)
                            alpha = overlayAlpha.coerceIn(0f, 1f)
                        }
                        .drawWithContent {
                            val p = expandProgress.coerceIn(0f, 1f)
                            val revealH = (size.height * p).coerceIn(0f, size.height)
                            if (revealH > 0f) {
                                drawRect(
                                    color = bgColor,
                                    topLeft = Offset.Zero,
                                    size = Size(size.width, revealH)
                                )
                                clipRect(
                                    left = 0f,
                                    top = 0f,
                                    right = size.width,
                                    bottom = revealH
                                ) {
                                    this@drawWithContent.drawContent()
                                }
                            }
                        }
                ) {
                    LazyColumn(
                        state = singleConvoListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val p = expandProgress.coerceIn(0f, 1f)
                                translationY = -unfurlSlidePx * (1f - p)
                            },
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        singleConversationDetail(
                            rollup = convoToDisplay,
                            actionsByKey = actionsByKey,
                            isSelectionMode = isSelectionMode,
                            selectedIds = selectedIds,
                            onSelectToggle = { id ->
                                val becomingSelected = !selectedIds.contains(id)
                                if (becomingSelected) performHapticClick(context) else performHapticTick(context)
                                selectedIds = if (becomingSelected) selectedIds + id else selectedIds - id
                            },
                            showActionChips = showActionChips,
                            onTriggerAction = { notif, action ->
                                val actionTitle = action.title?.toString()?.lowercase() ?: ""
                                if (actionTitle.contains("read") || actionTitle.contains("done") || actionTitle.contains("seen")) {
                                    viewModel.markNotificationsAsRead(convoToDisplay.activeMessages)
                                } else {
                                    viewModel.triggerAction(context, notif, action)
                                }
                            },
                            onReply = { notif, text, action ->
                                viewModel.sendReply(context, notif, text, action)
                            }
                        )
                    }
                }
            }
        }
    }
}
