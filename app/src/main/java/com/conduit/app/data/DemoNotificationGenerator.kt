package com.conduit.app.data

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.conduit.app.HubNotificationListenerService
import kotlin.random.Random

object DemoNotificationGenerator {

    private val notificationTemplates = mapOf(
        "channel_messages" to listOf(
            Triple("Mom", "Hey, are you coming over for dinner tonight? Let me know!", "MESSAGE"),
            Triple("Alex", "Can you pick up groceries on the way home?", "MESSAGE"),
            Triple("Work Group", "Meeting moved to 3pm. Please update your calendars.", "MESSAGE")
        ),
        "channel_gmail" to listOf(
            Triple("GitHub", "Your pull request #142 has been approved and merged into main.", "MESSAGE"),
            Triple("Amazon", "Your order has shipped! Expected delivery: Wednesday.", "MESSAGE")
        ),
        "channel_spark" to listOf(
            Triple("Team Update", "Weekly sync notes are attached.", "MESSAGE"),
            Triple("Newsletter", "Top 10 Kotlin features you should know.", "MESSAGE")
        ),
        "channel_outlook" to listOf(
            Triple("Project Alpha", "Please review the attached design documents by EOD.", "MESSAGE"),
            Triple("HR Department", "Reminder: Open enrollment ends tomorrow.", "MESSAGE")
        ),
        "channel_snapchat" to listOf(
            Triple("Jordan", "Sent you a snap! 👀", "MESSAGE"),
            Triple("Sarah", "New story available", "MESSAGE")
        ),
        "channel_linkedin" to listOf(
            Triple("LinkedIn", "John Smith viewed your profile. See their details.", "OTHER"),
            Triple("LinkedIn Jobs", "You have 3 new job recommendations matching your skills.", "OTHER")
        ),
        "channel_instagram" to listOf(
            Triple("Instagram", "photography_daily liked your photo.", "OTHER"),
            Triple("Instagram", "travel.vibes started following you.", "OTHER")
        ),
        "channel_phone" to listOf(
            Triple("Missed call", "Missed call from (555) 123-4567", "CALL"),
            Triple("Voicemail", "New voicemail from Dr. Johnson's office (2:34)", "CALL")
        ),
        "channel_truecaller" to listOf(
            Triple("Spam Call Blocked", "Blocked a call from Telemarketer (555) 000-0000", "CALL")
        ),
        "channel_telegram" to listOf(
            Triple("Crypto Group", "Bitcoin hits new all-time high!", "MESSAGE"),
            Triple("Mike", "Are we still on for the weekend trip?", "MESSAGE")
        ),
        "channel_telegram_x" to listOf(
            Triple("Dev Chat", "Anyone tried the new Android Studio yet?", "MESSAGE")
        ),
        "channel_reddit" to listOf(
            Triple("r/androiddev", "Trending: Compose multiplatform is amazing", "OTHER"),
            Triple("r/funny", "Popular on r/funny right now", "OTHER")
        ),
        "channel_steam" to listOf(
            Triple("Steam", "Half-Life 3 is now on sale for your wishlist!", "OTHER")
        ),
        "channel_steam_chat" to listOf(
            Triple("Gamer123", "Invite to play CS:GO", "MESSAGE")
        ),
        "channel_facebook" to listOf(
            Triple("Facebook", "Jane Doe mentioned you in a comment.", "OTHER"),
            Triple("Facebook Events", "Tech Meetup is happening tomorrow near you.", "OTHER")
        ),
        "channel_messenger" to listOf(
            Triple("Family Chat", "Grandma says hi!", "MESSAGE"),
            Triple("Dave", "Did you see the game last night?", "MESSAGE")
        ),
        "channel_twitter" to listOf(
            Triple("Twitter", "Breaking News: Major discovery in space exploration.", "OTHER"),
            Triple("Twitter", "Elon Musk tweeted recently.", "OTHER")
        ),
        "channel_teams" to listOf(
            Triple("Design Team", "Review the new mockups in the Figma channel.", "MESSAGE"),
            Triple("Microsoft Teams", "Your meeting starts in 15 minutes.", "OTHER")
        ),
        "channel_airbnb" to listOf(
            Triple("Airbnb", "Your reservation in Paris is confirmed!", "OTHER")
        )
    )

