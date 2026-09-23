package com.conduit.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FlipToFront
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conduit.app.BuildConfig
import com.conduit.app.HubNotificationListenerService
import com.conduit.app.data.ConduitSettings
import com.conduit.app.getInstalledChannels
import com.conduit.app.performHapticClick
import com.conduit.app.ui.settings.SettingsCategoryCard
import com.conduit.app.ui.settings.SettingsSectionHeader

interface SettingsScreenCallbacks {
    fun onNavigateToChannels()
    fun onNavigateToAppearance()
    fun onNavigateToDock()
    fun onNavigateToNotifications()
    fun onNavigateToData()
    fun onNavigateToBracket()
    fun onNavigateToAbout()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: ConduitSettings,
    callbacks: SettingsScreenCallbacks,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val adbCommandText = "adb shell appops set --user 0 com.conduit.app RECEIVE_SENSITIVE_NOTIFICATIONS allow"

    val installedChannels = remember { getInstalledChannels(context) }
    val enabledChannelsCount = remember(settings.channelStates, installedChannels) {
        installedChannels.count { (prefKey, _) ->
            settings.channelStates[prefKey] ?: (prefKey != "channel_airbnb")
        }
    }
    val totalSupportedChannelsCount = remember {
        HubNotificationListenerService.supportedApps.values.distinctBy { it.first }.size
    }
    val channelsSummary = "$totalSupportedChannelsCount supported channels/apps • $enabledChannelsCount channels enabled"

    val appearanceSummary = remember(settings.themePreference, settings.activeAppIcon) {
        val themeName = when (settings.themePreference) {
            0 -> "System Default"
            1 -> "Light Theme"
            2 -> "Dark Theme"
            3 -> "AMOLED (Jacob Mode)"
            else -> "Theme"
        }
        val iconName = settings.activeAppIcon.lowercase().replaceFirstChar { it.uppercase() }
        "$themeName • $iconName Icon"
    }

    val dockSummary = remember(settings.enableAppBundles, settings.dockExpandPosition, settings.dockSizeIndex) {
        val bundlesStatus = if (settings.enableAppBundles) "Bundles ON (${settings.dockExpandPosition.lowercase()})" else "Bundles OFF"
        val dockSize = when (settings.dockSizeIndex) {
            0 -> "Small"
            1 -> "Medium"
            2 -> "Large"
            else -> "Standard"
        }
        "$bundlesStatus • $dockSize Dock"
    }

    val syncSummary = remember(settings.syncDismissal, settings.showActionChips) {
        val syncText = if (settings.syncDismissal) "System sync on" else "Local only"
        val chipsText = if (settings.showActionChips) "Action chips" else "No action chips"
        "$syncText • $chipsText"
    }

    val dataSummary = remember(settings.retentionDays) {
        "${settings.retentionDays} days retention • Cleanup & filters"
    }

    val bracketSummary = remember(settings.enableBracket) {
        if (settings.enableBracket) "Enabled • Edge floating handle" else "Disabled"
    }

    val aboutSummary = remember(settings.updateInterval) {
        val intervalText = when (settings.updateInterval) {
            "DAILY" -> "Daily"
            "EVERY_3_DAYS" -> "Every 3 days"
            "WEEKLY" -> "Weekly"
            "DISABLED" -> "Manual only"
            else -> settings.updateInterval.lowercase()
        }
        "v${BuildConfig.VERSION_NAME} • $intervalText checks"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.SemiBold) },
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
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SettingsCategoryCard(
                title = "Channels",
                subtitle = channelsSummary,
                icon = Icons.Default.Notifications,
                onClick = {
                    performHapticClick(context)
                    callbacks.onNavigateToChannels()
                }
            )

            SettingsCategoryCard(
                title = "Appearance",
                subtitle = appearanceSummary,
                icon = Icons.Default.Palette,
                onClick = {
                    performHapticClick(context)
                    callbacks.onNavigateToAppearance()
                }
            )

            SettingsCategoryCard(
                title = "Dock & Gestures",
                subtitle = dockSummary,
                icon = Icons.Default.Apps,
                onClick = {
                    performHapticClick(context)
                    callbacks.onNavigateToDock()
                }
            )

            SettingsCategoryCard(
                title = "Notifications & Sync",
                subtitle = syncSummary,
                icon = Icons.Default.Sync,
                onClick = {
                    performHapticClick(context)
                    callbacks.onNavigateToNotifications()
                }
            )

            SettingsCategoryCard(
                title = "Data & Retention",
                subtitle = dataSummary,
                icon = Icons.Default.Storage,
                onClick = {
                    performHapticClick(context)
                    callbacks.onNavigateToData()
                }
            )

            SettingsCategoryCard(
                title = "Bracket Overlay",
                subtitle = bracketSummary,
                icon = Icons.Default.FlipToFront,
                onClick = {
                    performHapticClick(context)
                    callbacks.onNavigateToBracket()
                }
            )

            SettingsCategoryCard(
                title = "App Info & Updates",
                subtitle = aboutSummary,
                icon = Icons.Default.Info,
                onClick = {
                    performHapticClick(context)
                    callbacks.onNavigateToAbout()
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            SettingsSectionHeader(title = "Advanced Security & OTPs")

            OutlinedCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Read Sensitive Notifications",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Android 15+ redacts verification codes (OTPs), bank alerts, and other sensitive notifications by default. To allow Conduit to read and log these messages, connect your device to a PC with ADB enabled and run the command below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = adbCommandText,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                modifier = Modifier.weight(1f),
                                maxLines = 3
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Conduit ADB Command", adbCommandText)
                                    clipboard.setPrimaryClip(clip)

                                    performHapticClick(context)
                                    Toast.makeText(context, "Command copied to clipboard!", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(
                                    Icons.Filled.ContentCopy,
                                    contentDescription = "Copy command",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
