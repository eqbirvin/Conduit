package com.conduit.app.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import com.conduit.app.DEFAULT_FABS
import com.conduit.app.FabAction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevSettingsScreen(
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
    fabConfigs: List<FabAction>,
    onSaveFabConfigs: (List<FabAction>) -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var editingFab by remember { mutableStateOf<FabAction?>(null) }
    var showAddFabDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Developer & Lab Settings", fontSize = 20.sp, fontWeight = FontWeight.Bold) },
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
            Text("Floating Action Button (FAB) Customization", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Configure Menu Actions", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    fabConfigs.forEachIndexed { index, fab ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${index + 1}.", fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
                                Text(fab.label, style = MaterialTheme.typography.bodyMedium)
                            }
                            Row {
                                IconButton(onClick = { editingFab = fab }) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Edit FAB", modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = {
                                    val updated = fabConfigs.filter { it.id != fab.id }
                                    onSaveFabConfigs(updated)
                                }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete FAB", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                        if (index < fabConfigs.size - 1) Divider()
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { onSaveFabConfigs(DEFAULT_FABS) }) {
                            Text("Reset to Defaults")
                        }
                        Button(onClick = { showAddFabDialog = true }) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Custom FAB")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("Experimental Hangar Bracket (Side Tab)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Enable Hangar Bracket Overlay", style = MaterialTheme.typography.bodyLarge)
                    Text("Draw side tab edge handle on screen", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = enableBracket, onCheckedChange = onEnableBracketChanged)
            }

            if (enableBracket) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Bracket Notification Popups", style = MaterialTheme.typography.bodyLarge)
                        Text("Show toast banner when new notification arrives", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = bracketNotificationPopup, onCheckedChange = onBracketNotificationPopupChanged)
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Show Hanger Dock Icons", style = MaterialTheme.typography.bodyLarge)
                        Text("Display unread app icon dock in side tab overlay", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = bracketHangerEnabled, onCheckedChange = onBracketHangerEnabledChanged)
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Vertical Position Ratio (${(bracketVerticalPosition * 100).toInt()}%)", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = bracketVerticalPosition,
                    onValueChange = onBracketVerticalPositionChanged,
                    valueRange = 0.1f..0.9f
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("System Floating Bubbles", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Enable Android Bubble Windows", style = MaterialTheme.typography.bodyLarge)
                    Text("Allow opening notifications in floating bubble windows", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = enableBubbles, onCheckedChange = onEnableBubblesChanged)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
