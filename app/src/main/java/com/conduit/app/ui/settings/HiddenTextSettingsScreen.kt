package com.conduit.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conduit.app.data.HiddenTextApp
import com.conduit.app.data.HiddenTextRule
import com.conduit.app.data.HiddenTextTarget
import com.conduit.app.performHapticClick

interface HiddenTextSettingsScreenCallbacks {
    fun onSaveRule(rule: HiddenTextRule)
    fun onDeleteRule(id: String)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiddenTextSettingsScreen(
    rules: List<HiddenTextRule>,
    apps: List<HiddenTextApp>,
    callbacks: HiddenTextSettingsScreenCallbacks,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    // null = editor closed; a rule with a blank phrase = adding a new one.
    var editingRule by remember { mutableStateOf<HiddenTextRule?>(null) }
    val appLabels = remember(apps) { apps.associate { it.packageName to it.label } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hidden Text", fontSize = 22.sp, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    performHapticClick(context)
                    editingRule = newHiddenTextRule()
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add phrase") }
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
            SettingsSectionHeader(title = "Hidden Phrases")
            Text(
                text = "Remove exact phrases from notification titles and messages, such as the \"EXTERNAL:\" tag some email apps add. " +
                    "Conduit keeps the original text, so deleting a phrase brings it back everywhere.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (rules.isEmpty()) {
                Text(
                    text = "No hidden phrases yet. Tap \"Add phrase\" to create one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        rules.forEachIndexed { index, rule ->
                            ListItem(
                                modifier = Modifier.clickable { editingRule = rule },
                                headlineContent = {
                                    Text(
                                        text = "\"${rule.phrase}\"",
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        text = describeRule(rule, appLabels),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                },
                                trailingContent = {
                                    IconButton(onClick = {
                                        performHapticClick(context)
                                        callbacks.onDeleteRule(rule.id)
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            )
                            if (index < rules.lastIndex) {
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                            }
                        }
                    }
                }
            }

            // Keeps the last rule clear of the floating button.
            Spacer(modifier = Modifier.height(96.dp))
        }
    }

    editingRule?.let { rule ->
        HiddenTextRuleDialog(
            initialRule = rule,
            apps = apps,
            onSave = {
                callbacks.onSaveRule(it)
                editingRule = null
            },
            onDismiss = { editingRule = null }
        )
    }
}

private fun describeRule(rule: HiddenTextRule, appLabels: Map<String, String>): String {
    val where = when (rule.target) {
        HiddenTextTarget.TITLE -> "Title"
        HiddenTextTarget.BODY -> "Message"
        HiddenTextTarget.BOTH -> "Title & message"
    }
    val case = if (rule.matchCase) "Match case" else "Any case"
    val apps = when (rule.packageNames.size) {
        0 -> "All apps"
        1 -> appLabels[rule.packageNames[0]] ?: rule.packageNames[0]
        else -> "${rule.packageNames.size} apps"
    }
    return "$where • $case • $apps"
}
