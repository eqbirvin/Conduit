package com.conduit.app.data

import kotlinx.coroutines.flow.update

// Home screen layout preferences (top bar, custom view strip, date header actions).
// Kept apart from SettingsRepository.kt so that file stays under 500 lines.

internal const val KEY_HIDE_TOP_BAR = "hide_top_bar"
internal const val KEY_TOP_BAR_ACTIONS_IN_DOCK = "top_bar_actions_in_dock"
internal const val KEY_SHOW_CUSTOM_VIEW_STRIP = "show_custom_view_strip"
internal const val KEY_SHOW_DATE_MARK_ALL_READ = "show_date_mark_all_read"

fun SettingsRepository.updateHideTopBar(hidden: Boolean) {
    prefs.edit().putBoolean(KEY_HIDE_TOP_BAR, hidden).apply()
    _settings.update { it.copy(hideTopBar = hidden) }
}

fun SettingsRepository.updateTopBarActionsInDock(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_TOP_BAR_ACTIONS_IN_DOCK, enabled).apply()
    _settings.update { it.copy(topBarActionsInDock = enabled) }
}

fun SettingsRepository.updateShowCustomViewStrip(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_SHOW_CUSTOM_VIEW_STRIP, enabled).apply()
    _settings.update { it.copy(showCustomViewStrip = enabled) }
}

fun SettingsRepository.updateShowDateMarkAllRead(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_SHOW_DATE_MARK_ALL_READ, enabled).apply()
    _settings.update { it.copy(showDateMarkAllRead = enabled) }
}
