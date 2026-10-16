package com.conduit.app.ui

import android.content.Context
import com.conduit.app.ChannelRegistry
import com.conduit.app.data.HubNotification
import com.conduit.app.getRepresentativePackage

object SingleConversationHelper {

    val EMAIL_CHANNELS: Set<String> = setOf(
        "channel_gmail",
        "channel_spark",
        "channel_outlook",
        "channel_thunderbird",
        "channel_fairemail",
        "channel_k9_mail",
        "channel_edison_mail",
        "channel_proton_mail",
        "channel_yahoo_mail",
        "channel_spike"
    )

    fun isMessagingNotification(notif: HubNotification, context: Context): Boolean {
        if (notif.kind != "MESSAGE") return false
        val repPkg = getRepresentativePackage(context, notif.packageName)
        val channelKey = ChannelRegistry.supportedApps[repPkg]?.first
            ?: ChannelRegistry.supportedApps[notif.packageName]?.first
            ?: ""
        if (EMAIL_CHANNELS.contains(channelKey)) return false

        val ch = notif.channel
        if (ch.contains("MAIL", ignoreCase = true) ||
            ch.contains("OUTLOOK", ignoreCase = true) ||
            ch.contains("SPARK", ignoreCase = true) ||
            ch.contains("THUNDERBIRD", ignoreCase = true) ||
            ch.contains("PROTON", ignoreCase = true) ||
            ch.contains("K-9", ignoreCase = true) ||
            ch.contains("EDISON", ignoreCase = true)
        ) {
            return false
        }
        return true
    }

    fun normalizeTitle(title: String?): String {
        if (title.isNullOrBlank()) return ""
        var t = title.trim()
        while (t.endsWith(" - Replied", ignoreCase = true)) {
            t = t.substring(0, t.length - 10).trim()
        }
        return t
    }

    fun getConversationKey(context: Context, notif: HubNotification): String {
        val repPkg = getRepresentativePackage(context, notif.packageName)
        val normalized = normalizeTitle(notif.title)
        val isGroup = ConversationCombineHelper.isGroupChat(normalized)
        val convoName = if (isGroup) {
            ConversationCombineHelper.extractConversationName(normalized).orEmpty()
        } else {
            normalized
        }
        if (convoName.isBlank() && notif.notificationKey.isNotBlank()) {
            return "$repPkg::key::${notif.notificationKey}"
        }
        if (convoName.isBlank()) {
            return "$repPkg::id::${notif.id}"
        }
        return "$repPkg::${convoName.lowercase()}"
    }

    fun buildRollup(
        context: Context,
        convoKey: String,
        primaryNotification: HubNotification,
        allHistory: List<HubNotification>
    ): SingleConversationRollup {
        val activeMsgs = allHistory.filter { !it.isArchived }
        val hasActiveUnread = activeMsgs.isNotEmpty()
        val isGroup = allHistory.any { ConversationCombineHelper.isGroupChat(normalizeTitle(it.title)) }
        val normalizedPrimaryTitle = normalizeTitle(primaryNotification.title)
        val title = if (isGroup) {
            val groupMatchSource = allHistory.firstOrNull {
                ConversationCombineHelper.isGroupChat(normalizeTitle(it.title))
            } ?: primaryNotification
            ConversationCombineHelper.extractConversationName(normalizeTitle(groupMatchSource.title))
                ?: normalizedPrimaryTitle
        } else {
            normalizedPrimaryTitle
        }
        val subCount = if (allHistory.size > 1) allHistory.size - 1 else 0

        return SingleConversationRollup(
            convoKey = convoKey,
            primaryNotification = primaryNotification,
            allHistoryMessages = allHistory,
            activeMessages = activeMsgs,
            subNotificationCount = subCount,
            isGroupChat = isGroup,
            conversationTitle = title.ifBlank { primaryNotification.title.orEmpty() },
            appPackage = getRepresentativePackage(context, primaryNotification.packageName),
            hasActiveUnread = hasActiveUnread,
            isPinned = primaryNotification.isPinned || allHistory.any { it.isPinned }
        )
    }

