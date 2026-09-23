package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notification_history")
data class NotificationHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val body: String,
    val imageUrl: String? = null,
    val type: String? = null,
    val dataJson: String? = null,
    val deepLink: String? = null,
    val receivedAt: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val isClicked: Boolean = false
)
