package com.conduit.app.ui.settings

import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conduit.app.data.AppDatabase
import com.conduit.app.data.ConduitSettings
import com.conduit.app.data.PackageChannel
import com.conduit.app.getAppLabel
import com.conduit.app.performHapticClick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface DataRetentionSettingsScreenCallbacks {
    fun onRetentionDaysChanged(days: Int)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataRetentionSettingsScreen(
    settings: ConduitSettings,
    callbacks: DataRetentionSettingsScreenCallbacks,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("conduit_prefs", Context.MODE_PRIVATE) }

    var showUninstalledAppsDialog by remember { mutableStateOf(false) }
    var uninstalledAppsList by remember { mutableStateOf<List<PackageChannel>>(emptyList()) }

    val blockedRules = remember(prefs) {
        prefs.getStringSet("blocked_rules", emptySet()) ?: emptySet()
    }
    var blockedRulesState by remember { mutableStateOf(blockedRules) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Data & Retention", fontSize = 22.sp, fontWeight = FontWeight.SemiBold) },
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
            SettingsSectionHeader(title = "Notification Retention")
            Text(
                text = "Automatically clean up unpinned archived notifications after the selected retention period.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            val retentionOptions = listOf(
                30 to "30 Days",
                60 to "60 Days",
                90 to "90 Days",
                120 to "120 Days",
                365 to "365 Days"
            )
            SegmentedOptionRow(
                options = retentionOptions,
                selectedValue = settings.retentionDays,
                onValueSelected = {
                    callbacks.onRetentionDaysChanged(it)
                    performHapticClick(context)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        performHapticClick(context)
                        scope.launch(Dispatchers.IO) {
                            val db = AppDatabase.getDatabase(context)
                            val allPackages = db.notificationDao().getDistinctPackagesWithChannel()
                            val uninstalled = allPackages.filter { pkgInfo ->
                                try {
                                    pm.getPackageInfo(pkgInfo.packageName, 0)
                                    false
                                } catch (e: PackageManager.NameNotFoundException) {
                                    true
                                }
                            }
                            withContext(Dispatchers.Main) {
                                if (uninstalled.isEmpty()) {
                                    Toast.makeText(context, "No historical notifications from uninstalled apps found.", Toast.LENGTH_SHORT).show()
                                } else {
                                    uninstalledAppsList = uninstalled
                                    showUninstalledAppsDialog = true
                                }
                            }
                        }
                    }
            ) {
                ListItem(
                    headlineContent = { Text("Clean Uninstalled Apps") },
                    supportingContent = { Text("Remove historical notifications from apps you no longer have installed.") },
                    leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            SettingsSectionHeader(title = "Ignored Notifications")

            if (blockedRulesState.isEmpty()) {
                Text(
                    text = "No blocked notifications configured. You can block specific types of notifications by selecting them in the Hub list and tapping the Block icon.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                OutlinedCard(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Column {
                        val rulesList = blockedRulesState.toList()
                        for (index in rulesList.indices) {
                            val ruleStr = rulesList[index]
                            val parts = ruleStr.split("|", limit = 3)
                            if (parts.size >= 3) {
                                val pkg = parts[0]
                                val type = parts[1]
                                val pattern = parts[2]
                                val appLabel = remember(pkg) { getAppLabel(context, pkg) }
                                ListItem(
                                    headlineContent = { Text(appLabel, fontWeight = FontWeight.SemiBold) },
                                    supportingContent = {
                                        Text("Ignore if $type contains: \"$pattern\"", style = MaterialTheme.typography.bodySmall)
                                    },
                                    trailingContent = {
                                        IconButton(onClick = {
                                            val updated = blockedRulesState - ruleStr
                                            prefs.edit().putStringSet("blocked_rules", updated).apply()
                                            blockedRulesState = updated
                                        }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                )
                                if (index < rulesList.size - 1) {
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        if (showUninstalledAppsDialog) {
            var selectedPackages by remember { mutableStateOf(uninstalledAppsList.map { it.packageName }.toSet()) }

            AlertDialog(
                onDismissRequest = { showUninstalledAppsDialog = false },
                title = { Text("Clean Uninstalled Apps") },
                text = {
                    Column {
                        Text(
                            "These apps were installed and Conduit captured their notifications. However, they are no longer installed. Select what apps you want to clean historic notifications from:",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                            items(uninstalledAppsList.size) { index ->
                                val pkgInfo = uninstalledAppsList[index]
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedPackages = if (selectedPackages.contains(pkgInfo.packageName)) {
                                                selectedPackages - pkgInfo.packageName
                                            } else {
                                                selectedPackages + pkgInfo.packageName
                                            }
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = selectedPackages.contains(pkgInfo.packageName),
                                        onCheckedChange = null
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(pkgInfo.channel, style = MaterialTheme.typography.bodyLarge)
                                        Text(pkgInfo.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (selectedPackages.isNotEmpty()) {
                                scope.launch(Dispatchers.IO) {
                                    val db = AppDatabase.getDatabase(context)
                                    db.notificationDao().deleteByPackageNames(selectedPackages.toList())
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Cleaned notifications for ${selectedPackages.size} apps.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            showUninstalledAppsDialog = false
                        }
                    ) {
                        Text("Clean")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showUninstalledAppsDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
