@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.conduit.app.ui

import android.content.Context
import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conduit.app.getAppLabel
import com.conduit.app.getInstalledApps
import com.conduit.app.launchApp

data class BundleMenuEntry(
    val title: String,
    val icon: ImageVector,
    val packages: List<String>
)

fun getComposeActionLabel(action: String): String = when (action) {
    "conduit.action.SMS" -> "New Text"
    "conduit.action.EMAIL" -> "New Email"
    "conduit.action.DIAL" -> "New Call"
    "conduit.action.CONTACT" -> "New Contact"
    "conduit.action.NOTE" -> "New Note"
    "conduit.action.EVENT" -> "New Event"
    "conduit.action.TIMER" -> "Set Timer"
    "conduit.action.ALARM" -> "Set Alarm"
    "conduit.action.RECORD" -> "Quick Record"
    else -> "Compose Action"
}

@Composable
fun ComposeActionIcon(
    action: String,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val baseIcon: ImageVector = when (action) {
        "conduit.action.SMS" -> Icons.Filled.Sms
        "conduit.action.EMAIL" -> Icons.Filled.Email
        "conduit.action.DIAL" -> Icons.Filled.Phone
        "conduit.action.CONTACT" -> Icons.Filled.Person
        "conduit.action.NOTE" -> Icons.Filled.EditNote
        "conduit.action.EVENT" -> Icons.Filled.Event
        "conduit.action.TIMER" -> Icons.Filled.Timer
        "conduit.action.ALARM" -> Icons.Filled.Alarm
        "conduit.action.RECORD" -> Icons.Filled.Mic
        else -> Icons.Filled.Edit
    }

    val baseIconSize = size * 0.65f
    val badgeContainerSize = (size * 0.38f).coerceAtLeast(10.dp)
    val badgeIconSize = badgeContainerSize * 0.72f

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Main Action Icon (subtly shifted top-left to balance the corner badge)
        Icon(
            imageVector = baseIcon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(baseIconSize)
                .offset(x = (-1.5).dp, y = (-1.5).dp)
        )

        // Unified Mini Compose Plus Badge (Bottom-End Corner)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(badgeContainerSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(badgeIconSize)
            )
        }
    }
}

