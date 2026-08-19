package com.conduit.app.widget

import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import android.os.Bundle
import android.content.Intent
import com.conduit.app.HubNotificationListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.conduit.app.data.AppDatabase
import com.conduit.app.widget.ConduitWidgetProvider

class ConduitWidgetClickActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val key = intent.getStringExtra(ConduitWidgetProvider.EXTRA_ITEM_KEY)
        val pkg = intent.getStringExtra(ConduitWidgetProvider.EXTRA_ITEM_PKG)
        val actionIndex = intent.getIntExtra("action_index", -1)
        val isConduitMarkRead = intent.getBooleanExtra("is_conduit_mark_read", false)

        if (key != null && pkg != null) {
            if (isConduitMarkRead) {
                val db = AppDatabase.getDatabase(this)
                val service = HubNotificationListenerService.instance
                val prefs = getSharedPreferences("conduit_prefs", MODE_PRIVATE)
                val syncDismissal = prefs.getBoolean("sync_dismissal", true)

                lifecycleScope.launch(Dispatchers.IO) {
                    db.notificationDao().archiveNotificationByKey(key, System.currentTimeMillis())
                    if (syncDismissal) {
                        service?.cancel(key)
                    }
                    com.conduit.app.widget.WidgetUpdater.updateAllWidgets(this@ConduitWidgetClickActivity)
                    launch(Dispatchers.Main) { finish() }
                }
            } else if (actionIndex != -1) {
                // Launch native action
                val actions = HubNotificationListenerService.instance?.getNotificationActions(key)
                if (actions != null && actionIndex < actions.size) {
                    try {
                        val actionIntent = actions[actionIndex].actionIntent
                        val options = android.app.ActivityOptions.makeBasic()
                        if (android.os.Build.VERSION.SDK_INT >= 34) {
                            options.pendingIntentBackgroundActivityStartMode = android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                        }
                        actionIntent.send(this, 0, null, null, null, null, options.toBundle())
                    } catch (e: android.app.PendingIntent.CanceledException) {
                        android.util.Log.e("Conduit", "PendingIntent canceled", e)
                    } catch (e: SecurityException) {
                        android.util.Log.e("Conduit", "Security exception", e)
                    }
                }
                finish()
            } else {
                // Launch deep link
                launchNotificationDeepLink(key, pkg)
                finish()
            }
        } else {
            finish()
        }
    }

    private fun launchNotificationDeepLink(notificationKey: String, packageName: String) {
        var launched = false
        val service = HubNotificationListenerService.instance
        if (service != null) {
            try {
                // Try live notification first
                val activeNotifs = service.activeNotifications
                for (sbn in activeNotifs) {
                    if (sbn.key == notificationKey) {
                        val intent = sbn.notification.contentIntent
                        if (intent != null) {
                            val options = android.app.ActivityOptions.makeBasic()
                            if (android.os.Build.VERSION.SDK_INT >= 34) {
                                options.pendingIntentBackgroundActivityStartMode = android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                            }
                            intent.send(this, 0, null, null, null, null, options.toBundle())
                            launched = true
                        }
                        break
                    }
                }
                
                // Fallback to in-memory cache
                if (!launched) {
                    val cachedIntent = service.getCachedContentIntent(notificationKey)
                    if (cachedIntent != null) {
                        val options = android.app.ActivityOptions.makeBasic()
                        if (android.os.Build.VERSION.SDK_INT >= 34) {
                            options.pendingIntentBackgroundActivityStartMode = android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                        }
                        cachedIntent.send(this, 0, null, null, null, null, options.toBundle())
                        launched = true
                    }
                }
            } catch (e: android.app.PendingIntent.CanceledException) {
                android.util.Log.e("Conduit", "PendingIntent canceled", e)
            } catch (e: SecurityException) {
                android.util.Log.e("Conduit", "Security exception", e)
            }
        }

        if (!launched) {
            try {
                val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(launchIntent)
                }
            } catch (e: android.content.ActivityNotFoundException) {
                android.util.Log.e("Conduit", "Activity not found", e)
            } catch (e: SecurityException) {
                android.util.Log.e("Conduit", "Security exception", e)
            }
        }
    }
}



