@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.conduit.app.ui

import android.content.Context
import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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

@Composable
fun BundleFabMenu(
    expanded: Boolean,
    recorderBundle: List<String>,
    aiBundle: List<String>,
    notesBundle: List<String>,
    composeBundle: List<String>,
    bundleMenuSizing: String,
    dockIconSize: Dp,
    dockExpandPosition: String = "RIGHT",
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

    if (bundleEntries.isEmpty()) return

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
        val total = bundleEntries.size
        bundleEntries.forEachIndexed { index, entry ->
            // Bottom row (Compose) has index (total - 1), closest to the dock, enters first
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
                BundleMenuRow(
                    entry = entry,
                    isCompact = isCompact,
                    isLeft = isLeft,
                    floatingContainerSize = floatingContainerSize,
                    floatingAppIconSize = floatingAppIconSize,
                    pillHeight = pillHeight,
                    pillIconSize = pillIconSize,
                    entryButtonColor = entryButtonColor,
                    onAppClick = onAppClick,
                    onBundleClick = onBundleClick,
                    onBundleLongClick = onBundleLongClick
                )
            }
        }
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
                    color = entryButtonColor,
                    shadowElevation = 4.dp,
                    tonalElevation = 0.dp
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        when (pkg) {
                            "conduit.action.EMAIL" -> {
                                Icon(
                                    Icons.Filled.Email,
                                    contentDescription = "Email",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(floatingAppIconSize * 0.68f)
                                )
                            }
                            "conduit.action.SMS" -> {
                                Icon(
                                    Icons.Filled.Sms,
                                    contentDescription = "SMS",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(floatingAppIconSize * 0.68f)
                                )
                            }
                            else -> {
                                AppIcon(packageName = pkg, size = floatingAppIconSize)
                            }
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
            color = entryButtonColor,
            shadowElevation = 6.dp,
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = if (isCompact) 14.dp else 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (isLeft) {
                    // Left side: (title + icon)
                    Text(
                        text = entry.title,
                        style = if (isCompact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
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
                    Text(
                        text = entry.title,
                        style = if (isCompact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
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
fun BundleManagementBottomSheet(
    bundleTitle: String,
    currentApps: List<String>,
    onUpdateApps: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var showAppPicker by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(bundleTitle, style = MaterialTheme.typography.headlineSmall)
                FilledTonalButton(onClick = { showAppPicker = true }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add App")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (currentApps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No apps in this bundle",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column {
                        currentApps.forEachIndexed { index, pkg ->
                            val label = remember(pkg) {
                                if (pkg == "conduit.action.SMS") "Compose SMS"
                                else if (pkg == "conduit.action.EMAIL") "Compose Email"
                                else getAppLabel(context, pkg)
                            }
                            ListItem(
                                headlineContent = { Text(text = label, fontWeight = FontWeight.Medium) },
                                leadingContent = {
                                    if (pkg == "conduit.action.EMAIL") {
                                        Icon(Icons.Filled.Email, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    } else if (pkg == "conduit.action.SMS") {
                                        Icon(Icons.Filled.Sms, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    } else {
                                        AppIcon(pkg)
                                    }
                                },
                                trailingContent = {
                                    IconButton(onClick = {
                                        onUpdateApps(currentApps.filterIndexed { i, _ -> i != index })
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            )
                            if (index < currentApps.size - 1) {
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        if (showAppPicker) {
            ModalBottomSheet(onDismissRequest = { showAppPicker = false }) {
                var searchQuery by remember { mutableStateOf("") }
                val installedApps = remember { getInstalledApps(context) }
                val filteredApps by remember {
                    derivedStateOf {
                        if (searchQuery.isEmpty()) installedApps
                        else installedApps.filter {
                            it.second.contains(searchQuery, ignoreCase = true) || it.first.contains(searchQuery, ignoreCase = true)
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("Select App", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search apps...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(modifier = Modifier.weight(1f, fill = false).heightIn(max = 400.dp)) {
                        items(filteredApps, key = { it.first }) { (pkg, label) ->
                            val isAdded = currentApps.contains(pkg)
                            ListItem(
                                headlineContent = { Text(label) },
                                supportingContent = { Text(pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                leadingContent = { AppIcon(pkg) },
                                trailingContent = {
                                    if (isAdded) {
                                        Icon(Icons.Default.Check, contentDescription = "Added", tint = MaterialTheme.colorScheme.primary)
                                    }
                                },
                                modifier = Modifier.clickable(enabled = !isAdded) {
                                    onUpdateApps(currentApps + pkg)
                                    showAppPicker = false
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}
