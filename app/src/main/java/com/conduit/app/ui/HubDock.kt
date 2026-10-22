@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.conduit.app.ui

import com.conduit.app.*
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.animation.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.conduit.app.data.HubNotification
import androidx.compose.foundation.gestures.animateScrollBy
import android.content.SharedPreferences
import com.conduit.app.data.ConduitSettings
import com.conduit.app.data.CustomView

@Composable
fun HubDock(
    context: Context,
    prefs: SharedPreferences,
    settings: ConduitSettings,
    notifications: List<HubNotification>,
    channelStates: Map<String, Boolean>,
    aiBundle: List<String>,
    notesBundle: List<String>,
    recorderBundle: List<String>,
    composeBundle: List<String>,
    dockSizeIndex: Int,
    dockScrollIndicator: String,
    unifiedView: Boolean,
    isBundleFlyoutExpandedState: MutableState<Boolean>,
    editingBundleTitleState: MutableState<String?>,
    selectedDockPackageState: MutableState<String?>,
    launchAppOrAction: (String) -> Unit,
    launchBundleAction: (String, List<String>) -> Unit,
    activeView: CustomView?,
    activeViewDockFilter: Boolean,
    topBarState: HubTopBarState,
    topBarCallbacks: HubTopBarCallbacks
) {
    var isBundleFlyoutExpanded by isBundleFlyoutExpandedState
    var editingBundleTitle by editingBundleTitleState
    val pm = context.packageManager
    val enabledApps = remember(unifiedView, channelStates.toMap()) {
        if (!unifiedView) emptyList<String>()
        else {
            HubNotificationListenerService.supportedApps.keys
                .filter { pkg ->
                    val prefKey = HubNotificationListenerService.supportedApps[pkg]?.first
                    if (prefKey != null && channelStates[prefKey] == true) {
                        isPackageInstalled(context, pkg) || settings.demoModeEnabled
                    } else false
                }
                .map { getRepresentativePackage(context, it) }
                .distinct()
        }
    }
    val hasDockApps = notifications.isNotEmpty() || (unifiedView && enabledApps.isNotEmpty()) || (activeViewDockFilter && activeView != null && activeView.packageNames.isNotEmpty())
    if (hasDockApps || settings.dockExpandButtonVisible) {
        val grouped = remember(notifications) {
            notifications.groupBy { getRepresentativePackage(context, it.packageName) }
        }
        val dockPackagesList = remember(grouped, enabledApps, unifiedView, activeView, activeViewDockFilter, hasDockApps) {
            if (!hasDockApps) {
                emptyList()
            } else if (activeViewDockFilter && activeView != null) {
                val viewApps = activeView.packageNames.filter { enabledApps.contains(it) }
                val unread = viewApps.filter { grouped.containsKey(it) }
                val read = viewApps.filter { !grouped.containsKey(it) }
                (unread + read).distinct()
            } else if (unifiedView) {
                val unread = enabledApps.filter { grouped.containsKey(it) }
                val read = enabledApps.filter { !grouped.containsKey(it) }
                val otherUnread = grouped.keys.filter { !enabledApps.contains(it) }
                (unread + otherUnread + read).distinct()
            } else {
                grouped.keys.toList()
            }
        }
        val dockIconSize = when (dockSizeIndex) {
            0 -> 36.dp
            2 -> 44.dp
            else -> 40.dp
        }
        val dockSpacing = when (dockSizeIndex) {
            0 -> 6.dp
            2 -> 7.dp
            else -> 7.dp
        }
        val dockBoxPadding = when (dockSizeIndex) {
            0 -> 6.dp
            2 -> 8.dp
            else -> 6.dp
        }
        val dockPaddingHorizontal = when (dockSizeIndex) {
            0 -> 12.dp
            2 -> 16.dp
            else -> 14.dp
        }
        val dockPaddingVertical = when (dockSizeIndex) {
            0 -> 6.dp
            2 -> 8.dp
            else -> 7.dp
        }
        val dockBadgeOffsetY = when (dockSizeIndex) {
            0 -> 2.dp
            2 -> 4.dp
            else -> 3.dp
        }
        val dockBadgeOffsetX = when (dockSizeIndex) {
            0 -> (-2).dp
            2 -> (-4).dp
            else -> (-3).dp
        }
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        LaunchedEffect(dockPackagesList.size, dockScrollIndicator) {
            if (dockScrollIndicator == "BOUNCE" && dockPackagesList.size > 0) {
                kotlinx.coroutines.delay(500)
                if (listState.layoutInfo.totalItemsCount > listState.layoutInfo.visibleItemsInfo.size) {
                    listState.animateScrollBy(80f, androidx.compose.animation.core.tween(300))
                    listState.animateScrollBy(-80f, androidx.compose.animation.core.tween(300))
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .then(
                    if (isBundleFlyoutExpanded) {
                        Modifier.pointerInput(Unit) {
                            detectTapGestures { isBundleFlyoutExpanded = false }
                        }
                    } else Modifier
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                val conduitMenu = if (settings.showTopBarActionsInDock) {
                    remember(topBarState, topBarCallbacks) {
                        buildConduitMenu(topBarState, topBarCallbacks) {
                            performHapticTick(context)
                            isBundleFlyoutExpanded = false
                        }
                    }
                } else null
                BundleFabMenu(
                    expanded = isBundleFlyoutExpanded && settings.dockExpandButtonVisible,
                    recorderBundle = if (settings.enableAppBundles) recorderBundle else emptyList(),
                    aiBundle = if (settings.enableAppBundles) aiBundle else emptyList(),
                    notesBundle = if (settings.enableAppBundles) notesBundle else emptyList(),
                    composeBundle = if (settings.enableAppBundles) composeBundle else emptyList(),
                    bundleMenuSizing = settings.bundleMenuSizing,
                    dockIconSize = dockIconSize,
                    dockExpandPosition = settings.dockExpandPosition,
                    transparentBundlePills = settings.transparentBundlePills,
                    bundleMenuBlur = settings.bundleMenuBlur,
                    conduitMenu = conduitMenu,
                    onAppClick = { pkg ->
                        launchAppOrAction(pkg)
                    },
                    onBundleClick = { title, bundle ->
                        launchBundleAction(title, bundle)
                    },
                    onBundleLongClick = { title ->
                        isBundleFlyoutExpanded = false
                        editingBundleTitle = title
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                )

                val dockHeight = when (dockSizeIndex) {
                    0 -> 64.dp
                    2 -> 80.dp
                    else -> 72.dp
                }
                val dockCapRadius = dockHeight / 2

                val isExpandLeft = settings.dockExpandPosition == "LEFT"
                val rightSplitShape = rememberRightSplitButtonShape(dockCapRadius)
                val leftSplitShape = rememberLeftSplitButtonShape(dockCapRadius)

                val dockPackagesShape = if (settings.dockExpandButtonVisible) {
                    if (dockPackagesList.isNotEmpty()) {
                        if (isExpandLeft) rightSplitShape else leftSplitShape
                    } else {
                        androidx.compose.foundation.shape.CircleShape
                    }
                } else {
                    androidx.compose.foundation.shape.CircleShape
                }

                val expandButtonShape = if (dockPackagesList.isNotEmpty()) {
                    if (isExpandLeft) leftSplitShape else rightSplitShape
                } else {
                    androidx.compose.foundation.shape.CircleShape
                }

                val isFading = dockScrollIndicator == "FADING_EDGES"
                val fadeWidth = 32.dp
                val fadeWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { fadeWidth.toPx() }

                val dockPackagesContent: @Composable RowScope.() -> Unit = {
                    HubDockPackages(
                        context = context,
                        prefs = prefs,
                        settings = settings,
                        dockScrollIndicator = dockScrollIndicator,
                        unifiedView = unifiedView,
                        selectedDockPackageState = selectedDockPackageState,
                        activeViewDockFilter = activeViewDockFilter,
                        dockPackagesList = dockPackagesList,
                        grouped = grouped,
                        dockIconSize = dockIconSize,
                        dockSpacing = dockSpacing,
                        dockBoxPadding = dockBoxPadding,
                        dockPaddingHorizontal = dockPaddingHorizontal,
                        dockPaddingVertical = dockPaddingVertical,
                        dockBadgeOffsetY = dockBadgeOffsetY,
                        dockBadgeOffsetX = dockBadgeOffsetX,
                        listState = listState,
                        dockHeight = dockHeight,
                        isExpandLeft = isExpandLeft,
                        dockPackagesShape = dockPackagesShape,
                        isFading = isFading,
                        fadeWidthPx = fadeWidthPx
                    )
                }

                val expandButtonContent: @Composable () -> Unit = {
                    Surface(
                        modifier = Modifier
                            .height(dockHeight)
                            .wrapContentWidth(),
                        shape = expandButtonShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shadowElevation = 8.dp
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(vertical = 1.dp)
                                .clip(expandButtonShape)
                                .clickable {
                                    performHapticTick(context)
                                    isBundleFlyoutExpanded = !isBundleFlyoutExpanded
                                }
                                .padding(
                                    start = if (dockPackagesList.isNotEmpty()) {
                                        if (isExpandLeft) (dockPaddingHorizontal * 0.55f).coerceAtLeast(6.dp)
                                        else (dockPaddingHorizontal * 0.35f).coerceAtLeast(4.dp)
                                    } else dockPaddingHorizontal,
                                    end = if (dockPackagesList.isNotEmpty()) {
                                        if (isExpandLeft) (dockPaddingHorizontal * 0.35f).coerceAtLeast(4.dp)
                                        else (dockPaddingHorizontal * 0.55f).coerceAtLeast(6.dp)
                                    } else dockPaddingHorizontal,
                                    top = dockPaddingVertical,
                                    bottom = dockPaddingVertical
                                )
                                .padding(
                                    horizontal = if (dockPackagesList.isNotEmpty()) (dockBoxPadding * 0.5f).coerceAtLeast(3.dp) else dockBoxPadding,
                                    vertical = dockBoxPadding
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            val caretIconSize = (dockIconSize * 0.675f).coerceIn(24.dp, 30.dp)
                            Box(
                                modifier = Modifier
                                    .height(dockIconSize)
                                    .width(if (dockPackagesList.isNotEmpty()) caretIconSize else dockIconSize),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isBundleFlyoutExpanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                                    contentDescription = if (isBundleFlyoutExpanded) "Collapse App Bundles" else "Expand App Bundles",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(caretIconSize)
                                )
                                // With the top bar hidden, its update icon lives in the bundle menu; flag it here.
                                if (settings.hideTopBar && topBarState.hasUpdateAvailable && !isBundleFlyoutExpanded) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(8.dp)
                                            .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape)
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.wrapContentWidth()
                ) {
                    if (isExpandLeft) {
                        if (settings.dockExpandButtonVisible) {
                            expandButtonContent()
                        }
                        if (dockPackagesList.isNotEmpty() && settings.dockExpandButtonVisible) {
                            Spacer(modifier = Modifier.width(3.dp))
                        }
                        if (dockPackagesList.isNotEmpty()) {
                            dockPackagesContent()
                        }
                    } else {
                        if (dockPackagesList.isNotEmpty()) {
                            dockPackagesContent()
                        }
                        if (dockPackagesList.isNotEmpty() && settings.dockExpandButtonVisible) {
                            Spacer(modifier = Modifier.width(3.dp))
                        }
                        if (settings.dockExpandButtonVisible) {
                            expandButtonContent()
                        }
                    }
                }
            }
        }
    }
}
