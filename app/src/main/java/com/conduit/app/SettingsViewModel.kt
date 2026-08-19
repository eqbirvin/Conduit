package com.conduit.app

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.conduit.app.data.ConduitSettings
import com.conduit.app.data.SettingsRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    val settings: StateFlow<ConduitSettings> = repository.settings

    fun updateTheme(theme: Int) = viewModelScope.launch { repository.updateTheme(theme) }
    fun updateJacobMonochrome(enabled: Boolean) = viewModelScope.launch { repository.updateJacobMonochrome(enabled) }
    fun updateGroupByChannel(enabled: Boolean) = viewModelScope.launch { repository.updateGroupByChannel(enabled) }
    fun updatePersistentTrayEnabled(enabled: Boolean) = viewModelScope.launch { repository.updatePersistentTrayEnabled(enabled) }
    fun updateMasterExpandedState(enabled: Boolean) = viewModelScope.launch { repository.updateMasterExpandedState(enabled) }
    fun updateSyncDismissal(enabled: Boolean) = viewModelScope.launch { repository.updateSyncDismissal(enabled) }
    fun updateShowActionChips(enabled: Boolean) = viewModelScope.launch { repository.updateShowActionChips(enabled) }
    fun updateSyncPinned(enabled: Boolean) = viewModelScope.launch { repository.updateSyncPinned(enabled) }
    fun updateDockLongPressLaunch(enabled: Boolean) = viewModelScope.launch { repository.updateDockLongPressLaunch(enabled) }
    fun updateDockScrollIndicator(indicator: String) = viewModelScope.launch { repository.updateDockScrollIndicator(indicator) }
    fun updateSwipeLeftAction(action: String) = viewModelScope.launch { repository.updateSwipeLeftAction(action) }
    fun updateSwipeRightAction(action: String) = viewModelScope.launch { repository.updateSwipeRightAction(action) }
    fun updateDockSize(size: Int) = viewModelScope.launch { repository.updateDockSize(size) }
    fun updateEnableBubbles(enabled: Boolean) = viewModelScope.launch { repository.updateEnableBubbles(enabled) }
    fun updateEnableBracket(enabled: Boolean) = viewModelScope.launch { repository.updateEnableBracket(enabled) }
    fun updateBracketNotificationPopup(enabled: Boolean) = viewModelScope.launch { repository.updateBracketNotificationPopup(enabled) }
    fun updateBracketHangerEnabled(enabled: Boolean) = viewModelScope.launch { repository.updateBracketHangerEnabled(enabled) }
    fun updateBracketVerticalPosition(position: Float) = viewModelScope.launch { repository.updateBracketVerticalPosition(position) }
    fun updateUnifiedView(enabled: Boolean) = viewModelScope.launch { repository.updateUnifiedView(enabled) }
    fun updateActiveAppIcon(icon: String) = viewModelScope.launch { repository.updateActiveAppIcon(icon) }
    fun updateSmartMarkRead(enabled: Boolean) = viewModelScope.launch { repository.updateSmartMarkRead(enabled) }
    fun updateSmartMarkReadTarget(target: String) = viewModelScope.launch { repository.updateSmartMarkReadTarget(target) }
    fun updateFabConfigs(configs: List<FabAction>) = viewModelScope.launch { repository.updateFabConfigs(configs) }
    fun updateAiBundle(bundle: List<String>) = viewModelScope.launch { repository.updateAiBundle(bundle) }
    fun updateNotesBundle(bundle: List<String>) = viewModelScope.launch { repository.updateNotesBundle(bundle) }
    fun updateRecorderBundle(bundle: List<String>) = viewModelScope.launch { repository.updateRecorderBundle(bundle) }
    fun updateComposeBundle(bundle: List<String>) = viewModelScope.launch { repository.updateComposeBundle(bundle) }
    fun updateChannelState(prefKey: String, isEnabled: Boolean) = viewModelScope.launch { repository.updateChannelState(prefKey, isEnabled) }
    fun updateRetentionDays(days: Int) = viewModelScope.launch { repository.updateRetentionDays(days) }
    fun updateEnableAppBundles(enabled: Boolean) = viewModelScope.launch { repository.updateEnableAppBundles(enabled) }
    fun updateMinimizeIcons(enabled: Boolean) = viewModelScope.launch { repository.updateMinimizeIcons(enabled) }
    fun updateAutoCollapseRead(enabled: Boolean) = viewModelScope.launch { repository.updateAutoCollapseRead(enabled) }
    fun updateAutoDismissDetached(enabled: Boolean) = viewModelScope.launch { repository.updateAutoDismissDetached(enabled) }

    class Factory(private val prefs: SharedPreferences) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return SettingsViewModel(SettingsRepository(prefs)) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

