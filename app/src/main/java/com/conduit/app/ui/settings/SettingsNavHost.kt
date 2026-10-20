package com.conduit.app.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.conduit.app.Screen
import com.conduit.app.SettingsViewModel
import com.conduit.app.changeAppIcon
import com.conduit.app.data.ConduitSettings
import com.conduit.app.ui.ChannelsSettingsScreen
import com.conduit.app.ui.ChannelsSettingsScreenCallbacks
import com.conduit.app.ui.DevSettingsScreen
import com.conduit.app.ui.DevSettingsScreenCallbacks
import com.conduit.app.ui.SettingsScreen
import com.conduit.app.ui.SettingsScreenCallbacks
import com.conduit.app.updater.UpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsNavHost(
    currentScreen: Screen,
    settings: ConduitSettings,
    settingsViewModel: SettingsViewModel,
    onNavigateToScreen: (Screen) -> Unit,
    onNavigateBackToSettings: () -> Unit,
    onNavigateHome: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    when (currentScreen) {
        Screen.SETTINGS -> SettingsScreen(
            settings = settings,
            callbacks = object : SettingsScreenCallbacks {
                override fun onNavigateToChannels() { onNavigateToScreen(Screen.CHANNELS) }
                override fun onNavigateToAppearance() { onNavigateToScreen(Screen.SETTINGS_APPEARANCE) }
                override fun onNavigateToDock() { onNavigateToScreen(Screen.SETTINGS_DOCK) }
                override fun onNavigateToNotifications() { onNavigateToScreen(Screen.SETTINGS_NOTIFICATIONS) }
                override fun onNavigateToData() { onNavigateToScreen(Screen.SETTINGS_DATA) }
                override fun onNavigateToBracket() { onNavigateToScreen(Screen.SETTINGS_BRACKET) }
                override fun onNavigateToAbout() { onNavigateToScreen(Screen.SETTINGS_ABOUT) }
                override fun onShowWhatsNew() { onNavigateToScreen(Screen.WHATS_NEW) }
            },
            onNavigateBack = onNavigateHome
        )

        Screen.SETTINGS_APPEARANCE -> AppearanceSettingsScreen(
            settings = settings,
            callbacks = object : AppearanceSettingsScreenCallbacks {
                override fun onThemeChanged(theme: Int) { settingsViewModel.updateTheme(theme) }
                override fun onJacobMonochromeChanged(enabled: Boolean) { settingsViewModel.updateJacobMonochrome(enabled) }
                override fun onJacobFollowSystemDarkChanged(enabled: Boolean) { settingsViewModel.updateJacobFollowSystemDark(enabled) }
                override fun onActiveAppIconChanged(icon: String) {
                    settingsViewModel.updateActiveAppIcon(icon)
                    changeAppIcon(context, icon)
                }
                override fun onMinimizeIconsChanged(enabled: Boolean) { settingsViewModel.updateMinimizeIcons(enabled) }
                override fun onAutoCollapseReadChanged(enabled: Boolean) { settingsViewModel.updateAutoCollapseRead(enabled) }
                override fun onDefaultToTodoModeChanged(enabled: Boolean) { settingsViewModel.updateDefaultToTodoMode(enabled) }
                override fun onCombineConversationsChanged(enabled: Boolean) { settingsViewModel.updateCombineConversations(enabled) }
                override fun onSingleConversationViewChanged(enabled: Boolean) { settingsViewModel.updateSingleConversationView(enabled) }
                override fun onScrollTopBarWithFeedChanged(enabled: Boolean) { settingsViewModel.updateScrollTopBarWithFeed(enabled) }
                override fun onTopBarScrollBehaviorChanged(behavior: String) { settingsViewModel.updateTopBarScrollBehavior(behavior) }
            },
            onNavigateBack = onNavigateBackToSettings
        )

        Screen.SETTINGS_DOCK -> DockSettingsScreen(
            settings = settings,
            callbacks = object : DockSettingsScreenCallbacks {
                override fun onEnableAppBundlesChanged(enabled: Boolean) {
                    settingsViewModel.updateEnableAppBundles(enabled, context.applicationContext)
                }
                override fun onAiBundleChanged(bundle: List<String>) { settingsViewModel.updateAiBundle(bundle) }
                override fun onNotesBundleChanged(bundle: List<String>) { settingsViewModel.updateNotesBundle(bundle) }
                override fun onRecorderBundleChanged(bundle: List<String>) { settingsViewModel.updateRecorderBundle(bundle) }
                override fun onComposeBundleChanged(bundle: List<String>) { settingsViewModel.updateComposeBundle(bundle) }
                override fun onBundleMenuSizingChanged(sizing: String) { settingsViewModel.updateBundleMenuSizing(sizing) }
                override fun onDockExpandPositionChanged(position: String) { settingsViewModel.updateDockExpandPosition(position) }
                override fun onTransparentBundlePillsChanged(enabled: Boolean) { settingsViewModel.updateTransparentBundlePills(enabled) }
                override fun onBundleMenuBlurChanged(enabled: Boolean) { settingsViewModel.updateBundleMenuBlur(enabled) }
                override fun onDockLongPressLaunchChanged(enabled: Boolean) { settingsViewModel.updateDockLongPressLaunch(enabled) }
                override fun onDockSizeChanged(size: Int) { settingsViewModel.updateDockSize(size) }
                override fun onDockScrollIndicatorChanged(indicator: String) { settingsViewModel.updateDockScrollIndicator(indicator) }
                override fun onSwipeRightActionChanged(action: String) { settingsViewModel.updateSwipeRightAction(action) }
                override fun onSwipeLeftActionChanged(action: String) { settingsViewModel.updateSwipeLeftAction(action) }
            },
            onNavigateBack = onNavigateBackToSettings
        )

        Screen.SETTINGS_NOTIFICATIONS -> NotificationSyncSettingsScreen(
            settings = settings,
            callbacks = object : NotificationSyncSettingsScreenCallbacks {
                override fun onAutoDismissDetachedChanged(enabled: Boolean) { settingsViewModel.updateAutoDismissDetached(enabled) }
                override fun onSyncDismissalChanged(enabled: Boolean) { settingsViewModel.updateSyncDismissal(enabled) }
                override fun onSyncPinnedChanged(enabled: Boolean) { settingsViewModel.updateSyncPinned(enabled) }
                override fun onShowActionChipsChanged(enabled: Boolean) { settingsViewModel.updateShowActionChips(enabled) }
                override fun onSmartMarkReadChanged(enabled: Boolean) { settingsViewModel.updateSmartMarkRead(enabled) }
                override fun onSmartMarkReadTargetChanged(target: String) { settingsViewModel.updateSmartMarkReadTarget(target) }
                override fun onTriggerNativeMarkReadChanged(enabled: Boolean) { settingsViewModel.updateTriggerNativeMarkRead(enabled) }
            },
            onNavigateBack = onNavigateBackToSettings
        )

        Screen.SETTINGS_DATA -> DataRetentionSettingsScreen(
            settings = settings,
            callbacks = object : DataRetentionSettingsScreenCallbacks {
                override fun onRetentionDaysChanged(days: Int) { settingsViewModel.updateRetentionDays(days) }
            },
            onNavigateBack = onNavigateBackToSettings
        )

        Screen.SETTINGS_BRACKET -> BracketSettingsScreen(
            settings = settings,
            callbacks = object : BracketSettingsScreenCallbacks {
                override fun onEnableBracketChanged(enabled: Boolean) {
                    settingsViewModel.updateEnableBracket(enabled)
                    if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                }
                override fun onGroupByChannelChanged(enabled: Boolean) { settingsViewModel.updateGroupByChannel(enabled) }
                override fun onBracketNotificationPopupChanged(enabled: Boolean) { settingsViewModel.updateBracketNotificationPopup(enabled) }
                override fun onBracketHangerEnabledChanged(enabled: Boolean) { settingsViewModel.updateBracketHangerEnabled(enabled) }
                override fun onBracketVerticalPositionChanged(position: Float) { settingsViewModel.updateBracketVerticalPosition(position) }
            },
            onNavigateBack = onNavigateBackToSettings
        )

        Screen.SETTINGS_ABOUT -> AboutSettingsScreen(
            settings = settings,
            callbacks = object : AboutSettingsScreenCallbacks {
                override fun onUpdateIntervalChanged(interval: String) {
                    settingsViewModel.updateUpdateInterval(interval)
                    UpdateManager.setupWorker(context, interval)
                }
                override fun onManualUpdateCheck() {
                    scope.launch {
                        val updateInfo = UpdateManager.checkForUpdates()
                        withContext(Dispatchers.Main) {
                            if (updateInfo != null) {
                                settingsViewModel.updateUpdateAvailableState(true, updateInfo.first)
                                Toast.makeText(context, "Update available: ${updateInfo.first}. Check Hub.", Toast.LENGTH_SHORT).show()
                            } else {
                                settingsViewModel.updateUpdateAvailableState(false, "")
                                Toast.makeText(context, "Conduit is up to date.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                override fun onShowWhatsNew() { onNavigateToScreen(Screen.WHATS_NEW) }
                override fun onNavigateToDevSettings() { onNavigateToScreen(Screen.DEV_SETTINGS) }
            },
            onNavigateBack = onNavigateBackToSettings
        )

        Screen.CHANNELS -> ChannelsSettingsScreen(
            settings = settings,
            callbacks = object : ChannelsSettingsScreenCallbacks {
                override fun onChannelToggled(prefKey: String, isEnabled: Boolean) {
                    settingsViewModel.updateChannelState(prefKey, isEnabled)
                }
            },
            onNavigateBack = onNavigateBackToSettings
        )

        Screen.DEV_SETTINGS -> DevSettingsScreen(
            settings = settings,
            callbacks = object : DevSettingsScreenCallbacks {
                override fun onPersistentTrayEnabledChanged(enabled: Boolean) { settingsViewModel.updatePersistentTrayEnabled(enabled) }
                override fun onEnableBubblesChanged(enabled: Boolean) { settingsViewModel.updateEnableBubbles(enabled) }
                override fun onDemoModeEnabledChanged(enabled: Boolean) { settingsViewModel.updateDemoModeEnabled(enabled) }
                override fun onIngestionDiagnosticsEnabledChanged(enabled: Boolean) { settingsViewModel.updateIngestionDiagnosticsEnabled(enabled) }
            },
            onNavigateBack = onNavigateBackToSettings
        )

        else -> {}
    }
}
