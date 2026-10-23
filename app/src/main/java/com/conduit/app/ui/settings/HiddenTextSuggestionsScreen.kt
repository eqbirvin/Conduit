package com.conduit.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conduit.app.SuggestionScanState
import com.conduit.app.data.HIDDEN_TEXT_SCAN_LIMIT
import com.conduit.app.data.HiddenTextApp
import com.conduit.app.data.HiddenTextRule
import com.conduit.app.data.HiddenTextSuggestion
import com.conduit.app.data.HiddenTextTarget
import com.conduit.app.data.RecurringTextScanner
import com.conduit.app.performHapticClick

private const val MAX_APPS_LISTED = 3

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiddenTextSuggestionsScreen(
    scanState: SuggestionScanState,
    apps: List<HiddenTextApp>,
    onRescan: () -> Unit,
    onSaveRule: (HiddenTextRule) -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var pendingRule by remember { mutableStateOf<HiddenTextRule?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recurring Text", fontSize = 22.sp, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            performHapticClick(context)
                            onRescan()
                        },
                        enabled = scanState !is SuggestionScanState.Scanning
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Scan again")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        when (scanState) {
            SuggestionScanState.Idle, SuggestionScanState.Scanning -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Scanning your latest notifications…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            is SuggestionScanState.Done -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "summary") {
                        Text(
                            text = scanSummary(scanState),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(scanState.suggestions, key = { it.phrase }) { suggestion ->
                        SuggestionCard(
                            suggestion = suggestion,
                            onHide = {
                                performHapticClick(context)
                                pendingRule = ruleFromSuggestion(suggestion)
                            }
                        )
                    }
                }
            }
        }
    }

    pendingRule?.let { rule ->
        HiddenTextRuleDialog(
            initialRule = rule,
            isNew = true,
            apps = apps,
            onSave = {
                onSaveRule(it)
                pendingRule = null
            },
            onDismiss = { pendingRule = null }
        )
    }
}

@Composable
private fun SuggestionCard(suggestion: HiddenTextSuggestion, onHide: () -> Unit) {
    var expanded by remember(suggestion.phrase) { mutableStateOf(false) }
    val highlight = MaterialTheme.colorScheme.primaryContainer
    val example = remember(suggestion.example, suggestion.phrase, highlight) {
        highlightPhrase(suggestion.example, suggestion.phrase, SpanStyle(background = highlight, fontWeight = FontWeight.SemiBold))
    }
    val appsText = remember(suggestion.apps) { describeApps(suggestion) }

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }
            ) {
                Text(
                    text = "“${suggestion.phrase}”",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = if (expanded) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Found in ${suggestion.occurrences} notifications • ${describePosition(suggestion.position)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = appsText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))
            Text("Example use of recurring text", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = example,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))
            FilledTonalButton(onClick = onHide, modifier = Modifier.align(Alignment.End)) {
                Icon(Icons.Default.VisibilityOff, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Hide this text")
            }
        }
    }
}

private fun scanSummary(state: SuggestionScanState.Done): String {
    val scanned = if (state.scannedCount >= HIDDEN_TEXT_SCAN_LIMIT) {
        "the latest ${state.scannedCount} notifications"
    } else {
        "${state.scannedCount} notifications"
    }
    return when (state.suggestions.size) {
        0 -> "No recurring text found in $scanned. Text that is already hidden is skipped."
        1 -> "Scanned $scanned and found 1 recurring block of text. Tap a block to see all of it."
        else -> "Scanned $scanned and found ${state.suggestions.size} recurring blocks of text, most frequent first. Tap a block to see all of it."
    }
}

private fun describePosition(position: RecurringTextScanner.Position): String = when (position) {
    RecurringTextScanner.Position.START -> "usually at the start"
    RecurringTextScanner.Position.END -> "usually at the end"
    RecurringTextScanner.Position.MIDDLE -> "usually mid-message"
}

private fun describeApps(suggestion: HiddenTextSuggestion): String {
    val listed = suggestion.apps.take(MAX_APPS_LISTED).joinToString(", ") { "${it.label} (${it.count})" }
    val more = suggestion.apps.size - MAX_APPS_LISTED
    return if (more > 0) "$listed, +$more more" else listed
}

private fun highlightPhrase(text: String, phrase: String, style: SpanStyle): AnnotatedString = buildAnnotatedString {
    val index = text.indexOf(phrase)
    if (index < 0) {
        append(text)
        return@buildAnnotatedString
    }
    append(text.substring(0, index))
    withStyle(style) { append(phrase) }
    append(text.substring(index + phrase.length))
}

// Prefills the rule editor: body only (where the text was found), scoped to the app when only one app sends it.
private fun ruleFromSuggestion(suggestion: HiddenTextSuggestion): HiddenTextRule =
    newHiddenTextRule().copy(
        phrase = suggestion.phrase,
        target = HiddenTextTarget.BODY,
        packageNames = if (suggestion.apps.size == 1) listOf(suggestion.apps[0].packageName) else emptyList()
    )
