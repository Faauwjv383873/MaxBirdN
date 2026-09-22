package com.example.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CompletedItemDao {
    @Query("SELECT * FROM completed_items ORDER BY completedAt DESC")
    fun getAllCompletedItems(): Flow<List<CompletedItemEntity>>

    @Query("SELECT itemId FROM completed_items")
    fun getAllCompletedItemIds(): Flow<List<String>>

    @Query("SELECT itemId FROM completed_items")
    suspend fun getCompletedItemIdsList(): List<String>

    @Query("SELECT EXISTS(SELECT 1 FROM completed_items WHERE itemId = :itemId)")
    fun isItemCompletedFlow(itemId: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM completed_items WHERE itemId = :itemId)")
    suspend fun isItemCompleted(itemId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markCompleted(item: CompletedItemEntity)

    @Query("DELETE FROM completed_items WHERE itemId = :itemId")
    suspend fun removeCompleted(itemId: String)
}
