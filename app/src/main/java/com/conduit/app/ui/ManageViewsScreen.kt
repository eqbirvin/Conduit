package com.conduit.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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

    val channels = remember { getInstalledChannels(context) }

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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(views) { index, view ->
                    val isDefault = defaultViewId == view.id
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(view.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                                Text("${view.packageNames.size} apps included", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            IconButton(
                                onClick = {
                                    performHapticClick(context)
                                    if (isDefault) {
                                        viewsRepository.setDefaultView(null)
                                    } else {
                                        viewsRepository.setDefaultView(view.id)
                                    }
                                }
                            ) {
                                Icon(
                                    if (isDefault) Icons.Filled.Star else Icons.Outlined.Star,
                                    contentDescription = "Set Default",
                                    tint = if (isDefault) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            Column {
                                IconButton(
                                    onClick = { viewsRepository.reorderViews(index, index - 1) },
                                    enabled = index > 0,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move Up")
                                }
                                IconButton(
                                    onClick = { viewsRepository.reorderViews(index, index + 1) },
                                    enabled = index < views.lastIndex,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move Down")
                                }
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

    if (showEditSheet) {
        ModalBottomSheet(
            onDismissRequest = { showEditSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            EditViewContent(
                initialView = editingView,
                availableChannels = channels,
                onSave = { name, selectedPackages ->
                    if (editingView != null) {
                        viewsRepository.updateView(editingView!!.id, name, selectedPackages)
                    } else {
                        viewsRepository.addView(name, selectedPackages)
                    }
                    showEditSheet = false
                },
                onCancel = { showEditSheet = false }
            )
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
    availableChannels: List<Pair<String, String>>,
    onSave: (String, List<String>) -> Unit,
    onCancel: () -> Unit
) {
    val pm = LocalContext.current.packageManager
    var name by remember { mutableStateOf(initialView?.name ?: "") }
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
            .padding(bottom = 24.dp)
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
        ) {
            itemsIndexed(availableChannels) { _, (prefKey, appName) ->
                val isSelected = selectedKeys.contains(prefKey)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
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

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    val packagesToSave = selectedKeys.mapNotNull { channelPackages[it] }
                    onSave(name.trim(), packagesToSave)
                },
                enabled = name.isNotBlank()
            ) {
                Text("Save")
            }
        }
    }
}
