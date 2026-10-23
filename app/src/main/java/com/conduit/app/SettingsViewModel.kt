package com.conduit.app

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.conduit.app.data.ConduitSettings
import com.conduit.app.data.ConversationViewMode
import com.conduit.app.data.HiddenTextRule
import com.conduit.app.data.SettingsRepository
import com.conduit.app.data.updateHiddenTextRules
import com.conduit.app.data.updateHideTopBar
import com.conduit.app.data.updateShowCustomViewStrip
import com.conduit.app.data.updateShowDateMarkAllRead
import com.conduit.app.data.updateTopBarActionsInDock
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    val settings: StateFlow<ConduitSettings> = repository.settings

    fun updateTheme(theme: Int) = viewModelScope.launch { repository.updateTheme(theme) }
    fun updateJacobMonochrome(enabled: Boolean) = viewModelScope.launch { repository.updateJacobMonochrome(enabled) }
    fun updateJacobFollowSystemDark(enabled: Boolean) = viewModelScope.launch { repository.updateJacobFollowSystemDark(enabled) }
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
    fun updateTriggerNativeMarkRead(enabled: Boolean) = viewModelScope.launch { repository.updateTriggerNativeMarkRead(enabled) }
    fun updateFabConfigs(configs: List<FabAction>) = viewModelScope.launch { repository.updateFabConfigs(configs) }
    fun updateAiBundle(bundle: List<String>) = viewModelScope.launch { repository.updateAiBundle(bundle) }
    fun updateNotesBundle(bundle: List<String>) = viewModelScope.launch { repository.updateNotesBundle(bundle) }
    fun updateRecorderBundle(bundle: List<String>) = viewModelScope.launch { repository.updateRecorderBundle(bundle) }
    fun updateComposeBundle(bundle: List<String>) = viewModelScope.launch { repository.updateComposeBundle(bundle) }
    fun updateChannelState(prefKey: String, isEnabled: Boolean) = viewModelScope.launch { repository.updateChannelState(prefKey, isEnabled) }
    fun updateRetentionDays(days: Int) = viewModelScope.launch { repository.updateRetentionDays(days) }
    fun updateEnableAppBundles(enabled: Boolean, context: android.content.Context? = null) = viewModelScope.launch {
        repository.updateEnableAppBundles(enabled)
        if (enabled && context != null) {
            repository.seedDefaultAppBundlesIfFirstTime(context)
        }
    }
    fun seedDefaultAppBundlesIfFirstTime(context: android.content.Context) = viewModelScope.launch {
        repository.seedDefaultAppBundlesIfFirstTime(context)
    }
    fun updateBundleMenuSizing(sizing: String) = viewModelScope.launch { repository.updateBundleMenuSizing(sizing) }
    fun updateDockExpandPosition(position: String) = viewModelScope.launch { repository.updateDockExpandPosition(position) }
    fun updateTransparentBundlePills(enabled: Boolean) = viewModelScope.launch { repository.updateTransparentBundlePills(enabled) }
    fun updateBundleMenuBlur(enabled: Boolean) = viewModelScope.launch { repository.updateBundleMenuBlur(enabled) }
    fun updateMinimizeIcons(enabled: Boolean) = viewModelScope.launch { repository.updateMinimizeIcons(enabled) }
    fun updateAutoCollapseRead(enabled: Boolean) = viewModelScope.launch { repository.updateAutoCollapseRead(enabled) }
    fun updateAutoDismissDetached(enabled: Boolean) = viewModelScope.launch { repository.updateAutoDismissDetached(enabled) }
    fun updateUpdateInterval(interval: String) = viewModelScope.launch { repository.updateUpdateInterval(interval) }
    fun updateUpdateAvailableState(hasUpdate: Boolean, latestVersion: String) = viewModelScope.launch { repository.updateUpdateAvailableState(hasUpdate, latestVersion) }
    fun updateDefaultToTodoMode(enabled: Boolean) = viewModelScope.launch { repository.updateDefaultToTodoMode(enabled) }
    fun updateDemoModeEnabled(enabled: Boolean) = viewModelScope.launch { repository.updateDemoModeEnabled(enabled) }
    fun updateIngestionDiagnosticsEnabled(enabled: Boolean) = viewModelScope.launch { repository.updateIngestionDiagnosticsEnabled(enabled) }
    fun updateCustomViewsExpanded(enabled: Boolean) = viewModelScope.launch { repository.updateCustomViewsExpanded(enabled) }
    fun updateCombineConversations(enabled: Boolean) = viewModelScope.launch { repository.updateCombineConversations(enabled) }
    fun updateSingleConversationView(enabled: Boolean) = viewModelScope.launch { repository.updateSingleConversationView(enabled) }
    fun updateScrollTopBarWithFeed(enabled: Boolean) = viewModelScope.launch { repository.updateScrollTopBarWithFeed(enabled) }
    fun updateTopBarScrollBehavior(behavior: String) = viewModelScope.launch { repository.updateTopBarScrollBehavior(behavior) }
    fun updateHideTopBar(hidden: Boolean) = viewModelScope.launch { repository.updateHideTopBar(hidden) }
    fun updateTopBarActionsInDock(enabled: Boolean) = viewModelScope.launch { repository.updateTopBarActionsInDock(enabled) }
    fun updateShowCustomViewStrip(enabled: Boolean) = viewModelScope.launch { repository.updateShowCustomViewStrip(enabled) }
    fun updateShowDateMarkAllRead(enabled: Boolean) = viewModelScope.launch { repository.updateShowDateMarkAllRead(enabled) }
    fun saveHiddenTextRule(rule: HiddenTextRule) = viewModelScope.launch {
        val rules = repository.settings.value.hiddenTextRules
        val updated = if (rules.any { it.id == rule.id }) rules.map { if (it.id == rule.id) rule else it } else rules + rule
        repository.updateHiddenTextRules(updated)
    }
    fun deleteHiddenTextRule(id: String) = viewModelScope.launch {
        repository.updateHiddenTextRules(repository.settings.value.hiddenTextRules.filter { it.id != id })
    }
    fun cycleConversationViewMode(): ConversationViewMode = repository.cycleConversationViewMode()

    class Factory(private val repository: SettingsRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return SettingsViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

