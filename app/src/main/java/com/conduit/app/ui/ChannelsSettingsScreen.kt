package com.conduit.app.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conduit.app.HubNotificationListenerService
import com.conduit.app.getInstalledChannels
import com.conduit.app.performHapticClick
import java.util.Locale

interface ChannelsSettingsScreenCallbacks {
    fun onChannelToggled(prefKey: String, isEnabled: Boolean)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelsSettingsScreen(
    settings: com.conduit.app.data.ConduitSettings,
    callbacks: ChannelsSettingsScreenCallbacks,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager
    var showSupportedAppsDialog by remember { mutableStateOf(false) }

    // Sort installed and supported channels alphabetically by user-facing name
    val channelsToShow = remember {
        getInstalledChannels(context).sortedBy { it.second.lowercase(Locale.getDefault()) }
    }

    // Pre-resolve representative package for each channel to avoid IPC inside composition/scroll loops
    val channelPackageMap = remember(channelsToShow) {
        channelsToShow.associate { (prefKey, _) ->
            val pkg = HubNotificationListenerService.supportedApps.entries
                .firstOrNull { it.value.first == prefKey && try { pm.getApplicationInfo(it.key, 0).enabled } catch (e: Exception) { false } }?.key
                ?: HubNotificationListenerService.supportedApps.entries.firstOrNull { it.value.first == prefKey }?.key
                ?: ""
            prefKey to pkg
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Channels", fontSize = 22.sp, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Channels represent external applications linked to Conduit. When enabled, notification alerts from these apps will be integrated and managed directly within your Conduit workspace feed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (channelsToShow.isEmpty()) {
                Text(
                    text = "No supported channels are currently installed on this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                channelsToShow.forEach { (prefKey, name) ->
                    val pkgName = channelPackageMap[prefKey].orEmpty()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (pkgName.isNotEmpty()) {
                                AppIcon(packageName = pkgName, size = 28.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                            Text(name, style = MaterialTheme.typography.bodyLarge)
                        }
                        Switch(
                            checked = settings.channelStates[prefKey] ?: true,
                            onCheckedChange = { callbacks.onChannelToggled(prefKey, it) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        performHapticClick(context)
                        showSupportedAppsDialog = true
                    }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 16.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text("View Supported Apps", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "See all channels and package names Conduit supports",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (showSupportedAppsDialog) {
            AlertDialog(
                onDismissRequest = { showSupportedAppsDialog = false },
                title = { Text("Supported Apps & Channels") },
                text = {
                    // Group apps by channel name and sort groups and packages alphabetically
                    val groupedApps = remember {
                        HubNotificationListenerService.supportedApps.entries
                            .groupBy({ it.value.second }, { it.key })
                            .mapValues { (_, pkgs) -> pkgs.sortedWith(String.CASE_INSENSITIVE_ORDER) }
                            .entries
                            .sortedBy { it.key.lowercase(Locale.getDefault()) }
                    }

                    val installedPackages = remember {
                        HubNotificationListenerService.supportedApps.keys.filter { pkg ->
                            try {
                                pm.getApplicationInfo(pkg, 0).enabled
                            } catch (e: Exception) {
                                false
                            }
                        }.toSet()
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            items = groupedApps,
                            key = { it.key }
                        ) { (channelName, packages) ->
                            Column {
                                Text(channelName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                packages.forEach { pkg ->
                                    val isInstalled = installedPackages.contains(pkg)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 8.dp, top = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            pkg,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (isInstalled) {
                                            Text(
                                                "Installed",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(start = 8.dp)
                                            )
                                        } else {
                                            Text(
                                                "Not Installed",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                modifier = Modifier.padding(start = 8.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSupportedAppsDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}
