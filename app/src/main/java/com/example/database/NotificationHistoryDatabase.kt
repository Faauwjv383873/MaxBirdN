package com.example.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [NotificationHistoryEntity::class], version = 1, exportSchema = false)
abstract class NotificationHistoryDatabase : RoomDatabase() {

    abstract fun notificationHistoryDao(): NotificationHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: NotificationHistoryDatabase? = null

        fun getDatabase(context: Context): NotificationHistoryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NotificationHistoryDatabase::class.java,
                    "notification_history_db"
                ).fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
