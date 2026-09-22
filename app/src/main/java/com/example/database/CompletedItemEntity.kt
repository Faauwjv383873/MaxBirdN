package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "completed_items")
data class CompletedItemEntity(
    @PrimaryKey val itemId: String,
    val itemType: String = "LESSON", // "LESSON", "LIVE_CLASS", "EXAM", "QUIZ", "RESOURCE"
    val title: String = "",
    val subjectId: String = "",
    val programId: String = "",
    val chapterId: String = "",
    val completedAt: Long = System.currentTimeMillis()
)
