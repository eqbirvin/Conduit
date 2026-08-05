package com.conduit.app.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conduit.app.*
import com.conduit.app.data.AppDatabase
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    themePreference: Int,
    onThemeChanged: (Int) -> Unit,
    jacobMonochrome: Boolean,
    onJacobMonochromeChanged: (Boolean) -> Unit,
    groupByChannel: Boolean,
    onGroupByChannelChanged: (Boolean) -> Unit,
    channelStates: Map<String, Boolean>,
    onChannelToggled: (String, Boolean) -> Unit,
    persistentTrayEnabled: Boolean,
    onPersistentTrayEnabledChanged: (Boolean) -> Unit,
    syncDismissal: Boolean,
    onSyncDismissalChanged: (Boolean) -> Unit,
    syncPinned: Boolean,
    onSyncPinnedChanged: (Boolean) -> Unit,
    showActionChips: Boolean,
    onShowActionChipsChanged: (Boolean) -> Unit,
    aiBundle: List<String>,
    onAiBundleChanged: (List<String>) -> Unit,
    notesBundle: List<String>,
    onNotesBundleChanged: (List<String>) -> Unit,
    recorderBundle: List<String>,
    onRecorderBundleChanged: (List<String>) -> Unit,
    composeBundle: List<String>,
    onComposeBundleChanged: (List<String>) -> Unit,
    dockLongPressLaunch: Boolean,
    onDockLongPressLaunchChanged: (Boolean) -> Unit,
    swipeLeftAction: String,
    onSwipeLeftActionChanged: (String) -> Unit,
    swipeRightAction: String,
    onSwipeRightActionChanged: (String) -> Unit,
    dockSizeIndex: Int,
    onDockSizeIndexChanged: (Int) -> Unit,
    enableBubbles: Boolean,
    onEnableBubblesChanged: (Boolean) -> Unit,
    enableBracket: Boolean,
    onEnableBracketChanged: (Boolean) -> Unit,
    bracketNotificationPopup: Boolean,
    onBracketNotificationPopupChanged: (Boolean) -> Unit,
    bracketHangerEnabled: Boolean,
    onBracketHangerEnabledChanged: (Boolean) -> Unit,
    bracketVerticalPosition: Float,
    onBracketVerticalPositionChanged: (Float) -> Unit,
    unifiedView: Boolean,
    onUnifiedViewChanged: (Boolean) -> Unit,
    activeAppIcon: String,
    onActiveAppIconChanged: (String) -> Unit,
    smartMarkRead: Boolean,
    onSmartMarkReadChanged: (Boolean) -> Unit,
    smartMarkReadTarget: String,
    onSmartMarkReadTargetChanged: (String) -> Unit,
    onNavigateToDevSettings: () -> Unit,
    onShowWhatsNew: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var showFullChangelog by remember { mutableStateOf(false) }

    if (showFullChangelog) {
        WhatsNewDialog(
            releases = CHANGELOG,
            onDismiss = { showFullChangelog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
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
            Text("App Info & Updates", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {
                            performHapticClick(context)
                            showFullChangelog = true
                        },
                        onLongClick = {
                            performHapticClick(context)
                            onNavigateToDevSettings()
                        }
                    )
            ) {
                ListItem(
                    headlineContent = { Text("What's New") },
                    supportingContent = { Text("View update details and release history.") },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("Theme", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
            Column(Modifier.selectableGroup()) {
                val themes = listOf("System Default" to 0, "Light" to 1, "Dark" to 2, "Jacob Mode (AMOLED Black)" to 3)
                themes.forEach { (text, mode) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .selectable(
                                selected = (themePreference == mode),
                                onClick = { onThemeChanged(mode) }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (themePreference == mode),
                            onClick = null
                        )
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }

            if (themePreference == 3) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Monochrome Mode", style = MaterialTheme.typography.bodyLarge)
                        Text("Pure black & white theme styling", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = jacobMonochrome,
                        onCheckedChange = onJacobMonochromeChanged
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("Launcher Icon", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
            Column(Modifier.selectableGroup()) {
                val icons = listOf("Manila (Default)" to "MANILA", "Legacy Blue" to "LEGACY", "Dark" to "DARK", "Blue accent" to "BLUE")
                icons.forEach { (text, iconKey) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .selectable(
                                selected = (activeAppIcon == iconKey),
                                onClick = { onActiveAppIconChanged(iconKey) }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (activeAppIcon == iconKey),
                            onClick = null
                        )
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("Swipe Actions", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
            SwipeActionSelector(
                label = "Swipe Right",
                currentValue = swipeRightAction,
                onValueSelected = onSwipeRightActionChanged,
                isUnifiedView = unifiedView
            )
            Spacer(modifier = Modifier.height(12.dp))
            SwipeActionSelector(
                label = "Swipe Left",
                currentValue = swipeLeftAction,
                onValueSelected = onSwipeLeftActionChanged,
                isUnifiedView = unifiedView
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text("System Integration", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Sync Dismissal", style = MaterialTheme.typography.bodyLarge)
                    Text("Dismissing in Conduit clears system tray", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = syncDismissal,
                    onCheckedChange = onSyncDismissalChanged
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Persistent Notification Tray", style = MaterialTheme.typography.bodyLarge)
                    Text("Show active unread count in system status bar", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = persistentTrayEnabled,
                    onCheckedChange = onPersistentTrayEnabledChanged
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Action Chips", style = MaterialTheme.typography.bodyLarge)
                    Text("Show quick reply & action buttons under notifications", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = showActionChips,
                    onCheckedChange = onShowActionChipsChanged
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("Enabled Apps & Channels", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
            
            val appDisplayMap = mapOf(
                "app_phone" to ("Phone (Google Dialer)" to "com.google.android.dialer"),
                "app_whatsapp" to ("WhatsApp" to "com.whatsapp"),
                "app_slack" to ("Slack" to "com.Slack"),
                "app_messages" to ("Google Messages" to "com.google.android.apps.messaging"),
                "app_gmail" to ("Gmail" to "com.google.android.gm"),
                "app_outlook" to ("Outlook" to "com.microsoft.office.outlook"),
                "app_instagram" to ("Instagram" to "com.instagram.android"),
                "app_facebook" to ("Facebook" to "com.facebook.katana"),
                "app_messenger" to ("Messenger" to "com.facebook.orca"),
                "app_twitter" to ("Twitter / X" to "com.twitter.android"),
                "app_teams" to ("Microsoft Teams" to "com.microsoft.teams")
            )

            appDisplayMap.forEach { (prefKey, info) ->
                val (label, pkg) = info
                val isChecked = channelStates[prefKey] ?: true
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        AppIcon(packageName = pkg, size = 32.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                    Switch(
                        checked = isChecked,
                        onCheckedChange = { onChannelToggled(prefKey, it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("Data Management", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        kotlinx.coroutines.GlobalScope.launch {
                            val db = AppDatabase.getDatabase(context)
                            db.notificationDao().deleteAll()
                        }
                        performHapticClick(context)
                    }
            ) {
                ListItem(
                    headlineContent = { Text("Clear All Database Data") },
                    supportingContent = { Text("Delete all notifications from local storage") },
                    leadingContent = { Icon(Icons.Filled.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun SwipeActionSelector(
    label: String,
    currentValue: String,
    onValueSelected: (String) -> Unit,
    isUnifiedView: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    val displayNames = mapOf(
        "ARCHIVE" to if (isUnifiedView) "Mark Read" else "Clear",
        "SNOOZE" to "Snooze",
        "PIN" to "Pin / Unpin",
        "BLOCK" to "Block Notification Type"
    )
    val options = listOf("ARCHIVE", "SNOOZE", "PIN", "BLOCK")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text("Action triggered when swiping this direction", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        
        Box {
            TextButton(onClick = { expanded = true }) {
                Text(displayNames[currentValue] ?: currentValue)
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(displayNames[opt] ?: opt) },
                        onClick = {
                            onValueSelected(opt)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
