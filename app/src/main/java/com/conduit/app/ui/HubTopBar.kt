package com.conduit.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Settings as SettingsIcon
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conduit.app.data.HubNotification
import com.conduit.app.data.ConversationViewMode
import com.conduit.app.performHapticClick
import com.conduit.app.performHapticTick

data class HubTopBarState(
    val isSelectionMode: Boolean = false,
    val isSearchMode: Boolean = false,
    val selectedCount: Int = 0,
    val selectedNotification: HubNotification? = null,
    val searchQuery: String = "",
    val hasUpdateAvailable: Boolean = false,
    val masterExpandedState: Boolean = false,
    val unifiedView: Boolean = true,
    val conversationViewMode: ConversationViewMode = ConversationViewMode.COMBINE_CONVERSATIONS
)

interface HubTopBarCallbacks {
    fun onClearSelection()
    fun onBlockNotification(notification: HubNotification)
    fun onSnoozeNotification(notification: HubNotification)
    fun onPinSelected()
    fun onMarkSelectedAsRead()
    fun onSearchQueryChanged(query: String)
    fun onExitSearch()
    fun onShowUpdateDialog()
    fun onNavigateToSettings()
    fun onNavigateToManageViews()
    fun onCycleConversationView()
    fun onToggleMasterExpand()
    fun onNavigateToArchive()
    fun onToggleUnifiedView()
    fun onEnterSearch()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubTopBar(
    state: HubTopBarState,
    callbacks: HubTopBarCallbacks,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Box(modifier = modifier) {
        if (state.isSelectionMode) {
            TopAppBar(
                title = { Text("${state.selectedCount} selected", fontSize = 20.sp, fontWeight = FontWeight.Medium) },
                navigationIcon = {
                    IconButton(onClick = callbacks::onClearSelection) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear selection")
                    }
                },
                actions = {
                    if (state.selectedCount == 1 && state.selectedNotification != null) {
                        IconButton(onClick = { callbacks.onBlockNotification(state.selectedNotification) }) {
                            Icon(Icons.Filled.Block, contentDescription = "Block notification")
                        }

                        IconButton(onClick = { callbacks.onSnoozeNotification(state.selectedNotification) }) {
                            Icon(Icons.Filled.Snooze, contentDescription = "Snooze notification")
                        }

                        Box(
                            modifier = Modifier
                                .height(24.dp)
                                .width(1.dp)
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    IconButton(onClick = callbacks::onPinSelected) {
                        Icon(Icons.Filled.PushPin, contentDescription = "Pin/Unpin selected")
                    }

                    IconButton(onClick = callbacks::onMarkSelectedAsRead) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = "Mark selected as read")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        } else if (state.isSearchMode) {
            TopAppBar(
                title = {
                    TextField(
                        value = state.searchQuery,
                        onValueChange = callbacks::onSearchQueryChanged,
                        placeholder = { Text("Search notifications...") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        singleLine = true,
                        leadingIcon = {
                            IconButton(onClick = {
                                performHapticTick(context)
                                callbacks.onExitSearch()
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Exit search")
                            }
                        },
                        trailingIcon = {
                            if (state.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { callbacks.onSearchQueryChanged("") }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                                }
                            }
                        }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        } else {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.hasUpdateAvailable) {
                            IconButton(
                                onClick = {
                                    performHapticClick(context)
                                    callbacks.onShowUpdateDialog()
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Filled.SystemUpdate,
                                    contentDescription = "Update Available",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Conduit", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    }
                },
                navigationIcon = {
                    Row {
                        IconButton(onClick = {
                            performHapticTick(context)
                            callbacks.onNavigateToSettings()
                        }) {
                            Icon(Icons.Filled.SettingsIcon, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        IconButton(onClick = {
                            performHapticTick(context)
                            callbacks.onNavigateToManageViews()
                        }) {
                            Icon(Icons.Filled.FilterList, contentDescription = "Manage Views", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        IconButton(onClick = {
                            performHapticTick(context)
                            callbacks.onCycleConversationView()
                        }) {
                            val (icon, description) = when (state.conversationViewMode) {
                                ConversationViewMode.STOCK -> Icons.Filled.ViewStream to "Stock View"
                                ConversationViewMode.COMBINE_CONVERSATIONS -> Icons.Filled.Forum to "Combine Conversations"
                                ConversationViewMode.SINGLE_CONVERSATION -> Icons.AutoMirrored.Filled.Chat to "Single Conversation View"
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = "Conversation View: $description (tap to cycle)",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        performHapticClick(context)
                        callbacks.onToggleMasterExpand()
                    }) {
                        Icon(
                            imageVector = if (state.masterExpandedState) Icons.Filled.Compress else Icons.Filled.Expand,
                            contentDescription = "Toggle Master Expand",
                            tint = if (state.masterExpandedState) MaterialTheme.colorScheme.secondary else LocalContentColor.current
                        )
                    }

                    if (!state.unifiedView) {
                        IconButton(onClick = callbacks::onNavigateToArchive) {
                            Icon(Icons.Filled.History, contentDescription = "Actioned")
                        }
                    }

                    IconButton(onClick = {
                        performHapticTick(context)
                        callbacks.onToggleUnifiedView()
                    }) {
                        Icon(
                            if (state.unifiedView) Icons.Filled.Checklist else Icons.Filled.DynamicFeed,
                            contentDescription = if (state.unifiedView) "Switch to Todo Mode" else "Switch to Unified View"
                        )
                    }

                    IconButton(onClick = {
                        performHapticTick(context)
                        callbacks.onEnterSearch()
                    }) {
                        Icon(Icons.Filled.Search, contentDescription = "Search")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}
