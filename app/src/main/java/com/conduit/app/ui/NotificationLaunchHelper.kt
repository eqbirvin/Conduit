package com.conduit.app.ui

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.conduit.app.HubNotificationListenerService
import com.conduit.app.data.HubNotification
import com.conduit.app.getRepresentativePackage
import com.conduit.app.launchApp

object NotificationLaunchHelper {
    fun launchNotification(context: Context, notification: HubNotification) {
        var launched = false
        val service = HubNotificationListenerService.instance
        if (service != null) {
            try {
                val activeNotifs = service.activeNotifications
                for (sbn in activeNotifs) {
                    if (sbn.key == notification.notificationKey) {
                        val intent = sbn.notification.contentIntent
                        if (intent != null) {
                            val options = ActivityOptions.makeBasic()
                            if (Build.VERSION.SDK_INT >= 34) {
                                options.pendingIntentBackgroundActivityStartMode = ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                            }
                            intent.send(context, 0, null, null, null, null, options.toBundle())
                            launched = true
                        }
                        break
                    }
                }

                // Fallback to in-memory cache if not found in active live notifications
                if (!launched) {
                    val cachedIntent = service.getCachedContentIntent(notification.notificationKey)
                    if (cachedIntent != null) {
                        val options = ActivityOptions.makeBasic()
                        if (Build.VERSION.SDK_INT >= 34) {
                            options.pendingIntentBackgroundActivityStartMode = ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                        }
                        cachedIntent.send(context, 0, null, null, null, null, options.toBundle())
                        launched = true
                    }
                }
            } catch (e: Exception) {
                Log.e("Conduit", "Failed to launch notification intent", e)
            }
        }

        if (!launched) {
            try {
                val repPkg = getRepresentativePackage(context, notification.packageName)
                val launchedCrossProfile = launchApp(context, repPkg)
                if (!launchedCrossProfile) {
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(repPkg)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launchIntent)
                    }
                }
            } catch (e: Exception) {
                Log.e("Conduit", "Failed to launch representative package", e)
            }
        }
    }
}
