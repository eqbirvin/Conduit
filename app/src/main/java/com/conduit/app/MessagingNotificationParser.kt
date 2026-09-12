package com.conduit.app

import android.app.Notification
import android.os.Build
import android.service.notification.StatusBarNotification
import android.util.Log

data class ExtractedMessage(
    val title: String,
    val text: String,
    val timestamp: Long,
    val isSelfReply: Boolean = false
)

object MessagingNotificationParser {
    private const val TAG = "MessagingParser"

    private val NULL_SENDER_IS_SELF_PACKAGES = setOf(
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging"
    )

    fun extractMessages(
        sbn: StatusBarNotification,
        fallbackTitle: String,
        fallbackText: String,
        channelName: String
    ): List<ExtractedMessage> {
        val extras = sbn.notification.extras ?: return emptyList()
        val messagesArray = extras.getParcelableArray(Notification.EXTRA_MESSAGES) ?: return emptyList()

        val msgs = try {
            Notification.MessagingStyle.Message.getMessagesFromBundleArray(messagesArray)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse MessagingStyle messages for ${sbn.packageName}", e)
            return emptyList()
        }

        if (msgs.isNullOrEmpty()) return emptyList()

        val packageName = sbn.packageName
        val selfDisplayName = extras.getCharSequence(Notification.EXTRA_SELF_DISPLAY_NAME)?.toString()
        val convoTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
        val postTime = sbn.postTime
        val results = mutableListOf<ExtractedMessage>()

        for ((index, msg) in msgs.withIndex()) {
            val senderName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                msg.senderPerson?.name?.toString()
            } else {
                msg.sender?.toString()
            }

            val isSenderSameAsTitle = senderName != null && senderName.equals(fallbackTitle, ignoreCase = true) && !senderName.equals("You", ignoreCase = true)
            val isSelfDisplayNameBroken = packageName == "com.textra" && selfDisplayName != null && selfDisplayName.equals(fallbackTitle, ignoreCase = true)
            val isNullSenderSelf = senderName == null && NULL_SENDER_IS_SELF_PACKAGES.contains(packageName)

            val isFromSelf = !isSenderSameAsTitle && (isNullSenderSelf || 
                (!isSelfDisplayNameBroken && senderName != null && selfDisplayName != null && senderName.equals(selfDisplayName, ignoreCase = true)) || 
                (senderName != null && senderName.equals("You", ignoreCase = true)))

            var msgText = msg.text?.toString()?.trim().orEmpty()
            if (msgText.isBlank() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && msg.dataMimeType != null) {
                msgText = if (msg.dataMimeType?.startsWith("image/") == true) "Attachment: Image" else "Attachment"
            }
            if (msgText.isBlank() && index == msgs.size - 1 && fallbackText.isNotBlank()) {
                msgText = fallbackText.trim()
            }
            if (msgText.isBlank()) continue

            val msgTimestamp = if (msg.timestamp > 0) {
                msg.timestamp
            } else {
                postTime - (msgs.size - 1 - index) * 1000L
            }

            val msgTitle = when {
                !convoTitle.isNullOrBlank() && !senderName.isNullOrBlank() -> {
                    if (fallbackTitle.contains("(") && fallbackTitle.contains(")")) fallbackTitle else "$senderName ($convoTitle)"
                }
                !convoTitle.isNullOrBlank() -> convoTitle
                !senderName.isNullOrBlank() -> senderName
                fallbackTitle.isNotBlank() -> fallbackTitle
                else -> channelName
            }

            results.add(ExtractedMessage(msgTitle, msgText, msgTimestamp, isSelfReply = isFromSelf))
        }

        return results
    }
}
