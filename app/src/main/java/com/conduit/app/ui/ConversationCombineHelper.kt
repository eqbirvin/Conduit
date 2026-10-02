package com.conduit.app.ui

import android.content.Context
import com.conduit.app.data.HubNotification
import com.conduit.app.getRepresentativePackage

object ConversationCombineHelper {

    fun extractConversationName(title: String?): String? {
        if (title.isNullOrBlank()) return null
        val trimmed = title.trim()
        val match = Regex("""\((.+)\)$""").find(trimmed)
        return match?.groupValues?.get(1)?.trim() ?: trimmed
    }

    fun extractSenderName(title: String?): String? {
        if (title.isNullOrBlank()) return null
        val trimmed = title.trim()
        val match = Regex("""^(.*?)\s*\((.+)\)$""").find(trimmed)
        return if (match != null && match.groupValues[1].isNotBlank()) {
            match.groupValues[1].trim()
        } else {
            trimmed
        }
    }

    fun isGroupChat(title: String?): Boolean {
        if (title.isNullOrBlank()) return false
        return Regex("""\((.+)\)$""").containsMatchIn(title.trim())
    }

    fun areInSameConversation(
        context: Context,
        current: HubNotification,
        previous: HubNotification
    ): Boolean {
        // Both items must be MESSAGE kind
        if (current.kind != "MESSAGE" || previous.kind != "MESSAGE") return false

        // Both items must be from the same app (representative package)
        val repCurrent = getRepresentativePackage(context, current.packageName)
        val repPrevious = getRepresentativePackage(context, previous.packageName)
        if (repCurrent != repPrevious) return false

        // 1. Same notification key
        if (current.notificationKey.isNotBlank() && current.notificationKey == previous.notificationKey) {
            return true
        }

        // 2. Identical title (e.g. 1-on-1 chats "Danae Shepard")
        if (!current.title.isNullOrBlank() && current.title.equals(previous.title, ignoreCase = true)) {
            return true
        }

        // 3. Same group chat name in title (e.g. "Alice (Group)" and "Bob (Group)")
        val convoA = extractConversationName(current.title)
        val convoB = extractConversationName(previous.title)
        if (!convoA.isNullOrBlank() && convoA.equals(convoB, ignoreCase = true)) {
            return true
        }

        return false
    }

    fun findParentInConversation(
        context: Context,
        items: List<HubNotification>,
        currentIndex: Int
    ): HubNotification? {
        if (currentIndex <= 0 || currentIndex >= items.size) return null
        val current = items[currentIndex]
        val previous = items[currentIndex - 1]
        if (!areInSameConversation(context, current, previous)) return null

        var parentIdx = currentIndex - 1
        while (parentIdx > 0 && areInSameConversation(context, items[parentIdx], items[parentIdx - 1])) {
            parentIdx--
        }
        return items[parentIdx]
    }

    fun getChildDisplayText(child: HubNotification, parent: HubNotification): String {
        val text = child.text.orEmpty()
        if (isGroupChat(child.title) || isGroupChat(parent.title)) {
            val childSender = extractSenderName(child.title)
            val parentSender = extractSenderName(parent.title)
            if (!childSender.isNullOrBlank() && !childSender.equals(parentSender, ignoreCase = true)) {
                return "$childSender: $text"
            }
        }
        return text
    }
}
