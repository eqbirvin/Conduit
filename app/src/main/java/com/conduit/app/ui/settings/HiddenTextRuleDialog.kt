package com.conduit.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.conduit.app.data.HiddenTextApp
import com.conduit.app.data.HiddenTextRule
import com.conduit.app.data.HiddenTextTarget
import java.util.UUID

fun newHiddenTextRule(): HiddenTextRule = HiddenTextRule(id = UUID.randomUUID().toString(), phrase = "")

@Composable
fun HiddenTextRuleDialog(
    initialRule: HiddenTextRule,
    apps: List<HiddenTextApp>,
    onSave: (HiddenTextRule) -> Unit,
    onDismiss: () -> Unit
) {
    val isNew = initialRule.phrase.isEmpty()
    var phrase by remember { mutableStateOf(initialRule.phrase) }
    var matchCase by remember { mutableStateOf(initialRule.matchCase) }
    var target by remember { mutableStateOf(initialRule.target) }
    var chosenAppsOnly by remember { mutableStateOf(initialRule.packageNames.isNotEmpty()) }
    var selectedPackages by remember { mutableStateOf(initialRule.packageNames.toSet()) }

    // Keep apps the rule already names even if Conduit no longer has notifications from them.
    val pickerApps = remember(apps, initialRule.packageNames) {
        val known = apps.map { it.packageName }.toSet()
        apps + initialRule.packageNames.filter { it !in known }.map { HiddenTextApp(it, it) }
    }

    val canSave = phrase.isNotBlank() && (!chosenAppsOnly || selectedPackages.isNotEmpty())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Hide a phrase" else "Edit hidden phrase") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = phrase,
                    onValueChange = { phrase = it },
                    label = { Text("Exact text to hide") },
                    placeholder = { Text("EXTERNAL:") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(4.dp))
                SettingsSwitchRow(
                    title = "Match case",
                    description = if (matchCase) "Only exact upper/lower case is hidden." else "\"external:\" is hidden too.",
                    checked = matchCase,
                    onCheckedChange = { matchCase = it }
                )

                Text("Hide from", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
                SegmentedOptionRow(
                    options = listOf(
                        HiddenTextTarget.TITLE to "Title",
                        HiddenTextTarget.BODY to "Message",
                        HiddenTextTarget.BOTH to "Both"
                    ),
                    selectedValue = target,
                    onValueSelected = { target = it }
                )

                Text("Apply to", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                SegmentedOptionRow(
                    options = listOf(false to "All apps", true to "Chosen apps"),
                    selectedValue = chosenAppsOnly,
                    onValueSelected = { chosenAppsOnly = it }
                )

                if (chosenAppsOnly) {
                    Spacer(modifier = Modifier.height(8.dp))
                    if (pickerApps.isEmpty()) {
                        Text(
                            "No apps yet. Apps appear here once Conduit has captured a notification from them.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                            items(pickerApps, key = { it.packageName }) { app ->
                                val checked = app.packageName in selectedPackages
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedPackages = if (checked) selectedPackages - app.packageName else selectedPackages + app.packageName
                                        }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(checked = checked, onCheckedChange = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(app.label, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    onSave(
                        initialRule.copy(
                            phrase = phrase,
                            matchCase = matchCase,
                            target = target,
                            packageNames = if (chosenAppsOnly) pickerApps.map { it.packageName }.filter { it in selectedPackages } else emptyList()
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
