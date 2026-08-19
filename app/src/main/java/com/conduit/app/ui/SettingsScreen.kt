@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.conduit.app.ui

import com.conduit.app.*
import android.content.ComponentName
import android.content.Intent
import android.content.Context
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.IntOffset
import androidx.compose.material.icons.Icons
import androidx.compose.animation.*
import android.app.Notification
import android.app.RemoteInput
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.Settings as SettingsIcon
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.conduit.app.data.AppDatabase
import com.conduit.app.data.HubNotification
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.os.Build
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import android.os.Vibrator
import android.os.VibratorManager
import android.os.VibrationEffect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.activity.enableEdgeToEdge

interface SettingsScreenCallbacks {
    fun onThemeChanged(theme: Int)
    fun onJacobMonochromeChanged(enabled: Boolean)
    fun onGroupByChannelChanged(enabled: Boolean)
    fun onChannelToggled(prefKey: String, isEnabled: Boolean)
    fun onSyncDismissalChanged(enabled: Boolean)
    fun onSyncPinnedChanged(enabled: Boolean)
    fun onShowActionChipsChanged(enabled: Boolean)
    fun onAiBundleChanged(bundle: List<String>)
    fun onNotesBundleChanged(bundle: List<String>)
    fun onRecorderBundleChanged(bundle: List<String>)
    fun onComposeBundleChanged(bundle: List<String>)
    fun onDockLongPressLaunchChanged(enabled: Boolean)
    fun onDockScrollIndicatorChanged(indicator: String)
    fun onSwipeLeftActionChanged(action: String)
    fun onSwipeRightActionChanged(action: String)
    fun onDockSizeChanged(size: Int)
    fun onEnableBracketChanged(enabled: Boolean)
    fun onBracketNotificationPopupChanged(enabled: Boolean)
    fun onBracketHangerEnabledChanged(enabled: Boolean)
    fun onBracketVerticalPositionChanged(position: Float)
    fun onUnifiedViewChanged(enabled: Boolean)
    fun onActiveAppIconChanged(icon: String)
    fun onSmartMarkReadChanged(enabled: Boolean)
    fun onSmartMarkReadTargetChanged(target: String)
    fun onRetentionDaysChanged(days: Int)
    fun onEnableAppBundlesChanged(enabled: Boolean)
    fun onMinimizeIconsChanged(enabled: Boolean)
    fun onAutoCollapseReadChanged(enabled: Boolean)
    fun onAutoDismissDetachedChanged(enabled: Boolean)
    fun onShowWhatsNew()
    fun onNavigateToDevSettings()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: com.conduit.app.data.ConduitSettings,
    callbacks: SettingsScreenCallbacks,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager
    val channelsToShow = remember { getInstalledChannels(context) }
    var showSupportedAppsDialog by remember { mutableStateOf(false) }
    val options = listOf("System Default", "Light Theme", "Dark Theme", "Jacob Mode (AMOLED)")

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
            Text("App Info & Updates", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { 
                        performHapticClick(context)
                        callbacks.onShowWhatsNew() 
                    }
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
                options.forEachIndexed { index, text ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .selectable(
                                selected = (settings.themePreference == index),
                                onClick = { callbacks.onThemeChanged(index) },
                                role = androidx.compose.ui.semantics.Role.RadioButton
                            )
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (settings.themePreference == index),
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
            
            if (settings.themePreference == 3) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Monochrome Accents", style = MaterialTheme.typography.bodyLarge)
                        Text("Forces text and icons to pure white/light gray instead of device blue", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = settings.jacobMonochrome,
                        onCheckedChange = callbacks::onJacobMonochromeChanged
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))

            Text("Layout", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
            
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Minimize Notification Icons", style = MaterialTheme.typography.bodyLarge)
                    Text("Shrinks app icons and aligns them with the notification title to maximize space for text and action chips", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = settings.minimizeIcons, onCheckedChange = callbacks::onMinimizeIconsChanged)
            }

            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-Collapse Read Notifications", style = MaterialTheme.typography.bodyLarge)
                    Text("Automatically collapse read (archived) notifications. This overrides the Master 'Expand All' state unless you manually expand them one-by-one.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = settings.autoCollapseRead, onCheckedChange = callbacks::onAutoCollapseReadChanged)
            }

            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Show Action Chips", style = MaterialTheme.typography.bodyLarge)
                    Text("Displays quick action chips (like Reply, Mark as Read) directly on the notification card", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = settings.showActionChips, onCheckedChange = callbacks::onShowActionChipsChanged)
            }

            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Smart Action Chips", style = MaterialTheme.typography.bodyLarge)
                    Text("Automatically inserts a \"Mark Read\" action chip on messages (and \"Dismiss\" on other notifications) if an app notification doesn't natively support it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = settings.smartMarkRead, onCheckedChange = callbacks::onSmartMarkReadChanged)
            }

