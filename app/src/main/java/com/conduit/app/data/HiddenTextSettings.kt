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

class HiddenTextAppsRepository(private val context: Context) {
    // Apps Conduit has captured notifications from, labelled for the rule editor's app picker.
    suspend fun loadApps(): List<HiddenTextApp> = withContext(Dispatchers.IO) {
        AppDatabase.getDatabase(context).notificationDao().getDistinctPackagesWithChannel()
            .map { HiddenTextApp(it.packageName, getAppLabel(context, it.packageName, fallback = it.channel)) }
            .sortedBy { it.label.lowercase() }
    }
}
