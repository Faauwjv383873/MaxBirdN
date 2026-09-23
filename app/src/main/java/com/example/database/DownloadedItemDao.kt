package com.example.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadedItemDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(item: DownloadedItemEntity)

    @Query("DELETE FROM downloaded_items WHERE id = :id")
    suspend fun deleteDownloadedItem(id: String)

    @Query("SELECT * FROM downloaded_items WHERE id = :id")
    fun getDownloadedItemById(id: String): Flow<DownloadedItemEntity?>

    @Query("SELECT * FROM downloaded_items WHERE id = :id")
    suspend fun getDownloadedItemByIdOnce(id: String): DownloadedItemEntity?

    @Query("SELECT * FROM downloaded_items WHERE localFilePath = :path LIMIT 1")
    suspend fun getDownloadedItemByPath(path: String): DownloadedItemEntity?

    @Query("SELECT * FROM downloaded_items WHERE localFilePath = :path LIMIT 1")
    fun getDownloadedItemByPathFlow(path: String): Flow<DownloadedItemEntity?>

    @Query("SELECT * FROM downloaded_items WHERE status = 'COMPLETED' ORDER BY createdAt DESC")
    fun getAllCompletedDownloads(): Flow<List<DownloadedItemEntity>>

    @Query("SELECT * FROM downloaded_items ORDER BY createdAt DESC")
    fun getAllDownloads(): Flow<List<DownloadedItemEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM downloaded_items WHERE id = :id AND status = 'COMPLETED')")
    fun isItemDownloaded(id: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM downloaded_items WHERE id = :id AND status = 'COMPLETED')")
    suspend fun isItemDownloadedOnce(id: String): Boolean
}
