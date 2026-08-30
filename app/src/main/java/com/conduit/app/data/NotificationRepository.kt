package com.conduit.app.data

import android.app.Notification
import android.content.Context
import com.conduit.app.HubNotificationListenerService
import com.conduit.app.widget.ConduitWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi

class NotificationRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val settingsRepository: SettingsRepository
) {
    private val notificationDao = database.notificationDao()

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeNotifications: Flow<List<HubNotification>> = settingsRepository.settings.flatMapLatest { settings ->
        notificationDao.getAllNotifications(settings.demoModeEnabled)
    }
    
    @OptIn(ExperimentalCoroutinesApi::class)
    val archivedNotifications: Flow<List<HubNotification>> = settingsRepository.settings.flatMapLatest { settings ->
        notificationDao.getArchivedNotifications(settings.demoModeEnabled)
    }

    suspend fun archiveNotification(id: Int, timestamp: Long) = withContext(Dispatchers.IO) {
        notificationDao.archiveNotification(id, timestamp)
        triggerWidgetUpdate()
    }
    
    suspend fun archiveNotificationByKey(key: String, timestamp: Long) = withContext(Dispatchers.IO) {
        notificationDao.archiveNotificationByKey(key, timestamp)
        val prefs = context.getSharedPreferences("conduit_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("sync_dismissal", true)) {
            HubNotificationListenerService.instance?.cancel(key)
        }
        triggerWidgetUpdate()
    }

    suspend fun archiveMany(ids: List<Int>) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        val now = System.currentTimeMillis()
        notificationDao.archiveNotifications(ids, now)
        val prefs = context.getSharedPreferences("conduit_prefs", Context.MODE_PRIVATE)
        val syncDismissal = prefs.getBoolean("sync_dismissal", true)
        if (syncDismissal) {
            val allNotifs = notificationDao.getAllNotificationsSync()
            ids.forEach { id ->
                allNotifs.find { it.id == id }?.let {
                    HubNotificationListenerService.instance?.cancel(it.notificationKey)
                }
            }
        }
        triggerWidgetUpdate()
    }

    suspend fun snoozeNotification(id: Int, timestamp: Long, durationMs: Long) = withContext(Dispatchers.IO) {
        notificationDao.snoozeNotification(id, timestamp)
        val allNotifs = notificationDao.getAllNotificationsSync()
        allNotifs.find { it.id == id }?.let {
            HubNotificationListenerService.instance?.snooze(it.notificationKey, durationMs)
        }
        triggerWidgetUpdate()
    }
    
    suspend fun togglePinWithSync(notification: HubNotification, syncPinned: Boolean) = withContext(Dispatchers.IO) {
        val newPinned = !notification.isPinned
        notificationDao.togglePin(notification.id)
        if (syncPinned) {
            if (newPinned) {
                val titleString = notification.title?.toString() ?: ""
                com.conduit.app.postPinnedNotification(
                    context,
                    notification.id,
                    titleString,
                    notification.text?.toString() ?: "",
                    notification.packageName
                )
            } else {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                notificationManager.cancel(notification.id)
            }
        }
        triggerWidgetUpdate()
    }
    
    suspend fun pinMany(ids: List<Int>, pin: Boolean) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        notificationDao.pinNotifications(ids, pin)
        triggerWidgetUpdate()
    }

    suspend fun markReadMany(keys: List<String>) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        keys.forEach { key ->
            notificationDao.archiveNotificationByKey(key, now)
            val prefs = context.getSharedPreferences("conduit_prefs", Context.MODE_PRIVATE)
            if (prefs.getBoolean("sync_dismissal", true)) {
                HubNotificationListenerService.instance?.cancel(key)
            }
        }
        triggerWidgetUpdate()
    }

    suspend fun markRead(key: String) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        notificationDao.archiveNotificationByKey(key, now)
        val prefs = context.getSharedPreferences("conduit_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("sync_dismissal", true)) {
            HubNotificationListenerService.instance?.cancel(key)
        }
        triggerWidgetUpdate()
    }

    // Service passthroughs (null-safe)
    fun cancelServiceNotification(key: String) {
        if (key.startsWith("demo_")) return
        HubNotificationListenerService.instance?.cancel(key)
    }

    fun snoozeServiceNotification(key: String, durationMs: Long) {
        if (key.startsWith("demo_")) return
        HubNotificationListenerService.instance?.snooze(key, durationMs)
    }

    fun getNotificationActions(key: String): List<Notification.Action>? {
        return HubNotificationListenerService.instance?.getNotificationActions(key)
    }

    fun getReplyAction(key: String): Notification.Action? {
        return HubNotificationListenerService.instance?.getReplyAction(key)
    }

    fun getContentIntent(key: String): android.app.PendingIntent? {
        if (key.startsWith("demo_")) {
            return android.app.PendingIntent.getBroadcast(context, 0, android.content.Intent("com.conduit.app.DEMO_ACTION"), android.app.PendingIntent.FLAG_IMMUTABLE)
        }
        val sbn = HubNotificationListenerService.instance?.activeNotifications?.find { it.key == key }
        if (sbn?.notification?.contentIntent != null) {
            return sbn.notification.contentIntent
        }
        return HubNotificationListenerService.instance?.getCachedContentIntent(key)
    }
    
    suspend fun getActiveNotificationByKey(key: String): HubNotification? = withContext(Dispatchers.IO) {
        notificationDao.getActiveNotificationByKey(key)
    }

    private fun triggerWidgetUpdate() {
        ConduitWidgetProvider.updateAllWidgets(context)
    }
}

