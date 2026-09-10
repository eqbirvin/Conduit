package com.conduit.app.data

import android.app.Notification
import android.content.Context
import com.conduit.app.HubNotificationListenerService
import com.conduit.app.widget.ConduitWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.ExperimentalCoroutinesApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery

class NotificationRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val settingsRepository: SettingsRepository
) {
    private val notificationDao = database.notificationDao()
    private var cachedVocabulary: Set<String>? = null
    private var lastVocabFetchTime: Long = 0L

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeNotifications: Flow<List<HubNotification>> = settingsRepository.settings.flatMapLatest { settings ->
        notificationDao.getAllNotifications(settings.demoModeEnabled)
    }
    
    @OptIn(ExperimentalCoroutinesApi::class)
    val archivedNotifications: Flow<List<HubNotification>> = settingsRepository.settings.flatMapLatest { settings ->
        notificationDao.getArchivedNotifications(settings.demoModeEnabled)
    }

    private fun buildSearchQuery(tokens: List<String>, isDemo: Boolean, countOnly: Boolean): SupportSQLiteQuery {
        val selectClause = if (countOnly) "SELECT COUNT(*) FROM notifications" else "SELECT * FROM notifications"
        val whereClauses = mutableListOf("isDemo = ?")
        val bindArgs = mutableListOf<Any>(if (isDemo) 1 else 0)

        for (token in tokens) {
            whereClauses.add("(title LIKE '%' || ? || '%' OR text LIKE '%' || ? || '%' OR packageName LIKE '%' || ? || '%')")
            bindArgs.add(token)
            bindArgs.add(token)
            bindArgs.add(token)
        }

        val sql = buildString {
            append(selectClause)
            append(" WHERE ")
            append(whereClauses.joinToString(" AND "))
            if (!countOnly) {
                append(" ORDER BY timestamp DESC")
            }
        }

        return SimpleSQLiteQuery(sql, bindArgs.toTypedArray())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun searchNotificationsPaged(query: String): Flow<PagingData<HubNotification>> = settingsRepository.settings.flatMapLatest { settings ->
        val tokens = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        Pager(
            config = PagingConfig(
                pageSize = 30,
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                if (tokens.isEmpty()) {
                    notificationDao.searchNotificationsPaged("", settings.demoModeEnabled)
                } else {
                    val sqliteQuery = buildSearchQuery(tokens, settings.demoModeEnabled, countOnly = false)
                    notificationDao.searchNotificationsRaw(sqliteQuery)
                }
            }
        ).flow
    }

    suspend fun countSearchResults(query: String): Int = withContext(Dispatchers.IO) {
        val settings = settingsRepository.settings.first()
        val tokens = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return@withContext 0
        val countQuery = buildSearchQuery(tokens, settings.demoModeEnabled, countOnly = true)
        try {
            notificationDao.countNotificationsRaw(countQuery)
        } catch (e: Exception) {
            android.util.Log.e("NotificationRepository", "Failed to count search results", e)
            0
        }
    }

    suspend fun getSearchVocabulary(): Set<String> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cached = cachedVocabulary
        if (cached != null && now - lastVocabFetchTime < 60_000L) {
            return@withContext cached
        }
        val settings = settingsRepository.settings.first()
        val snippets = notificationDao.getRecentNotificationTexts(settings.demoModeEnabled)
        val words = mutableSetOf<String>()
        for (snippet in snippets) {
            words.addAll(FuzzySearchEngine.extractWords(snippet.title, snippet.text, snippet.packageName))
        }
        cachedVocabulary = words
        lastVocabFetchTime = now
        words
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

    suspend fun appendReplyAndArchive(id: Int, currentText: String, replyText: String, currentTitle: String, timestamp: Long) = withContext(Dispatchers.IO) {
        val suffix = "\n\u21aa You: $replyText"
        val textUpdated = !currentText.endsWith(suffix)
        val titleUpdated = !currentTitle.endsWith(" - Replied")
        val newText = if (textUpdated) currentText + suffix else currentText
        val newTitle = if (titleUpdated) "$currentTitle - Replied" else currentTitle
        
        notificationDao.updateAndArchive(id, newTitle, newText, timestamp)
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

    fun findNativeReadAction(key: String): Notification.Action? {
        val actions = getNotificationActions(key) ?: return null
        return actions.find { action ->
            val title = action.title?.toString()?.trim()?.lowercase() ?: return@find false
            if (title.contains("unread")) return@find false
            title == "read" || title == "done" ||
            title.contains("mark read") || title.contains("mark as read") ||
            title.startsWith("read ") || title.endsWith(" read") ||
            title == "mark done" || title == "mark as done"
        }
    }

    fun triggerNotificationAction(action: Notification.Action) {
        try {
            val options = android.app.ActivityOptions.makeBasic()
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                options.pendingIntentBackgroundActivityStartMode = android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            }
            action.actionIntent.send(context, 0, null, null, null, null, options.toBundle())
        } catch (e: android.app.PendingIntent.CanceledException) {
            android.util.Log.e("NotificationRepository", "Failed to send notification action intent (canceled)", e)
        } catch (e: SecurityException) {
            android.util.Log.e("NotificationRepository", "SecurityException when sending notification action intent", e)
        } catch (e: Exception) {
            android.util.Log.e("NotificationRepository", "Unexpected error triggering notification action", e)
        }
    }

    suspend fun markNotificationsAsRead(
        notifications: List<HubNotification>,
        triggerNative: Boolean = settingsRepository.settings.value.triggerNativeMarkRead,
        syncDismissal: Boolean = settingsRepository.settings.value.syncDismissal
    ) = withContext(Dispatchers.IO) {
        if (notifications.isEmpty()) return@withContext
        val idsToArchive = mutableListOf<Int>()

        for (notif in notifications) {
            idsToArchive.add(notif.id)
            if (triggerNative) {
                val nativeAction = findNativeReadAction(notif.notificationKey)
                if (nativeAction != null) {
                    triggerNotificationAction(nativeAction)
                }
            }
            if (syncDismissal) {
                cancelServiceNotification(notif.notificationKey)
            }
        }

        val now = System.currentTimeMillis()
        notificationDao.archiveNotifications(idsToArchive, now)
        triggerWidgetUpdate()
    }

    private fun triggerWidgetUpdate() {
        ConduitWidgetProvider.updateAllWidgets(context)
    }
}

