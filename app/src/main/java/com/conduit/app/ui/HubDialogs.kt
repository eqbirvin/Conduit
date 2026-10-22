@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.conduit.app.ui

import com.conduit.app.*
import android.content.Intent
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.*
import androidx.compose.animation.*
import android.app.Notification
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.conduit.app.data.AppDatabase
import com.conduit.app.data.HubNotification
import kotlinx.coroutines.Dispatchers
import android.content.SharedPreferences
import com.conduit.app.data.ConduitSettings

@Composable
fun HubUpdateDialog(
    context: Context,
    latestVersionAvailable: String,
    showUpdateDialogState: MutableState<Boolean>
) {
    var showUpdateDialog by showUpdateDialogState
    if (showUpdateDialog) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = { Text("Update Available", fontWeight = FontWeight.Bold) },
            text = { Text("A new version of Conduit is available!\n\nCurrent Version: ${BuildConfig.VERSION_NAME}\nNew Version: $latestVersionAvailable") },
            confirmButton = {
                Button(onClick = {
                    showUpdateDialog = false
                    kotlinx.coroutines.GlobalScope.launch {
                        val updateInfo = com.conduit.app.updater.UpdateManager.checkForUpdates()
                        if (updateInfo != null) {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                android.widget.Toast.makeText(context, "Starting download...", android.widget.Toast.LENGTH_SHORT).show()
                                com.conduit.app.updater.UpdateManager.downloadAndInstall(context, updateInfo.second, updateInfo.first)
                            }
                        }
                    }
                }) {
                    Text("Update")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun HubBlockDialog(
    context: Context,
    prefs: SharedPreferences,
    notificationToBlockState: MutableState<HubNotification?>,
    selectedIdsState: MutableState<Set<Int>>
) {
    var notificationToBlock by notificationToBlockState
    var selectedIds by selectedIdsState
    if (notificationToBlock != null) {
        val notif = notificationToBlock!!
        val appName = remember(notif.packageName) { appLabelCache[notif.packageName] ?: notif.channel }
        var blockType by remember { mutableStateOf("TITLE") }

        val titleText = notif.title ?: ""
        val bodyText = notif.text ?: ""
        val scope = rememberCoroutineScope()

        AlertDialog(
            onDismissRequest = { notificationToBlock = null },
            title = { Text("Block similar notifications?") },
            text = {
                Column {
                    Text(
                        text = "Conduit will automatically ignore future notifications from $appName matching the rule below.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { blockType = "TITLE" }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(selected = blockType == "TITLE", onClick = { blockType = "TITLE" })
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Match by Title", fontWeight = FontWeight.SemiBold)
                            Text("\"$titleText\"", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    if (bodyText.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { blockType = "TEXT" }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(selected = blockType == "TEXT", onClick = { blockType = "TEXT" })
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Match by Content Text", fontWeight = FontWeight.SemiBold)
                                Text("\"$bodyText\"", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pattern = if (blockType == "TITLE") titleText else bodyText
                        if (pattern.isNotBlank()) {
                            val newRule = "${notif.packageName}|$blockType|$pattern"
                            val currentSet = prefs.getStringSet("blocked_rules", emptySet<String>()) ?: emptySet<String>()
                            val updatedSet = currentSet + newRule
                            prefs.edit().putStringSet("blocked_rules", updatedSet).apply()

                            kotlinx.coroutines.GlobalScope.launch {
                                val database = AppDatabase.getDatabase(context)
                                val allNotifs = database.notificationDao().getAllNotificationsSync()
                                val idsToDelete = mutableListOf<Int>()
                                val keysToCancel = mutableListOf<String>()
                                allNotifs.forEach { item ->
                                    if (item.packageName == notif.packageName) {
                                        val match = if (blockType == "TITLE") {
                                            item.title?.contains(pattern, ignoreCase = true) == true
                                        } else {
                                            item.text?.contains(pattern, ignoreCase = true) == true
                                        }
                                        if (match) {
                                            idsToDelete.add(item.id)
                                            keysToCancel.add(item.notificationKey)
                                        }
                                    }
                                }
                                if (idsToDelete.isNotEmpty()) {
                                    keysToCancel.forEach { key ->
                                        HubNotificationListenerService.instance?.cancel(key)
                                    }
                                    database.notificationDao().deleteNotifications(idsToDelete)
                                    com.conduit.app.widget.WidgetUpdater.updateAllWidgets(context)
                                }
                            }
                        }
                        performHapticClick(context)
                        notificationToBlock = null
                        selectedIds = emptySet()
                    }
                ) {
                    Text("Block")
                }
            },
            dismissButton = {
                TextButton(onClick = { notificationToBlock = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun HubSnoozeSheet(
    onSnoozeNotification: (Int, Long) -> Unit,
    notificationToSnoozeState: MutableState<HubNotification?>,
    selectedIdsState: MutableState<Set<Int>>
) {
    var notificationToSnooze by notificationToSnoozeState
    var selectedIds by selectedIdsState
    notificationToSnooze?.let { notif ->
        ModalBottomSheet(onDismissRequest = { notificationToSnooze = null }) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Snooze Notification", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 16.dp))

                val snoozeOptions = listOf(
                    "15 Minutes" to 15 * 60 * 1000L,
                    "1 Hour" to 60 * 60 * 1000L,
                    "2 Hours" to 2 * 60 * 60 * 1000L
                )

                snoozeOptions.forEach { (label, durationMs) ->
                    ListItem(
                        headlineContent = { Text(label) },
                        modifier = Modifier.clickable {
                            HubNotificationListenerService.instance?.snooze(notif.notificationKey, durationMs)
                            onSnoozeNotification(notif.id, System.currentTimeMillis())
                            notificationToSnooze = null
                            selectedIds = emptySet()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun HubBundleSheets(
    context: Context,
    settingsViewModel: SettingsViewModel,
    settings: ConduitSettings,
    showBundleMenuState: MutableState<Pair<String, List<String>>?>,
    editingBundleTitleState: MutableState<String?>
) {
    var showBundleMenu by showBundleMenuState
    var editingBundleTitle by editingBundleTitleState
    val editingTitle = editingBundleTitle
    if (editingTitle != null) {
        val currentApps = when (editingTitle) {
            "AI Assistants" -> settings.aiBundle
            "Notes" -> settings.notesBundle
            "Recorders", "Recorder" -> settings.recorderBundle
            "Compose" -> settings.composeBundle
            else -> emptyList()
        }
        val onUpdate: (List<String>) -> Unit = when (editingTitle) {
            "AI Assistants" -> settingsViewModel::updateAiBundle
            "Notes" -> settingsViewModel::updateNotesBundle
            "Recorders", "Recorder" -> settingsViewModel::updateRecorderBundle
            "Compose" -> settingsViewModel::updateComposeBundle
            else -> { _ -> }
        }
        BundleManagementBottomSheet(
            bundleTitle = editingTitle,
            currentApps = currentApps,
            onUpdateApps = onUpdate,
            onDismiss = { editingBundleTitle = null }
        )
    }

    if (showBundleMenu != null) {
        val (title, packages) = showBundleMenu!!
        ModalBottomSheet(onDismissRequest = { showBundleMenu = null }) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 16.dp))

                packages.forEach { pkg ->
                    val pm = context.packageManager
                    val label = remember(pkg) {
                        if (isPackageInstalled(context, pkg)) {
                            getAppLabel(context, pkg)
                        } else {
                            null
                        }
                    }
                    if (label != null) {
                        ListItem(
                            headlineContent = { Text(label) },
                            leadingContent = { AppIcon(pkg) },
                            modifier = Modifier.clickable {
                                showBundleMenu = null
                                var launchIntent: Intent? = null

                                // Try Email Compose
                                val emailIntent = Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:")).apply { setPackage(pkg) }
                                if (emailIntent.resolveActivity(pm) != null) {
                                    launchIntent = emailIntent
                                } else {
                                    // Try SMS Compose
                                    val smsIntent = Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:")).apply { setPackage(pkg) }
                                    if (smsIntent.resolveActivity(pm) != null) {
                                        launchIntent = smsIntent
                                    }
                                }

                                if (launchIntent != null) {
                                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    try { context.startActivity(launchIntent) } catch (e: Exception) { e.printStackTrace() }
                                } else {
                                    launchApp(context, pkg)
                                }
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
