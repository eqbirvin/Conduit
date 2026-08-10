package com.conduit.app.data

import com.conduit.app.FabAction

data class ConduitSettings(
    val themePreference: Int = 0,
    val jacobMonochrome: Boolean = false,
    val groupByChannel: Boolean = false,
    val persistentTrayEnabled: Boolean = false,
    val syncDismissal: Boolean = true,
    val showActionChips: Boolean = true,
    val syncPinned: Boolean = false,
    val dockLongPressLaunch: Boolean = true,
    val swipeLeftAction: String = "SNOOZE",
    val swipeRightAction: String = "ARCHIVE",
    val dockSizeIndex: Int = 1,
    val enableBubbles: Boolean = false,
    val enableBracket: Boolean = false,
    val bracketNotificationPopup: Boolean = true,
    val bracketHangerEnabled: Boolean = true,
    val bracketVerticalPosition: Float = 0.5f,
    val unifiedView: Boolean = true,
    val activeAppIcon: String = "MANILA",
    val smartMarkRead: Boolean = true,
    val smartMarkReadTarget: String = "widget_and_app",
    val minimizeIcons: Boolean = false,
    val masterExpandedState: Boolean = true,
    val fabConfigs: List<FabAction> = emptyList(),
    val aiBundle: List<String> = listOf("com.anthropic.claude", "com.google.android.apps.bard"),
    val notesBundle: List<String> = listOf("com.google.android.keep", "com.notion.id"),
    val recorderBundle: List<String> = listOf("com.google.android.apps.recorder"),
    val composeBundle: List<String> = listOf("com.google.android.apps.messaging", "com.google.android.gm"),
    val channelStates: Map<String, Boolean> = emptyMap(),
    val retentionDays: Int = 90,
    val enableAppBundles: Boolean = false
)

