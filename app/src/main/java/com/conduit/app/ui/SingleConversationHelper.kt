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
        
        val channelUpper = notif.channel.uppercase()
        if (channelUpper.contains("GMAIL") || channelUpper.contains("OUTLOOK") || 
            channelUpper.contains("SPARK") || channelUpper.contains("EMAIL") || 
            channelUpper.contains("MAIL") || channelUpper.contains("THUNDERBIRD") ||
            channelUpper.contains("PROTON") || channelUpper.contains("K-9") || 
            channelUpper.contains("EDISON")) {
            return false
        }
        return true
    }

    fun normalizeTitle(title: String?): String {
        if (title.isNullOrBlank()) return ""
        var t = title.trim()
        if (t.endsWith(" - Replied", ignoreCase = true)) {
            t = t.substring(0, t.length - 10).trim()
        }
        return t
    }

    fun getConversationKey(context: Context, notif: HubNotification): String {
        val repPkg = getRepresentativePackage(context, notif.packageName)
        val isGroup = ConversationCombineHelper.isGroupChat(notif.title)
        val convoName = if (isGroup) {
            ConversationCombineHelper.extractConversationName(notif.title).orEmpty()
        } else {
            normalizeTitle(notif.title)
        }
        if (convoName.isBlank() && notif.notificationKey.isNotBlank()) {
            return "$repPkg::key::${notif.notificationKey}"
        }
        if (convoName.isBlank()) {
            return "$repPkg::id::${notif.id}"
        }
        return "$repPkg::${convoName.lowercase()}"
    }

    fun groupFeedForSingleConversation(
        context: Context,
        displayNotifications: List<HubNotification>,
        allNotifications: List<HubNotification>
    ): List<SingleConversationFeedItem> {
        if (displayNotifications.isEmpty()) return emptyList()

        // Index all notifications by conversation key for fast full history lookups
        val allConvoMap = mutableMapOf<String, MutableList<HubNotification>>()
        for (notif in allNotifications) {
            if (isMessagingNotification(notif, context)) {
                val key = getConversationKey(context, notif)
                allConvoMap.getOrPut(key) { mutableListOf() }.add(notif)
            }
        }
        // Ensure each conversation history is sorted descending by timestamp (newest first)
        for (list in allConvoMap.values) {
            list.sortByDescending { it.timestamp }
        }

        val result = mutableListOf<SingleConversationFeedItem>()
        val seenConvoKeys = mutableSetOf<String>()

        for (notif in displayNotifications) {
            if (!isMessagingNotification(notif, context)) {
                result.add(SingleConversationFeedItem.Standalone(notif))
            } else {
                val key = getConversationKey(context, notif)
                if (seenConvoKeys.add(key)) {
                    val allHistory = allConvoMap[key] ?: listOf(notif)
                    val activeMsgs = allHistory.filter { !it.isArchived }
                    val hasActiveUnread = activeMsgs.isNotEmpty()
                    val isGroup = allHistory.any { ConversationCombineHelper.isGroupChat(it.title) }
                    val title = if (isGroup) {
                        ConversationCombineHelper.extractConversationName(notif.title) ?: notif.title.orEmpty()
                    } else {
                        normalizeTitle(notif.title)
                    }
                    val subCount = if (allHistory.size > 1) allHistory.size - 1 else 0

                    val rollup = SingleConversationRollup(
                        convoKey = key,
                        primaryNotification = notif,
                        allHistoryMessages = allHistory,
                        activeMessages = activeMsgs,
                        subNotificationCount = subCount,
                        isGroupChat = isGroup,
                        conversationTitle = if (title.isBlank()) notif.title.orEmpty() else title,
                        appPackage = getRepresentativePackage(context, notif.packageName),
                        hasActiveUnread = hasActiveUnread,
                        isPinned = notif.isPinned || allHistory.any { it.isPinned }
                    )
                    result.add(SingleConversationFeedItem.Conversation(rollup))
                } else {
                    // Suppressed from feed; already rolled into the conversation entry
                    continue
                }
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