@Composable
fun BundleFabMenu(
    expanded: Boolean,
    recorderBundle: List<String>,
    aiBundle: List<String>,
    notesBundle: List<String>,
    composeBundle: List<String>,
    bundleMenuSizing: String,
    dockIconSize: Dp,
    dockExpandPosition: String = "LEFT",
    transparentBundlePills: Boolean = false,
    bundleMenuBlur: Boolean = true,
    conduitMenu: ConduitMenu? = null,
    onAppClick: (String) -> Unit,
    onBundleClick: (String, List<String>) -> Unit,
    onBundleLongClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Ordered top-to-bottom on screen: Recorders (top) -> AI Assistants -> Notes -> Compose (bottom, nearest dock)
    val bundleEntries = remember(recorderBundle, aiBundle, notesBundle, composeBundle) {
        listOf(
            BundleMenuEntry("Recorders", Icons.Filled.PlayArrow, recorderBundle),
            BundleMenuEntry("AI Assistants", Icons.Filled.Star, aiBundle),
            BundleMenuEntry("Notes", Icons.AutoMirrored.Filled.List, notesBundle),
            BundleMenuEntry("Compose", Icons.Filled.Edit, composeBundle)
        ).filter { it.packages.isNotEmpty() }
    }

    if (bundleEntries.isEmpty() && conduitMenu == null) return

    val isLeft = dockExpandPosition == "LEFT"
    val isCompact = bundleMenuSizing == "COMPACT"
    val floatingAppIconSize = if (isCompact) 28.dp else dockIconSize
    val floatingContainerSize = if (isCompact) 38.dp else (dockIconSize + 12.dp)
    val pillHeight = if (isCompact) 42.dp else (floatingContainerSize + 4.dp)
    val pillIconSize = if (isCompact) 18.dp else 24.dp
    val rowSpacing = if (isCompact) 8.dp else 12.dp

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val isAmoled = isDark && (MaterialTheme.colorScheme.surface == Color.Black || MaterialTheme.colorScheme.background == Color.Black)
    val colorScheme = MaterialTheme.colorScheme

    val entryButtonColor = remember(isDark, isAmoled, colorScheme) {
        if (isDark) {
            if (isAmoled) {
                Color(0xFF323842)
            } else {
                colorScheme.onSurface.copy(alpha = 0.10f).compositeOver(colorScheme.surfaceContainerHighest)
            }
        } else {
            colorScheme.onSurface.copy(alpha = 0.08f).compositeOver(colorScheme.surfaceVariant)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalAlignment = if (isLeft) Alignment.Start else Alignment.End,
        verticalArrangement = Arrangement.spacedBy(rowSpacing)
    ) {
        // The Conduit actions row (when present) sits below the bundles, nearest the dock
        val total = bundleEntries.size + if (conduitMenu != null) 1 else 0
        bundleEntries.forEachIndexed { index, entry ->
            BundleMenuRowVisibility(expanded = expanded, index = index, total = total) {
                BundleMenuRow(
                    entry = entry,
                    isCompact = isCompact,
                    isLeft = isLeft,
                    floatingContainerSize = floatingContainerSize,
                    floatingAppIconSize = floatingAppIconSize,
                    pillHeight = pillHeight,
                    pillIconSize = pillIconSize,
                    entryButtonColor = entryButtonColor,
                    transparentBundlePills = transparentBundlePills,
                    bundleMenuBlur = bundleMenuBlur,
                    onAppClick = onAppClick,
                    onBundleClick = onBundleClick,
                    onBundleLongClick = onBundleLongClick
                )
            }
        }
        if (conduitMenu != null) {
            BundleMenuRowVisibility(expanded = expanded, index = total - 1, total = total) {
                ConduitActionsMenuRow(
                    menu = conduitMenu,
                    isCompact = isCompact,
                    isLeft = isLeft,
                    floatingContainerSize = floatingContainerSize,
                    floatingAppIconSize = floatingAppIconSize,
                    pillHeight = pillHeight,
                    pillIconSize = pillIconSize,
                    entryButtonColor = entryButtonColor,
                    transparentBundlePills = transparentBundlePills,
                    bundleMenuBlur = bundleMenuBlur
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.BundleMenuRowVisibility(
    expanded: Boolean,
    index: Int,
    total: Int,
    content: @Composable () -> Unit
) {
    // Bottom row has index (total - 1), closest to the dock, enters first
    val distanceFromBottom = total - 1 - index
    val enterDelay = distanceFromBottom * 35
    val exitDelay = index * 25

    AnimatedVisibility(
        visible = expanded,
        enter = slideInVertically(
            initialOffsetY = { it / 2 },
            animationSpec = tween(durationMillis = 220, delayMillis = enterDelay, easing = FastOutSlowInEasing)
        ) + fadeIn(
            animationSpec = tween(durationMillis = 200, delayMillis = enterDelay)
        ),
        exit = slideOutVertically(
            targetOffsetY = { it / 2 },
            animationSpec = tween(durationMillis = 180, delayMillis = exitDelay)
        ) + fadeOut(
            animationSpec = tween(durationMillis = 150, delayMillis = exitDelay)
        )
    ) {
        content()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BundleMenuRow(
    entry: BundleMenuEntry,
    isCompact: Boolean,
    isLeft: Boolean,
    floatingContainerSize: Dp,
    floatingAppIconSize: Dp,
    pillHeight: Dp,
    pillIconSize: Dp,
    entryButtonColor: Color,
    transparentBundlePills: Boolean,
    bundleMenuBlur: Boolean,
    onAppClick: (String) -> Unit,
    onBundleClick: (String, List<String>) -> Unit,
    onBundleLongClick: (String) -> Unit
) {
    val elementSpacing = if (isCompact) 8.dp else 10.dp

    val appIconsContent: @Composable RowScope.() -> Unit = {
        Row(
            modifier = Modifier
                .weight(1f, fill = false)
                .horizontalScroll(rememberScrollState(initial = if (isLeft) 0 else Int.MAX_VALUE)),
            horizontalArrangement = Arrangement.spacedBy(elementSpacing, if (isLeft) Alignment.Start else Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            entry.packages.forEach { pkg ->
                Surface(
                    modifier = Modifier
                        .size(floatingContainerSize)
                        .clip(CircleShape)
                        .clickable { onAppClick(pkg) },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shadowElevation = 4.dp,
                    tonalElevation = 2.dp
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (pkg.startsWith("conduit.action.")) {
                            ComposeActionIcon(
                                action = pkg,
                                size = floatingAppIconSize
                            )
                        } else {
                            AppIcon(packageName = pkg, size = floatingAppIconSize)
                        }
                    }
                }
            }
        }
    }

    val pillContent: @Composable () -> Unit = {
        Surface(
            modifier = Modifier
                .height(pillHeight)
                .clip(CircleShape)
                .combinedClickable(
                    onClick = { onBundleClick(entry.title, entry.packages) },
                    onLongClick = { onBundleLongClick(entry.title) }
                ),
            shape = CircleShape,
            color = if (transparentBundlePills) Color.Transparent else entryButtonColor,
            shadowElevation = if (transparentBundlePills) 0.dp else 6.dp,
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = if (isCompact) 14.dp else 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                val textStyle = if (isCompact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium
                val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

                if (isLeft) {
                    // Left side: (title + icon)
                    BundlePillText(
                        title = entry.title,
                        style = textStyle,
                        color = MaterialTheme.colorScheme.onSurface,
                        hasTransparentPills = transparentBundlePills,
                        isBlurOff = !bundleMenuBlur,
                        isDark = isDark
                    )
                    Spacer(modifier = Modifier.width(if (isCompact) 8.dp else 10.dp))
                    Icon(
                        imageVector = entry.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(pillIconSize)
                    )
                } else {
                    // Right side: (icon + title)
                    Icon(
                        imageVector = entry.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(pillIconSize)
                    )
                    Spacer(modifier = Modifier.width(if (isCompact) 8.dp else 10.dp))
                    BundlePillText(
                        title = entry.title,
                        style = textStyle,
                        color = MaterialTheme.colorScheme.onSurface,
                        hasTransparentPills = transparentBundlePills,
                        isBlurOff = !bundleMenuBlur,
                        isDark = isDark
                    )
                }
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        horizontalArrangement = if (isLeft) Arrangement.Start else Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isLeft) {
            pillContent()
            Spacer(modifier = Modifier.width(elementSpacing))
            appIconsContent()
        } else {
            appIconsContent()
            Spacer(modifier = Modifier.width(elementSpacing))
            pillContent()
        }
    }
}

@Composable
internal fun BundlePillText(
    title: String,
    style: TextStyle,
    color: Color,
    hasTransparentPills: Boolean,
    isBlurOff: Boolean,
    isDark: Boolean
) {
    if (hasTransparentPills) {
        val strokeColor = if (isDark) Color.Black.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.95f)
        val strokeWidth = if (isBlurOff) 3.5f else 2.5f
        val textShadow = Shadow(
            color = if (isDark) Color.Black.copy(alpha = 0.95f) else Color.Black.copy(alpha = 0.35f),
            offset = Offset(0f, 1.5f),
            blurRadius = if (isBlurOff) 6f else 4f
        )
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = title,
                style = style.copy(
                    color = strokeColor,
                    drawStyle = Stroke(
                        width = strokeWidth,
                        join = StrokeJoin.Round
                    )
                ),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Text(
                text = title,
                style = style.copy(
                    color = color,
                    shadow = textShadow
                ),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    } else {
        Text(
            text = title,
            style = style,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1
        )
    }
}

