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
        subjectTitle: String? = null,
        otherChapterIds: List<String> = emptyList(),
        otherChapterNames: List<String> = emptyList(),
        chapterNo: String? = null
    ): List<StudentLessonItem> {
        return filterLessons(
            lessons = allLessons.values.toList(),
            candidateChapterIds = candidateChapterIds,
            chapterName = chapterName,
            subjectTitle = subjectTitle,
            otherChapterIds = otherChapterIds,
            otherChapterNames = otherChapterNames,
            chapterNo = chapterNo
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
        chapterNo: String? = null
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

        val matched = lessons.filter { item ->
            val lTitle = item.title ?: ""
            val lLiveClass = item.live_class
            val lChapterName = (lLiveClass?.chapter_name ?: "").trim()
            val lCleanChapterName = cleanChapterTitle(lChapterName)
            val lSubject = (item.subject_name ?: lLiveClass?.subject_name ?: "").trim()

            // Collect all chapter IDs associated with this lesson
            val lessonChapterIds = listOfNotNull(
                item.chapter_id?.trim()?.takeIf { it.isNotBlank() },
                lLiveClass?.chapter_id?.trim()?.takeIf { it.isNotBlank() }
            )

            // 1. Direct positive chapter ID match
            val isDirectIdMatch = lessonChapterIds.any { id ->
                cleanCandidateIds.any { cid -> cid.equals(id, ignoreCase = true) }
            }

            // 2. Direct negative chapter ID check:
            // If the lesson explicitly has a chapter ID belonging to ANOTHER chapter, exclude it!
            val belongsToOtherChapter = lessonChapterIds.any { id ->
                cleanOtherIds.any { otherId -> otherId.equals(id, ignoreCase = true) }
            }
            if (belongsToOtherChapter && !isDirectIdMatch) {
                return@filter false
            }

            // 3. Negative chapter name check:
            // If the lesson's chapter name matches another chapter, and DOES NOT match our chapter
            val matchesOtherChapterName = cleanOtherNames.any { otherName ->
                otherName.length >= 3 && (
                    lCleanChapterName.contains(otherName, ignoreCase = true) ||
                    otherName.contains(lCleanChapterName, ignoreCase = true)
                )
            }
            val matchesThisChapterName = cleanName.length >= 3 && (
                lCleanChapterName.contains(cleanName, ignoreCase = true) ||
                cleanName.contains(lCleanChapterName, ignoreCase = true)
            )
            if (matchesOtherChapterName && !matchesThisChapterName && !isDirectIdMatch) {
                return@filter false
            }

            // 4. Positive Chapter Name Match
            val chapterNameMatch = matchesThisChapterName || (
                rawName.isNotBlank() && (
                    lChapterName.contains(rawName, ignoreCase = true) ||
                    rawName.contains(lChapterName, ignoreCase = true)
                )
            )

            // 5. Positive Lesson Title Match
            val titleMatch = (cleanName.length >= 3 && lTitle.contains(cleanName, ignoreCase = true)) ||
                (rawName.isNotBlank() && lTitle.contains(rawName, ignoreCase = true))

            // 6. Chapter number match in title
            val noMatch = if (!cleanChapterNo.isNullOrBlank()) {
                val bnNo = com.example.utils.toBengaliDigits(cleanChapterNo)
                val enNo = cleanChapterNo.replace(Regex("[^0-9]"), "")
                val regexNo = "(?:অধ্যায়|অধ্যায়|চ্যাপ্টার|Chapter|Ch)\\s*([০-৯0-9]+)"
                val m = Regex(regexNo, RegexOption.IGNORE_CASE).find(lTitle)
                if (m != null) {
                    val foundNo = m.groupValues[1]
                    foundNo == cleanChapterNo || foundNo == bnNo || (enNo.isNotBlank() && foundNo == enNo)
                } else false
            } else false

            // Subject relevance check
            val subjectMatch = cleanSubject.isBlank() || lSubject.isBlank() ||
                lSubject.contains(cleanSubject, ignoreCase = true) ||
                cleanSubject.contains(lSubject, ignoreCase = true)

            (isDirectIdMatch || ((chapterNameMatch || titleMatch || noMatch) && subjectMatch))
        }

        return matched
            .distinctBy { it.id.ifBlank { "${it.content_id}_${it.start_time}_${it.title}" } }
            .sortedBy { it.start_time ?: "" }
    }
}
