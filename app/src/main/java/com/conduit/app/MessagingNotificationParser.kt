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
        "com.samsung.android.messaging",
        "xyz.klinker.messenger",
        "com.p1.chompsms",
        "com.moez.QKSMS",
        "dev.octoshrimpy.quik",
        "dev.octoshrimpy.quik.fdroid",
        "com.anindra.messages",
        "com.simplemobiletools.smsmessenger",
        "org.fossify.messages",
        "com.calea.echo",
        "rpkandrodev.yaata",
        "com.bluebubbles.messaging",
        "me.tagavari.airmessage",
        "org.thoughtcrime.securesms",
        "com.whatsapp",
        "com.whatsapp.w4b",
        "com.Slack",
        "com.google.android.apps.dynamite",
        "com.discord",
        "com.discord.canary",
        "com.facebook.orca",
        "org.telegram.messenger",
        "com.beeper.chat",
        "com.beeper.ima",
        "com.beeper.android",
        "com.skype.raider",
        "com.skype.m2",
        "com.viber.voip",
        "com.groupme.android",
        "com.textra",
        "net.thunderbird.android",
        "net.thunderbird.android.beta",
        "eu.faircode.email",
        "com.fsck.k9",
        "com.easilydo.mail",
        "ch.protonmail.android",
        "com.yahoo.mobile.client.android.mail",
        "com.yahoo.mobile.client.android.mail.go",
        "com.pingapp.app"
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
            val isNullSenderSelf = senderName == null && (msgs.size > 1 || selfDisplayName != null || NULL_SENDER_IS_SELF_PACKAGES.contains(packageName))

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
                !convoTitle.isNullOrBlank() && !senderName.isNullOrBlank() && !isFromSelf -> {
                    if (fallbackTitle.contains("(") && fallbackTitle.contains(")")) fallbackTitle else "$senderName ($convoTitle)"
                }
                !convoTitle.isNullOrBlank() && !isFromSelf -> convoTitle
                !senderName.isNullOrBlank() && !isFromSelf -> senderName
                fallbackTitle.isNotBlank() -> fallbackTitle
                else -> channelName
            }

            results.add(ExtractedMessage(msgTitle, msgText, msgTimestamp, isSelfReply = isFromSelf))
        }

        return results
    }
}
