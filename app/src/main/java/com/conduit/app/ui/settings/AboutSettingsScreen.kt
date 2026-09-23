package com.conduit.app.ui.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conduit.app.BuildConfig
import com.conduit.app.data.ConduitSettings
import com.conduit.app.performHapticClick
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

interface AboutSettingsScreenCallbacks {
    fun onUpdateIntervalChanged(interval: String)
    fun onManualUpdateCheck()
    fun onShowWhatsNew()
    fun onNavigateToDevSettings()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutSettingsScreen(
    settings: ConduitSettings,
    callbacks: AboutSettingsScreenCallbacks,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val devScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Info & Updates", fontSize = 22.sp, fontWeight = FontWeight.SemiBold) },
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
            SettingsSectionHeader(title = "Updates")

            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text("Auto-Check for Updates", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "Select how often Conduit should check for app updates in the background",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                val intervalOptions = listOf(
                    "DAILY" to "Daily",
                    "EVERY_3_DAYS" to "Every 3 Days",
                    "WEEKLY" to "Weekly",
                    "DISABLED" to "Disabled"
                )
                SegmentedOptionRow(
                    options = intervalOptions,
                    selectedValue = settings.updateInterval,
                    onValueSelected = {
                        callbacks.onUpdateIntervalChanged(it)
                        performHapticClick(context)
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        performHapticClick(context)
                        callbacks.onManualUpdateCheck()
                    }
            ) {
                ListItem(
                    headlineContent = { Text("Check for Updates") },
                    supportingContent = { Text("Current version: ${BuildConfig.VERSION_NAME}") },
                    leadingContent = { Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

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
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            SettingsSectionHeader(title = "Testing & Developer Tools")

            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                val job = devScope.launch {
                                    delay(4000)
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
                                Toast.makeText(context, "Long press for 4 seconds to enter Developer Settings", Toast.LENGTH_SHORT).show()
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

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
