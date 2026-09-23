package com.example.database

import android.content.Context
import kotlinx.coroutines.flow.Flow

class NotificationHistoryRepository(private val dao: NotificationHistoryDao) {

    suspend fun save(item: NotificationHistoryEntity): Long = dao.insert(item)

    fun getAll(): Flow<List<NotificationHistoryEntity>> = dao.getAll()

    fun getUnreadCount(): Flow<Int> = dao.getUnreadCount()

    suspend fun markAsRead(id: Long) = dao.markAsRead(id)

    suspend fun markAsClicked(id: Long) = dao.markAsClicked(id)

    suspend fun markAllAsRead() = dao.markAllAsRead()

    suspend fun clearAll() = dao.clearAll()

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    suspend fun deleteOlderThan(timestamp: Long) = dao.deleteOlderThan(timestamp)

    companion object {
        @Volatile
        private var INSTANCE: NotificationHistoryRepository? = null

        fun getInstance(context: Context): NotificationHistoryRepository {
            return INSTANCE ?: synchronized(this) {
                val database = NotificationHistoryDatabase.getDatabase(context)
                val instance = NotificationHistoryRepository(database.notificationHistoryDao())
                INSTANCE = instance
                instance
            }
        }
    }
}