            if (settings.smartMarkRead) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                    Text("Smart Chips Target", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val targets = listOf("widget_only" to "Widget Only", "widget_and_app" to "Widget and App")
                        targets.forEach { (value, label) ->
                            val isSelected = settings.smartMarkReadTarget == value
                            Surface(
                                onClick = { callbacks.onSmartMarkReadTargetChanged(value) },
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                ),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                modifier = Modifier.weight(1f).height(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Text(
                                        text = label,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("Swipe Gestures", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
            
            SwipeActionSelector(
                label = "Swipe Right",
                currentValue = settings.swipeRightAction,
                isUnifiedView = settings.unifiedView,
                onValueSelected = callbacks::onSwipeRightActionChanged
            )
            
            SwipeActionSelector(
                label = "Swipe Left",
                currentValue = settings.swipeLeftAction,
                isUnifiedView = settings.unifiedView,
                onValueSelected = callbacks::onSwipeLeftActionChanged
            )
            
            Spacer(modifier = Modifier.height(24.dp))

            Text("Unread App Dock Settings", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
            
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Launch App on Long Press", style = MaterialTheme.typography.bodyLarge)
                    Text("When enabled, long-pressing an app icon in the bottom unread dock will immediately open that application", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = settings.dockLongPressLaunch, onCheckedChange = callbacks::onDockLongPressLaunchChanged)
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text("Dock Size", style = MaterialTheme.typography.bodyLarge)
                Text("Adjust the size and spacing of app icons inside the unread dock", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp))
                
                val sizeOptions = listOf("Small", "Medium", "Large")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sizeOptions.forEachIndexed { index, label ->
                        val isSelected = settings.dockSizeIndex == index
                        OutlinedCard(
                            onClick = { 
                                callbacks.onDockSizeChanged(index)
                                performHapticClick(context)
                            },
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp, 
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text("Scroll Indicator", style = MaterialTheme.typography.bodyLarge)
                Text("Choose how the dock shows when it has more apps to scroll", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp))
                
                val indicatorOptions = listOf("NONE" to "None", "FADING_EDGES" to "Fade Edges", "SCROLLBAR" to "Track", "BOUNCE" to "Bounce")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    indicatorOptions.forEach { (value, label) ->
                        val isSelected = settings.dockScrollIndicator == value
                        OutlinedCard(
                            onClick = { 
                                callbacks.onDockScrollIndicatorChanged(value)
                                performHapticClick(context)
                            },
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp, 
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))

            Text("Sync", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
            
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-dismiss detached notifications", style = MaterialTheme.typography.bodyLarge)
                    Text("When a non-message notification is no longer in the system tray (cleared by reboot or the app), dismiss it in Conduit automatically. Messages and calls always stay until you act on them.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = settings.autoDismissDetached, onCheckedChange = callbacks::onAutoDismissDetachedChanged)
            }

            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Sync Dismissal with System", style = MaterialTheme.typography.bodyLarge)
                    Text("When enabled, archiving a notification in Conduit will also dismiss it from the Android system tray", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = settings.syncDismissal, onCheckedChange = callbacks::onSyncDismissalChanged)
            }
            
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Sync Pinned Notifications", style = MaterialTheme.typography.bodyLarge)
                    Text("When enabled, pinned notifications in Conduit will replace original notifications and remain pinned permanently in the Android system tray until unpinned", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = settings.syncPinned, onCheckedChange = callbacks::onSyncPinnedChanged)
            }
            
            Spacer(modifier = Modifier.height(24.dp))

            Text("Notification Retention", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
            Text("Automatically clean up unpinned archived notifications after the selected retention period.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp))
            
            val retentionOptions = listOf(30 to "30 Days", 60 to "60 Days", 90 to "90 Days", 120 to "120 Days", 365 to "365 Days")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                retentionOptions.forEach { (days, label) ->
                    val isSelected = settings.retentionDays == days
                    OutlinedCard(
                        onClick = { 
                            callbacks.onRetentionDaysChanged(days)
                            performHapticClick(context)
                        },
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, 
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                maxLines = 1,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("Channels", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
            Text(
                text = "Channels represent external applications linked to Conduit. When enabled, notification alerts from these apps will be integrated and managed directly within your Conduit workspace feed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            // Use pre-computed channelsToShow
            channelsToShow.forEach { (prefKey, name) ->
                val pkgName = remember(prefKey) {
                    HubNotificationListenerService.supportedApps.entries
                        .firstOrNull { it.value.first == prefKey && try { pm.getApplicationInfo(it.key, 0).enabled } catch(e: Exception) { false } }?.key
                        ?: HubNotificationListenerService.supportedApps.entries.firstOrNull { it.value.first == prefKey }?.key
                        ?: ""
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
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
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showSupportedAppsDialog = true }
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
                    Text("See all channels and package names Conduit supports", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (showSupportedAppsDialog) {
                AlertDialog(
                    onDismissRequest = { showSupportedAppsDialog = false },
                    title = { Text("Supported Apps & Channels") },
                    text = {
                        val groupedApps = remember {
                            HubNotificationListenerService.supportedApps.entries
                                .groupBy { it.value.second }
                        }
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            groupedApps.forEach { (channelName, entries) ->
                                item {
                                    Column {
                                        Text(channelName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                        entries.forEach { entry ->
                                            val pkg = entry.key
                                            val isInstalled = try {
                                                val appInfo = pm.getApplicationInfo(pkg, 0)
                                                appInfo.enabled
                                            } catch (e: Exception) {
                                                false
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 2.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                                if (isInstalled) {
                                                    Text("Installed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp))
                                                } else {
                                                    Text("Not Installed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.padding(start = 8.dp))
                                                }
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
            
            Spacer(modifier = Modifier.height(24.dp))

            Text("Ignored Notifications", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
            
            val prefs = remember { context.getSharedPreferences("conduit_prefs", android.content.Context.MODE_PRIVATE) }
            val blockedRules = remember(prefs) {
                prefs.getStringSet("blocked_rules", emptySet<String>()) ?: emptySet<String>()
            }
            var blockedRulesState by remember { mutableStateOf<Set<String>>(blockedRules) }
            
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
                                val appLabel = remember(pkg) {
                                    getAppLabel(context, pkg)
                                }
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

            Text("App Bundles", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Enable App Bundles FAB", style = MaterialTheme.typography.bodyLarge)
                    Text("Show the floating action button for quick app launching", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = settings.enableAppBundles,
                    onCheckedChange = { callbacks.onEnableAppBundlesChanged(it) }
                )
            }
            
            AnimatedVisibility(visible = settings.enableAppBundles) {
                Column {
                    Text("Customize which apps appear in your FAB menus", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
            
            var editingBundleTitle by remember { mutableStateOf<String?>(null) }
            
            val currentApps = when (editingBundleTitle) {
                "AI Assistants" -> settings.aiBundle
                "Notes" -> settings.notesBundle
                "Recorder" -> settings.recorderBundle
                "Compose" -> settings.composeBundle
                else -> emptyList()
            }
            val onUpdate: (List<String>) -> Unit = when (editingBundleTitle) {
                "AI Assistants" -> callbacks::onAiBundleChanged
                "Notes" -> callbacks::onNotesBundleChanged
                "Recorder" -> callbacks::onRecorderBundleChanged
                "Compose" -> callbacks::onComposeBundleChanged
                else -> { _ -> }
            }

            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large
            ) {
                Column {
                    ListItem(
                        headlineContent = { Text("Recorder") },
                        supportingContent = { Text("${settings.recorderBundle.size} apps configured") },
                        leadingContent = { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
                        modifier = Modifier.clickable { editingBundleTitle = "Recorder" }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    ListItem(
                        headlineContent = { Text("AI Assistants") },
                        supportingContent = { Text("${settings.aiBundle.size} apps configured") },
                        leadingContent = { Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
                        modifier = Modifier.clickable { editingBundleTitle = "AI Assistants" }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    ListItem(
                        headlineContent = { Text("Notes") },
                        supportingContent = { Text("${settings.notesBundle.size} apps configured") },
                        leadingContent = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
                        modifier = Modifier.clickable { editingBundleTitle = "Notes" }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    ListItem(
                        headlineContent = { Text("Compose") },
                        supportingContent = { Text("${settings.composeBundle.size} apps configured") },
                        leadingContent = { Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
                        modifier = Modifier.clickable { editingBundleTitle = "Compose" }
                    )
                }
            }

            if (editingBundleTitle != null) {
                val title = editingBundleTitle!!
                var showAppPicker by remember { mutableStateOf(false) }

                ModalBottomSheet(onDismissRequest = { editingBundleTitle = null }) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(title, style = MaterialTheme.typography.headlineSmall)
                            FilledTonalButton(onClick = { showAppPicker = true }) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Add App")
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        if (currentApps.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text("No apps in this bundle", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                                Column {
                                    currentApps.forEachIndexed { index, pkg ->
                                        val label = remember(pkg) {
                                            getAppLabel(context, pkg)
                                        }
                                        ListItem(
                                            headlineContent = { Text(text = label, fontWeight = FontWeight.Medium) },
                                            leadingContent = { AppIcon(pkg) },
                                            trailingContent = {
                                                IconButton(onClick = { onUpdate(currentApps.filter { it != pkg }) }) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                        )
                                        if (index < currentApps.size - 1) {
                                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(48.dp))
                    }
                    
                    if (showAppPicker) {
                        ModalBottomSheet(onDismissRequest = { showAppPicker = false }) {
                            var searchQuery by remember { mutableStateOf("") }
                            val installedApps = remember {
                                getInstalledApps(context)
                            }
                            
                            val filteredApps by remember {
                                derivedStateOf {
                                    if (searchQuery.isEmpty()) installedApps
                                    else installedApps.filter { it.second.contains(searchQuery, ignoreCase = true) || it.first.contains(searchQuery, ignoreCase = true) }
                                }
                            }
                            
                            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                Text("Select App", style = MaterialTheme.typography.titleLarge)
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text("Search apps...") },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                    trailingIcon = {
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { searchQuery = "" }) {
                                                Icon(Icons.Default.Close, contentDescription = "Clear")
                                            }
                                        }
                                    },
                                    shape = MaterialTheme.shapes.medium
                                )
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                LazyColumn(modifier = Modifier.heightIn(max = 500.dp)) {
                                    items(filteredApps) { pair ->
                                        val pkg = pair.first
                                        val label = pair.second
                                        val isAdded = currentApps.contains(pkg)
                                        
                                        ListItem(
                                            headlineContent = { Text(text = label, color = if (isAdded) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface) },
                                            leadingContent = { AppIcon(pkg) },
                                            trailingContent = {
                                                if (isAdded) {
                                                    Icon(Icons.Default.Check, contentDescription = "Added", tint = MaterialTheme.colorScheme.primary)
                                                }
                                            },
                                            modifier = Modifier.clickable(enabled = !isAdded) {
                                                onUpdate(currentApps + pkg)
                                                showAppPicker = false
                                            }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(32.dp))
                            }
                        }
                    }
                }
            }
            }
            }
            
            Spacer(modifier = Modifier.height(24.dp))

            Text("App Launcher Icon", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
            Text("Select the app launcher icon for Conduit. Changes might take a moment to reflect on your home screen launcher.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp))
            
            val iconOptions = listOf(
                Triple("LEGACY", "Legacy", MaterialTheme.colorScheme.primary),
                Triple("MANILA", "Manila", Color(0xFFD9A53C)),
                Triple("DARK", "Dark", Color(0xFF15171C)),
                Triple("BLUE", "Blue", Color(0xFF2E6FE0))
            )
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                iconOptions.forEach { (id, label, color) ->
                    val isSelected = settings.activeAppIcon.uppercase(java.util.Locale.ROOT) == id
                    OutlinedCard(
                        onClick = { 
                            callbacks.onActiveAppIconChanged(id)
                            performHapticClick(context)
                        },
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, 
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(color, shape = androidx.compose.foundation.shape.CircleShape)
                                    .border(1.5.dp, MaterialTheme.colorScheme.outline, shape = androidx.compose.foundation.shape.CircleShape)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))

            Text("Bracket (Floating Overlay) [BETA]", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 4.dp))
            Text("Note: This feature is currently in beta testing.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(bottom = 12.dp))
            
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Enable Bracket", style = MaterialTheme.typography.bodyLarge)
                    Text("Shows a persistent floating handle on the edge of your screen. Long-press to quickly launch Conduit from anywhere.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = settings.enableBracket, onCheckedChange = callbacks::onEnableBracketChanged)
            }

            if (settings.enableBracket) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Group by Channel (Hangar)", style = MaterialTheme.typography.bodyLarge)
                        Text("When enabled, notifications inside the Hangar (the swipe-in panel on the Bracket) will be grouped by their channel type rather than listed individually.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = settings.groupByChannel, onCheckedChange = callbacks::onGroupByChannelChanged)
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Animate Icon on Notification", style = MaterialTheme.typography.bodyLarge)
                        Text("Briefly show the incoming notification's app icon next to the bracket. Tap the icon or bracket to open the notification.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = settings.bracketNotificationPopup, onCheckedChange = callbacks::onBracketNotificationPopupChanged)
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Bracket Hanger", style = MaterialTheme.typography.bodyLarge)
                        Text("Swipe inward on the bracket to pull open a quick view of pending notifications.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = settings.bracketHangerEnabled, onCheckedChange = callbacks::onBracketHangerEnabledChanged)
                }

                Spacer(modifier = Modifier.height(12.dp))
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text("Bracket Vertical Position", style = MaterialTheme.typography.bodyLarge)
                    Text("Adjust the vertical placement of the bracket along the right edge.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    androidx.compose.material3.Slider(
                        value = settings.bracketVerticalPosition,
                        onValueChange = callbacks::onBracketVerticalPositionChanged,
                        steps = 9,
                        valueRange = 0f..1f,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("Advanced Security & OTPs", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
            
            val adbCommandText = "adb shell appops set --user 0 com.conduit.app RECEIVE_SENSITIVE_NOTIFICATIONS allow"
            
            OutlinedCard(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
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
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
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
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                                modifier = Modifier.weight(1f),
                                maxLines = 3
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("Conduit ADB Command", adbCommandText)
                                    clipboard.setPrimaryClip(clip)
                                    
                                    performHapticClick(context)
                                    android.widget.Toast.makeText(context, "Command copied to clipboard!", android.widget.Toast.LENGTH_SHORT).show()
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
            
            Spacer(modifier = Modifier.height(24.dp))

            Text("Testing & Developer Settings", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
            val devScope = rememberCoroutineScope()
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                val job = devScope.launch {
                                    kotlinx.coroutines.delay(4000)
                                    performHapticClick(context)
                                    callbacks.onNavigateToDevSettings()
                                }
                                try {
                                    awaitRelease()
                                } finally {
                                    job.cancel()
                                }
                            },
                            onTap = {
                                performHapticClick(context)
                                android.widget.Toast.makeText(context, "Long press for 4 seconds to enter Developer Settings", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
            ) {
                ListItem(
                    headlineContent = { Text("Testing & Developer Settings") },
                    supportingContent = { Text("Advanced tools and options for debugging and previewing beta features.") },
                    leadingContent = { Icon(Icons.Default.Build, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = { Icon(Icons.Default.Lock, contentDescription = "Locked") }
                )
            }
        }
    }
}

@Composable
fun SwipeActionSelector(
    label: String,
    currentValue: String,
    isUnifiedView: Boolean,
    onValueSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val displayNames = mapOf(
        "ARCHIVE" to "Mark Read / Dismiss",
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
