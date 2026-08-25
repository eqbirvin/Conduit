package com.conduit.app.data

import android.content.SharedPreferences
import com.conduit.app.DEFAULT_FABS
import com.conduit.app.FabAction
import com.conduit.app.HubNotificationListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class SettingsRepository(private val prefs: SharedPreferences) {

    private val _settings = MutableStateFlow(loadInitialSettings())
    val settings: StateFlow<ConduitSettings> = _settings.asStateFlow()

    private fun loadInitialSettings(): ConduitSettings {
        val channelStates = mutableMapOf<String, Boolean>()
        HubNotificationListenerService.supportedApps.values.forEach { (prefKey, _) ->
            val defaultVal = if (prefKey == "channel_airbnb") false else true
            channelStates[prefKey] = prefs.getBoolean(prefKey, defaultVal)
        }

        val fabConfigsJson = prefs.getString("fab_configs", null)
        val fabConfigs = if (fabConfigsJson != null) {
            try {
                fabConfigsJson.split("|").map {
                    val parts = it.split(",")
                    FabAction(parts[0], parts[1], parts[2], parts[3], parts[4])
                }
            } catch (e: Exception) { DEFAULT_FABS }
        } else { DEFAULT_FABS }

        fun getBundle(key: String, default: String): List<String> {
            return prefs.getString(key, default)?.split(",")?.filter { it.isNotEmpty() } 
                ?: default.split(",").filter { it.isNotEmpty() }
        }

        return ConduitSettings(
            themePreference = prefs.getInt("theme", 0),
            jacobMonochrome = prefs.getBoolean("jacob_monochrome", false),
            groupByChannel = prefs.getBoolean("group_by_channel", false),
            persistentTrayEnabled = prefs.getBoolean("persistent_tray_enabled", false),
            syncDismissal = prefs.getBoolean("sync_dismissal", true),
            showActionChips = prefs.getBoolean("show_action_chips", true),
            syncPinned = prefs.getBoolean("sync_pinned", false),
            dockLongPressLaunch = prefs.getBoolean("dock_long_press_launch", true),
            dockScrollIndicator = prefs.getString("dock_scroll_indicator", "FADING_EDGES") ?: "FADING_EDGES",
            swipeLeftAction = prefs.getString("swipe_left_action", "SNOOZE") ?: "SNOOZE",
            swipeRightAction = prefs.getString("swipe_right_action", "ARCHIVE") ?: "ARCHIVE",
            dockSizeIndex = prefs.getInt("dock_size", 1),
            enableBubbles = prefs.getBoolean("enable_bubbles", false),
            enableBracket = prefs.getBoolean("enable_bracket", false),
            bracketNotificationPopup = prefs.getBoolean("bracket_notification_popup", true),
            bracketHangerEnabled = prefs.getBoolean("bracket_hanger_enabled", true),
            bracketVerticalPosition = prefs.getFloat("bracket_vertical_position", 0.5f),
            unifiedView = prefs.getBoolean("unified_view", true),
            activeAppIcon = prefs.getString("active_app_icon", "MANILA") ?: "MANILA",
            smartMarkRead = prefs.getBoolean("smart_mark_read", true),
            smartMarkReadTarget = prefs.getString("smart_mark_read_target", "widget_only") ?: "widget_only",
            fabConfigs = fabConfigs,
            aiBundle = getBundle("ai_bundle", "com.anthropic.claude,com.google.android.apps.bard"),
            notesBundle = getBundle("notes_bundle", "com.google.android.keep,com.notion.id"),
            recorderBundle = getBundle("recorder_bundle", "com.google.android.apps.recorder"),
            composeBundle = getBundle("compose_bundle", "com.google.android.apps.messaging,com.google.android.gm"),
            channelStates = channelStates,
            retentionDays = prefs.getInt("retention_days", 90),
            enableAppBundles = prefs.getBoolean("enable_app_bundles", false),
            minimizeIcons = prefs.getBoolean("minimize_icons", false),
            masterExpandedState = prefs.getBoolean("master_expanded_state", true),
            autoCollapseRead = prefs.getBoolean("auto_collapse_read", false),
            autoDismissDetached = prefs.getBoolean("auto_dismiss_detached", true),
            updateInterval = prefs.getString("update_interval", "DAILY") ?: "DAILY",
            hasUpdateAvailable = prefs.getBoolean("has_update_available", false),
            latestVersionAvailable = prefs.getString("latest_version_available", "") ?: ""
        )
    }

    fun updateTheme(theme: Int) {
        prefs.edit().putInt("theme", theme).apply()
        _settings.update { it.copy(themePreference = theme) }
    }

    fun updateJacobMonochrome(enabled: Boolean) {
        prefs.edit().putBoolean("jacob_monochrome", enabled).apply()
        _settings.update { it.copy(jacobMonochrome = enabled) }
    }

    fun updateGroupByChannel(enabled: Boolean) {
        prefs.edit().putBoolean("group_by_channel", enabled).apply()
        _settings.update { it.copy(groupByChannel = enabled) }
    }

    fun updatePersistentTrayEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("persistent_tray_enabled", enabled).apply()
        _settings.update { it.copy(persistentTrayEnabled = enabled) }
    }

    fun updateMasterExpandedState(enabled: Boolean) {
        prefs.edit().putBoolean("master_expanded_state", enabled).apply()
        _settings.update { it.copy(masterExpandedState = enabled) }
    }

    fun updateSyncDismissal(enabled: Boolean) {
        prefs.edit().putBoolean("sync_dismissal", enabled).apply()
        _settings.update { it.copy(syncDismissal = enabled) }
    }

    fun updateShowActionChips(enabled: Boolean) {
        prefs.edit().putBoolean("show_action_chips", enabled).apply()
        _settings.update { it.copy(showActionChips = enabled) }
    }

    fun updateSyncPinned(enabled: Boolean) {
        prefs.edit().putBoolean("sync_pinned", enabled).apply()
        _settings.update { it.copy(syncPinned = enabled) }
    }

    fun updateDockLongPressLaunch(enabled: Boolean) {
        prefs.edit().putBoolean("dock_long_press_launch", enabled).apply()
        _settings.update { it.copy(dockLongPressLaunch = enabled) }
    }

    fun updateDockScrollIndicator(indicator: String) {
        prefs.edit().putString("dock_scroll_indicator", indicator).apply()
        _settings.update { it.copy(dockScrollIndicator = indicator) }
    }

    fun updateSwipeLeftAction(action: String) {
        prefs.edit().putString("swipe_left_action", action).apply()
        _settings.update { it.copy(swipeLeftAction = action) }
    }

    fun updateSwipeRightAction(action: String) {
        prefs.edit().putString("swipe_right_action", action).apply()
        _settings.update { it.copy(swipeRightAction = action) }
    }

    fun updateDockSize(size: Int) {
        prefs.edit().putInt("dock_size", size).apply()
        _settings.update { it.copy(dockSizeIndex = size) }
    }

    fun updateEnableBubbles(enabled: Boolean) {
        prefs.edit().putBoolean("enable_bubbles", enabled).apply()
        _settings.update { it.copy(enableBubbles = enabled) }
    }

    fun updateEnableBracket(enabled: Boolean) {
        prefs.edit().putBoolean("enable_bracket", enabled).apply()
        _settings.update { it.copy(enableBracket = enabled) }
    }

    fun updateBracketNotificationPopup(enabled: Boolean) {
        prefs.edit().putBoolean("bracket_notification_popup", enabled).apply()
        _settings.update { it.copy(bracketNotificationPopup = enabled) }
    }

    fun updateBracketHangerEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("bracket_hanger_enabled", enabled).apply()
        _settings.update { it.copy(bracketHangerEnabled = enabled) }
    }

    fun updateBracketVerticalPosition(position: Float) {
        prefs.edit().putFloat("bracket_vertical_position", position).apply()
        _settings.update { it.copy(bracketVerticalPosition = position) }
    }

    fun updateUnifiedView(enabled: Boolean) {
        prefs.edit().putBoolean("unified_view", enabled).apply()
        _settings.update { it.copy(unifiedView = enabled) }
    }

    fun updateActiveAppIcon(icon: String) {
        prefs.edit().putString("active_app_icon", icon).apply()
        _settings.update { it.copy(activeAppIcon = icon) }
    }

    fun updateSmartMarkRead(enabled: Boolean) {
        prefs.edit().putBoolean("smart_mark_read", enabled).apply()
        _settings.update { it.copy(smartMarkRead = enabled) }
    }

    fun updateSmartMarkReadTarget(target: String) {
        prefs.edit().putString("smart_mark_read_target", target).apply()
        _settings.update { it.copy(smartMarkReadTarget = target) }
    }

    fun updateFabConfigs(configs: List<FabAction>) {
        val json = configs.joinToString("|") { "${it.id},${it.label},${it.iconName},${it.type},${it.target}" }
        prefs.edit().putString("fab_configs", json).apply()
        _settings.update { it.copy(fabConfigs = configs) }
    }

    fun updateAiBundle(bundle: List<String>) {
        prefs.edit().putString("ai_bundle", bundle.joinToString(",")).apply()
        _settings.update { it.copy(aiBundle = bundle) }
    }

    fun updateNotesBundle(bundle: List<String>) {
        prefs.edit().putString("notes_bundle", bundle.joinToString(",")).apply()
        _settings.update { it.copy(notesBundle = bundle) }
    }

    fun updateRecorderBundle(bundle: List<String>) {
        prefs.edit().putString("recorder_bundle", bundle.joinToString(",")).apply()
        _settings.update { it.copy(recorderBundle = bundle) }
    }

    fun updateComposeBundle(bundle: List<String>) {
        prefs.edit().putString("compose_bundle", bundle.joinToString(",")).apply()
        _settings.update { it.copy(composeBundle = bundle) }
    }

    fun updateChannelState(prefKey: String, isEnabled: Boolean) {
        prefs.edit().putBoolean(prefKey, isEnabled).apply()
        _settings.update { current ->
            val updatedMap = current.channelStates.toMutableMap()
            updatedMap[prefKey] = isEnabled
            current.copy(channelStates = updatedMap)
        }
    }

    fun updateRetentionDays(days: Int) {
        prefs.edit().putInt("retention_days", days).apply()
        _settings.update { it.copy(retentionDays = days) }
    }

    fun updateEnableAppBundles(enabled: Boolean) {
        prefs.edit().putBoolean("enable_app_bundles", enabled).apply()
        _settings.update { it.copy(enableAppBundles = enabled) }
    }

    fun updateMinimizeIcons(enabled: Boolean) {
        prefs.edit().putBoolean("minimize_icons", enabled).apply()
        _settings.update { it.copy(minimizeIcons = enabled) }
    }

    fun updateAutoCollapseRead(enabled: Boolean) {
        prefs.edit().putBoolean("auto_collapse_read", enabled).apply()
        _settings.update { it.copy(autoCollapseRead = enabled) }
    }

    fun updateAutoDismissDetached(enabled: Boolean) {
        prefs.edit().putBoolean("auto_dismiss_detached", enabled).apply()
        _settings.update { it.copy(autoDismissDetached = enabled) }
    }
    
    fun updateUpdateInterval(interval: String) {
        prefs.edit().putString("update_interval", interval).apply()
        _settings.update { it.copy(updateInterval = interval) }
    }
    
    fun updateUpdateAvailableState(hasUpdate: Boolean, latestVersion: String) {
        prefs.edit()
            .putBoolean("has_update_available", hasUpdate)
            .putString("latest_version_available", latestVersion)
            .apply()
        _settings.update { 
            it.copy(
                hasUpdateAvailable = hasUpdate,
                latestVersionAvailable = latestVersion
            )
        }
    }
}

