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
        "channel_thunderbird" to listOf(
            Triple("Mozilla", "Thunderbird updates: New account setup and sync ready.", "MESSAGE"),
            Triple("Dev Digest", "Open source email enhancements in the latest release.", "MESSAGE")
        ),
        "channel_fairemail" to listOf(
            Triple("Privacy Weekly", "Your monthly security and privacy report is ready.", "MESSAGE"),
            Triple("F-Droid Monitor", "FairEmail build completed successfully.", "MESSAGE")
        ),
        "channel_k9_mail" to listOf(
            Triple("Server Admin", "Scheduled IMAP server maintenance this Sunday at 2 AM.", "MESSAGE"),
            Triple("Alice", "Sent you the updated report files.", "MESSAGE")
        ),
        "channel_edison_mail" to listOf(
            Triple("Flight Tracker", "Flight #UA-482 is on time. Gate B12.", "MESSAGE"),
            Triple("Package Delivery", "Your shipment has arrived at the front porch.", "MESSAGE")
        ),
        "channel_proton_mail" to listOf(
            Triple("Proton Team", "You have received a secure end-to-end encrypted email.", "MESSAGE"),
            Triple("Security Notice", "New device login detected from Firefox on Linux.", "MESSAGE")
        ),
        "channel_yahoo_mail" to listOf(
            Triple("Yahoo Finance", "Market Close: Tech stocks rise after earnings reports.", "MESSAGE"),
            Triple("Order Receipt", "Thank you for your purchase. Total: $42.18.", "MESSAGE")
        ),
        "channel_spike" to listOf(
            Triple("Emma", "Can we quickly review the presentation slides before the 3pm sync?", "MESSAGE"),
            Triple("Product Team", "New conversation started in Design Review.", "MESSAGE")
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
        ),
        "channel_discord" to listOf(
            Triple("Alex", "Are we still playing tonight? Let me know when you're online!", "MESSAGE"),
            Triple("Dev Server (#announcements)", "Version 2.0 has just been released to production! 🚀", "MESSAGE"),
            Triple("General (#gaming-lounge)", "Sarah: Check out the new clip posted in media channel.", "MESSAGE")
        ),
        "channel_whatsapp" to listOf(
            Triple("Family Group", "Dinner tonight at 7!", "MESSAGE"),
            Triple("Carlos", "Hey, did you get the files?", "MESSAGE")
        ),
        "channel_whatsapp_business" to listOf(
            Triple("Apex Support", "Your appointment is confirmed for Friday at 10 AM.", "MESSAGE")
        ),
        "channel_beeper" to listOf(
            Triple("Multi-Network Chat", "Synced message across platforms.", "MESSAGE")
        ),
        "channel_textra" to listOf(
            Triple("Sam", "See you tomorrow morning!", "MESSAGE")
        ),
        "channel_slack" to listOf(
            Triple("Dev Team (#general)", "Sarah: The production deployment succeeded! 🚀", "MESSAGE"),
            Triple("Alex Chen", "Can you review the PR when you get a chance?", "MESSAGE")
        ),
        "channel_google_chat" to listOf(
            Triple("Product Team", "Sprint planning is scheduled for 10am tomorrow.", "MESSAGE"),
            Triple("Mark Davis", "Shared a Google Doc: 'Q3 Strategy Draft'", "MESSAGE")
        ),
        "channel_zoom" to listOf(
            Triple("Zoom Workplace", "Upcoming Meeting: Design Review in 10 minutes", "CALL"),
            Triple("Project Sync", "Elena: Let's join the call early to test screenshare.", "MESSAGE")
        ),
        "channel_google_meet" to listOf(
            Triple("Google Meet", "Incoming video call from Michael Scott", "CALL"),
            Triple("Standup Meeting", "Your scheduled video meeting is starting now.", "CALL")
        ),
        "channel_signal" to listOf(
            Triple("David Kim", "Sent encrypted media. Tap to view.", "MESSAGE"),
            Triple("Family Group", "Dinner reservation is confirmed for 7pm!", "MESSAGE")
        ),
        "channel_viber" to listOf(
            Triple("Maria Santos", "Are you available for a quick call?", "MESSAGE"),
            Triple("Viber Community", "Weekly newsletter and announcements posted.", "MESSAGE")
        ),
        "channel_groupme" to listOf(
            Triple("Campus Study Group", "Jake: Library study room 302 is booked.", "MESSAGE"),
            Triple("Soccer League", "Game is rescheduled to Sunday at 4pm.", "MESSAGE")
        ),
        "channel_skype" to listOf(
            Triple("Enterprise Support", "Engineer joined the chat session.", "MESSAGE"),
            Triple("Robert Green", "Incoming Skype Call", "CALL")
        ),
        "channel_bluebubbles" to listOf(
            Triple("Sam Wilson", "Loved 'Sounds good, see you there!'", "MESSAGE"),
            Triple("Taylor Brooks", "Just landed! Headed to baggage claim now.", "MESSAGE")
        ),
        "channel_airmessage" to listOf(
            Triple("Jordan Lee", "Sent an image via iMessage", "MESSAGE"),
            Triple("Weekend Trip", "Chris: Don't forget to pack hiking shoes!", "MESSAGE")
        ),
        "channel_pulse_sms" to listOf(
            Triple("Mom", "Can you call me when you have a minute?", "MESSAGE"),
            Triple("Dr. Evans Office", "Appointment confirmed for tomorrow at 2:15pm.", "MESSAGE")
        ),
        "channel_chomp_sms" to listOf(
            Triple("Delivery Alert", "Your package has been delivered to your front porch.", "MESSAGE"),
            Triple("Jason", "Running about 5 minutes late!", "MESSAGE")
        ),
        "channel_qksms" to listOf(
            Triple("Security Code", "Your verification code is 482910. Valid for 10 minutes.", "MESSAGE"),
            Triple("Rachel", "Thanks for the recommendation, loved that movie!", "MESSAGE")
        ),
        "channel_quik" to listOf(
            Triple("Alex", "Check out the new QUIK SMS build, it's super smooth!", "MESSAGE"),
            Triple("Delivery Status", "Your package has been delivered to the front door.", "MESSAGE")
        ),
        "channel_anindra_messages" to listOf(
            Triple("Jordan", "Hey, are we still meeting later?", "MESSAGE"),
            Triple("Dentist Office", "Reminder: Appointment tomorrow at 2:00 PM.", "MESSAGE")
        ),
        "channel_simple_sms" to listOf(
            Triple("Bank Notice", "Alert: Recent transaction of $24.50 at Cafe.", "MESSAGE"),
            Triple("Kevin", "Did you get the keys?", "MESSAGE")
        ),
        "channel_mood_messenger" to listOf(
            Triple("Chloe", "Happy Birthday!! 🎉🎂 Have an amazing day!", "MESSAGE"),
            Triple("Apartment Manager", "Water service maintenance tomorrow 9am-12pm.", "MESSAGE")
        ),
        "channel_yaata" to listOf(
            Triple("Transit Alert", "Line 4 bus delayed by 10 minutes due to weather.", "MESSAGE"),
            Triple("Brian", "Leaving now, ETA 15 mins.", "MESSAGE")
        ),
        "channel_tiktok" to listOf(
            Triple("TikTok", "chef_cooking started a live video: 'Cooking Italian Classics'", "OTHER"),
            Triple("TikTok Direct", "maya_travel sent you a video.", "MESSAGE")
        ),
        "channel_twitch" to listOf(
            Triple("Twitch", "shroud is live playing VALORANT: Ranked grind!", "OTHER"),
            Triple("Twitch Whispers", "GamerPro: GG on that last match!", "MESSAGE")
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
