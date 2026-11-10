package com.conduit.app

import android.app.Notification
import android.app.Person
import android.content.Context
import android.os.Build
import android.os.Parcelable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.os.BundleCompat
import com.conduit.app.data.CallDiagnosticsEvent
import com.conduit.app.data.CallDiagnosticsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Feeds phone-app notification posts and removals, with Conduit's decision for each, into
 * [CallDiagnosticsStore] when the developer setting is on. Snapshots are taken on the caller's
 * thread (plain field reads); the disk write happens on the owner's IO scope.
 */
class CallDiagnosticsRecorder(context: Context, scope: CoroutineScope) {

    private val prefs = context.applicationContext.getSharedPreferences("conduit_prefs", Context.MODE_PRIVATE)
    private val store = CallDiagnosticsStore(context)
    private val pending = Channel<CallDiagnosticsEvent>(Channel.UNLIMITED)

    init {
        scope.launch {
            var sincePrune = 0
            for (event in pending) {
                store.append(event)
                if (++sincePrune >= PRUNE_EVERY) {
                    store.prune()
                    sincePrune = 0
                }
            }
        }
    }

    fun recordPosted(sbn: StatusBarNotification, decision: String) {
        if (!isEnabled() || !isCallRelated(sbn)) return
        pending.trySend(snapshot(sbn, "POSTED", decision))
    }

    fun recordRemoved(sbn: StatusBarNotification, reason: Int, decision: String) {
        if (!isEnabled() || !isCallRelated(sbn)) return
        pending.trySend(snapshot(sbn, "REMOVED (${reasonName(reason)})", decision))
    }

    private fun isEnabled() = prefs.getBoolean(CallDiagnosticsStore.PREF_KEY_ENABLED, false)

    private fun isCallRelated(sbn: StatusBarNotification): Boolean {
        val pkg = sbn.packageName
        val category = sbn.notification.category
        return ChannelRegistry.supportedApps[pkg]?.first == "channel_phone" ||
            pkg.contains(".dialer", ignoreCase = true) ||
            pkg.endsWith(".phone", ignoreCase = true) ||
            category == Notification.CATEGORY_CALL ||
            category == Notification.CATEGORY_MISSED_CALL
    }

    private fun snapshot(sbn: StatusBarNotification, event: String, decision: String): CallDiagnosticsEvent {
        val notification = sbn.notification
        val extras = notification.extras
        val fields = linkedMapOf<String, String>()
        fun putText(name: String, key: String) {
            extras.getCharSequence(key)?.toString()?.let { fields[name] = quote(it) }
        }
        putText("title", Notification.EXTRA_TITLE)
        putText("titleBig", Notification.EXTRA_TITLE_BIG)
        putText("text", Notification.EXTRA_TEXT)
        putText("bigText", Notification.EXTRA_BIG_TEXT)
        putText("subText", Notification.EXTRA_SUB_TEXT)
        putText("summaryText", Notification.EXTRA_SUMMARY_TEXT)
        putText("infoText", Notification.EXTRA_INFO_TEXT)
        putText("conversationTitle", Notification.EXTRA_CONVERSATION_TITLE)
        notification.tickerText?.toString()?.let { fields["ticker"] = quote(it) }
        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.let { lines ->
            fields["textLines"] = lines.joinToString(" | ") { quote(it?.toString().orEmpty()) }
        }
        extras.getString(Notification.EXTRA_TEMPLATE)?.let { fields["template"] = it }
        if (extras.containsKey(EXTRA_CALL_TYPE)) fields["callType"] = callTypeName(extras.getInt(EXTRA_CALL_TYPE))
        if (extras.containsKey(Notification.EXTRA_SHOW_CHRONOMETER)) {
            fields["showChronometer"] = extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER).toString()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            BundleCompat.getParcelable(extras, Notification.EXTRA_CALL_PERSON, Person::class.java)?.let {
                fields["callPerson"] = quote(it.name?.toString().orEmpty())
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            BundleCompat.getParcelableArrayList(extras, Notification.EXTRA_PEOPLE_LIST, Person::class.java)?.let { people ->
                fields["people"] = people.joinToString(" | ") { quote(it.name?.toString().orEmpty()) }
            }
        }
        BundleCompat.getParcelableArray(extras, Notification.EXTRA_MESSAGES, Parcelable::class.java)?.let {
            fields["messageCount"] = it.size.toString()
        }
        fields["actions"] = notification.actions?.joinToString(" | ") { quote(it.title?.toString().orEmpty()) } ?: "-"

        return CallDiagnosticsEvent(
            time = System.currentTimeMillis(),
            event = event,
            decision = decision,
            packageName = sbn.packageName,
            key = sbn.key,
            tag = sbn.tag,
            id = sbn.id,
            category = notification.category,
            channelId = notification.channelId,
            groupKey = notification.group,
            flags = flagsDescription(notification.flags),
            ongoing = sbn.isOngoing,
            postTime = sbn.postTime,
            whenTime = notification.`when`,
            hasFullScreenIntent = notification.fullScreenIntent != null,
            fields = fields,
            extraKeys = extras.keySet().sorted()
        )
    }

    private fun quote(value: String) = "\"" + value.replace("\n", "\\n").take(MAX_FIELD_CHARS) + "\""

    private fun flagsDescription(flags: Int): String {
        val names = FLAG_NAMES.filter { (bit, _) -> (flags and bit) != 0 }.map { it.second }
        return "0x" + Integer.toHexString(flags) + if (names.isEmpty()) "" else " (${names.joinToString(", ")})"
    }

    private fun callTypeName(type: Int) = when (type) {
        1 -> "INCOMING"
        2 -> "ONGOING"
        3 -> "SCREENING"
        else -> "UNKNOWN($type)"
    }

    private fun reasonName(reason: Int) = when (reason) {
        NotificationListenerService.REASON_CLICK -> "CLICK"
        NotificationListenerService.REASON_CANCEL -> "CANCEL"
        NotificationListenerService.REASON_CANCEL_ALL -> "CANCEL_ALL"
        NotificationListenerService.REASON_APP_CANCEL -> "APP_CANCEL"
        NotificationListenerService.REASON_APP_CANCEL_ALL -> "APP_CANCEL_ALL"
        NotificationListenerService.REASON_LISTENER_CANCEL -> "LISTENER_CANCEL"
        NotificationListenerService.REASON_GROUP_SUMMARY_CANCELED -> "GROUP_SUMMARY_CANCELED"
        NotificationListenerService.REASON_SNOOZED -> "SNOOZED"
        NotificationListenerService.REASON_TIMEOUT -> "TIMEOUT"
        else -> "REASON_$reason"
    }

    private companion object {
        const val PRUNE_EVERY = 50
        const val MAX_FIELD_CHARS = 300
        // Notification.EXTRA_CALL_TYPE (API 31), spelled out for older SDK levels.
        const val EXTRA_CALL_TYPE = "android.callType"

        val FLAG_NAMES = listOf(
            0x00000001 to "SHOW_LIGHTS",
            0x00000002 to "ONGOING_EVENT",
            0x00000004 to "INSISTENT",
            0x00000008 to "ONLY_ALERT_ONCE",
            0x00000010 to "AUTO_CANCEL",
            0x00000020 to "NO_CLEAR",
            0x00000040 to "FOREGROUND_SERVICE",
            0x00000080 to "HIGH_PRIORITY",
            0x00000100 to "LOCAL_ONLY",
            0x00000200 to "GROUP_SUMMARY",
            0x00001000 to "BUBBLE"
        )
    }
}
