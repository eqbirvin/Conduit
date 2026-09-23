package com.conduit.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.conduit.app.getAppLabel
import com.conduit.app.getInstalledApps
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorder
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BundleManagementBottomSheet(
    bundleTitle: String,
    currentApps: List<String>,
    onUpdateApps: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var showAppPicker by remember { mutableStateOf(false) }
    val isComposeBundle = remember(bundleTitle) { bundleTitle.equals("Compose", ignoreCase = true) }

    // Maintain local state for smooth continuous drag-reordering
    var appList by remember(currentApps) { mutableStateOf(currentApps.distinct()) }

    val reorderState = rememberReorderableLazyListState(onMove = { from, to ->
        val updated = appList.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
        appList = updated
        onUpdateApps(updated)
    })

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(bundleTitle, style = MaterialTheme.typography.headlineSmall)
                FilledTonalButton(onClick = { showAppPicker = true }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isComposeBundle) "Add Action" else "Add App")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (appList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (isComposeBundle) "No compose actions configured" else "No apps in this bundle",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    text = "Drag handles to reorder",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyColumn(
                    state = reorderState.listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .reorderable(reorderState),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(appList, key = { it }) { pkg ->
                        ReorderableItem(reorderState, key = pkg) { isDragging ->
                            val elevation = if (isDragging) 8.dp else 0.dp
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                color = if (isDragging) {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                },
                                shadowElevation = elevation
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.DragHandle,
                                        contentDescription = "Drag to reorder",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .padding(horizontal = 8.dp)
                                            .detectReorder(reorderState)
                                    )

                                    if (pkg.startsWith("conduit.action.")) {
                                        ComposeActionIcon(action = pkg, size = 32.dp)
                                    } else {
                                        AppIcon(packageName = pkg, size = 32.dp)
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    val label = remember(pkg) {
                                        if (pkg.startsWith("conduit.action.")) getComposeActionLabel(pkg)
                                        else getAppLabel(context, pkg)
                                    }

                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f)
                                    )

                                    IconButton(
                                        onClick = {
                                            val updated = appList.filter { it != pkg }
                                            appList = updated
                                            onUpdateApps(updated)
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Remove",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        if (showAppPicker) {
            ModalBottomSheet(onDismissRequest = { showAppPicker = false }) {
                var searchQuery by remember { mutableStateOf("") }
                val allComposeActions = remember {
                    listOf(
                        "conduit.action.SMS" to "New Text",
                        "conduit.action.EMAIL" to "New Email",
                        "conduit.action.DIAL" to "New Call",
                        "conduit.action.CONTACT" to "New Contact",
                        "conduit.action.NOTE" to "New Note",
                        "conduit.action.EVENT" to "New Event",
                        "conduit.action.TIMER" to "Set Timer",
                        "conduit.action.ALARM" to "Set Alarm",
                        "conduit.action.RECORD" to "Quick Record"
                    )
                }

                // If compose bundle, strictly restrict available options to native compose actions
                val baseItems = remember(isComposeBundle) {
                    if (isComposeBundle) allComposeActions else getInstalledApps(context)
                }

                val filteredItems by remember(searchQuery, baseItems) {
                    derivedStateOf {
                        if (searchQuery.isEmpty()) baseItems
                        else baseItems.filter {
                            it.second.contains(searchQuery, ignoreCase = true) ||
                            it.first.contains(searchQuery, ignoreCase = true)
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        if (isComposeBundle) "Select Compose Action" else "Select App",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(if (isComposeBundle) "Search actions..." else "Search apps...")
                        },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(modifier = Modifier.weight(1f, fill = false).heightIn(max = 400.dp)) {
                        items(filteredItems, key = { it.first }) { (pkg, label) ->
                            val isAdded = currentApps.contains(pkg)
                            ListItem(
                                headlineContent = { Text(label) },
                                supportingContent = {
                                    Text(
                                        if (pkg.startsWith("conduit.action.")) "Compose Action" else pkg,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                leadingContent = {
                                    if (pkg.startsWith("conduit.action.")) {
                                        ComposeActionIcon(action = pkg, size = 32.dp)
                                    } else {
                                        AppIcon(packageName = pkg, size = 32.dp)
                                    }
                                },
                                trailingContent = {
                                    if (isAdded) {
                                        Icon(Icons.Default.Check, contentDescription = "Added", tint = MaterialTheme.colorScheme.primary)
                                    }
                                },
                                modifier = Modifier.clickable(enabled = !isAdded) {
                                    onUpdateApps(currentApps + pkg)
                                    showAppPicker = false
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}
