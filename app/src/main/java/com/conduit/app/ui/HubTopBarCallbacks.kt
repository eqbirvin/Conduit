@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.conduit.app.ui

import com.conduit.app.*
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.*
import androidx.compose.animation.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import com.conduit.app.data.AppDatabase
import com.conduit.app.data.HubNotification
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.snapshots.SnapshotStateMap

@Composable
fun rememberHubTopBarCallbacks(
    context: Context,
    viewModel: HubViewModel,
    settingsViewModel: SettingsViewModel,
    notificationsState: State<List<HubNotification>>,
    unifiedView: Boolean,
    showUpdateDialogState: MutableState<Boolean>,
    masterExpandedState: Boolean,
    individualToggles: SnapshotStateMap<Int, Boolean>,
    conversationRollupStates: SnapshotStateMap<Int, ConversationRollupState>,
    notificationToSnoozeState: MutableState<HubNotification?>,
    notificationToBlockState: MutableState<HubNotification?>,
    selectedIdsState: MutableState<Set<Int>>,
    searchQueryState: MutableState<String>,
    isSearchModeState: MutableState<Boolean>,
    activeSingleConversationKeyState: MutableState<String?>,
    onNavigateToSettings: () -> Unit,
    onNavigateToManageViews: () -> Unit,
    onNavigateToArchive: () -> Unit,
    onUnifiedViewChanged: (Boolean) -> Unit
): HubTopBarCallbacks {
    val notifications by notificationsState
    var showUpdateDialog by showUpdateDialogState
    var notificationToSnooze by notificationToSnoozeState
    var notificationToBlock by notificationToBlockState
    var selectedIds by selectedIdsState
    var searchQuery by searchQueryState
    var isSearchMode by isSearchModeState
    var activeSingleConversationKey by activeSingleConversationKeyState
    val topBarCallbacks = remember(
        context, viewModel, settingsViewModel, onNavigateToSettings, onNavigateToManageViews,
        onNavigateToArchive, onUnifiedViewChanged
    ) {
        object : HubTopBarCallbacks {
            override fun onClearSelection() {
                selectedIds = emptySet()
            }
            override fun onBlockNotification(notification: HubNotification) {
                notificationToBlock = notification
            }
            override fun onSnoozeNotification(notification: HubNotification) {
                notificationToSnooze = notification
            }
            override fun onPinSelected() {
                val idsToPin = selectedIds.toList()
                val anyUnpinned = notifications.filter { it.id in selectedIds }.any { !it.isPinned }
                viewModel.viewModelScope.launch(Dispatchers.IO) {
                    val database = AppDatabase.getDatabase(context)
                    database.notificationDao().pinNotifications(idsToPin, anyUnpinned)
                    com.conduit.app.widget.WidgetUpdater.updateAllWidgets(context)
                }
                performHapticClick(context)
                selectedIds = emptySet()
            }
            override fun onMarkSelectedAsRead() {
                val selectedNotifs = notifications.filter { it.id in selectedIds }
                if (selectedNotifs.isNotEmpty()) {
                    viewModel.markNotificationsAsRead(selectedNotifs)
                }
                performHapticClick(context)
                selectedIds = emptySet()
            }
            override fun onSearchQueryChanged(query: String) {
                searchQuery = query
                viewModel.setSearchQuery(query)
            }
            override fun onExitSearch() {
                isSearchMode = false
                searchQuery = ""
                viewModel.setSearchQuery("")
            }
            override fun onShowUpdateDialog() {
                showUpdateDialog = true
            }
            override fun onNavigateToSettings() {
                onNavigateToSettings()
            }
            override fun onNavigateToManageViews() {
                onNavigateToManageViews()
            }
            override fun onToggleCustomViewStrip() {
                val show = !settingsViewModel.settings.value.showCustomViewStrip
                settingsViewModel.updateShowCustomViewStrip(show)
                val toastText = if (show) "Custom view strip shown" else "Custom view strip hidden"
                android.widget.Toast.makeText(context, toastText, android.widget.Toast.LENGTH_SHORT).show()
            }
            override fun onCycleConversationView() {
                activeSingleConversationKey = null
                conversationRollupStates.clear()
                val nextMode = settingsViewModel.cycleConversationViewMode()
                val toastText = when (nextMode) {
                    com.conduit.app.data.ConversationViewMode.STOCK -> "Stock View"
                    com.conduit.app.data.ConversationViewMode.COMBINE_CONVERSATIONS -> "Combine Conversations"
                    com.conduit.app.data.ConversationViewMode.SINGLE_CONVERSATION -> "Single Conversation View"
                }
                android.widget.Toast.makeText(context, toastText, android.widget.Toast.LENGTH_SHORT).show()
            }
            override fun onToggleMasterExpand() {
                val newState = !masterExpandedState
                settingsViewModel.updateMasterExpandedState(newState)
                individualToggles.clear()
                conversationRollupStates.clear()
            }
            override fun onNavigateToArchive() {
                onNavigateToArchive()
            }
            override fun onToggleUnifiedView() {
                onUnifiedViewChanged(!unifiedView)
            }
            override fun onEnterSearch() {
                isSearchMode = true
                searchQuery = ""
                viewModel.setSearchQuery("")
            }
        }
    }
    return topBarCallbacks
}
