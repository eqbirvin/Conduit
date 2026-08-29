package com.conduit.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications WHERE isArchived = 0 AND isDemo = :isDemo ORDER BY timestamp DESC")
    fun getAllNotifications(isDemo: Boolean): Flow<List<HubNotification>>

    @Query("SELECT * FROM notifications WHERE isArchived = 1 AND isDemo = :isDemo ORDER BY archivedTimestamp DESC")
    fun getArchivedNotifications(isDemo: Boolean): Flow<List<HubNotification>>

    @Insert
    suspend fun insert(notification: HubNotification)

    @Query("UPDATE notifications SET isArchived = 1, archivedTimestamp = :timestamp WHERE id = :id")
    suspend fun archiveNotification(id: Int, timestamp: Long)
    
    @Query("UPDATE notifications SET isArchived = 1, archivedTimestamp = :timestamp WHERE notificationKey = :key AND isArchived = 0 AND isPinned = 0")
    suspend fun archiveNotificationByKey(key: String, timestamp: Long)
    
    @Query("UPDATE notifications SET isArchived = 1, isSnoozed = 1, archivedTimestamp = :timestamp WHERE id = :id")
    suspend fun snoozeNotification(id: Int, timestamp: Long)
    
    @Query("UPDATE notifications SET isPinned = NOT isPinned WHERE id = :id")
    suspend fun togglePin(id: Int)
    
    @Query("UPDATE notifications SET isArchived = 1, archivedTimestamp = :timestamp WHERE id IN (:ids)")
    suspend fun archiveNotifications(ids: List<Int>, timestamp: Long)

    @Query("UPDATE notifications SET isPinned = :pin WHERE id IN (:ids)")
    suspend fun pinNotifications(ids: List<Int>, pin: Boolean)

    @Query("DELETE FROM notifications")
    suspend fun deleteAll()

    @Query("DELETE FROM notifications WHERE isDemo = 1")
    suspend fun deleteDemoNotifications()

    @Query("SELECT * FROM notifications WHERE isArchived = 0 ORDER BY timestamp DESC")
    fun getActiveNotificationsWidgetSync(): List<HubNotification>

    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    fun getAllNotificationsWidgetSync(): List<HubNotification>

    @Query("SELECT * FROM notifications WHERE isArchived = 0")
    suspend fun getActiveNotificationsSync(): List<HubNotification>

    @Query("SELECT * FROM notifications")
    suspend fun getAllNotificationsSync(): List<HubNotification>

    @Query("DELETE FROM notifications WHERE id IN (:ids)")
    suspend fun deleteNotifications(ids: List<Int>)

    @Query("SELECT * FROM notifications WHERE packageName = :pkg AND (title = :title OR title = :title || ' - Replied') ORDER BY timestamp DESC LIMIT 1")
    suspend fun getMostRecentByTitleAndPackage(pkg: String, title: String): HubNotification?

    @Query("SELECT * FROM notifications WHERE packageName = :pkg AND title = :title AND text = :text ORDER BY timestamp DESC LIMIT 1")
    suspend fun getMostRecentExactMatch(pkg: String, title: String, text: String): HubNotification?

    @Query("SELECT * FROM notifications WHERE notificationKey = :key AND isArchived = 0 ORDER BY timestamp DESC LIMIT 1")
    suspend fun getActiveNotificationByKey(key: String): HubNotification?

    @Query("UPDATE notifications SET title = :newTitle, text = :newText, timestamp = :newTimestamp WHERE id = :id")
    suspend fun updateNotificationContent(id: Int, newTitle: String, newText: String, newTimestamp: Long)

    @Query("UPDATE notifications SET title = :newTitle, text = :newText, timestamp = :newTimestamp, isArchived = 0 WHERE id = :id")
    suspend fun updateAndUnarchive(id: Int, newTitle: String, newText: String, newTimestamp: Long)

    @Query("DELETE FROM notifications WHERE isArchived = 1 AND isPinned = 0 AND timestamp < :cutoffTimestamp")
    suspend fun deleteOldArchivedNotifications(cutoffTimestamp: Long): Int

    @Query("UPDATE notifications SET title = :newTitle, text = :newText, timestamp = :newTimestamp, kind = :kind, detachedAt = NULL WHERE id = :id")
    suspend fun updateNotificationContentDetailed(id: Int, newTitle: String, newText: String, newTimestamp: Long, kind: String)

    @Query("UPDATE notifications SET title = :newTitle, text = :newText, timestamp = :newTimestamp, isArchived = 0, kind = :kind, detachedAt = NULL WHERE id = :id")
    suspend fun updateAndUnarchiveDetailed(id: Int, newTitle: String, newText: String, newTimestamp: Long, kind: String)

    @Query("UPDATE notifications SET detachedAt = :timestamp WHERE notificationKey = :key AND isArchived = 0")
    suspend fun setDetachedByKey(key: String, timestamp: Long)

    @Query("UPDATE notifications SET detachedAt = :timestamp WHERE id = :id")
    suspend fun setDetached(id: Int, timestamp: Long)

    @Query("UPDATE notifications SET detachedAt = NULL WHERE id = :id")
    suspend fun clearDetached(id: Int)

    @Query("SELECT id, notificationKey, detachedAt, kind, isPinned, isSnoozed FROM notifications WHERE isArchived = 0")
    suspend fun getActiveNotificationMetadata(): List<NotificationMetadata>

    @Query("UPDATE notifications SET notificationKey = :newKey, timestamp = :newTimestamp, kind = :kind, detachedAt = NULL WHERE id = :id")
    suspend fun adoptRow(id: Int, newKey: String, newTimestamp: Long, kind: String)

    @Query("SELECT * FROM notifications WHERE packageName = :pkg AND title = :title AND text = :text AND isArchived = 0 LIMIT 1")
    suspend fun getUnarchivedExactMatch(pkg: String, title: String, text: String): HubNotification?

    companion object {
        const val RETENTION_DAYS = 90
    }
}

