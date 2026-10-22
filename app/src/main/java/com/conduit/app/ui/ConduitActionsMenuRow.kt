package com.conduit.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.conduit.app.data.ConversationViewMode

data class ConduitMenuAction(
    val label: String,
    val icon: ImageVector,
    val highlighted: Boolean = false,
    val onClick: () -> Unit
)

/** The top bar's actions as a dock bundle-menu row: [settings] is the pill, [actions] the round buttons. */
data class ConduitMenu(
    val settings: ConduitMenuAction,
    val actions: List<ConduitMenuAction>
)

/**
 * Mirrors the idle top bar (HubTopBar) so every action stays reachable when the bar is hidden.
 * [onActionInvoked] runs before each action (haptic + closing the bundle menu).
 */
fun buildConduitMenu(
    state: HubTopBarState,
    callbacks: HubTopBarCallbacks,
    onActionInvoked: () -> Unit
): ConduitMenu {
    fun action(label: String, icon: ImageVector, highlighted: Boolean = false, run: () -> Unit) =
        ConduitMenuAction(label, icon, highlighted) { onActionInvoked(); run() }

    val actions = buildList {
        if (state.hasUpdateAvailable) {
            add(action("Update Available", Icons.Filled.SystemUpdate, highlighted = true, run = callbacks::onShowUpdateDialog))
        }
        add(action("Search", Icons.Filled.Search, run = callbacks::onEnterSearch))
        add(
            action(
                if (state.unifiedView) "Switch to Todo Mode" else "Switch to Unified View",
                if (state.unifiedView) Icons.Filled.Checklist else Icons.Filled.DynamicFeed,
                run = callbacks::onToggleUnifiedView
            )
        )
        if (!state.unifiedView) {
            add(action("Actioned", Icons.Filled.History, run = callbacks::onNavigateToArchive))
        }
        add(
            action(
                "Toggle Master Expand",
                if (state.masterExpandedState) Icons.Filled.Compress else Icons.Filled.Expand,
                highlighted = state.masterExpandedState,
                run = callbacks::onToggleMasterExpand
            )
        )
        val (modeIcon, modeLabel) = when (state.conversationViewMode) {
            ConversationViewMode.STOCK -> Icons.Filled.ViewStream to "Stock View"
            ConversationViewMode.COMBINE_CONVERSATIONS -> Icons.Filled.Forum to "Combine Conversations"
            ConversationViewMode.SINGLE_CONVERSATION -> Icons.AutoMirrored.Filled.Chat to "Single Conversation View"
        }
        add(action("Conversation View: $modeLabel (tap to cycle)", modeIcon, run = callbacks::onCycleConversationView))
        add(action("Manage Views", Icons.Filled.FilterList, run = callbacks::onNavigateToManageViews))
    }
    return ConduitMenu(
        settings = action("Settings", Icons.Filled.Settings, run = callbacks::onNavigateToSettings),
        actions = actions
    )
}

/** Laid out like BundleMenuRow so the Conduit row matches the app bundle rows above it. */
@Composable
internal fun ConduitActionsMenuRow(
    menu: ConduitMenu,
    isCompact: Boolean,
    isLeft: Boolean,
    floatingContainerSize: Dp,
    floatingAppIconSize: Dp,
    pillHeight: Dp,
    pillIconSize: Dp,
    entryButtonColor: Color,
    transparentBundlePills: Boolean,
    bundleMenuBlur: Boolean
) {
    val elementSpacing = if (isCompact) 8.dp else 10.dp
    val actionIconSize = floatingAppIconSize * 0.6f

    val actionButtons: @Composable RowScope.() -> Unit = {
        Row(
            modifier = Modifier
                .weight(1f, fill = false)
                .horizontalScroll(rememberScrollState(initial = if (isLeft) 0 else Int.MAX_VALUE)),
            horizontalArrangement = Arrangement.spacedBy(elementSpacing, if (isLeft) Alignment.Start else Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            menu.actions.forEach { action ->
                Surface(
                    modifier = Modifier
                        .size(floatingContainerSize)
                        .clip(CircleShape)
                        .clickable(onClickLabel = action.label, onClick = action.onClick),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shadowElevation = 4.dp,
                    tonalElevation = 2.dp
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = action.icon,
                            contentDescription = action.label,
                            tint = if (action.highlighted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(actionIconSize)
                        )
                    }
                }
            }
        }
    }

    val settingsPill: @Composable () -> Unit = {
        Surface(
            modifier = Modifier
                .height(pillHeight)
                .clip(CircleShape)
                .clickable(onClick = menu.settings.onClick),
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
                val text: @Composable () -> Unit = {
                    BundlePillText(
                        title = menu.settings.label,
                        style = textStyle,
                        color = MaterialTheme.colorScheme.onSurface,
                        hasTransparentPills = transparentBundlePills,
                        isBlurOff = !bundleMenuBlur,
                        isDark = isDark
                    )
                }
                val icon: @Composable () -> Unit = {
                    Icon(
                        imageVector = menu.settings.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(pillIconSize)
                    )
                }
                if (isLeft) {
                    text()
                    Spacer(modifier = Modifier.width(if (isCompact) 8.dp else 10.dp))
                    icon()
                } else {
                    icon()
                    Spacer(modifier = Modifier.width(if (isCompact) 8.dp else 10.dp))
                    text()
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
            settingsPill()
            Spacer(modifier = Modifier.width(elementSpacing))
            actionButtons()
        } else {
            actionButtons()
            Spacer(modifier = Modifier.width(elementSpacing))
            settingsPill()
        }
    }
}
