package com.conduit.app

import com.conduit.app.HubNotificationListenerService.Companion.supportedApps
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.StatusBarNotification
import android.graphics.Color
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.core.app.NotificationCompat

    fun HubNotificationListenerService.updatePersistentNotification() {
        val prefs = applicationContext.getSharedPreferences("conduit_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("persistent_tray_enabled", false)) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(2001)
            return
        }

        val activeNotifs = activeNotifications.sortedByDescending { it.postTime }
        val filteredNotifs = mutableListOf<android.service.notification.StatusBarNotification>()
        for (sbn in activeNotifs) {
            val packageName = sbn.packageName
            val notif = sbn.notification
            
            val appInfo = supportedApps[packageName]
            val isSystemPhoneFallback = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && notif.category == Notification.CATEGORY_CALL) ||
                                        packageName.contains(".dialer", ignoreCase = true) ||
                                        packageName.endsWith(".phone", ignoreCase = true)
            
            val channelInfo = appInfo ?: if (isSystemPhoneFallback) Pair("channel_phone", "Phone") else null
            if (channelInfo != null) {
                val prefKey = channelInfo.first
                if (prefs.getBoolean(prefKey, true)) {
                    if ((notif.flags and Notification.FLAG_GROUP_SUMMARY) == 0) {
                        filteredNotifs.add(sbn)
                    }
                }
            }
        }

        val groupedNotifs = filteredNotifs.groupBy { it.packageName }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (groupedNotifs.isEmpty()) {
            nm.cancel(2001)
            return
        }

        val remoteViews = android.widget.RemoteViews(packageName, R.layout.persistent_notification)
        remoteViews.removeAllViews(R.id.persistent_icon_container)

        val density = resources.displayMetrics.density

        for ((pkg, notifs) in groupedNotifs) {
            val count = notifs.size
            val pm = packageManager
            val appIcon = getAppIcon(this, pkg) ?: continue
            
            val size = (48 * density).toInt()
            val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            appIcon.setBounds(0, (8 * density).toInt(), (40 * density).toInt(), (48 * density).toInt())
            appIcon.draw(canvas)
            
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            paint.color = android.graphics.Color.RED
            val cx = size - (12 * density)
            val cy = (12 * density)
            canvas.drawCircle(cx, cy, (10 * density), paint)
            
            paint.color = android.graphics.Color.WHITE
            paint.textSize = (12 * density)
            paint.textAlign = android.graphics.Paint.Align.CENTER
            paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
            val textY = cy - ((paint.descent() + paint.ascent()) / 2)
            canvas.drawText(count.toString(), cx, textY, paint)

            val childView = android.widget.RemoteViews(packageName, R.layout.persistent_notification_icon)
            childView.setImageViewBitmap(R.id.app_icon, bitmap)
            
            val intent = Intent(this, MainActivity::class.java).apply {
                action = "com.conduit.app.OPEN_FILTER"
                putExtra("FILTER_PACKAGE", pkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val pi = PendingIntent.getActivity(this, pkg.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            childView.setOnClickPendingIntent(R.id.app_icon, pi)
            
            remoteViews.addView(R.id.persistent_icon_container, childView)
        }

        val channelId = "persistent_tray_default"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Persistent Tray", NotificationManager.IMPORTANCE_DEFAULT)
            channel.setShowBadge(false)
            nm.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setCustomContentView(remoteViews)
            .setCustomBigContentView(remoteViews)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            
        nm.notify(2001, builder.build())
    }
