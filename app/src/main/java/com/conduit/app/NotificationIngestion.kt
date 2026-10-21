package com.conduit.app

import com.conduit.app.HubNotificationListenerService.Companion.supportedApps
import android.app.Notification
import android.content.Context
import android.os.Build
import android.service.notification.StatusBarNotification
import android.util.Log
import com.conduit.app.data.HubNotification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

    internal fun HubNotificationListenerService.logIngestionDiagnostic(sbn: StatusBarNotification, decision: String) {
        val prefs = applicationContext.getSharedPreferences("conduit_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("ingestion_diagnostics", false)) return
        
        val packageName = sbn.packageName
        val extras = sbn.notification.extras
        val flags = sbn.notification.flags
        val hasGroupSummary = (flags and Notification.FLAG_GROUP_SUMMARY) != 0
        val category = sbn.notification.category
        val isOngoing = sbn.isOngoing
        val eTitle = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "null"
        val eText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.take(60) ?: "null"
        val selfDisplayName = extras.getCharSequence(Notification.EXTRA_SELF_DISPLAY_NAME)?.toString() ?: "null"
        val convoTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString() ?: "null"
        
        val messagesArray = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        val msgCount = messagesArray?.size ?: 0
        var lastSenderPerson = "null"
        var lastLegacySender = "null"
        var lastText = "null"
        
        if (messagesArray != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val msgs = Notification.MessagingStyle.Message.getMessagesFromBundleArray(messagesArray)
                if (msgs.isNotEmpty()) {
                    val lastMsg = msgs.last()
                    lastSenderPerson = lastMsg.senderPerson?.name?.toString() ?: "null"
                    lastLegacySender = lastMsg.sender?.toString() ?: "null"
                    lastText = lastMsg.text?.toString()?.take(40) ?: "null"
                }
            } catch (e: Exception) {}
        }
        
        val logLine = "pkg=$packageName key=${sbn.key} flags=$flags(summary=$hasGroupSummary) category=$category ongoing=$isOngoing " +
                      "EXTRA_TITLE=[$eTitle] EXTRA_TEXT=[$eText] EXTRA_SELF_DISPLAY_NAME=[$selfDisplayName] " +
                      "EXTRA_CONVERSATION_TITLE=[$convoTitle] msgCount=$msgCount lastSenderPerson=[$lastSenderPerson] " +
                      "lastLegacySender=[$lastLegacySender] lastText=[$lastText] -> DECISION: $decision"
        android.util.Log.d("ConduitIngest", logLine)
    }

    internal fun HubNotificationListenerService.ingestPostedNotification(sbn: StatusBarNotification?) {
        sbn?.let {
            val packageName = it.packageName
            val isSystemPhoneFallback = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && it.notification.category == Notification.CATEGORY_CALL) ||
                                        packageName.contains(".dialer", ignoreCase = true) ||
                                        packageName.endsWith(".phone", ignoreCase = true)
            
            val isSupported = supportedApps.containsKey(packageName) || isSystemPhoneFallback
            if (!isSupported) return@let

            // Ignore group summaries to avoid duplicate notifications (since apps post both child and summary)
            if ((it.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) {
                logIngestionDiagnostic(it, "discarded-summary")
                return@let
            }

            val notificationKey = it.key
            
            val postedActions = mutableListOf<Notification.Action>()
            it.notification.actions?.let { a -> postedActions.addAll(a) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val contextual = it.notification.extras.getParcelableArrayList<Notification.Action>("android.contextualActions")
                    contextual?.let { c -> postedActions.addAll(c) }
                } catch (e: ClassCastException) {} catch (e: android.os.BadParcelableException) {}
            }
            actionCache[notificationKey] = postedActions
            contentIntentCache.remove(notificationKey)

            
            val appInfo = supportedApps[packageName]
            
            val channel = appInfo?.second ?: if (isSystemPhoneFallback) "Phone" else null
            
            if (channel != null) {
                // Answered and outgoing calls arrive as ongoing call notifications
                if (callTracker.isOngoingCall(it)) {
                    callTracker.onCallPosted(it, channel, appInfo?.first ?: "channel_phone")
                    logIngestionDiagnostic(it, "call-tracked")
                    return@let
                }

                // Ignore ongoing notifications (like background services)
                if (it.isOngoing) {
                    logIngestionDiagnostic(it, "discarded-ongoing")
                    return@let
                }

                val extras = it.notification.extras
                var title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
                if (title.isBlank()) {
                    title = extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString() ?: ""
                }
                
                val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                val extraText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
                var text = if (!bigText.isNullOrBlank() && bigText.length > extraText.length) {
                    bigText
                } else if (extraText.isNotBlank()) {
                    extraText
                } else {
                    extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString() ?: ""
                }
                if (text.isBlank()) {
                    val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
                    if (lines != null && lines.isNotEmpty()) {
                        text = lines.last()?.toString() ?: ""
                    }
                }
                
                val messagesArray = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
                
                // Fallback for apps like Textra that only use MessagingStyle fields
                if (title.isBlank() && text.isBlank() && messagesArray != null) {
                    try {
                        val msgs = Notification.MessagingStyle.Message.getMessagesFromBundleArray(messagesArray)
                        if (msgs.isNotEmpty()) {
                            val lastMsg = msgs.last()
                            val senderName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                lastMsg.senderPerson?.name?.toString()
                            } else {
                                lastMsg.sender?.toString()
                            }
                            title = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString() ?: senderName ?: ""
                            text = lastMsg.text?.toString() ?: ""
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("Conduit", "Failed to extract fallback text from MessagingStyle", e)
                    }
                }
                
                if (text.isBlank()) {
                    text = it.notification.tickerText?.toString() ?: ""
                }
                val timestamp = it.postTime

                val extractedMessages = MessagingNotificationParser.extractMessages(
                    sbn = it,
                    fallbackTitle = title,
                    fallbackText = text,
                    channelName = channel
                )

                // Intercept MessagingStyle self-replies
                var isSelfReply = false
                var replyText = ""

                if (channel != "Snapchat" && extractedMessages.isNotEmpty() && extractedMessages.last().isSelfReply) {
                    isSelfReply = true
                    replyText = extractedMessages.last().text
                }

                if (isSelfReply && replyText.isNotEmpty()) {
                    var hasExisting = false
                    kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                        val existingByKey = database.notificationDao().getMostRecentByKey(notificationKey)
                        val existing = existingByKey ?: database.notificationDao().getMostRecentByTitleAndPackage(packageName, title)
                        if (existing != null) {
                            hasExisting = true
                            val suffix = "\n\u21aa You: $replyText"
                            val currentText = existing.text ?: ""
                            val currentTitle = existing.title ?: ""
                            
                            val textUpdated = !currentText.endsWith(suffix)
                            val titleUpdated = !currentTitle.endsWith(" - Replied")
                            
                            if (textUpdated || titleUpdated) {
                                val newText = if (textUpdated) currentText + suffix else currentText
                                val newTitle = if (titleUpdated) "$currentTitle - Replied" else currentTitle
                                database.notificationDao().updateAndArchive(existing.id, newTitle, newText, timestamp)
                                com.conduit.app.widget.WidgetUpdater.updateAllWidgets(this@ingestPostedNotification)
                            }
                        }
                    }
                    if (hasExisting) {
                        logIngestionDiagnostic(it, "discarded-self-reply")
                        return@let // Discard this duplicate system tray post since it's just our own reply!
                    }
                }

                val incomingFromStyle = extractedMessages.filter { !it.isSelfReply }
                val messagesToIngest: List<ExtractedMessage> = if (extractedMessages.isNotEmpty()) {
                    incomingFromStyle
                } else if (text.isNotBlank() || title.isNotBlank()) {
                    listOf(ExtractedMessage(title, text, timestamp))
                } else {
                    emptyList()
                }

                if (messagesToIngest.isEmpty()) {
                    logIngestionDiagnostic(it, "discarded-blank")
                    return@let
                }

                val prefKey = appInfo?.first ?: if (isSystemPhoneFallback) "channel_phone" else null
                val prefs = applicationContext.getSharedPreferences("conduit_prefs", Context.MODE_PRIVATE)
                val enabled = if (prefKey != null) prefs.getBoolean(prefKey, true) else true
                if (!enabled) {
                    logIngestionDiagnostic(it, "disabled-channel")
                    return@let
                }

                Log.d("HubService", "Intercepted MSG [$channel]: $title - $text (count=${messagesToIngest.size})")

                val overrideMap = emptyMap<String, String>()
                var kind = overrideMap[packageName]
                if (kind == null) {
                    val category = it.notification.category
                    val hasMessagingStyle = it.notification.extras.containsKey(Notification.EXTRA_MESSAGES)
                    val hasReply = postedActions.any { a -> a.remoteInputs?.isNotEmpty() == true }
                    val isMessage = hasMessagingStyle || hasReply || category == Notification.CATEGORY_MESSAGE || category == Notification.CATEGORY_EMAIL
                    val isCall = category == Notification.CATEGORY_CALL || category == Notification.CATEGORY_MISSED_CALL
                    kind = if (isMessage) "MESSAGE" else if (isCall) "CALL" else "OTHER"
                }

                val contentIntent = it.notification.contentIntent
                if (contentIntent != null) {
                    contentIntentCache[notificationKey] = contentIntent
                }

                scope.launch {
                    notificationMutex.withLock {
                        val existingForConversation = database.notificationDao().getNotificationsByKey(notificationKey)
                        var insertedAny = false
                        var latestNewMessage: ExtractedMessage? = null

                        for (msgItem in messagesToIngest) {
                            val itemTitle = msgItem.title
                            val itemText = msgItem.text
                            val itemTimestamp = msgItem.timestamp

                            // Check custom block rules
                            if (shouldBlockNotification(applicationContext, packageName, itemTitle, itemText)) {
                                logIngestionDiagnostic(it, "blocked")
                                continue
                            }

                            // Ignore background work notifications
                            if (itemText.contains("doing work in the background", ignoreCase = true) ||
                                itemText.contains("updating messages", ignoreCase = true)) {
                                logIngestionDiagnostic(it, "blocked")
                                continue
                            }

                            // Reboot-repost dedup:
                            val unarchivedMatch = database.notificationDao().getUnarchivedExactMatch(packageName, itemTitle, itemText)
                            if (unarchivedMatch != null) {
                                database.notificationDao().adoptRow(unarchivedMatch.id, notificationKey, itemTimestamp, kind)
                                logIngestionDiagnostic(it, "duplicate")
                                continue
                            }

                            // Check if this message was already ingested for this conversation key
                            val alreadyInConversation = existingForConversation.any { existing ->
                                existing.text == itemText
                            }
                            if (alreadyInConversation) {
                                logIngestionDiagnostic(it, "duplicate")
                                continue
                            }

                            // Prevent aggressive duplicates when Google Messages changes the notification key
                            val exactMatch = database.notificationDao().getMostRecentExactMatch(packageName, itemTitle, itemText)
                            if (exactMatch != null && Math.abs(itemTimestamp - exactMatch.timestamp) < 60000) {
                                logIngestionDiagnostic(it, "duplicate")
                                continue // Exact same message received within 60 seconds, ignore
                            }

                            // Prevent native apps from resurrecting a notification we just locally archived with a smart reply
                            val recentReplyMatch = database.notificationDao().getMostRecentByTitleAndPackage(packageName, itemTitle)
                            if (recentReplyMatch != null && recentReplyMatch.text != null && recentReplyMatch.text.contains("\n\u21aa You:")) {
                                val originalText = recentReplyMatch.text.substringBefore("\n\u21aa You:")
                                if (originalText == itemText) {
                                    logIngestionDiagnostic(it, "duplicate")
                                    continue
                                }
                            }

                            // Handle edited messages for MessagingStyle: same conversation key & timestamp, but updated text
                            val editedMatch = existingForConversation.find { existing ->
                                !existing.isArchived && Math.abs(existing.timestamp - itemTimestamp) < 1000
                            }
                            if (editedMatch != null) {
                                database.notificationDao().updateNotificationContentDetailed(editedMatch.id, itemTitle, itemText, itemTimestamp, kind)
                                logIngestionDiagnostic(it, "ingested-update")
                                continue
                            }

                            // In-place update for non-messaging status notifications (downloads, media players, etc.)
                            if (kind != "MESSAGE" && incomingFromStyle.isEmpty()) {
                                val existingActive = database.notificationDao().getActiveNotificationByKey(notificationKey)
                                if (existingActive != null) {
                                    if (existingActive.title == itemTitle && existingActive.text == itemText) {
                                        database.notificationDao().updateNotificationContentDetailed(existingActive.id, itemTitle, itemText, itemTimestamp, kind)
                                        logIngestionDiagnostic(it, "duplicate")
                                    } else {
                                        database.notificationDao().updateNotificationContentDetailed(existingActive.id, itemTitle, itemText, itemTimestamp, kind)
                                        logIngestionDiagnostic(it, "ingested-update")
                                    }
                                    continue
                                }
                            }

                            // Insert new message
                            val hubNotification = HubNotification(
                                packageName = packageName,
                                notificationKey = notificationKey,
                                title = itemTitle,
                                text = itemText,
                                channel = channel,
                                timestamp = itemTimestamp,
                                kind = kind
                            )
                            database.notificationDao().insert(hubNotification)
                            logIngestionDiagnostic(it, "ingested")
                            insertedAny = true
                            latestNewMessage = msgItem
                        }

                        if (insertedAny) {
                            com.conduit.app.widget.WidgetUpdater.updateAllWidgets(this@ingestPostedNotification)
                            
                            val bubbleTarget = latestNewMessage ?: messagesToIngest.lastOrNull()
                            if (bubbleTarget != null) {
                                // Post native Bubble notification
                                val bubblePrefs = applicationContext.getSharedPreferences("conduit_prefs", Context.MODE_PRIVATE)
                                if (bubblePrefs.getBoolean("enable_bubbles", false)) {
                                    postBubbleNotification(applicationContext, packageName, bubbleTarget.title, bubbleTarget.text)
                                }

                                // Trigger Bracket popup if enabled
                                if (bubblePrefs.getBoolean("enable_bracket", false) && bubblePrefs.getBoolean("bracket_notification_popup", true)) {
                                    handler.post {
                                        showBracketNotificationPopup(packageName, contentIntent)
                                    }
                                }
                            }
                        }

                        // Future-proofing: If enabled, dismiss the original notification to avoid duplication
                        if (DISMISS_ORIGINAL_NOTIFICATIONS) {
                            try {
                                cancelNotification(notificationKey)
                            } catch (e: Exception) {
                                android.util.Log.e("Conduit", "Exception caught", e)
                            }
                        }

                        scheduleUpdatePersistentNotification()
                    }
                }
            }
        }
    }

    internal fun HubNotificationListenerService.shouldBlockNotification(context: Context, packageName: String, title: String, text: String): Boolean {
        val prefs = context.getSharedPreferences("conduit_prefs", Context.MODE_PRIVATE)
        val blockedSet = prefs.getStringSet("blocked_rules", emptySet()) ?: emptySet()
        for (ruleStr in blockedSet) {
            val parts = ruleStr.split("|", limit = 3)
            if (parts.size >= 3) {
                val rulePackage = parts[0]
                val ruleType = parts[1]
                val rulePattern = parts[2]
                
                if (rulePackage == packageName) {
                    if (ruleType == "TITLE" && title.contains(rulePattern, ignoreCase = true)) {
                        return true
                    }
                    if (ruleType == "TEXT" && text.contains(rulePattern, ignoreCase = true)) {
                        return true
                    }
                }
            }
        }
        return false
    }
