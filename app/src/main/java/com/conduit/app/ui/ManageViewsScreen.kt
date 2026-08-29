package com.conduit.app.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.conduit.app.HubViewModel
import com.conduit.app.data.CustomView
import com.conduit.app.getInstalledChannels
import com.conduit.app.performHapticClick
import com.conduit.app.HubNotificationListenerService
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorder
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageViewsScreen(
    hubViewModel: HubViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val viewsRepository = hubViewModel.viewsRepository
    val views by viewsRepository.views.collectAsStateWithLifecycle()
    val defaultViewId by viewsRepository.defaultViewId.collectAsStateWithLifecycle()
    val activeViewId by hubViewModel.activeViewId.collectAsStateWithLifecycle()

    var showEditSheet by remember { mutableStateOf(false) }
    var editingView by remember { mutableStateOf<CustomView?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf<CustomView?>(null) }

    val settingsViewModel: com.conduit.app.SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = com.conduit.app.SettingsViewModel.Factory(com.conduit.app.data.SettingsRepository(context.getSharedPreferences("conduit_prefs", android.content.Context.MODE_PRIVATE)))
    )
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

    val channels = remember(settings.channelStates) {
        getInstalledChannels(context).filter { settings.channelStates[it.first] == true }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Views", fontSize = 22.sp, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (views.isNotEmpty()) {
                        IconButton(onClick = {
                            editingView = null
                            showEditSheet = true
                        }) {
                            Icon(Icons.Filled.Add, contentDescription = "New View")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
            )
        }
    ) { padding ->
        if (views.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(Icons.Filled.FilterList, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No Custom Views", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Create custom views to filter your notification feed to specific apps.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = {
                        editingView = null
                        showEditSheet = true
                    }) {
                        Text("Create View")
                    }
                }
            }
        } else {
            val state = rememberReorderableLazyListState(onMove = { from, to ->
                viewsRepository.reorderViews(from.index, to.index)
            })
            
            LazyColumn(
                state = state.listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .reorderable(state),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(views, key = { it.id }) { view ->
                    ReorderableItem(state, key = view.id) { isDragging ->
                        val elevation = if (isDragging) 8.dp else 0.dp
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDragging) 0.8f else 0.5f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = elevation)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.DragHandle,
                                    contentDescription = "Drag to reorder",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .padding(end = 12.dp)
                                        .detectReorder(state)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(view.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                                    Text("${view.packageNames.size} apps included", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                IconButton(onClick = {
                                    editingView = view
                                    showEditSheet = true
                                }) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Edit View")
                                }

                                IconButton(onClick = {
                                    showDeleteConfirmDialog = view
                                }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete View", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditSheet) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showEditSheet = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .wrapContentHeight(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)
            ) {
                EditViewContent(
                initialView = editingView,
                initialIsDefault = editingView != null && editingView?.id == defaultViewId,
                availableChannels = channels,
                onSave = { name, selectedPackages, isDefault, filterDock ->
                    val viewId = if (editingView != null) {
                        viewsRepository.updateView(editingView!!.id, name, selectedPackages, filterDock)
                        editingView!!.id
                    } else {
                        viewsRepository.addView(name, selectedPackages, filterDock)
                    }
                    if (isDefault) {
                        viewsRepository.setDefaultView(viewId)
                    } else if (defaultViewId == viewId) {
                        viewsRepository.setDefaultView(null)
                    }
                    showEditSheet = false
                },
                onDelete = {
                    showDeleteConfirmDialog = editingView
                    showEditSheet = false
                },
                onCancel = { showEditSheet = false }
            )
            }
        }
    }

    if (showDeleteConfirmDialog != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            title = { Text("Delete View") },
            text = { Text("Are you sure you want to delete '${showDeleteConfirmDialog?.name}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog?.let { viewToDelete ->
                            viewsRepository.deleteView(viewToDelete.id)
                            if (activeViewId == viewToDelete.id) {
                                hubViewModel.setActiveViewId(null)
                            }
                        }
                        showDeleteConfirmDialog = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun EditViewContent(
    initialView: CustomView?,
    initialIsDefault: Boolean,
    availableChannels: List<Pair<String, String>>,
    onSave: (String, List<String>, Boolean, Boolean) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit
) {
    var name by remember { mutableStateOf(initialView?.name ?: "") }
    var isDefault by remember { mutableStateOf(initialIsDefault) }
    var filterDock by remember { mutableStateOf(initialView?.filterDock ?: false) }
    val initialPackages = remember { initialView?.packageNames ?: emptyList() }
    
    // We map the prefKey to its representative package name to store in the view
    // Since HubNotificationListenerService.supportedApps.values are (prefKey, Name)
    val channelPackages = remember(availableChannels) {
        availableChannels.associate { (prefKey, _) ->
            val pkgName = HubNotificationListenerService.supportedApps.entries
                .firstOrNull { it.value.first == prefKey }?.key ?: ""
            prefKey to pkgName
        }.filterValues { it.isNotEmpty() }
    }

    var selectedKeys by remember { 
        mutableStateOf(
            availableChannels.filter { (prefKey, _) -> 
                val pkgName = channelPackages[prefKey]
                pkgName != null && initialPackages.contains(pkgName)
            }.map { it.first }.toSet()
        ) 
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 24.dp, bottom = 24.dp)
    ) {
        Text(
            text = if (initialView != null) "Edit View" else "New View",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        OutlinedTextField(
            value = name,
            onValueChange = { if (it.length <= 32) name = it },
            label = { Text("View Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = name.isBlank(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words
            )
        )
        
        Text(
            text = "${name.length}/32",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.align(Alignment.End).padding(top = 4.dp, bottom = 16.dp)
        )

        Text("Included Apps", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .heightIn(max = 300.dp)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(8.dp)
                ),
            contentPadding = PaddingValues(8.dp)
        ) {
            itemsIndexed(availableChannels) { _, (prefKey, appName) ->
                val isSelected = selectedKeys.contains(prefKey)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 0.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { checked ->
                            if (checked) {
                                selectedKeys = selectedKeys + prefKey
                            } else {
                                selectedKeys = selectedKeys - prefKey
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(appName, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Set as Default View", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = isDefault, onCheckedChange = { isDefault = it })
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Filter App Dock", style = MaterialTheme.typography.bodyLarge)
                Text("Only show included apps in the dock when active", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = filterDock, onCheckedChange = { filterDock = it })
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (initialView != null) {
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete View")
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    val packagesToSave = selectedKeys.mapNotNull { channelPackages[it] }
                    onSave(name.trim(), packagesToSave, isDefault, filterDock)
                },
                enabled = name.isNotBlank()
            ) {
                Text("Save")
            }
        }
    }
}