    suspend fun generate(context: Context, database: AppDatabase, service: HubNotificationListenerService?) {
        val now = System.currentTimeMillis()
        val dayMs = 86_400_000L
        val random = Random(42) // Fixed seed for consistent demo mode
        
        val testNotifications = mutableListOf<HubNotification>()
        
        var notifCounter = 0
        
        HubNotificationListenerService.supportedApps.forEach { (pkg, channelInfo) ->
            val channelKey = channelInfo.first
            val channelName = channelInfo.second
            
            val templates = notificationTemplates[channelKey]
            
            if (templates != null) {
                // Generate 1 to 3 notifications for this channel
                val count = random.nextInt(1, 4)
                for (i in 0 until count) {
                    val template = templates[random.nextInt(templates.size)]
                    // Spread across 5 days (0 to 5)
                    val daysAgo = random.nextDouble(0.0, 5.0)
                    val timestamp = (now - (daysAgo * dayMs)).toLong()
                    
                    val key = "demo_${pkg}_$notifCounter"
                    notifCounter++
                    
                    val notif = HubNotification(
                        packageName = pkg,
                        notificationKey = key,
                        title = template.first,
                        text = template.second,
                        timestamp = timestamp,
                        channel = channelName,
                        isDemo = true,
                        kind = template.third
                    )
                    testNotifications.add(notif)
                    
                    // Generate mock actions for the UI
                    if (service != null) {
                        service.injectDemoActions(key, generateMockActions(context, template.third, channelName))
                    }
                }
            } else {
                // Fallback generic template
                val timestamp = (now - (random.nextDouble(0.0, 5.0) * dayMs)).toLong()
                val key = "demo_${pkg}_$notifCounter"
                notifCounter++
                
                val notif = HubNotification(
                    packageName = pkg,
                    notificationKey = key,
                    title = "New update from $channelName",
                    text = "Tap to view the latest information.",
                    timestamp = timestamp,
                    channel = channelName,
                    isDemo = true,
                    kind = "OTHER"
                )
                testNotifications.add(notif)
            }
        }
        
        testNotifications.forEach { database.notificationDao().insert(it) }
    }
    
    private fun generateMockActions(context: Context, kind: String, channelName: String): List<Notification.Action> {
        val dummyIntent = PendingIntent.getActivity(context, 0, Intent().setPackage(context.packageName), PendingIntent.FLAG_IMMUTABLE)
        val actions = mutableListOf<Notification.Action>()
        
        if (kind == "MESSAGE") {
            // Add a Mark as Read action
            actions.add(Notification.Action.Builder(0, "Mark as read", dummyIntent).build())
            
            // Add a Reply action
            val remoteInput = RemoteInput.Builder("demo_reply").setLabel("Reply...").build()
            val replyIntent = PendingIntent.getActivity(
                context, 
                0, 
                Intent().setPackage(context.packageName), 
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_UPDATE_CURRENT
            )
            actions.add(
                Notification.Action.Builder(0, "Reply", replyIntent)
                    .addRemoteInput(android.app.RemoteInput.Builder(remoteInput.resultKey).setLabel(remoteInput.label).build())
                    .build()
            )
        } else if (kind == "CALL") {
            actions.add(Notification.Action.Builder(0, "Call back", dummyIntent).build())
            actions.add(Notification.Action.Builder(0, "Message", dummyIntent).build())
        } else {
            // General actions based on channel
            if (channelName.contains("LinkedIn") || channelName.contains("Facebook") || channelName.contains("Instagram")) {
                actions.add(Notification.Action.Builder(0, "Like", dummyIntent).build())
                actions.add(Notification.Action.Builder(0, "Comment", dummyIntent).build())
            }
        }
        
        return actions
    }
}
