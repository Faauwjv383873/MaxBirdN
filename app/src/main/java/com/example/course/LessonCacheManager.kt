package com.example.course

import com.example.api.StudentLessonItem
import java.util.concurrent.ConcurrentHashMap

/**
 * Singleton in-memory cache for all academic and routine lessons.
 * Allows instant lookup of lessons across Home, Routine, and Course Chapter views.
 */
object LessonCacheManager {
    private val allLessons = ConcurrentHashMap<String, StudentLessonItem>()

    fun markLessonCompletedInCache(lessonId: String) {
        if (lessonId.isBlank()) return
        allLessons.forEach { (key, item) ->
            if (item.id == lessonId || item.content_id == lessonId || item.live_class?.id == lessonId) {
                allLessons[key] = item.copy(user_activity_state = "COMPLETED")
            }
        }
    }

    fun enrichWithCompletedState(lessons: List<StudentLessonItem>, completedIds: Set<String>): List<StudentLessonItem> {
        if (completedIds.isEmpty()) return lessons
        return lessons.map { item ->
            val isComp = completedIds.contains(item.id) || 
                         completedIds.contains(item.content_id) || 
                         completedIds.contains(item.live_class?.id) ||
                         item.user_activity_state.equals("COMPLETED", ignoreCase = true) ||
                         item.user_activity_state.equals("ATTENDED", ignoreCase = true)
            if (isComp && item.user_activity_state != "COMPLETED") {
                item.copy(user_activity_state = "COMPLETED")
            } else item
        }
    }

    fun saveLessons(lessons: List<StudentLessonItem>, programId: String? = null) {
        if (lessons.isEmpty()) return
        val cleanProg = programId?.trim()?.takeIf { it.isNotBlank() }
        lessons.forEach { item ->
            val updatedItem = if (cleanProg != null && item.program_id.isNullOrBlank()) {
                item.copy(program_id = cleanProg)
            } else item
            val key = updatedItem.id.ifBlank { "${updatedItem.content_id}_${updatedItem.start_time}_${updatedItem.title}" }
            if (key.isNotBlank()) {
                allLessons[key] = updatedItem
            }
        }
    }

    fun getAllLessons(): List<StudentLessonItem> {
        return allLessons.values.toList()
    }

    fun findLessonsForChapter(
        candidateChapterIds: List<String>,
        chapterName: String?,
        subjectTitle: String? = null,
        otherChapterIds: List<String> = emptyList(),
        otherChapterNames: List<String> = emptyList(),
        chapterNo: String? = null,
        programId: String? = null
    ): List<StudentLessonItem> {
        return filterLessons(
            lessons = allLessons.values.toList(),
            candidateChapterIds = candidateChapterIds,
            chapterName = chapterName,
            subjectTitle = subjectTitle,
            otherChapterIds = otherChapterIds,
            otherChapterNames = otherChapterNames,
            chapterNo = chapterNo,
            programId = programId
        )
    }

    fun cleanChapterTitle(title: String?): String {
        if (title.isNullOrBlank()) return ""
        val withoutPrefixes = title.replace(
            Regex("^(?:অধ্যায়|অধ্যায়|চ্যাপ্টার|Chapter|Ch|পর্ব|পার্ট|[০-৯0-9]+(?:ম|য়|র্থ|ষ্ঠ|তম)?)\\s*([০-৯0-9]+)?[:\\.\\-\\s]*", RegexOption.IGNORE_CASE),
            ""
        ).trim()
        return if (withoutPrefixes.length >= 2) withoutPrefixes else title.trim()
    }

