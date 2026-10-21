package com.conduit.app

import android.app.Notification
import android.app.Person
import android.content.Context
import android.os.Build
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.os.BundleCompat
import com.conduit.app.data.HubNotification
import com.conduit.app.data.NotificationDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Records answered and outgoing calls from the ongoing call notifications that phone and VoIP
 * apps post, which the regular ingestion path skips because they are ongoing.
 *
 * One call updates its tray notification many times (ringing, active, hold, mute), so each
 * call is tracked as a session per tray key and produces a single CALL entry. The entry gets
 * its own "<tray key>#call@<start>" key so tray events for the dialer's notification never
 * touch it, and its text gains the call duration when the call ends.
 * Missed calls are not handled here: they arrive as regular notifications.
 */
class CallNotificationTracker(
    private val context: Context,
    private val dao: NotificationDao,
    scope: CoroutineScope,
    private val onEntriesChanged: () -> Unit
) {
    private sealed interface CallEvent {
        val sbn: StatusBarNotification
        class Posted(override val sbn: StatusBarNotification, val channel: String, val prefKey: String) : CallEvent
        class Removed(override val sbn: StatusBarNotification) : CallEvent
    }

    private class CallSession(
        val packageName: String,
        var sawIncoming: Boolean = false,
        var ignored: Boolean = false,
        var entryKey: String? = null,
        var startedAt: Long = 0L,
        var connectedAt: Long? = null
    )

    private enum class CallState { INCOMING, ONGOING }

    // A single consumer keeps posts and removals for a call strictly in order.
    private val events = Channel<CallEvent>(Channel.UNLIMITED)

    // Only touched from the consumer coroutine.
    private val sessions = HashMap<String, CallSession>()

    // Package -> when its last ringing-only session ended, for dialers that post the ringing and
    // in-progress notifications under different keys.
    private val ringingEndedAt = HashMap<String, Long>()

    init {
        scope.launch {
            for (event in events) {
                when (event) {
                    is CallEvent.Posted -> handlePosted(event)
                    is CallEvent.Removed -> handleRemoved(event.sbn)
                }
            }
        }
    }

    fun isOngoingCall(sbn: StatusBarNotification): Boolean {
        if (!sbn.isOngoing) return false
        val notification = sbn.notification
        return notification.category == Notification.CATEGORY_CALL || notification.extras.containsKey(EXTRA_CALL_TYPE)
    }

    fun onCallPosted(sbn: StatusBarNotification, channel: String, prefKey: String) {
        events.trySend(CallEvent.Posted(sbn, channel, prefKey))
    }

    fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (isOngoingCall(sbn)) events.trySend(CallEvent.Removed(sbn))
    }

    private suspend fun handlePosted(event: CallEvent.Posted) {
        val sbn = event.sbn
        val notification = sbn.notification
        val session = sessions.getOrPut(sbn.key) { CallSession(sbn.packageName) }
        if (session.ignored) return

        if (callState(notification) == CallState.INCOMING) {
            session.sawIncoming = true
            return
        }

        if (session.connectedAt == null && notification.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)) {
            session.connectedAt = notification.`when`.takeIf { it > 0 } ?: sbn.postTime
        }
        if (session.entryKey != null) return

        val wasRinging = sessions.any { (key, other) -> key != sbn.key && other.sawIncoming && other.entryKey == null && other.packageName == sbn.packageName } ||
            ringingEndedAt[sbn.packageName]?.let { sbn.postTime - it < RINGING_HANDOFF_MS } == true
        if (wasRinging) session.sawIncoming = true

        val prefs = context.getSharedPreferences("conduit_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean(event.prefKey, true)) {
            session.ignored = true
            return
        }

        // The listener may have restarted mid-call and lost this session; pick its entry back up.
        val existing = findInProgressEntry(sbn.key)
        if (existing != null) {
            session.entryKey = existing.notificationKey
            session.startedAt = existing.timestamp
            session.sawIncoming = existing.text == TEXT_ANSWERED
            return
        }

        val startedAt = sbn.postTime
        val entryKey = "${sbn.key}$ENTRY_KEY_MARKER$startedAt"
        // Answered and outgoing calls are a call log, not something to catch up on, so they are
        // stored already read (archived). Missed calls still arrive unread through regular ingestion.
        dao.insert(
            HubNotification(
                packageName = sbn.packageName,
                notificationKey = entryKey,
                title = callerName(notification).ifBlank { event.channel },
                text = if (session.sawIncoming) TEXT_ANSWERED else TEXT_OUTGOING,
                channel = event.channel,
                timestamp = startedAt,
                isArchived = true,
                archivedTimestamp = startedAt,
                kind = KIND_CALL
            )
        )
        session.entryKey = entryKey
        session.startedAt = startedAt
        Log.d(TAG, "Recorded ${if (session.sawIncoming) "answered" else "outgoing"} call [${event.channel}]")
        onEntriesChanged()
    }

    private suspend fun handleRemoved(sbn: StatusBarNotification) {
        val session = sessions.remove(sbn.key)
        if (session?.ignored == true) return
        if (session != null && session.entryKey == null) {
            if (session.sawIncoming) ringingEndedAt[sbn.packageName] = System.currentTimeMillis()
            return
        }

        // A null session means the listener restarted mid-call; finish the entry from the database.
        val entry = session?.entryKey?.let { dao.getMostRecentByKey(it) } ?: findInProgressEntry(sbn.key) ?: return
        val baseText = entry.text.orEmpty()
        if (baseText != TEXT_ANSWERED && baseText != TEXT_OUTGOING) return

        val now = System.currentTimeMillis()
        // Outgoing calls only have a known duration once the dialer showed a running timer;
        // without one the call may never have connected.
        val durationStart = session?.connectedAt ?: if (baseText == TEXT_ANSWERED) entry.timestamp else null
        if (durationStart != null) {
            val text = "$baseText · ${formatDuration(now - durationStart)}"
            dao.updateNotificationContentDetailed(entry.id, entry.title.orEmpty(), text, entry.timestamp, KIND_CALL)
        }
        dao.setDetached(entry.id, now)
        onEntriesChanged()
    }

    private suspend fun findInProgressEntry(trayKey: String): HubNotification? =
        dao.getInProgressCallEntry(
            keyPrefix = "$trayKey$ENTRY_KEY_MARKER",
            inProgressTexts = listOf(TEXT_ANSWERED, TEXT_OUTGOING),
            since = System.currentTimeMillis() - IN_PROGRESS_WINDOW_MS
        )

    private fun callState(notification: Notification): CallState {
        return when (notification.extras.getInt(EXTRA_CALL_TYPE, CALL_TYPE_UNKNOWN)) {
            CALL_TYPE_INCOMING, CALL_TYPE_SCREENING -> CallState.INCOMING
            CALL_TYPE_ONGOING -> CallState.ONGOING
            // Dialers that predate CallStyle: a ringing call carries a full-screen intent.
            else -> if (notification.fullScreenIntent != null &&
                !notification.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)
            ) CallState.INCOMING else CallState.ONGOING
        }
    }

    private fun callerName(notification: Notification): String {
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        if (title.isNotBlank()) return title
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val person = BundleCompat.getParcelable(extras, Notification.EXTRA_CALL_PERSON, Person::class.java)
            person?.name?.toString()?.let { if (it.isNotBlank()) return it }
        }
        return ""
    }

    private fun formatDuration(millis: Long): String {
        val totalSeconds = (millis / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return when {
            hours > 0 -> "${hours}h ${"%02d".format(minutes)}m"
            minutes > 0 -> "${minutes}m ${"%02d".format(seconds)}s"
            else -> "${seconds}s"
        }
    }

    private companion object {
        const val TAG = "CallNotificationTracker"
        const val KIND_CALL = "CALL"
        const val TEXT_ANSWERED = "Answered call"
        const val TEXT_OUTGOING = "Outgoing call"
        const val ENTRY_KEY_MARKER = "#call@"
        const val IN_PROGRESS_WINDOW_MS = 12L * 60 * 60 * 1000
        const val RINGING_HANDOFF_MS = 5_000L

        // Notification.EXTRA_CALL_TYPE and CALL_TYPE_* (API 31), spelled out so older
        // SDK levels and non-CallStyle dialers share one code path.
        const val EXTRA_CALL_TYPE = "android.callType"
        const val CALL_TYPE_UNKNOWN = 0
        const val CALL_TYPE_INCOMING = 1
        const val CALL_TYPE_ONGOING = 2
        const val CALL_TYPE_SCREENING = 3
    }
}
