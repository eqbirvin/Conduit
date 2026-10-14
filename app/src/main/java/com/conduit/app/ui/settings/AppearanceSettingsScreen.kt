package com.conduit.app.ui.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conduit.app.data.ConduitSettings
import com.conduit.app.performHapticClick

interface AppearanceSettingsScreenCallbacks {
    fun onThemeChanged(theme: Int)
    fun onJacobMonochromeChanged(enabled: Boolean)
    fun onJacobFollowSystemDarkChanged(enabled: Boolean)
    fun onActiveAppIconChanged(icon: String)
    fun onMinimizeIconsChanged(enabled: Boolean)
    fun onAutoCollapseReadChanged(enabled: Boolean)
    fun onDefaultToTodoModeChanged(enabled: Boolean)
    fun onCombineConversationsChanged(enabled: Boolean)
    fun onSingleConversationViewChanged(enabled: Boolean)
    fun onScrollTopBarWithFeedChanged(enabled: Boolean)
    fun onTopBarScrollBehaviorChanged(behavior: String)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsScreen(
    settings: ConduitSettings,
    callbacks: AppearanceSettingsScreenCallbacks,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val themeOptions = listOf("System Default", "Light Theme", "Dark Theme", "Jacob Mode (AMOLED)")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Appearance", fontSize = 22.sp, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsSectionHeader(title = "Theme")

            Column(Modifier.selectableGroup()) {
                themeOptions.forEachIndexed { index, text ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .selectable(
                                selected = (settings.themePreference == index),
                                onClick = { callbacks.onThemeChanged(index) },
                                role = Role.RadioButton
                            )
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (settings.themePreference == index),
                            onClick = null
                        )
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }

                    if (index == 3) {
                        val isJacobActive = settings.themePreference == 3
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = isJacobActive) {
                                    callbacks.onJacobFollowSystemDarkChanged(!settings.jacobFollowSystemDark)
                                }
                                .padding(start = 48.dp, end = 16.dp, top = 2.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = settings.jacobFollowSystemDark,
                                onCheckedChange = if (isJacobActive) { { callbacks.onJacobFollowSystemDarkChanged(it) } } else null,
                                enabled = isJacobActive
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Follow System Dark Mode",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isJacobActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                )
                                Text(
                                    text = "Uses Light theme during the day and AMOLED pure black when system dark mode is on",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isJacobActive) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                )
                            }
                        }
                    }
                }
            }

            if (settings.themePreference == 3) {
                Spacer(modifier = Modifier.height(4.dp))
                SettingsSwitchRow(
                    title = "Monochrome Accents",
                    description = "Forces text and icons to pure white/light gray instead of device blue",
                    checked = settings.jacobMonochrome,
                    onCheckedChange = callbacks::onJacobMonochromeChanged,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            SettingsSectionHeader(title = "App Launcher Icon")
            Text(
                text = "Select the app launcher icon for Conduit. Changes might take a moment to reflect on your home screen launcher.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            val iconOptions = listOf(
                Triple("LEGACY", "Legacy", MaterialTheme.colorScheme.primary),
                Triple("MANILA", "Manila", Color(0xFFD9A53C)),
                Triple("DARK", "Dark", Color(0xFF15171C)),
                Triple("BLUE", "Blue", Color(0xFF2E6FE0))
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                iconOptions.forEach { (id, label, color) ->
                    val isSelected = settings.activeAppIcon.uppercase(java.util.Locale.ROOT) == id
                    OutlinedCard(
                        onClick = {
                            callbacks.onActiveAppIconChanged(id)
                            performHapticClick(context)
                        },
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(color, shape = CircleShape)
                                    .border(1.5.dp, MaterialTheme.colorScheme.outline, shape = CircleShape)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            SettingsSectionHeader(title = "Notification Feed Presentation")

            SettingsSwitchRow(
                title = "Minimize Notification Icons",
                description = "Shrinks app icons and aligns them with the notification title to maximize space for text and action chips",
                checked = settings.minimizeIcons,
                onCheckedChange = callbacks::onMinimizeIconsChanged
            )

            SettingsSwitchRow(
                title = "Auto-Collapse Read Notifications",
                description = "Automatically collapse read (archived) notifications. This overrides the Master 'Expand All' state unless you manually expand them one-by-one.",
                checked = settings.autoCollapseRead,
                onCheckedChange = callbacks::onAutoCollapseReadChanged
            )

            SettingsSwitchRow(
                title = "Default to Todo Mode",
                description = "Automatically open Conduit in Todo Mode on startup instead of the Unified View",
                checked = settings.defaultToTodoMode,
                onCheckedChange = callbacks::onDefaultToTodoModeChanged
            )

            SettingsSwitchRow(
                title = "Combine Conversations",
                description = "Group adjacent messages from the same conversation into a streamlined thread, hiding duplicate titles and app icons",
                checked = settings.combineConversations,
                onCheckedChange = callbacks::onCombineConversationsChanged
            )

            SettingsSwitchRow(
                title = "Single Conversation View",
                description = "Roll all notifications per conversation across messaging apps into a single entry in the feed. Tapping expand opens a dedicated view of that conversation's entire history.",
                checked = settings.singleConversationView,
                onCheckedChange = callbacks::onSingleConversationViewChanged
            )

            SettingsSwitchRow(
                title = "Scroll Top Bar with Feed",
                description = "Allow the top bar and custom view strip to scroll off-screen with the notification log",
                checked = settings.scrollTopBarWithFeed,
                onCheckedChange = callbacks::onScrollTopBarWithFeedChanged
            )

            androidx.compose.animation.AnimatedVisibility(visible = settings.scrollTopBarWithFeed) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, bottom = 8.dp)
                ) {
                    val scrollOptions = listOf(
                        "NATURAL" to ("Natural Scroll" to "Returns only when scrolled all the way to the top of the feed"),
                        "QUICK_RETURN" to ("Quick Return" to "Slides back into view whenever scrolling up anywhere in the feed")
                    )

                    scrollOptions.forEach { (key, info) ->
                        val (title, description) = info
                        val isSelected = settings.topBarScrollBehavior == key
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { callbacks.onTopBarScrollBehaviorChanged(key) }
                                .padding(vertical = 8.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { callbacks.onTopBarScrollBehaviorChanged(key) }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
