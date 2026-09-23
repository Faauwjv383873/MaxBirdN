package com.example.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: NotificationHistoryEntity): Long

    @Query("SELECT * FROM notification_history ORDER BY receivedAt DESC")
    fun getAll(): Flow<List<NotificationHistoryEntity>>

    @Query("SELECT COUNT(*) FROM notification_history WHERE isRead = 0")
    fun getUnreadCount(): Flow<Int>

    @Query("UPDATE notification_history SET isRead = 1 WHERE id = :id")
    suspend fun markAsRead(id: Long)

    @Query("UPDATE notification_history SET isClicked = 1, isRead = 1 WHERE id = :id")
    suspend fun markAsClicked(id: Long)

    @Query("UPDATE notification_history SET isRead = 1")
    suspend fun markAllAsRead()

    @Query("DELETE FROM notification_history")
    suspend fun clearAll()

    @Query("DELETE FROM notification_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM notification_history WHERE receivedAt < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)
}
