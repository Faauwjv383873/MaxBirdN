package com.example.course

import com.example.api.StudentLessonItem
import java.util.concurrent.ConcurrentHashMap

/**
 * Singleton in-memory cache for all academic and routine lessons.
 * Allows instant lookup of lessons across Home, Routine, and Course Chapter views.
 */
object LessonCacheManager {
    private val allLessons = ConcurrentHashMap<String, StudentLessonItem>()

    fun saveLessons(lessons: List<StudentLessonItem>) {
        if (lessons.isEmpty()) return
        lessons.forEach { item ->
            val key = item.id.ifBlank { "${item.content_id}_${item.start_time}_${item.title}" }
            if (key.isNotBlank()) {
                allLessons[key] = item
            }
        }
    }

    fun getAllLessons(): List<StudentLessonItem> {
        return allLessons.values.toList()
    }

    fun findLessonsForChapter(
        candidateChapterIds: List<String>,
        chapterName: String?,
        subjectTitle: String? = null
    ): List<StudentLessonItem> {
        return filterLessons(allLessons.values.toList(), candidateChapterIds, chapterName, subjectTitle)
    }

    fun filterLessons(
        lessons: List<StudentLessonItem>,
        candidateChapterIds: List<String>,
        chapterName: String?,
        subjectTitle: String? = null
    ): List<StudentLessonItem> {
        val cleanCandidateIds = candidateChapterIds.filter { it.isNotBlank() }
        val rawName = chapterName?.trim() ?: ""
        // Remove prefixes like "অধ্যায় ১:", "অধ্যায় ১০ -", "Chapter 3:"
        val cleanName = rawName.replace(
            Regex("^(অধ্যায়|অধ্যায়|চ্যাপ্টার|Chapter)\\s*([০-৯0-9]+)?[:\\.\\-\\s]*", RegexOption.IGNORE_CASE),
            ""
        ).trim()

        val cleanSubject = subjectTitle?.replace("পত্র", "")?.trim() ?: ""

        val matched = lessons.filter { item ->
            val lTitle = item.title ?: ""
            val lChapterName = item.live_class?.chapter_name ?: ""
            val lChapterId = item.chapter_id ?: ""
            val lContentId = item.content_id ?: ""
            val lSubject = item.subject_name ?: ""

            // 1. Chapter ID or Content ID match
            val idMatch = cleanCandidateIds.any { cid ->
                lChapterId.equals(cid, ignoreCase = true) || lContentId.equals(cid, ignoreCase = true)
            }

            // 2. Chapter name in live_class
            val chapterNameMatch = cleanName.isNotBlank() && (
                lChapterName.contains(cleanName, ignoreCase = true) ||
                cleanName.contains(lChapterName, ignoreCase = true)
            )

            // 3. Chapter name inside lesson title (e.g. "পর্ব-৩: আন্তর্জাতিক বাণিজ্য" contains "আন্তর্জাতিক বাণিজ্য")
            val titleMatch = cleanName.isNotBlank() && (
                lTitle.contains(cleanName, ignoreCase = true) ||
                (rawName.isNotBlank() && lTitle.contains(rawName, ignoreCase = true)) ||
                (cleanName.length >= 4 && lTitle.contains(cleanName.take(8), ignoreCase = true))
            )

            // Subject relevance check (if both lesson and filter specify subject)
            val subjectMatch = cleanSubject.isBlank() || lSubject.isBlank() ||
                lSubject.contains(cleanSubject, ignoreCase = true) ||
                cleanSubject.contains(lSubject, ignoreCase = true)

            (idMatch || ((chapterNameMatch || titleMatch) && subjectMatch))
        }

        return matched
            .distinctBy { it.id.ifBlank { "${it.content_id}_${it.start_time}_${it.title}" } }
            .sortedBy { it.start_time ?: "" }
    }
}
