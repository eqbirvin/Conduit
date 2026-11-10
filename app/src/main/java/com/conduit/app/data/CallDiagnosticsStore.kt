package com.conduit.app.data

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// One phone-app notification post or removal, plus what Conduit decided to do with it.
@Serializable
data class CallDiagnosticsEvent(
    val time: Long,
    val event: String,
    val decision: String,
    val packageName: String,
    val key: String,
    val tag: String? = null,
    val id: Int = 0,
    val category: String? = null,
    val channelId: String? = null,
    val groupKey: String? = null,
    val flags: String = "",
    val ongoing: Boolean = false,
    val postTime: Long = 0L,
    val whenTime: Long = 0L,
    val hasFullScreenIntent: Boolean = false,
    val fields: Map<String, String> = emptyMap(),
    val extraKeys: List<String> = emptyList()
)

/**
 * Rolling on-device log of phone-app notifications for troubleshooting missing calls.
 * Off by default; keeps at most [MAX_EVENTS] events from the last 24 hours.
 * All functions block on disk I/O: call them from Dispatchers.IO.
 */
class CallDiagnosticsStore(context: Context) {

    private val appContext = context.applicationContext
    private val logFile = File(File(appContext.filesDir, DIR_NAME), LOG_FILE_NAME)

    fun append(event: CallDiagnosticsEvent) = synchronized(lock) {
        try {
            logFile.parentFile?.mkdirs()
            logFile.appendText(json.encodeToString(event) + "\n")
        } catch (e: IOException) {
            Log.e(TAG, "Could not write call diagnostics event", e)
        }
    }

    fun readRecent(): List<CallDiagnosticsEvent> = synchronized(lock) {
        val cutoff = System.currentTimeMillis() - MAX_AGE_MS
        readAllLocked().filter { it.time >= cutoff }.takeLast(MAX_EVENTS)
    }

    fun prune() = synchronized(lock) {
        val kept = readRecent()
        try {
            if (kept.isEmpty()) {
                logFile.delete()
            } else {
                logFile.writeText(kept.joinToString(separator = "\n", postfix = "\n") { json.encodeToString(it) })
            }
        } catch (e: IOException) {
            Log.e(TAG, "Could not prune call diagnostics log", e)
        }
    }

    fun clear() = synchronized(lock) {
        logFile.delete()
        exportDir().listFiles()?.forEach { it.delete() }
    }

    // Writes a readable .txt of the recent events to the cache dir (shared through FileProvider).
    // Returns null when nothing has been recorded.
    fun writeExport(versionName: String): File? = synchronized(lock) {
        val events = readRecent()
        if (events.isEmpty()) return null
        val dir = exportDir()
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(dir, "conduit-call-diagnostics-$stamp.txt")
        return try {
            file.writeText(render(events, versionName))
            file
        } catch (e: IOException) {
            Log.e(TAG, "Could not write call diagnostics export", e)
            null
        }
    }

    private fun exportDir() = File(appContext.cacheDir, DIR_NAME)

    private fun readAllLocked(): List<CallDiagnosticsEvent> {
        if (!logFile.exists()) return emptyList()
        val lines = try {
            logFile.readLines()
        } catch (e: IOException) {
            Log.e(TAG, "Could not read call diagnostics log", e)
            return emptyList()
        }
        return lines.mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null
            try {
                json.decodeFromString<CallDiagnosticsEvent>(line)
            } catch (e: SerializationException) {
                Log.w(TAG, "Skipping unreadable call diagnostics line", e)
                null
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Skipping unreadable call diagnostics line", e)
                null
            }
        }
    }

    private fun render(events: List<CallDiagnosticsEvent>, versionName: String): String {
        val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        fun time(millis: Long) = if (millis > 0) timeFormat.format(Date(millis)) else "-"
        return buildString {
            appendLine("Conduit call diagnostics")
            appendLine("Exported: ${time(System.currentTimeMillis())}")
            appendLine("Conduit: v$versionName")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Events: ${events.size} (last 24 hours, oldest first)")
            appendLine()
            for (e in events) {
                appendLine("[${time(e.time)}] ${e.event} -> ${e.decision}")
                appendLine("  package=${e.packageName}")
                appendLine("  key=${e.key}")
                appendLine("  tag=${e.tag ?: "-"}  id=${e.id}  category=${e.category ?: "-"}  channel=${e.channelId ?: "-"}  group=${e.groupKey ?: "-"}")
                appendLine("  flags=${e.flags}  ongoing=${e.ongoing}  fullScreenIntent=${e.hasFullScreenIntent}")
                appendLine("  posted=${time(e.postTime)}  when=${time(e.whenTime)}")
                for ((name, value) in e.fields) appendLine("  $name=$value")
                if (e.extraKeys.isNotEmpty()) appendLine("  extraKeys=${e.extraKeys.joinToString(", ")}")
                appendLine()
            }
        }
    }

    companion object {
        const val PREF_KEY_ENABLED = "call_diagnostics_enabled"
        const val MAX_EVENTS = 500
        const val MAX_AGE_MS = 24L * 60 * 60 * 1000
        private const val TAG = "CallDiagnostics"
        private const val DIR_NAME = "diagnostics"
        private const val LOG_FILE_NAME = "call_events.jsonl"

        // Process-wide: the listener service and the settings screen each hold their own store.
        private val lock = Any()
        private val json = Json { ignoreUnknownKeys = true }
    }
}