    fun filterLessons(
        lessons: List<StudentLessonItem>,
        candidateChapterIds: List<String>,
        chapterName: String?,
        subjectTitle: String? = null,
        otherChapterIds: List<String> = emptyList(),
        otherChapterNames: List<String> = emptyList(),
        chapterNo: String? = null,
        programId: String? = null
    ): List<StudentLessonItem> {
        val cleanCandidateIds = candidateChapterIds.map { it.trim() }.filter { it.isNotBlank() }
        val cleanOtherIds = otherChapterIds.map { it.trim() }.filter { it.isNotBlank() && !cleanCandidateIds.contains(it) }

        val rawName = chapterName?.trim() ?: ""
        val cleanName = cleanChapterTitle(rawName)

        val cleanOtherNames = otherChapterNames
            .map { cleanChapterTitle(it) }
            .filter { it.isNotBlank() && !it.equals(cleanName, ignoreCase = true) }

        val cleanSubject = subjectTitle?.replace("পত্র", "")?.replace("১ম", "")?.replace("২য়", "")?.trim() ?: ""
        val cleanChapterNo = chapterNo?.trim()?.takeIf { it.isNotBlank() }
        val cleanProgramId = programId?.trim()?.takeIf { it.isNotBlank() }

        fun isSameId(id1: String, id2: String): Boolean {
            if (id1.equals(id2, ignoreCase = true)) return true
            val d1 = id1.replace(Regex("[^0-9]"), "")
            val d2 = id2.replace(Regex("[^0-9]"), "")
            return d1.isNotBlank() && d2.isNotBlank() && d1 == d2
        }

        val matched = lessons.filter { item ->
            // CRITICAL PROGRAM/COURSE ISOLATION CHECK:
            // If programId is specified and the item has a program_id set, they MUST match!
            val itemProgId = item.program_id?.trim()?.takeIf { it.isNotBlank() }
            if (cleanProgramId != null && itemProgId != null) {
                if (!isSameId(itemProgId, cleanProgramId)) {
                    return@filter false
                }
            }
            val lTitle = item.title ?: ""
            val lLiveClass = item.live_class
            val lChapterName = (lLiveClass?.chapter_name ?: "").trim()
            val lCleanChapterName = cleanChapterTitle(lChapterName)
            val lSubject = (item.subject_name ?: lLiveClass?.subject_name ?: "").trim()

            // Collect all chapter IDs associated with this lesson
            val lessonChapterIds = listOfNotNull(
                item.chapter_id?.trim()?.takeIf { it.isNotBlank() },
                lLiveClass?.chapter_id?.trim()?.takeIf { it.isNotBlank() }
            ).distinct()

            // 1. Direct positive chapter ID match
            val isDirectIdMatch = lessonChapterIds.any { id ->
                cleanCandidateIds.any { cid -> isSameId(id, cid) }
            }

            // If we have explicit candidate IDs and the lesson has explicit chapter IDs,
            // we MUST strictly match by ID. No fallbacks allowed.
            if (cleanCandidateIds.isNotEmpty() && lessonChapterIds.isNotEmpty()) {
                return@filter isDirectIdMatch
            }

            // Subject relevance check is always required
            val subjectMatch = cleanSubject.isBlank() || lSubject.isBlank() ||
                lSubject.contains(cleanSubject, ignoreCase = true) ||
                cleanSubject.contains(lSubject, ignoreCase = true)

            if (!subjectMatch) {
                return@filter false
            }

            // 2. Direct negative chapter ID check against other known chapter IDs
            val belongsToOtherChapter = lessonChapterIds.any { id ->
                cleanOtherIds.any { otherId -> isSameId(id, otherId) }
            }
            if (belongsToOtherChapter && !isDirectIdMatch) {
                return@filter false
            }

            // 3. Exact or clean chapter name matching (when IDs are empty)
            var matchesThisChapterName = false
            if (lCleanChapterName.isNotBlank() && cleanName.isNotBlank()) {
                matchesThisChapterName = lCleanChapterName.equals(cleanName, ignoreCase = true) ||
                    (cleanName.length >= 4 && lCleanChapterName.contains(cleanName, ignoreCase = true)) ||
                    (lCleanChapterName.length >= 4 && cleanName.contains(lCleanChapterName, ignoreCase = true))
            } else if (lChapterName.isNotBlank() && rawName.isNotBlank()) {
                matchesThisChapterName = lChapterName.equals(rawName, ignoreCase = true) ||
                    (rawName.length >= 4 && lChapterName.contains(rawName, ignoreCase = true)) ||
                    (lChapterName.length >= 4 && rawName.contains(lChapterName, ignoreCase = true))
            }

            // 4. Negative chapter name check:
            // If the lesson's chapter name matches another chapter, and DOES NOT match our chapter
            val matchesOtherChapterName = cleanOtherNames.any { otherName ->
                otherName.length >= 4 && (
                    lCleanChapterName.contains(otherName, ignoreCase = true) ||
                    otherName.contains(lCleanChapterName, ignoreCase = true)
                )
            }
            if (matchesOtherChapterName && !matchesThisChapterName) {
                return@filter false
            }

            if (matchesThisChapterName) {
                return@filter true
            }

            // Last resort: If the lesson has NO chapter ID and NO chapter name at all,
            // we can check if the lesson title contains the clean chapter name,
            // but ONLY if the clean chapter name is long enough (>= 5 chars) to avoid matching common noise like numbers or "Ch".
            if (lessonChapterIds.isEmpty() && lCleanChapterName.isBlank() && lChapterName.isBlank()) {
                if (cleanName.length >= 5 && lTitle.contains(cleanName, ignoreCase = true)) {
                    // Prevent matching if it contains another chapter's name
                    val matchesOtherTitle = cleanOtherNames.any { otherName ->
                        otherName.length >= 5 && lTitle.contains(otherName, ignoreCase = true)
                    }
                    if (!matchesOtherTitle) {
                        return@filter true
                    }
                }
            }

            false
        }

        return matched
            .distinctBy { it.id.ifBlank { "${it.content_id}_${it.start_time}_${it.title}" } }
            .sortedBy { it.start_time ?: "" }
    }
}
