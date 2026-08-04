package com.conduit.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notifications",
    indices = [
        Index(value = ["notificationKey"]),
        Index(value = ["isArchived", "timestamp"]),
        Index(value = ["packageName"])
    ]
)
data class HubNotification(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val packageName: String,
    val notificationKey: String,
    val title: String?,
    val text: String?,
    val timestamp: Long,
    val channel: String,
    val isArchived: Boolean = false,
    val archivedTimestamp: Long? = null,
    val isSnoozed: Boolean = false,
    val isPinned: Boolean = false
)