    fun buildRollupForKey(
        context: Context,
        convoKey: String,
        allNotifications: List<HubNotification>
    ): SingleConversationRollup? {
        val seenIds = HashSet<Int>()
        val messages = mutableListOf<HubNotification>()
        for (notif in allNotifications) {
            if (!seenIds.add(notif.id)) continue
            if (isMessagingNotification(notif, context) && getConversationKey(context, notif) == convoKey) {
                messages.add(notif)
            }
        }
        if (messages.isEmpty()) return null
        messages.sortByDescending { it.timestamp }
        return buildRollup(
            context = context,
            convoKey = convoKey,
            primaryNotification = messages.first(),
            allHistory = messages
        )
    }

    fun groupFeedForSingleConversation(
        context: Context,
        displayNotifications: List<HubNotification>,
        allNotifications: List<HubNotification>
    ): List<SingleConversationFeedItem> {
        if (displayNotifications.isEmpty()) return emptyList()

        // Index all notifications by conversation key once and cache per-ID keys
        val allConvoMap = HashMap<String, MutableList<HubNotification>>()
        val convoKeyById = HashMap<Int, String>(allNotifications.size)
        val seenIds = HashSet<Int>(allNotifications.size)

        for (notif in allNotifications) {
            if (!seenIds.add(notif.id)) continue
            if (isMessagingNotification(notif, context)) {
                val key = getConversationKey(context, notif)
                convoKeyById[notif.id] = key
                allConvoMap.getOrPut(key) { mutableListOf() }.add(notif)
            }
        }
        // Ensure each conversation history is sorted descending by timestamp (newest first)
        for (list in allConvoMap.values) {
            list.sortByDescending { it.timestamp }
        }

        val result = ArrayList<SingleConversationFeedItem>(displayNotifications.size)
        val seenConvoKeys = HashSet<String>()
        val seenDisplayIds = HashSet<Int>(displayNotifications.size)

        for (notif in displayNotifications) {
            if (!seenDisplayIds.add(notif.id)) continue
            val key = if (seenIds.contains(notif.id)) {
                convoKeyById[notif.id]
            } else if (isMessagingNotification(notif, context)) {
                getConversationKey(context, notif)
            } else {
                null
            }

            if (key == null) {
                result.add(SingleConversationFeedItem.Standalone(notif))
            } else if (seenConvoKeys.add(key)) {
                val allHistory = allConvoMap[key] ?: listOf(notif)
                result.add(
                    SingleConversationFeedItem.Conversation(
                        buildRollup(
                            context = context,
                            convoKey = key,
                            primaryNotification = notif,
                            allHistory = allHistory
                        )
                    )
                )
            }
        }

        return result
    }
}

data class SingleConversationRollup(
    val convoKey: String,
    val primaryNotification: HubNotification,
    val allHistoryMessages: List<HubNotification>,
    val activeMessages: List<HubNotification>,
    val subNotificationCount: Int,
    val isGroupChat: Boolean,
    val conversationTitle: String,
    val appPackage: String,
    val hasActiveUnread: Boolean,
    val isPinned: Boolean
)

sealed interface SingleConversationFeedItem {
    val id: Int
    val timestamp: Long
    val isPinned: Boolean

    data class Conversation(
        val rollup: SingleConversationRollup
    ) : SingleConversationFeedItem {
        override val id: Int get() = rollup.primaryNotification.id
        override val timestamp: Long get() = rollup.primaryNotification.timestamp
        override val isPinned: Boolean get() = rollup.isPinned
    }

    data class Standalone(
        val notification: HubNotification
    ) : SingleConversationFeedItem {
        override val id: Int get() = notification.id
        override val timestamp: Long get() = notification.timestamp
        override val isPinned: Boolean get() = notification.isPinned
    }
}
