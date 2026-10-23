package com.conduit.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.FlipToFront
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conduit.app.BuildConfig
import com.conduit.app.HubNotificationListenerService
import com.conduit.app.SensitiveAccessUiState
import com.conduit.app.data.ConduitSettings
import com.conduit.app.getInstalledChannels
import com.conduit.app.performHapticClick
import com.conduit.app.ui.settings.SettingsMenuItemRow
import com.conduit.app.ui.settings.SensitiveNotificationsCard
import com.conduit.app.ui.settings.SettingsSectionHeader

interface SettingsScreenCallbacks {
    fun onNavigateToChannels()
    fun onNavigateToAppearance()
    fun onNavigateToDock()
    fun onNavigateToNotifications()
    fun onNavigateToHiddenText()
    fun onNavigateToData()
    fun onNavigateToBracket()
    fun onNavigateToAbout()
    fun onShowWhatsNew()
    fun onGrantSensitiveWithShizuku()
    fun onOpenShizuku()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: ConduitSettings,
    sensitiveAccess: SensitiveAccessUiState,
    callbacks: SettingsScreenCallbacks,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current

    val isDark = when (settings.themePreference) {
        1 -> false // Light
        2 -> true  // Dark
        3 -> if (settings.jacobFollowSystemDark) isSystemInDarkTheme() else true // Jacob AMOLED
        else -> isSystemInDarkTheme() // System
    }
    val isAmoled = settings.themePreference == 3 && isDark

    val cardContainerColor = when {
        isAmoled -> Color(0xFF141414)
        !isDark -> Color.White
        else -> MaterialTheme.colorScheme.surface
    }

    val borderColor = if (isAmoled) {
        Color.White.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    }

    val dividerColor = if (isAmoled) {
        Color.White.copy(alpha = 0.10f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    }

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

    val hiddenTextSummary = remember(settings.hiddenTextRules) {
        when (val count = settings.hiddenTextRules.size) {
            0 -> "Hide repeated phrases like \"EXTERNAL:\""
            1 -> "1 phrase hidden"
            else -> "$count phrases hidden"
        }
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
                .verticalScroll(rememberScrollState())
        ) {
            Card(
                onClick = {
                    performHapticClick(context)
                    callbacks.onShowWhatsNew()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = cardContainerColor),
                border = BorderStroke(width = 1.dp, color = borderColor)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(18.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "What's New",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "v${BuildConfig.VERSION_NAME}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "View update details and release history",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 13.sp,
                                lineHeight = 17.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = cardContainerColor),
                border = BorderStroke(width = 1.dp, color = borderColor)
            ) {
                Column {
                    SettingsMenuItemRow(
                        title = "Channels",
                        subtitle = channelsSummary,
                        icon = Icons.Default.Notifications,
                        onClick = {
                            performHapticClick(context)
                            callbacks.onNavigateToChannels()
                        }
                    )
                    HorizontalDivider(thickness = 1.dp, color = dividerColor)

                    SettingsMenuItemRow(
                        title = "Appearance",
                        subtitle = appearanceSummary,
                        icon = Icons.Default.Palette,
                        onClick = {
                            performHapticClick(context)
                            callbacks.onNavigateToAppearance()
                        }
                    )
                    HorizontalDivider(thickness = 1.dp, color = dividerColor)

                    SettingsMenuItemRow(
                        title = "Dock & Gestures",
                        subtitle = dockSummary,
                        icon = Icons.Default.Apps,
                        onClick = {
                            performHapticClick(context)
                            callbacks.onNavigateToDock()
                        }
                    )
                    HorizontalDivider(thickness = 1.dp, color = dividerColor)

                    SettingsMenuItemRow(
                        title = "Notifications & Sync",
                        subtitle = syncSummary,
                        icon = Icons.Default.Sync,
                        onClick = {
                            performHapticClick(context)
                            callbacks.onNavigateToNotifications()
                        }
                    )
                    HorizontalDivider(thickness = 1.dp, color = dividerColor)

                    SettingsMenuItemRow(
                        title = "Hidden Text",
                        subtitle = hiddenTextSummary,
                        icon = Icons.Default.VisibilityOff,
                        onClick = {
                            performHapticClick(context)
                            callbacks.onNavigateToHiddenText()
                        }
                    )
                    HorizontalDivider(thickness = 1.dp, color = dividerColor)

                    SettingsMenuItemRow(
                        title = "Data & Retention",
                        subtitle = dataSummary,
                        icon = Icons.Default.Storage,
                        onClick = {
                            performHapticClick(context)
                            callbacks.onNavigateToData()
                        }
                    )
                    HorizontalDivider(thickness = 1.dp, color = dividerColor)

                    SettingsMenuItemRow(
                        title = "Bracket Overlay",
                        subtitle = bracketSummary,
                        icon = Icons.Default.FlipToFront,
                        onClick = {
                            performHapticClick(context)
                            callbacks.onNavigateToBracket()
                        }
                    )
                    HorizontalDivider(thickness = 1.dp, color = dividerColor)

                    SettingsMenuItemRow(
                        title = "App Info & Updates",
                        subtitle = aboutSummary,
                        icon = Icons.Default.Info,
                        onClick = {
                            performHapticClick(context)
                            callbacks.onNavigateToAbout()
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            SettingsSectionHeader(title = "Advanced Security & OTPs")

            SensitiveNotificationsCard(
                state = sensitiveAccess,
                isAmoled = isAmoled,
                cardContainerColor = cardContainerColor,
                borderColor = borderColor,
                onGrantWithShizuku = callbacks::onGrantSensitiveWithShizuku,
                onOpenShizuku = callbacks::onOpenShizuku
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
