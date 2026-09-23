package com.conduit.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conduit.app.data.ConduitSettings

interface NotificationSyncSettingsScreenCallbacks {
    fun onAutoDismissDetachedChanged(enabled: Boolean)
    fun onSyncDismissalChanged(enabled: Boolean)
    fun onSyncPinnedChanged(enabled: Boolean)
    fun onShowActionChipsChanged(enabled: Boolean)
    fun onSmartMarkReadChanged(enabled: Boolean)
    fun onSmartMarkReadTargetChanged(target: String)
    fun onTriggerNativeMarkReadChanged(enabled: Boolean)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSyncSettingsScreen(
    settings: ConduitSettings,
    callbacks: NotificationSyncSettingsScreenCallbacks,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications & Sync", fontSize = 22.sp, fontWeight = FontWeight.SemiBold) },
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
            SettingsSectionHeader(title = "System Tray Sync")

            SettingsSwitchRow(
                title = "Auto-dismiss detached notifications",
                description = "When a non-message notification is no longer in the system tray (cleared by reboot or the app), dismiss it in Conduit automatically. Messages and calls always stay until you act on them.",
                checked = settings.autoDismissDetached,
                onCheckedChange = callbacks::onAutoDismissDetachedChanged
            )

            SettingsSwitchRow(
                title = "Sync Dismissal with System",
                description = "When enabled, archiving a notification in Conduit will also dismiss it from the Android system tray",
                checked = settings.syncDismissal,
                onCheckedChange = callbacks::onSyncDismissalChanged
            )

            SettingsSwitchRow(
                title = "Sync Pinned Notifications",
                description = "When enabled, pinned notifications in Conduit will replace original notifications and remain pinned permanently in the Android system tray until unpinned",
                checked = settings.syncPinned,
                onCheckedChange = callbacks::onSyncPinnedChanged
            )

            Spacer(modifier = Modifier.height(24.dp))

            SettingsSectionHeader(title = "Action Chips")

            SettingsSwitchRow(
                title = "Show Action Chips",
                description = "Displays quick action chips (like Reply, Mark as Read) directly on the notification card",
                checked = settings.showActionChips,
                onCheckedChange = callbacks::onShowActionChipsChanged
            )

            SettingsSwitchRow(
                title = "Smart Action Chips",
                description = "Automatically inserts a \"Mark Read\" action chip on messages (and \"Dismiss\" on other notifications) if an app notification doesn't natively support it",
                checked = settings.smartMarkRead,
                onCheckedChange = callbacks::onSmartMarkReadChanged
            )

            if (settings.smartMarkRead) {
                Spacer(modifier = Modifier.height(4.dp))
                Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                    Text("Smart Chips Target", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(6.dp))
                    val targetOptions = listOf("widget_only" to "Widget Only", "widget_and_app" to "Widget and App")
                    SegmentedOptionRow(
                        options = targetOptions,
                        selectedValue = settings.smartMarkReadTarget,
                        onValueSelected = callbacks::onSmartMarkReadTargetChanged
                    )
                }
            }

            SettingsSwitchRow(
                title = "Trigger Native Mark as Read",
                description = "When marking notifications as read via swipe, date header, or bulk selection, automatically trigger the app's native 'Mark as Read' or 'Done' action if available; otherwise falls back to normal dismissal.",
                checked = settings.triggerNativeMarkRead,
                onCheckedChange = callbacks::onTriggerNativeMarkReadChanged
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
