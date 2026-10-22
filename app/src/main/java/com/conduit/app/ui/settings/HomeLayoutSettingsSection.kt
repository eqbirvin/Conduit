package com.conduit.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.conduit.app.data.ConduitSettings

const val TOP_BAR_MODE_SHOWN = "SHOWN"
const val TOP_BAR_MODE_SCROLL = "SCROLL"
const val TOP_BAR_MODE_HIDDEN = "HIDDEN"

fun ConduitSettings.topBarMode(): String = when {
    hideTopBar -> TOP_BAR_MODE_HIDDEN
    scrollTopBarWithFeed -> TOP_BAR_MODE_SCROLL
    else -> TOP_BAR_MODE_SHOWN
}

@Composable
fun HomeLayoutSettingsSection(
    settings: ConduitSettings,
    callbacks: AppearanceSettingsScreenCallbacks
) {
    val topBarMode = settings.topBarMode()

    SettingsSectionHeader(title = "Home Screen Layout")

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("Top Bar", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        Text(
            text = when (topBarMode) {
                TOP_BAR_MODE_HIDDEN -> "The top bar is hidden. Settings, Search, Todo Mode and the other top bar actions are in the dock's app bundle menu"
                TOP_BAR_MODE_SCROLL -> "The top bar and custom view strip scroll off-screen with the notification feed"
                else -> "The top bar stays pinned at the top of the feed"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        SegmentedOptionRow(
            options = listOf(
                TOP_BAR_MODE_SHOWN to "Shown",
                TOP_BAR_MODE_SCROLL to "Scrolls",
                TOP_BAR_MODE_HIDDEN to "Hidden"
            ),
            selectedValue = topBarMode,
            onValueSelected = callbacks::onTopBarModeChanged
        )
    }

    androidx.compose.animation.AnimatedVisibility(visible = topBarMode == TOP_BAR_MODE_SCROLL) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, bottom = 8.dp)
        ) {
            val scrollOptions = listOf(
                "NATURAL" to ("Natural Scroll" to "Returns only when scrolled all the way to the top of the feed"),
                "QUICK_RETURN" to ("Quick Return" to "Slides back into view whenever scrolling up anywhere in the feed")
            )

            scrollOptions.forEach { (key, info) ->
                val (title, description) = info
                val isSelected = settings.topBarScrollBehavior == key
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { callbacks.onTopBarScrollBehaviorChanged(key) }
                        .padding(vertical = 8.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { callbacks.onTopBarScrollBehaviorChanged(key) }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    SettingsSwitchRow(
        title = "Top Bar Actions in Dock Menu",
        description = if (settings.hideTopBar) {
            "Always on while the top bar is hidden, so Settings stays reachable from the dock's expand button"
        } else {
            "Adds a row with Settings, Search, Todo Mode and the other top bar actions to the dock's app bundle menu"
        },
        checked = settings.showTopBarActionsInDock,
        onCheckedChange = callbacks::onTopBarActionsInDockChanged,
        enabled = !settings.hideTopBar
    )

    SettingsSwitchRow(
        title = "Custom View Strip",
        description = "Show the custom view chips below the top bar. Hiding the strip returns the feed to All; custom view widgets still work",
        checked = settings.showCustomViewStrip,
        onCheckedChange = callbacks::onShowCustomViewStripChanged
    )

    SettingsSwitchRow(
        title = "Date Header Mark-All-Read Button",
        description = "Show the mark-all-read button next to the unread count on each day's header in the feed",
        checked = settings.showDateMarkAllRead,
        onCheckedChange = callbacks::onShowDateMarkAllReadChanged
    )

    SettingsSwitchRow(
        title = "Show Action Chips",
        description = "Quick action chips (like Reply and Mark as Read) on notification cards. Turning them off also removes inline reply",
        checked = settings.showActionChips,
        onCheckedChange = callbacks::onShowActionChipsChanged
    )

    SettingsSwitchRow(
        title = "Expand All Notifications",
        description = "Show notifications expanded in the feed. Same as the expand/collapse all button in the top bar or dock menu",
        checked = settings.masterExpandedState,
        onCheckedChange = callbacks::onMasterExpandedChanged
    )
}
