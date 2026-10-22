@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.conduit.app.ui

import com.conduit.app.*
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.combinedClickable
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.conduit.app.data.HubNotification
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Shape
import android.content.SharedPreferences
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.unit.Dp
import com.conduit.app.data.ConduitSettings

@Composable
fun RowScope.HubDockPackages(
    context: Context,
    prefs: SharedPreferences,
    settings: ConduitSettings,
    dockScrollIndicator: String,
    unifiedView: Boolean,
    selectedDockPackageState: MutableState<String?>,
    activeViewDockFilter: Boolean,
    dockPackagesList: List<String>,
    grouped: Map<String, List<HubNotification>>,
    dockIconSize: Dp,
    dockSpacing: Dp,
    dockBoxPadding: Dp,
    dockPaddingHorizontal: Dp,
    dockPaddingVertical: Dp,
    dockBadgeOffsetY: Dp,
    dockBadgeOffsetX: Dp,
    listState: LazyListState,
    dockHeight: Dp,
    isExpandLeft: Boolean,
    dockPackagesShape: Shape,
    isFading: Boolean,
    fadeWidthPx: Float
) {
    var selectedDockPackage by selectedDockPackageState
    Surface(
        modifier = if (settings.dockExpandButtonVisible) {
            Modifier.height(dockHeight).weight(1f, fill = false).wrapContentWidth()
        } else {
            Modifier.height(dockHeight).wrapContentWidth()
        },
        shape = dockPackagesShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 8.dp
    ) {
        Box(
            modifier = Modifier.fillMaxHeight().wrapContentWidth(),
            contentAlignment = Alignment.Center
        ) {
            LazyRow(
                state = listState,
                modifier = Modifier
                    .padding(vertical = 1.dp)
                    .wrapContentWidth()
                    .then(
                        if (isFading) {
                            Modifier
                                .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                                .drawWithContent {
                                    drawContent()
                                    val canScrollBackward = listState.canScrollBackward
                                    val canScrollForward = listState.canScrollForward
                                    if (canScrollBackward) {
                                        drawRect(
                                            brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                                colors = listOf(Color.Transparent, Color.Black),
                                                startX = 0f,
                                                endX = fadeWidthPx
                                            ),
                                            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
                                        )
                                    }
                                    if (canScrollForward) {
                                        drawRect(
                                            brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                                colors = listOf(Color.Black, Color.Transparent),
                                                startX = this.size.width - fadeWidthPx,
                                                endX = this.size.width
                                            ),
                                            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
                                        )
                                    }
                                }
                        } else Modifier
                    ),
                contentPadding = PaddingValues(
                    start = if (settings.dockExpandButtonVisible && isExpandLeft) (dockPaddingHorizontal * 0.65f).coerceAtLeast(8.dp) else dockPaddingHorizontal,
                    end = if (settings.dockExpandButtonVisible && !isExpandLeft) (dockPaddingHorizontal * 0.65f).coerceAtLeast(8.dp) else dockPaddingHorizontal,
                    top = dockPaddingVertical,
                    bottom = dockPaddingVertical
                ), 
                horizontalArrangement = Arrangement.spacedBy(dockSpacing, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(
                    count = dockPackagesList.size,
                    key = { index -> dockPackagesList[index] }
                ) { index ->
                    val pkg = dockPackagesList[index]
                    val groupNotifs = grouped[pkg] ?: emptyList()

                    if ((unifiedView || activeViewDockFilter) && index > 0) {
                        val prevPkg = dockPackagesList[index - 1]
                        val isPrevUnread = grouped.containsKey(prevPkg)
                        val isCurrentRead = !grouped.containsKey(pkg)
                        if (isPrevUnread && isCurrentRead) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .height(24.dp)
                                    .width(1.dp)
                                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                    }

                    val isSelected = selectedDockPackage == pkg
                    Box(
                        modifier = Modifier
                            .combinedClickable(
                                onClick = {
                                    if (isSelected) selectedDockPackage = null else selectedDockPackage = pkg
                                },
                                onLongClick = {
                                    val shouldLaunch = prefs.getBoolean("dock_long_press_launch", true)
                                    if (shouldLaunch) {
                                        try {
                                            val launched = launchApp(context, pkg)
                                            if (launched) {
                                                performHapticClick(context)
                                            } else {
                                                val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
                                                if (launchIntent != null) {
                                                    context.startActivity(launchIntent)
                                                    performHapticClick(context)
                                                } else {
                                                    android.widget.Toast.makeText(context, "Cannot open this application.", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        } catch (e: Exception) {
                                            android.util.Log.e("HubScreen", "Failed to launch application for $pkg", e)
                                            android.widget.Toast.makeText(context, "Failed to launch application.", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent,
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                            .padding(dockBoxPadding)
                    ) {
                        androidx.compose.material3.BadgedBox(
                            badge = {
                                if (groupNotifs.isNotEmpty()) {
                                    androidx.compose.material3.Badge(
                                        modifier = Modifier.offset(y = dockBadgeOffsetY, x = dockBadgeOffsetX)
                                    ) {
                                        Text(groupNotifs.size.toString())
                                    }
                                }
                            }
                        ) {
                            AppIcon(
                                packageName = pkg,
                                size = dockIconSize
                            )
                        }
                    }
                }
            }

            if (dockScrollIndicator == "SCROLLBAR" && dockPackagesList.isNotEmpty()) {
                val layoutInfo = listState.layoutInfo
                val totalItems = layoutInfo.totalItemsCount
                val visibleItems = layoutInfo.visibleItemsInfo.size
                if (totalItems > 0 && visibleItems < totalItems) {
                    val scrollRange = totalItems - visibleItems
                    val scrollFraction = if (scrollRange > 0) {
                        val firstIndex = listState.firstVisibleItemIndex
                        val firstOffset = listState.firstVisibleItemScrollOffset
                        val itemSize = layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 1
                        ((firstIndex.toFloat() + (firstOffset.toFloat() / itemSize)) / scrollRange).coerceIn(0f, 1f)
                    } else 0f

                    val trackWidth = 40.dp
                    val thumbWidth = 12.dp
                    val maxThumbOffset = trackWidth - thumbWidth

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 2.dp)
                            .width(trackWidth)
                            .height(3.dp)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f), androidx.compose.foundation.shape.CircleShape)
                    ) {
                        Box(
                            modifier = Modifier
                                .offset(x = maxThumbOffset * scrollFraction)
                                .width(thumbWidth)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape)
                        )
                    }
                }
            }
        }
    }
}
