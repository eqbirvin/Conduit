package com.conduit.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conduit.app.data.ConduitSettings

interface BracketSettingsScreenCallbacks {
    fun onEnableBracketChanged(enabled: Boolean)
    fun onGroupByChannelChanged(enabled: Boolean)
    fun onBracketNotificationPopupChanged(enabled: Boolean)
    fun onBracketHangerEnabledChanged(enabled: Boolean)
    fun onBracketVerticalPositionChanged(position: Float)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BracketSettingsScreen(
    settings: ConduitSettings,
    callbacks: BracketSettingsScreenCallbacks,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bracket Overlay", fontSize = 22.sp, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsSectionHeader(title = "Bracket Floating Handle [BETA]")
            Text(
                text = "Note: This feature is currently in beta testing.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            SettingsSwitchRow(
                title = "Enable Bracket",
                description = "Shows a persistent floating handle on the edge of your screen. Long-press to quickly launch Conduit from anywhere.",
                checked = settings.enableBracket,
                onCheckedChange = callbacks::onEnableBracketChanged
            )

            if (settings.enableBracket) {
                Spacer(modifier = Modifier.height(12.dp))

                SettingsSwitchRow(
                    title = "Group by Channel (Hangar)",
                    description = "When enabled, notifications inside the Hangar (the swipe-in panel on the Bracket) will be grouped by their channel type rather than listed individually.",
                    checked = settings.groupByChannel,
                    onCheckedChange = callbacks::onGroupByChannelChanged
                )

                SettingsSwitchRow(
                    title = "Animate Icon on Notification",
                    description = "Briefly show the incoming notification's app icon next to the bracket. Tap the icon or bracket to open the notification.",
                    checked = settings.bracketNotificationPopup,
                    onCheckedChange = callbacks::onBracketNotificationPopupChanged
                )

                SettingsSwitchRow(
                    title = "Bracket Hanger",
                    description = "Swipe inward on the bracket to pull open a quick view of pending notifications.",
                    checked = settings.bracketHangerEnabled,
                    onCheckedChange = callbacks::onBracketHangerEnabledChanged
                )

                Spacer(modifier = Modifier.height(8.dp))

                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text("Bracket Vertical Position", style = MaterialTheme.typography.bodyLarge)
                    Text("Adjust the vertical placement of the bracket along the right edge.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(
                        value = settings.bracketVerticalPosition,
                        onValueChange = callbacks::onBracketVerticalPositionChanged,
                        steps = 9,
                        valueRange = 0f..1f,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))



            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
