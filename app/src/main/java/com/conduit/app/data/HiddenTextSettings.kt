package com.conduit.app.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.conduit.app.getAppLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Hidden text rules: phrases removed from notification titles/bodies at display time.
// The stored notification text is never modified, so deleting a rule restores the original.
// Kept apart from SettingsRepository.kt so that file stays under 500 lines.

internal const val KEY_HIDDEN_TEXT_RULES = "hidden_text_rules"
private const val TAG = "HiddenTextSettings"

@Serializable
enum class HiddenTextTarget { TITLE, BODY, BOTH }

@Serializable
data class HiddenTextRule(
    val id: String,
    val phrase: String,
    val matchCase: Boolean = false,
    val target: HiddenTextTarget = HiddenTextTarget.BOTH,
    // Empty means the rule applies to every app.
    val packageNames: List<String> = emptyList()
)

private val rulesJson = Json { ignoreUnknownKeys = true }

internal fun decodeHiddenTextRules(raw: String?): List<HiddenTextRule> {
    if (raw.isNullOrEmpty()) return emptyList()
    return try {
        rulesJson.decodeFromString<List<HiddenTextRule>>(raw)
    } catch (e: SerializationException) {
        Log.e(TAG, "Failed to decode hidden text rules; ignoring them", e)
        emptyList()
    } catch (e: IllegalArgumentException) {
        Log.e(TAG, "Malformed hidden text rules; ignoring them", e)
        emptyList()
    }
}

internal fun loadHiddenTextRules(prefs: SharedPreferences): List<HiddenTextRule> =
    decodeHiddenTextRules(prefs.getString(KEY_HIDDEN_TEXT_RULES, null))

// For callers outside the app UI (widgets, the listener service) that have no SettingsRepository.
// Re-decodes only when the stored JSON changes, so repeated calls cost one in-memory prefs read.
object HiddenTextRulesCache {
    @Volatile private var cachedRaw: String? = null
    @Volatile private var cachedRules: List<HiddenTextRule> = emptyList()

    fun get(context: Context): List<HiddenTextRule> {
        val raw = context.getSharedPreferences("conduit_prefs", Context.MODE_PRIVATE)
            .getString(KEY_HIDDEN_TEXT_RULES, null)
        if (raw != cachedRaw) {
            cachedRules = decodeHiddenTextRules(raw)
            cachedRaw = raw
        }
        return cachedRules
    }
}

fun SettingsRepository.updateHiddenTextRules(rules: List<HiddenTextRule>) {
    prefs.edit().putString(KEY_HIDDEN_TEXT_RULES, rulesJson.encodeToString(rules)).apply()
    _settings.update { it.copy(hiddenTextRules = rules) }
}

data class HiddenTextApp(val packageName: String, val label: String)

data class SuggestionApp(val packageName: String, val label: String, val count: Int)

data class HiddenTextSuggestion(
    val phrase: String,
    val occurrences: Int,
    // Most frequent first.
    val apps: List<SuggestionApp>,
    val position: RecurringTextScanner.Position,
    val example: String
)

data class HiddenTextScanResult(val scannedCount: Int, val suggestions: List<HiddenTextSuggestion>)

// Bounds the suggestion scan to the most recent notifications (the table can hold many thousands).
const val HIDDEN_TEXT_SCAN_LIMIT = 5000

class HiddenTextRepository(private val context: Context) {
    private val dao = AppDatabase.getDatabase(context).notificationDao()

    // Apps Conduit has captured notifications from, labelled for the rule editor's app picker.
    suspend fun loadApps(): List<HiddenTextApp> = withContext(Dispatchers.IO) {
        dao.getDistinctPackagesWithChannel()
            .map { HiddenTextApp(it.packageName, getAppLabel(context, it.packageName, fallback = it.channel)) }
            .sortedBy { it.label.lowercase() }
    }

    // Scans recent message bodies for recurring text. Phrases already hidden are removed first,
    // so they are not suggested again and new suggestions match what a new rule will see.
    suspend fun scanForSuggestions(isDemo: Boolean, rules: List<HiddenTextRule>): HiddenTextScanResult {
        val rows = withContext(Dispatchers.IO) { dao.getRecentBodies(isDemo, HIDDEN_TEXT_SCAN_LIMIT) }
        val found = withContext(Dispatchers.Default) {
            val bodies = rows.mapNotNull { row ->
                val text = row.text?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                RecurringTextScanner.Body(row.packageName, HiddenTextFilter.stripBody(row.packageName, text, rules))
            }
            RecurringTextScanner.scan(bodies)
        }
        val channels = rows.associate { it.packageName to it.channel }
        val suggestions = withContext(Dispatchers.IO) {
            found.map { result ->
                HiddenTextSuggestion(
                    phrase = result.phrase,
                    occurrences = result.occurrences,
                    apps = result.appCounts.map { (pkg, count) ->
                        SuggestionApp(pkg, getAppLabel(context, pkg, fallback = channels[pkg] ?: pkg), count)
                    },
                    position = result.position,
                    example = result.example
                )
            }
        }
        return HiddenTextScanResult(rows.size, suggestions)
    }
}
