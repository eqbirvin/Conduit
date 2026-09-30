package com.conduit.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conduit.app.data.ConduitSettings
import com.conduit.app.performHapticClick
import com.conduit.app.ui.BundleManagementBottomSheet

interface DockSettingsScreenCallbacks {
    fun onEnableAppBundlesChanged(enabled: Boolean)
    fun onAiBundleChanged(bundle: List<String>)
    fun onNotesBundleChanged(bundle: List<String>)
    fun onRecorderBundleChanged(bundle: List<String>)
    fun onComposeBundleChanged(bundle: List<String>)
    fun onBundleMenuSizingChanged(sizing: String)
    fun onDockExpandPositionChanged(position: String)
    fun onTransparentBundlePillsChanged(enabled: Boolean)
    fun onBundleMenuBlurChanged(enabled: Boolean)
    fun onDockLongPressLaunchChanged(enabled: Boolean)
    fun onDockSizeChanged(size: Int)
    fun onDockScrollIndicatorChanged(indicator: String)
    fun onSwipeRightActionChanged(action: String)
    fun onSwipeLeftActionChanged(action: String)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DockSettingsScreen(
    settings: ConduitSettings,
    callbacks: DockSettingsScreenCallbacks,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var editingBundleTitle by remember { mutableStateOf<String?>(null) }

    val currentApps = when (editingBundleTitle) {
        "AI Assistants" -> settings.aiBundle
        "Notes" -> settings.notesBundle
        "Recorder", "Recorders" -> settings.recorderBundle
        "Compose" -> settings.composeBundle
        else -> emptyList()
    }
    val onUpdate: (List<String>) -> Unit = when (editingBundleTitle) {
        "AI Assistants" -> callbacks::onAiBundleChanged
        "Notes" -> callbacks::onNotesBundleChanged
        "Recorder", "Recorders" -> callbacks::onRecorderBundleChanged
        "Compose" -> callbacks::onComposeBundleChanged
        else -> { _ -> }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dock & Gestures", fontSize = 22.sp, fontWeight = FontWeight.SemiBold) },
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
            SettingsSectionHeader(title = "App Bundles")

            SettingsSwitchRow(
                title = "Enable App Bundles in Dock",
                description = "Show a split button on the dock for quick app launching",
                checked = settings.enableAppBundles,
                onCheckedChange = callbacks::onEnableAppBundlesChanged
            )

            AnimatedVisibility(visible = settings.enableAppBundles) {
                Column {
                    Text(
                        text = "Customize which apps appear in your bundle menus",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Column {
                            ListItem(
                                headlineContent = { Text("Recorders") },
                                supportingContent = { Text("${settings.recorderBundle.size} apps configured") },
                                leadingContent = { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
                                modifier = Modifier.clickable { editingBundleTitle = "Recorders" }
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

                    Spacer(modifier = Modifier.height(16.dp))

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Menu Sizing", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        Text("Choose whether the expanded menu scales with the dock or stays compact", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
                        val sizingOptions = listOf("MATCH_DOCK" to "Match Dock", "COMPACT" to "Compact")
                        SegmentedOptionRow(
                            options = sizingOptions,
                            selectedValue = settings.bundleMenuSizing,
                            onValueSelected = callbacks::onBundleMenuSizingChanged
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Expand Button Position", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        Text("Choose whether the expand button and bundle menu align to the left or right", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
                        val positionOptions = listOf("LEFT" to "Left", "RIGHT" to "Right")
                        SegmentedOptionRow(
                            options = positionOptions,
                            selectedValue = settings.dockExpandPosition,
                            onValueSelected = callbacks::onDockExpandPositionChanged
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    SettingsSwitchRow(
                        title = "Transparent Bundle Pills",
                        description = "Make the background and shadow of bundle title pills transparent",
                        checked = settings.transparentBundlePills,
                        onCheckedChange = callbacks::onTransparentBundlePillsChanged
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    SettingsSwitchRow(
                        title = "Bundle Menu Background Blur",
                        description = "Blur the background when the app bundle flyout is expanded",
                        checked = settings.bundleMenuBlur,
                        onCheckedChange = callbacks::onBundleMenuBlurChanged
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            SettingsSectionHeader(title = "Unread App Dock")

            SettingsSwitchRow(
                title = "Launch App on Long Press",
                description = "When enabled, long-pressing an app icon in the bottom unread dock will immediately open that application",
                checked = settings.dockLongPressLaunch,
                onCheckedChange = callbacks::onDockLongPressLaunchChanged
            )

            Spacer(modifier = Modifier.height(8.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Dock Size", style = MaterialTheme.typography.bodyLarge)
                Text("Adjust the size and spacing of app icons inside the unread dock", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                val sizeOptions = listOf(0 to "Small", 1 to "Medium", 2 to "Large")
                SegmentedOptionRow(
                    options = sizeOptions,
                    selectedValue = settings.dockSizeIndex,
                    onValueSelected = {
                        callbacks.onDockSizeChanged(it)
                        performHapticClick(context)
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Scroll Indicator", style = MaterialTheme.typography.bodyLarge)
                Text("Choose how the dock shows when it has more apps to scroll", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                val indicatorOptions = listOf("NONE" to "None", "FADING_EDGES" to "Fade Edges", "SCROLLBAR" to "Track", "BOUNCE" to "Bounce")
                SegmentedOptionRow(
                    options = indicatorOptions,
                    selectedValue = settings.dockScrollIndicator,
                    onValueSelected = {
                        callbacks.onDockScrollIndicatorChanged(it)
                        performHapticClick(context)
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            SettingsSectionHeader(title = "Swipe Gestures")

            SwipeActionSelector(
                label = "Swipe Right",
                currentValue = settings.swipeRightAction,
                onValueSelected = callbacks::onSwipeRightActionChanged
            )

            SwipeActionSelector(
                label = "Swipe Left",
                currentValue = settings.swipeLeftAction,
                onValueSelected = callbacks::onSwipeLeftActionChanged
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

        if (editingBundleTitle != null) {
            BundleManagementBottomSheet(
                bundleTitle = editingBundleTitle!!,
                currentApps = currentApps,
                onUpdateApps = onUpdate,
                onDismiss = { editingBundleTitle = null }
            )
        }
    }
}
