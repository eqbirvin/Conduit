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

interface DevSettingsScreenCallbacks {
    fun onPersistentTrayEnabledChanged(enabled: Boolean)
    fun onEnableBubblesChanged(enabled: Boolean)
    fun onDemoModeEnabledChanged(enabled: Boolean)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevSettingsScreen(
    settings: com.conduit.app.data.ConduitSettings,
    callbacks: DevSettingsScreenCallbacks,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Testing & Developer Settings", fontSize = 20.sp, fontWeight = FontWeight.SemiBold) },
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
            Text("Persistent Notification Tray", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Persistent Notification Tray", style = MaterialTheme.typography.bodyLarge)
                    Text("Keep a running summary in the system status bar at all times.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = settings.persistentTrayEnabled, onCheckedChange = callbacks::onPersistentTrayEnabledChanged)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("Conversation Bubbles", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
            
            OutlinedCard(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Bubble Mode", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text("When enabled, Conduit will post Android Bubbles for new incoming messages.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = settings.enableBubbles, onCheckedChange = callbacks::onEnableBubblesChanged)
                    }

                    AnimatedVisibility(visible = settings.enableBubbles) {
                        Column {
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text(
                                text = "Floating Overlay Setup",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Conduit supports Android's native conversation bubbles, allowing notification threads from active chats to float dynamically above other applications.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Text("First-Time Setup Instructions:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("• Step 1: Tap \"Open Bubble Settings\" below and verify Bubbles are allowed (set to \"All conversations\" or \"Selected\").", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("• Step 2: Tap \"Send Test Bubble\" to trigger a dynamic conversation notification thread.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("• Step 3: Swipe down your system notification tray and tap the small conversation bubble expander icon in the bottom-right corner of the Conduit notification card.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("• Step 4: Once active, the floating overlay floats on your screen and will automatically reappear for any future messages!", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        performHapticClick(context)
                                        val intent = Intent().apply {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                action = android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
                                                putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                            } else {
                                                action = "android.settings.APP_NOTIFICATION_SETTINGS"
                                                putExtra("app_package", context.packageName)
                                                putExtra("app_uid", context.applicationInfo.uid)
                                            }
                                        }
                                        try {
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            android.widget.Toast.makeText(context, "Could not open system settings.", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                                ) {
                                    Text("Open Bubble Settings", fontSize = 11.sp, maxLines = 1)
                                }
                                
                                Button(
                                    onClick = {
                                        val service = HubNotificationListenerService.instance
                                        if (service != null) {
                                            service.postBubbleNotification(
                                                context = context,
                                                packageName = "com.conduit.app",
                                                title = "Conduit Test Bubble",
                                                text = "Tap the bubble icon in the bottom-right of this notification to launch the floating overlay!"
                                            )
                                            performHapticClick(context)
                                            android.widget.Toast.makeText(context, "Test bubble notification sent!", android.widget.Toast.LENGTH_SHORT).show()
                                        } else {
                                            android.widget.Toast.makeText(context, "Notification service not running! Enable access first.", android.widget.Toast.LENGTH_LONG).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                                ) {
                                    Text("Send Test Bubble", fontSize = 11.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("Developer", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
            
            OutlinedCard(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Demo Mode", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text("Populates Conduit with fake notifications for screenshots.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = settings.demoModeEnabled,
                            onCheckedChange = { enabled ->
                                callbacks.onDemoModeEnabledChanged(enabled)
                                val database = AppDatabase.getDatabase(context)
                                scope.launch {
                                    if (enabled) {
                                        com.conduit.app.data.DemoNotificationGenerator.generate(context, database, HubNotificationListenerService.instance)
                                    } else {
                                        database.notificationDao().deleteDemoNotifications()
                                    }
                                    com.conduit.app.widget.WidgetUpdater.updateAllWidgets(context)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}






