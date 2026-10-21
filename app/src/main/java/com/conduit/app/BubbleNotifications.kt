package com.conduit.app

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.content.LocusIdCompat

    internal fun HubNotificationListenerService.createBubbleNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Conduit Bubbles"
            val descriptionText = "Notifications for floating bubbles"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel("conduit_bubble_channel", name, importance).apply {
                description = descriptionText
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setAllowBubbles(true)
                }
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    internal fun HubNotificationListenerService.createBubbleShortcut(context: Context, contactName: String, contactKey: String, icon: IconCompat) {
        val person = Person.Builder()
            .setName(contactName)
            .setKey(contactKey)
            .setIcon(icon)
            .build()

        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra("from_bubble", true)
        }

        val shortcut = ShortcutInfoCompat.Builder(context, contactKey)
            .setShortLabel(contactName)
            .setLongLived(true)
            .setIntent(intent)
            .setPerson(person)
            .setIcon(icon)
            .setCategories(java.util.Collections.singleton("android.shortcut.conversation"))
            .build()

        ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
    }
    fun HubNotificationListenerService.postBubbleNotification(context: Context, packageName: String, title: String, text: String) {
        val senderName = if (title.isBlank()) "Conduit Hub" else title
        val shortcutId = "conduit_shortcut_" + packageName

        var appIcon: IconCompat? = null
        try {
            val drawable = getAppIcon(context, packageName)
            if (drawable != null) {
                val bitmap = drawable.toBitmap(width = 192, height = 192)
                appIcon = IconCompat.createWithBitmap(bitmap)
            }
        } catch (e: Exception) {
            android.util.Log.e("Conduit", "Exception caught", e)
        }
        val finalIcon = appIcon ?: IconCompat.createWithResource(context, R.mipmap.ic_launcher)

        createBubbleNotificationChannel(context)
        createBubbleShortcut(context, senderName, shortcutId, finalIcon)

        val isTestBubble = packageName == "com.conduit.app" || title == "Conduit Test Bubble"
        val bubbleMetadata = createBubbleMetadata(shortcutId, suppress = !isTestBubble)

        val person = Person.Builder()
            .setName(senderName)
            .setKey(shortcutId)
            .setIcon(finalIcon)
            .build()

        val style = NotificationCompat.MessagingStyle(person)
            .addMessage(text, System.currentTimeMillis(), person)

        val notificationBuilder = NotificationCompat.Builder(context, "conduit_bubble_channel")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(senderName)
            .setContentText(text)
            .setShortcutId(shortcutId)
            .setLocusId(LocusIdCompat(shortcutId))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setStyle(style)
            .setBubbleMetadata(bubbleMetadata)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setGroup("com.conduit.app.BUBBLE_GROUP")

        val summaryNotification = NotificationCompat.Builder(context, "conduit_bubble_channel")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Conduit Messages")
            .setContentText("New active conversations")
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setGroup("com.conduit.app.BUBBLE_GROUP")
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()

        val notificationManager = NotificationManagerCompat.from(context)
        try {
            notificationManager.notify(shortcutId.hashCode(), notificationBuilder.build())
            notificationManager.notify(9999, summaryNotification)
        } catch (e: SecurityException) {
            android.util.Log.e("Conduit", "Exception caught", e)
        }
    }

    internal fun HubNotificationListenerService.createBubbleMetadata(shortcutId: String, suppress: Boolean = true): NotificationCompat.BubbleMetadata {
        return NotificationCompat.BubbleMetadata.Builder(shortcutId)
            .setDesiredHeight(600)
            .setAutoExpandBubble(true)
            .setSuppressNotification(suppress)
            .build()
    }
