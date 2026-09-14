package com.example.database

import kotlinx.coroutines.flow.Flow

class SavedItemRepository(
    private val savedItemDao: SavedItemDao
) {
    val allSavedItems: Flow<List<SavedItemEntity>> = savedItemDao.getAllSavedItems()

    val allSavedItemIds: Flow<List<String>> = savedItemDao.getAllSavedItemIds()

    fun getSavedItemsByType(type: String): Flow<List<SavedItemEntity>> {
        return savedItemDao.getSavedItemsByType(type)
    }

    fun isItemSaved(id: String): Flow<Boolean> {
        return savedItemDao.isItemSaved(id)
    }

    suspend fun saveItem(item: SavedItemEntity) {
        savedItemDao.insertSavedItem(item)
    }

    suspend fun deleteSavedItemById(id: String) {
        savedItemDao.deleteSavedItemById(id)
    }
}
