package com.conduit.app

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.conduit.app.data.AppDatabase
import com.conduit.app.data.HubNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.view.WindowManager
import android.graphics.PixelFormat
import android.view.Gravity
import android.widget.FrameLayout
import android.view.View
import android.graphics.drawable.GradientDrawable
import android.graphics.Color
import android.provider.Settings
import android.content.SharedPreferences
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.content.LocusIdCompat

class HubNotificationListenerService : NotificationListenerService(), SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        var instance: HubNotificationListenerService? = null

        val supportedApps = ChannelRegistry.supportedApps
    }

    internal val scope = CoroutineScope(Dispatchers.IO)
    internal val notificationMutex = Mutex()
    internal lateinit var database: AppDatabase
    internal lateinit var callTracker: CallNotificationTracker
    internal val actionCache = java.util.concurrent.ConcurrentHashMap<String, List<Notification.Action>>()
    internal val contentIntentCache = java.util.concurrent.ConcurrentHashMap<String, PendingIntent>()
    internal val DISMISS_ORIGINAL_NOTIFICATIONS = false
    
    internal var windowManager: WindowManager? = null
    internal var bracketView: View? = null
    internal var hangerRootView: FrameLayout? = null
    internal var hangerLinearLayout: android.widget.LinearLayout? = null
    internal var popupIconView: View? = null
    internal var popupImageView: android.widget.ImageView? = null
    internal var popupWindowParams: WindowManager.LayoutParams? = null
    internal var popupWindowAdded = false
    internal var activePendingIntent: PendingIntent? = null
    internal var activePackageName: String? = null
    
    internal var hangerOpened = false
    internal var startX = 0f
    internal var startY = 0f
    internal var gestureStartTime = 0L
    internal var isDragging = false
    internal var hoverIndex = -1
    sealed class HangerItem {
        data class BundleItem(val channelKey: String, val packageName: String) : HangerItem()
        data class NotificationItem(val pendingIntent: PendingIntent?, val packageName: String) : HangerItem()
    }
    internal var pendingIntentList = mutableListOf<HangerItem>()
    internal var expandedBundleKey: String? = null
    internal val expandBundleRunnable = Runnable {
        val index = hoverIndex
        if (index >= 0 && index < pendingIntentList.size) {
            val item = pendingIntentList[index]
            if (item is HangerItem.BundleItem) {
                if (expandedBundleKey != item.channelKey) {
                    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
                        manager.defaultVibrator
                    } else {
                        getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        vibrator.vibrate(android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_HEAVY_CLICK))
                    } else {
                        vibrator.vibrate(50)
                    }
                    expandedBundleKey = item.channelKey
                    populateHangerNotifications()
                }
            }
        }
    }
    internal val handler = android.os.Handler(android.os.Looper.getMainLooper())
    internal val collapseRunnable = Runnable { animateCollapse() }
    private val updatePersistentRunnable = Runnable { updatePersistentNotification() }

    internal fun scheduleUpdatePersistentNotification() {
        handler.removeCallbacks(updatePersistentRunnable)
        handler.postDelayed(updatePersistentRunnable, 500)
    }

    private val closeSystemDialogsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_CLOSE_SYSTEM_DIALOGS) {
                closeHanger()
            }
        }
    }

    internal fun getBasBundle(): android.os.Bundle {
        val options = android.app.ActivityOptions.makeBasic()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            options.pendingIntentBackgroundActivityStartMode = android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
        }
        return options.toBundle()
    }

    fun importActiveNotifications() {
        val active = try { activeNotifications } catch (e: Exception) { null }
        active?.forEach { sbn ->
            try {
                onNotificationPosted(sbn)
            } catch (e: Exception) {
                android.util.Log.e("HubService", "Error importing notification", e)
            }
        }
    }

    fun injectDemoActions(key: String, actions: List<Notification.Action>) {
        actionCache[key] = actions
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        scheduleUpdatePersistentNotification()

        scope.launch {
            try {
                val prefs = getSharedPreferences("conduit_prefs", Context.MODE_PRIVATE)
                val autoDismissDetached = prefs.getBoolean("auto_dismiss_detached", true)
                val now = System.currentTimeMillis()
                
                val activeKeys = try { activeNotifications.map { it.key }.toSet() } catch (e: Exception) { emptySet() }
                val unarchivedMetadata = database.notificationDao().getActiveNotificationMetadata()
                
                var hasChanges = false
                for (meta in unarchivedMetadata) {
                    if (meta.isPinned) continue
                    
                    val inTray = activeKeys.contains(meta.notificationKey)
                    if (!inTray && meta.detachedAt == null) {
                        database.notificationDao().setDetached(meta.id, now)
                        if (meta.kind == "OTHER" && autoDismissDetached && !meta.isSnoozed) {
                            database.notificationDao().archiveNotification(meta.id, now)
                        }
                        hasChanges = true
                    } else if (inTray && meta.detachedAt != null) {
                        database.notificationDao().clearDetached(meta.id)
                        hasChanges = true
                    }
                }
                
                if (hasChanges) {
                    com.conduit.app.widget.WidgetUpdater.updateAllWidgets(this@HubNotificationListenerService)
                }

                val retentionDays = prefs.getInt("retention_days", 90)
                val cutoffTimestamp = System.currentTimeMillis() - (retentionDays * 24L * 60L * 60L * 1000L)
                val deletedCount = database.notificationDao().deleteOldArchivedNotifications(cutoffTimestamp)
                if (deletedCount > 0) {
                    android.util.Log.d("HubNotificationService", "Pruned $deletedCount notifications older than $retentionDays days")
                }
            } catch (e: Exception) {
                android.util.Log.e("HubNotificationService", "Failed to run notification retention cleanup", e)
            }
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        instance = null
    }

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(applicationContext)
        callTracker = CallNotificationTracker(applicationContext, database.notificationDao(), scope) {
            com.conduit.app.widget.WidgetUpdater.updateAllWidgets(this@HubNotificationListenerService)
            scheduleUpdatePersistentNotification()
        }
        val prefs = applicationContext.getSharedPreferences("conduit_prefs", Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(this)
        
        try {
            val filter = IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(closeSystemDialogsReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(closeSystemDialogsReceiver, filter)
            }
        } catch (e: IllegalArgumentException) {
            android.util.Log.e("Conduit", "Invalid receiver", e)
        } catch (e: SecurityException) {
            android.util.Log.e("Conduit", "Security exception", e)
        }
        
        updateBracketState()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        com.conduit.app.widget.WidgetUpdater.updateAllWidgets(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        val prefs = applicationContext.getSharedPreferences("conduit_prefs", Context.MODE_PRIVATE)
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        try {
            unregisterReceiver(closeSystemDialogsReceiver)
        } catch (e: IllegalArgumentException) {
            android.util.Log.e("Conduit", "Receiver not registered", e)
        }
        removeBracketView()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == "enable_bracket") {
            updateBracketState()
        } else if (key == "bracket_vertical_position") {
            updateBracketPosition()
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        ingestPostedNotification(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?, rankingMap: NotificationListenerService.RankingMap?, reason: Int) {
        super.onNotificationRemoved(sbn, rankingMap, reason)
        sbn?.let {
            callTracker.onNotificationRemoved(it)
            actionCache[it.key] = emptyList()
            val prefs = getSharedPreferences("conduit_prefs", android.content.Context.MODE_PRIVATE)
            val syncDismissal = prefs.getBoolean("sync_dismissal", true)
            val autoDismissDetached = prefs.getBoolean("auto_dismiss_detached", true)
            
            val readReasons = setOf(
                REASON_CANCEL, REASON_CANCEL_ALL, REASON_CLICK,
                REASON_APP_CANCEL, REASON_APP_CANCEL_ALL
            )
            
            scope.launch {
                if (reason in readReasons) {
                    if (syncDismissal) {
                        database.notificationDao().archiveNotificationByKey(it.key, System.currentTimeMillis())
                        com.conduit.app.widget.WidgetUpdater.updateAllWidgets(this@HubNotificationListenerService)
                    }
                } else {
                    val activeNotification = database.notificationDao().getActiveNotificationByKey(it.key)
                    if (activeNotification != null) {
                        val now = System.currentTimeMillis()
                        database.notificationDao().setDetached(activeNotification.id, now)
                        if (activeNotification.kind == "OTHER" && autoDismissDetached && !activeNotification.isPinned && !activeNotification.isSnoozed) {
                            database.notificationDao().archiveNotification(activeNotification.id, now)
                        }
                        com.conduit.app.widget.WidgetUpdater.updateAllWidgets(this@HubNotificationListenerService)
                    }
                }
            }
        }
        scheduleUpdatePersistentNotification()
    }
    fun cancel(key: String) {
        try {
            cancelNotification(key)
        } catch (e: Exception) {
            android.util.Log.e("Conduit", "Exception caught", e)
        }
    }

    fun snooze(key: String, durationMs: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                snoozeNotification(key, durationMs)
            } catch (e: Exception) {
                android.util.Log.e("Conduit", "Exception caught", e)
            }
        }
    }

    fun getReplyAction(key: String): Notification.Action? {
        val actions = getNotificationActions(key) ?: return null
        return actions.find { it.remoteInputs != null && it.remoteInputs.isNotEmpty() }
    }

    fun getNotificationActions(key: String): List<Notification.Action>? {
        if (actionCache.containsKey(key)) {
            val cached = actionCache[key]
            return if (cached.isNullOrEmpty()) null else cached
        }

        try {
            val active = activeNotifications
            for (sbn in active) {
                if (sbn.key == key) {
                    val list = mutableListOf<Notification.Action>()
                    sbn.notification.actions?.let { list.addAll(it) }
                    
                    // Add contextual actions if available (Android 10+)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val extras = sbn.notification.extras
                        try {
                            val contextual = extras.getParcelableArrayList<Notification.Action>("android.contextualActions")
                            contextual?.let { list.addAll(it) }
                        } catch (e: Exception) {
                            // Sometimes parceling fails for custom actions
                        }
                    }
                    
                    val result = if (list.isEmpty()) emptyList() else list
                    actionCache[key] = result
                    return if (result.isEmpty()) null else result
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("Conduit", "Exception caught", e)
        }
        actionCache[key] = emptyList()
        return null
    }

    fun getCachedContentIntent(key: String): PendingIntent? {
        return contentIntentCache[key]
    }
}

