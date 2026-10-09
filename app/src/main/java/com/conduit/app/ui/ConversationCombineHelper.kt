package com.conduit.app.ui

import android.content.Context
import com.conduit.app.data.HubNotification
import com.conduit.app.getRepresentativePackage

object ConversationCombineHelper {

    private val CONVERSATION_NAME_REGEX = Regex("""\((.+)\)$""")
    private val SENDER_NAME_REGEX = Regex("""^(.*?)\s*\((.+)\)$""")

    fun extractConversationName(title: String?): String? {
        if (title.isNullOrBlank()) return null
        val trimmed = title.trim()
        val match = CONVERSATION_NAME_REGEX.find(trimmed)
        return match?.groupValues?.get(1)?.trim() ?: trimmed
    }

    fun extractSenderName(title: String?): String? {
        if (title.isNullOrBlank()) return null
        val trimmed = title.trim()
        val match = SENDER_NAME_REGEX.find(trimmed)
        return if (match != null && match.groupValues[1].isNotBlank()) {
            match.groupValues[1].trim()
        } else {
            trimmed
        }
    }

    fun isGroupChat(title: String?): Boolean {
        if (title.isNullOrBlank()) return false
        return CONVERSATION_NAME_REGEX.containsMatchIn(title.trim())
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

    fun groupConversationItems(
        context: Context,
        items: List<HubNotification>,
        combineEnabled: Boolean
    ): List<GroupedConversationItem> {
        if (items.isEmpty()) return emptyList()
        if (!combineEnabled) {
            return items.map { notif ->
                GroupedConversationItem(
                    primaryNotification = notif,
                    allNotifications = listOf(notif),
                    isChildInConversation = false,
                    senderName = null,
                    isGroupChat = isGroupChat(notif.title),
                    combinedText = notif.text.orEmpty(),
                    subNotificationCount = 0,
                    isLastInConversation = false,
                    conversationClusterId = notif.id
                )
            }
        }

        val result = mutableListOf<GroupedConversationItem>()
        var i = 0
        while (i < items.size) {
            val clusterStart = i
            // Find contiguous conversation cluster
            while (i + 1 < items.size && areInSameConversation(context, items[i + 1], items[i])) {
                i++
            }
            val clusterEnd = i // inclusive
            val cluster = items.subList(clusterStart, clusterEnd + 1)
            val isGroup = cluster.any { isGroupChat(it.title) }
            val clusterPrimaryId = cluster.first().id
            val subCount = cluster.size - 1

            if (!isGroup) {
                // 1-on-1 chat: Each notification is its own item; first is primary, rest are children without action chips
                cluster.forEachIndexed { index, notif ->
                    result.add(
                        GroupedConversationItem(
                            primaryNotification = notif,
                            allNotifications = if (index == 0) cluster else listOf(notif),
                            isChildInConversation = index > 0,
                            senderName = null,
                            isGroupChat = false,
                            combinedText = notif.text.orEmpty(),
                            subNotificationCount = if (index == 0) subCount else 0,
                            isLastInConversation = index > 0 && index == cluster.size - 1,
                            conversationClusterId = clusterPrimaryId
                        )
                    )
                }
            } else {
                // Group chat: Partition consecutive messages within the cluster by sender
                var s = 0
                val senderGroups = mutableListOf<List<HubNotification>>()
                while (s < cluster.size) {
                    val senderGroupStart = s
                    val currentSender = extractSenderName(cluster[s].title)
                    while (s + 1 < cluster.size) {
                        val nextSender = extractSenderName(cluster[s + 1].title)
                        if ((currentSender.isNullOrBlank() && nextSender.isNullOrBlank()) ||
                            (!currentSender.isNullOrBlank() && currentSender.equals(nextSender, ignoreCase = true))
                        ) {
                            s++
                        } else {
                            break
                        }
                    }
                    senderGroups.add(cluster.subList(senderGroupStart, s + 1))
                    s++
                }

                val groupSubCount = if (senderGroups.size > 1) senderGroups.size - 1 else 0

                senderGroups.forEachIndexed { groupIdx, senderGroup ->
                    val currentSender = extractSenderName(senderGroup.first().title)
                    val chronological = senderGroup.sortedBy { it.timestamp }
                    val combinedText = chronological.mapNotNull { it.text?.trim() }
                        .filter { it.isNotEmpty() }
                        .joinToString("\n")
                        .ifEmpty { senderGroup.first().text.orEmpty() }

                    val primaryNotif = senderGroup.maxByOrNull { it.timestamp } ?: senderGroup.first()
                    val isChild = groupIdx > 0
                    val isLast = isChild && groupIdx == senderGroups.size - 1

                    result.add(
                        GroupedConversationItem(
                            primaryNotification = primaryNotif,
                            allNotifications = if (groupIdx == 0) cluster else senderGroup,
                            isChildInConversation = isChild,
                            senderName = currentSender,
                            isGroupChat = true,
                            combinedText = combinedText,
                            subNotificationCount = if (groupIdx == 0) groupSubCount else 0,
                            isLastInConversation = isLast,
                            conversationClusterId = clusterPrimaryId
                        )
                    )
                }
            }

            i++
        }
        return result
    }
}

enum class ConversationRollupState {
    PARTIALLY_EXPANDED, // State 1: Top card expanded, children hidden, [v], +N pill, double divider
    ALL_EXPANDED,        // State 2: Top card expanded, children shown & expanded, [^], double divider at end
    FULLY_COLLAPSED     // State 3: Top card collapsed, children hidden, [v], +N pill, double divider
}

data class GroupedConversationItem(
    val primaryNotification: HubNotification,
    val allNotifications: List<HubNotification>,
    val isChildInConversation: Boolean,
    val senderName: String?,
    val isGroupChat: Boolean,
    val combinedText: String,
    val subNotificationCount: Int = 0,
    val isLastInConversation: Boolean = false,
    val conversationClusterId: Int = primaryNotification.id
)
