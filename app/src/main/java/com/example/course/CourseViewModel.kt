package com.example.course

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.api.*
import com.example.auth.SessionManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SubjectWithProgress(
    val subject: AcademicSubjectItem,
    val progressBar: SubjectProgressBarItem?
) {
    val progressPercentage: Int
        get() = progressBar?.percentage?.toInt() ?: 0

    val completedChapters: Int
        get() = progressBar?.completed_chapters ?: 0

    val totalChapters: Int
        get() = progressBar?.total_chapters ?: 0
}

data class CourseUiState(
    // Enrolled & Available Programs lists
    val enrolledPrograms: List<EnrolledProgram> = emptyList(),
    val freePrograms: List<OtherProgram> = emptyList(),
    val otherPrograms: List<OtherProgram> = emptyList(),
    val isProgramsLoading: Boolean = false,
    val selectedCourseProgram: EnrolledProgram? = null,

    // Active Program Info
    val programId: String = "",
    val programTitle: String = "",
    val activePhaseId: String = "",
    val activePhaseTitle: String = "",
    val phases: List<PhaseItem> = emptyList(),
    val selectedPhase: PhaseItem? = null,

    // Tier 1: Subjects
    val subjects: List<SubjectWithProgress> = emptyList(),
    val isSubjectsLoading: Boolean = false,
    val subjectsErrorMessage: String? = null,

    // Tier 2: Selected Subject & Chapters
    val selectedSubjectCode: String = "",
    val selectedSubjectTitle: String = "",
    val selectedSubjectColor: String = "",
    val chapters: List<AcademicChapterItem> = emptyList(),
    val isChaptersLoading: Boolean = false,
    val chaptersErrorMessage: String? = null,

    // Tier 3: Selected Chapter & Lessons
    val selectedChapterId: String = "",
    val selectedChapterName: String = "",
    val selectedChapterStatus: String = "",
    val lessons: List<StudentLessonItem> = emptyList(),
    val isLessonsLoading: Boolean = false,
    val lessonsErrorMessage: String? = null,
    val lessonsDiagnosticInfo: String? = null,

    // Tier 4: Selected Lesson Detail
    val selectedLesson: StudentLessonItem? = null,

    // Animated Lessons
    val chapterAnimatedLessons: List<TopicFullItem> = emptyList(),
    val isChapterAnimationsLoading: Boolean = false,
    val subjectAnimatedLessons: List<TopicFullItem> = emptyList(),
    val isSubjectAnimationsLoading: Boolean = false
)

class CourseViewModel(
    private val apiService: ShikhoApiService,
    private val sessionManager: SessionManager
) : ViewModel() {

    // Caching for instant sub-second loading
    private val chaptersCache = java.util.concurrent.ConcurrentHashMap<String, List<AcademicChapterItem>>()
    private val lessonsCache = java.util.concurrent.ConcurrentHashMap<String, List<StudentLessonItem>>()

    private val _uiState = MutableStateFlow(CourseUiState())
    val uiState: StateFlow<CourseUiState> = _uiState.asStateFlow()

    init {
        fetchEnrolledPrograms()
        loadSubjects()
    }

    fun openCourse(program: EnrolledProgram) {
        val title = program.title_bn ?: "প্রোগ্রাম"
        _uiState.update { it.copy(selectedCourseProgram = program) }
        switchProgram(
            newProgramId = program.id,
            newProgramTitle = title,
            batchId = program.enrollment_details?.batch_id,
            classCode = program.classes?.firstOrNull()
        )
    }

    fun closeCourseDetails() {
        _uiState.update { it.copy(selectedCourseProgram = null) }
    }

    fun fetchEnrolledPrograms() {
        viewModelScope.launch {
            _uiState.update { it.copy(isProgramsLoading = true) }
            try {
                val userClassName = sessionManager.getUserClassName() ?: "C11"
                val group = sessionManager.getUserGroup() ?: "Humanities"
                val formattedGroup = when (group.lowercase()) {
                    "humanities", "humanities_group" -> "Humanities"
                    "science", "science_group" -> "Science"
                    "businessstudies" -> "BusinessStudies"
                    "commerce" -> "Commerce"
                    "business" -> "Business"
                    "business_studies", "business studies" -> "Business_Studies"
                    else -> group
                }

                val query = GraphQlQuery(
                    operationName = "GetEnrolledAcademicProgram",
                    query = """
                        query GetEnrolledAcademicProgram(${'$'}className: AcademicProgramClassEnum, ${'$'}group: StudyGroupTypeEnum) {
                          listAcademicProgramByEnrollment(class: ${'$'}className, group: ${'$'}group) {
                            enrolled_programs {
                              id
                              classes
                              title_bn
                              banner_url
                              color
                              is_free
                              trial_enabled
                              enrollment_details {
                                batch_id
                                is_active
                                trial_end_date
                                type
                                expiry_date
                              }
                              subjects {
                                code
                                display_bn
                                color_code
                                icon
                              }
                            }
                            other_programs {
                              id
                              classes
                              title_bn
                              banner_url
                              phase_pricing
                              is_free
                              has_animated_video
                              full_program_discount_price
                              trial_enabled
                              trial_duration
                            }
                          }
                        }
                    """.trimIndent(),
                    variables = mutableMapOf<String, Any?>(
                        "className" to userClassName,
                        "group" to null // Fetch ALL groups under this class
                    )
                )

                val response = apiService.getAcademicProgram(query)
                var enrolled = response.data?.listAcademicProgramByEnrollment?.enrolled_programs ?: emptyList()
                var otherAll = response.data?.listAcademicProgramByEnrollment?.other_programs ?: emptyList()

                val enrolledIds = enrolled.map { it.id }.toSet()
                val nonEnrolledOthers = otherAll.filter { it.id !in enrolledIds }

                // Promote syllabus other programs into enrolled programs so all syllabus courses are unlocked
                val promotedEnrolled = nonEnrolledOthers.map { it.toEnrolledProgram() }
                enrolled = (enrolled + promotedEnrolled).distinctBy { it.id }

                var freeList = nonEnrolledOthers.filter { it.is_free == true }
                var paidOtherList = nonEnrolledOthers.filter { it.is_free != true }

                // Prioritize/sort lists based on selected group so that the selected department is #1
                val groupLower = group.lowercase()
                val keywords = when {
                    groupLower.contains("science") || groupLower.contains("বিজ্ঞান") -> listOf("বিজ্ঞান", "science")
                    groupLower.contains("humanities") || groupLower.contains("মানবিক") || groupLower.contains("hum") -> listOf("মানবিক", "humanities")
                    groupLower.contains("business") || groupLower.contains("commerce") || groupLower.contains("ব্যবসায়") || groupLower.contains("ব্যাবসা") -> listOf("ব্যবসায়", "ব্যাবসা", "business", "commerce", "studies")
                    else -> emptyList()
                }

                val otherKeywords = when {
                    groupLower.contains("science") || groupLower.contains("বিজ্ঞান") -> listOf("মানবিক", "humanities", "ব্যবসায়", "ব্যাবসা", "business", "commerce", "studies")
                    groupLower.contains("humanities") || groupLower.contains("মানবিক") || groupLower.contains("hum") -> listOf("বিজ্ঞান", "science", "ব্যবসায়", "ব্যাবসা", "business", "commerce")
                    groupLower.contains("business") || groupLower.contains("commerce") || groupLower.contains("ব্যবসায়") || groupLower.contains("ব্যাবসা") -> listOf("বিজ্ঞান", "science", "মানবিক", "humanities")
                    else -> emptyList()
                }

                val filterPredicate = { title: String? ->
                    val t = title?.lowercase() ?: ""
                    var matchesOther = false
                    for (okw in otherKeywords) {
                        if (t.contains(okw)) {
                            matchesOther = true
                            break
                        }
                    }
                    !matchesOther
                }

                enrolled = enrolled.filter { filterPredicate(it.title_bn) }
                freeList = freeList.filter { filterPredicate(it.title_bn) }
                paidOtherList = paidOtherList.filter { filterPredicate(it.title_bn) }

                val scoreCalculator = { title: String? ->
                    val t = title?.lowercase() ?: ""
                    var score = 0
                    for (kw in keywords) {
                        if (t.contains(kw)) score += 10
                    }
                    if (t.contains("কমন") || t.contains("common") || t.contains("আবশ্যিক")) {
                        score += 5
                    }
                    score
                }

                enrolled = enrolled.sortedByDescending { scoreCalculator(it.title_bn) }
                freeList = freeList.sortedByDescending { scoreCalculator(it.title_bn) }
                paidOtherList = paidOtherList.sortedByDescending { scoreCalculator(it.title_bn) }

                if (enrolled.isEmpty()) {
                    enrolled = getFallbackEnrolledPrograms(userClassName, group)
                }
                if (freeList.isEmpty()) {
                    freeList = getFallbackFreePrograms(userClassName, group)
                }
                if (paidOtherList.isEmpty()) {
                    paidOtherList = getFallbackOtherPrograms(userClassName, group)
                }

                _uiState.update {
                    it.copy(
                        enrolledPrograms = enrolled,
                        freePrograms = freeList,
                        otherPrograms = paidOtherList,
                        isProgramsLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { current ->
                    val userClassName = sessionManager.getUserClassName() ?: "C11"
                    val group = sessionManager.getUserGroup() ?: "Humanities"
                    if (current.enrolledPrograms.isEmpty()) {
                        current.copy(
                            enrolledPrograms = getFallbackEnrolledPrograms(userClassName, group),
                            freePrograms = if (current.freePrograms.isEmpty()) getFallbackFreePrograms(userClassName, group) else current.freePrograms,
                            otherPrograms = if (current.otherPrograms.isEmpty()) getFallbackOtherPrograms(userClassName, group) else current.otherPrograms,
                            isProgramsLoading = false
                        )
                    } else {
                        current.copy(isProgramsLoading = false)
                    }
                }
            }
        }
    }

    private fun getFallbackEnrolledPrograms(className: String, group: String): List<EnrolledProgram> {
        val displayClass = when (className.uppercase()) {
            "C9", "CLASS 9", "C09" -> "নবম শ্রেণি"
            "C10", "CLASS 10" -> "দশম শ্রেণি"
            "C11", "CLASS 11" -> "একাদশ শ্রেণি"
            "C12", "CLASS 12" -> "দ্বাদশ শ্রেণি"
            else -> "এইচএসসি"
        }
        
        val examYear = when (className.uppercase()) {
            "C9", "C09" -> "SSC '28"
            "C10" -> "SSC '27"
            "C11" -> "HSC '27"
            "C12" -> "HSC '26"
            else -> "HSC"
        }

        val displayGroup = when (group.lowercase()) {
            "humanities", "humanities_group" -> "মানবিক"
            "science", "science_group" -> "বিজ্ঞান"
            "business", "business_studies", "commerce", "businessstudies" -> "ব্যবসায় শিক্ষা"
            else -> "মানবিক"
        }

        return listOf(
            EnrolledProgram(
                id = "fallback-ep-1-${className}-${group}",
                classes = listOf(className),
                title_bn = "$displayClass - $examYear ($displayGroup) বার্ষিক পরীক্ষা প্রস্তুতি",
                banner_url = "https://res.cloudinary.com/cross-border-education-technologies-pte-ltd/image/upload/v1751437593/hftof9ha6mequpiqpkbh.jpg",
                color = null,
                is_free = false,
                trial_enabled = true,
                enrollment_details = EnrollmentDetails(batch_id = "fallback-batch-1", is_active = true, trial_end_date = null, type = "Paid", expiry_date = "2026-12-31T17:59:59Z"),
                subjects = emptyList()
            ),
            EnrolledProgram(
                id = "fallback-ep-2-${className}-${group}",
                classes = listOf(className),
                title_bn = "দুরন্ত $displayClass - $displayGroup",
                banner_url = "https://res.cloudinary.com/cross-border-education-technologies-pte-ltd/image/upload/v1751274615/mqllgmiyqdg3mf7xyxva.jpg",
                color = null,
                is_free = false,
                trial_enabled = false,
                enrollment_details = EnrollmentDetails(batch_id = "fallback-batch-2", is_active = true, trial_end_date = null, type = "Paid", expiry_date = "2025-09-15T17:59:59Z"),
                subjects = emptyList()
            ),
            EnrolledProgram(
                id = "fallback-ep-3-${className}-${group}",
                classes = listOf(className),
                title_bn = "Think AI",
                banner_url = "https://res.cloudinary.com/cross-border-education-technologies-pte-ltd/image/upload/v1777438148/mjpjbimt6wkkwsmtrh8l.jpg",
                color = null,
                is_free = false,
                trial_enabled = false,
                enrollment_details = EnrollmentDetails(batch_id = "fallback-batch-3", is_active = true, trial_end_date = null, type = "Paid", expiry_date = "2029-04-30T17:59:59Z"),
                subjects = emptyList()
            )
        )
    }

    private fun getFallbackFreePrograms(className: String, group: String): List<OtherProgram> {
        val displayClass = when (className.uppercase()) {
            "C9", "CLASS 9", "C09" -> "নবম শ্রেণি"
            "C10", "CLASS 10" -> "দশম শ্রেণি"
            "C11", "CLASS 11" -> "একাদশ শ্রেণি"
            "C12", "CLASS 12" -> "দ্বাদশ শ্রেণি"
            else -> "এইচএসসি"
        }
        return listOf(
            OtherProgram(
                id = "fallback-free-${className}-${group}",
                classes = listOf(className),
                title_bn = "$displayClass বেসিক কনসেপ্ট ও ফ্রি রিভিশন ক্লাস",
                banner_url = null,
                is_free = true,
                trial_enabled = true
            )
        )
    }

    private fun getFallbackOtherPrograms(className: String, group: String): List<OtherProgram> {
        val displayClass = when (className.uppercase()) {
            "C9", "CLASS 9", "C09" -> "দশম শ্রেণি"
            "C10", "CLASS 10" -> "একাদশ শ্রেণি"
            "C11", "CLASS 11" -> "দ্বাদশ শ্রেণি"
            "C12", "CLASS 12" -> "এইচএসসি এডমিশন"
            else -> "এইচএসসি"
        }
        val displayGroup = when (group.lowercase()) {
            "humanities", "humanities_group" -> "মানবিক"
            "science", "science_group" -> "বিজ্ঞান"
            "business", "business_studies", "commerce", "businessstudies" -> "ব্যবসায় শিক্ষা"
            else -> "বিজ্ঞান"
        }
        return listOf(
            OtherProgram(
                id = "fallback-other-${className}-${group}",
                classes = listOf(className),
                title_bn = "$displayClass পরীক্ষার প্রস্তুতি ($displayGroup)",
                banner_url = null,
                is_free = false,
                trial_enabled = false
            )
        )
    }

    fun selectLesson(lesson: StudentLessonItem) {
        val recUrl = lesson.resolvedVideoUrl ?: lesson.live_class?.resolvedVideoUrl ?: lesson.live_class?.recording_url ?: ""
        val isEnrolled = recUrl.isNotBlank()
        val sessionId = lesson.live_class?.session_id ?: lesson.session_id ?: "N/A"
        _uiState.update {
            it.copy(selectedLesson = lesson)
        }

        val liveClassId = lesson.live_class?.id ?: lesson.content_id ?: lesson.id
        if (liveClassId.isNotBlank()) {
            viewModelScope.launch {
                try {
                    val query = GraphQlQuery(
                        operationName = "GetAcademicLiveClassDetails",
                        query = """
                            query GetAcademicLiveClassDetails(${'$'}id: String!) {
                              academicProgramLiveClass(id: ${'$'}id) {
                                batch_ids
                                create_practice_mcq
                                chapter {
                                  id
                                  name
                                  no
                                }
                                class_type
                                end_time
                                id
                                on_going
                                playback_url
                                start_time
                                study_materials {
                                  file_url
                                  id
                                  name
                                }
                                subject {
                                  attr
                                  class
                                  code
                                  color_code
                                  display
                                  display_bn
                                  group
                                  icon
                                  parent_code
                                  ref
                                }
                                teacher {
                                  bio
                                  id
                                  marketing_avatar
                                  marketing_points
                                  name
                                  subjects
                                  university_degree
                                }
                                title
                                topics {
                                  id
                                  name
                                }
                              }
                            }
                        """.trimIndent(),
                        variables = mapOf("id" to liveClassId)
                    )
                    val res = apiService.getAcademicLiveClassDetails(query)
                    val liveClassData = res.data?.academicProgramLiveClass
                    if (liveClassData != null) {
                        val pbUrl = liveClassData.playback_url
                        val updatedLiveClass = (lesson.live_class ?: LiveClassDetails()).copy(
                            id = liveClassData.id ?: lesson.live_class?.id,
                            playback_url = pbUrl ?: lesson.live_class?.playback_url,
                            recording_url = pbUrl ?: lesson.live_class?.recording_url,
                            start_time = liveClassData.start_time ?: lesson.live_class?.start_time,
                            end_time = liveClassData.end_time ?: lesson.live_class?.end_time,
                            teacher = liveClassData.teacher ?: lesson.live_class?.teacher,
                            topics = if (!liveClassData.topics.isNullOrEmpty()) liveClassData.topics else lesson.live_class?.topics
                        )

                        val newAttachments = mutableListOf<LessonAttachmentItem>()
                        liveClassData.study_materials?.forEach { mat ->
                            if (!mat.file_url.isNullOrBlank()) {
                                newAttachments.add(LessonAttachmentItem(id = mat.id, title = mat.name ?: "লেকচার স্লাইড (PDF)", url = mat.file_url, file_type = "pdf"))
                            }
                        }
                        lesson.attachments?.let { newAttachments.addAll(it) }

                        val updatedLesson = lesson.copy(
                            live_class = updatedLiveClass,
                            attachments = newAttachments.distinctBy { it.downloadUrl }
                        )

                        var currentLessonState = updatedLesson

                        // 1. Fetch Teacher Details if teacher_id exists
                        val teacherId = liveClassData.teacher?.id
                        if (!teacherId.isNullOrBlank()) {
                            try {
                                val tQuery = GraphQlQuery(
                                    operationName = "GetTeacherDetails",
                                    query = """
                                        query GetTeacherDetails(${'$'}teacher_id: String!) {
                                          teacher(teacher_id: ${'$'}teacher_id) {
                                            id
                                            first_name
                                            last_name
                                            avatar
                                            marketing_avatar
                                            university_degree
                                            marketing_points
                                            bio
                                            cover_photo
                                            subjects_taken {
                                              code
                                              icon
                                              display_bn
                                            }
                                            color_code
                                            teacher_experience
                                            total_students_taught
                                            consumed_video_hours
                                          }
                                        }
                                    """.trimIndent(),
                                    variables = mapOf("teacher_id" to teacherId)
                                )
                                val tRes = apiService.getTeacherDetails(tQuery)
                                val fullTeacher = tRes.data?.teacher
                                if (fullTeacher != null) {
                                    val withTeacher = currentLessonState.live_class?.copy(teacher = fullTeacher)
                                    currentLessonState = currentLessonState.copy(live_class = withTeacher)
                                }
                            } catch (e: Exception) {
                            }
                        }

                        // 2. Fetch Topic Videos via GetTopics if playback_url is blank
                        val chapterIdForTopics = lesson.chapter_id ?: liveClassData.chapter?.id
                        val topicIds = liveClassData.topics?.mapNotNull { it.id }?.filter { it.isNotBlank() }
                        if (currentLessonState.resolvedVideoUrl.isNullOrBlank() && !chapterIdForTopics.isNullOrBlank() && !topicIds.isNullOrEmpty()) {
                            try {
                                val topQuery = GraphQlQuery(
                                    operationName = "GetTopics",
                                    query = """
                                        query GetTopics(${'$'}chapter_id: String!, ${'$'}topic_ids: [String]) {
                                          topics(chapter_id: ${'$'}chapter_id, topic_ids: ${'$'}topic_ids, filter: { limit: 150 } ) {
                                            data {
                                              id
                                              no
                                              name
                                              description
                                              subscription_type
                                              session {
                                                progress
                                              }
                                              videos {
                                                data {
                                                  id
                                                  playback_url
                                                  video_thumbnail_url
                                                  category
                                                }
                                              }
                                              header {
                                                chapter_id
                                                chapter_name
                                              }
                                            }
                                          }
                                        }
                                    """.trimIndent(),
                                    variables = mapOf("chapter_id" to chapterIdForTopics, "topic_ids" to topicIds)
                                )
                                val topRes = apiService.getTopics(topQuery)
                                val topicVideos = topRes.data?.topics?.data?.flatMap { it.videos?.data ?: emptyList() }
                                val fallbackTopicUrl = topicVideos?.firstOrNull { !it.playback_url.isNullOrBlank() }?.playback_url
                                if (!fallbackTopicUrl.isNullOrBlank()) {
                                    val withTopicPb = currentLessonState.live_class?.copy(
                                        playback_url = fallbackTopicUrl,
                                        recording_url = fallbackTopicUrl
                                    )
                                    currentLessonState = currentLessonState.copy(
                                        recording_url = fallbackTopicUrl,
                                        live_class = withTopicPb
                                    )
                                }
                            } catch (e: Exception) {
                            }
                        }

                        if (_uiState.value.selectedLesson?.id == lesson.id) {
                            _uiState.update { it.copy(selectedLesson = currentLessonState) }
                        }

                        // 3. Attempt to fetch live room & meeting link via JoinLiveClass mutation
                        joinLiveClass(currentLessonState)

                    }
                } catch (e: Exception) {
                }
            }
        }
    }

    /**
     * Executes the GraphQL mutation JoinLiveClass to obtain live room ID, join link, and streaming credentials.
     */
    fun joinLiveClass(lesson: StudentLessonItem, onResult: ((JoinLiveClassPayload?) -> Unit)? = null) {
        val liveClassId = lesson.live_class?.id ?: lesson.content_id ?: lesson.id
        val lessonId = lesson.id.ifBlank { lesson.content_id ?: liveClassId }

        if (liveClassId.isBlank()) {
            onResult?.invoke(null)
            return
        }

        viewModelScope.launch {
            try {
                val joinQuery = GraphQlQuery(
                    operationName = "JoinLiveClass",
                    query = """
                        mutation JoinLiveClass(${'$'}joinLiveCLassId: String!, ${'$'}lesson_id: String!) {
                          joinLiveCLass(id: ${'$'}joinLiveCLassId, lesson_id: ${'$'}lesson_id) {
                            join_link
                            provider
                            hms_room_id
                          }
                        }
                    """.trimIndent(),
                    variables = mapOf(
                        "joinLiveCLassId" to liveClassId,
                        "lesson_id" to lessonId
                    )
                )
                val response = apiService.joinLiveClass(joinQuery)
                val payload = response.data?.joinLiveCLass
                if (payload != null) {
                    var hmsToken: String? = null
                    var isChatBlocked: Boolean? = false

                    // Step 2: Request 100ms JWT viewer token from Shikho /hms/token
                    val roomId = payload.hms_room_id
                    if (!roomId.isNullOrBlank()) {
                        try {
                            val tokenResp = apiService.getHmsToken(HmsTokenRequest(room_id = roomId.trim(), type = "android"))
                            hmsToken = tokenResp.token
                            isChatBlocked = tokenResp.blocked_chat
                        } catch (e: Exception) {
                            // Non-fatal if token call fails, fallback to join_link
                        }
                    }

                    val currentSelected = _uiState.value.selectedLesson
                    if (currentSelected != null && (currentSelected.id == lesson.id || currentSelected.content_id == lesson.content_id || currentSelected.live_class?.id == liveClassId)) {
                        val updatedLiveClass = (currentSelected.live_class ?: LiveClassDetails()).copy(
                            join_link = payload.join_link ?: currentSelected.live_class?.join_link,
                            provider = payload.provider ?: currentSelected.live_class?.provider,
                            hms_room_id = payload.hms_room_id ?: currentSelected.live_class?.hms_room_id,
                            hms_token = hmsToken ?: currentSelected.live_class?.hms_token,
                            blocked_chat = isChatBlocked ?: currentSelected.live_class?.blocked_chat
                        )
                        _uiState.update {
                            it.copy(selectedLesson = currentSelected.copy(live_class = updatedLiveClass))
                        }
                    }
                }
                onResult?.invoke(payload)
            } catch (e: Exception) {
                onResult?.invoke(null)
            }
        }
    }

    /**
     * Reloads the currently selected lesson fresh from server (for slides, live updates, or recordings).
     */
    fun reloadSelectedLesson() {
        val current = _uiState.value.selectedLesson
        val currentChapterId = _uiState.value.selectedChapterId
        val currentLessonId = current?.id

        if (current != null) {
            selectLesson(current)
        }

        if (currentChapterId.isNotBlank()) {
            viewModelScope.launch {
                loadLessonsForChapter(currentChapterId)
                if (!currentLessonId.isNullOrBlank()) {
                    val updated = _uiState.value.lessons.find { it.id == currentLessonId }
                    if (updated != null) {
                        _uiState.update { it.copy(selectedLesson = updated) }
                    }
                }
            }
        }
    }

    /**
     * Switches the active program, clears stale cached data, and reloads subjects fresh.
     */
    fun switchProgram(
        newProgramId: String,
        newProgramTitle: String? = null,
        batchId: String? = null,
        classCode: String? = null,
        targetPhaseId: String? = null
    ) {
        val title = if (!newProgramTitle.isNullOrBlank()) newProgramTitle else "এইচএসসি কোর্স"
        // NOTE: We deliberately DO NOT call sessionManager.saveActiveProgram here!
        // Home Screen active program is persisted separately in SessionManager/DB and must never be mutated
        // when browsing or exploring courses in the Course tab.
        _uiState.update {
            it.copy(
                programId = newProgramId,
                programTitle = title,
                phases = emptyList(),
                selectedPhase = null,
                activePhaseId = targetPhaseId ?: "",
                activePhaseTitle = "",
                subjects = emptyList(),
                selectedSubjectCode = "",
                selectedSubjectTitle = "",
                selectedSubjectColor = "",
                chapters = emptyList(),
                selectedChapterId = "",
                selectedChapterName = "",
                selectedChapterStatus = "",
                lessons = emptyList(),
                selectedLesson = null,
                isSubjectsLoading = true,
                subjectsErrorMessage = null,
                lessonsDiagnosticInfo = null
            )
        }
        loadSubjects(forceRefresh = true, targetPhaseId = targetPhaseId)
    }

    fun loadSubjects(forceRefresh: Boolean = false, targetPhaseId: String? = null) {
        viewModelScope.launch {
            val currentProgId = _uiState.value.programId
            val programId = if (currentProgId.isNotBlank()) currentProgId else (sessionManager.getActiveProgramId() ?: "")
            val currentProgTitle = _uiState.value.programTitle
            val programTitle = if (currentProgTitle.isNotBlank()) currentProgTitle else (sessionManager.getActiveProgramTitleBn() ?: "এইচএসসি কোর্স")

            val oldProgramId = _uiState.value.programId
            val isProgramChanged = oldProgramId.isNotBlank() && oldProgramId != programId
            val activeTargetPhaseId = if (!targetPhaseId.isNullOrBlank()) targetPhaseId else if (isProgramChanged) "" else _uiState.value.activePhaseId
            
            _uiState.update { 
                it.copy(
                    programId = programId,
                    programTitle = programTitle,
                    isSubjectsLoading = true,
                    subjectsErrorMessage = null,
                    phases = if (isProgramChanged) emptyList() else it.phases,
                    selectedPhase = if (isProgramChanged) null else it.selectedPhase,
                    activePhaseId = activeTargetPhaseId,
                    activePhaseTitle = if (isProgramChanged) "" else it.activePhaseTitle,
                    subjects = if (isProgramChanged) emptyList() else it.subjects,
                    chapters = if (isProgramChanged) emptyList() else it.chapters,
                    lessons = if (isProgramChanged) emptyList() else it.lessons
                )
            }

            if (programId.isBlank()) {
                _uiState.update { 
                    it.copy(
                        isSubjectsLoading = false,
                        subjectsErrorMessage = "কোনো সক্রিয় কোর্স পাওয়া যায়নি। অনুগ্রহ করে হোম পেজ থেকে একটি কোর্স নির্বাচন করুন।"
                    )
                }
                return@launch
            }

            try {
                // 1. Fetch Phases
                var currentPhaseId = if (isProgramChanged) "" else _uiState.value.activePhaseId
                var phasesList = if (isProgramChanged) emptyList() else _uiState.value.phases

                if (phasesList.isEmpty() || currentPhaseId.isBlank() || isProgramChanged || forceRefresh) {
                    try {
                        val phaseQuery = GraphQlQuery(
                            operationName = "ProgramPhasesByStudent",
                            query = """
                                query ProgramPhasesByStudent(${'$'}program_id: String!) {
                                  programPhasesByStudent(program_id: ${'$'}program_id) {
                                    data {
                                      id
                                      academic_program_id
                                      title
                                      status
                                      is_current
                                      has_enrolment
                                      has_free_trial_enrolment
                                      course_progress_percentage
                                      start_date
                                      end_date
                                    }
                                  }
                                }
                            """.trimIndent(),
                            variables = mapOf("program_id" to programId)
                        )
                        val phaseRes = apiService.getProgramPhases(phaseQuery)
                        val fetchedPhases = (phaseRes.data?.programPhasesByStudent?.data ?: emptyList()).map { 
                            it.copy(has_enrolment = true) 
                        }
                        phasesList = fetchedPhases
                        val activePhase = if (!targetPhaseId.isNullOrBlank()) {
                            fetchedPhases.find { it.id == targetPhaseId }
                        } else null
                            ?: fetchedPhases.firstOrNull { it.is_current == true }
                            ?: fetchedPhases.firstOrNull { it.status.equals("ACTIVE", ignoreCase = true) }
                            ?: fetchedPhases.firstOrNull { it.has_enrolment == true && !it.status.equals("COMPLETED", true) }
                            ?: fetchedPhases.firstOrNull { !it.status.equals("COMPLETED", true) }
                            ?: fetchedPhases.firstOrNull()

                        currentPhaseId = activePhase?.id ?: ""
                        _uiState.update {
                            it.copy(
                                programId = programId,
                                phases = phasesList,
                                selectedPhase = activePhase,
                                activePhaseId = currentPhaseId,
                                activePhaseTitle = activePhase?.title ?: ""
                            )
                        }
                    } catch (_: Exception) {}
                }

                // 2. Fetch Academic Subjects
                var rawSubjects: List<AcademicSubjectItem> = emptyList()
                var progressBars: List<SubjectProgressBarItem> = emptyList()

                if (currentPhaseId.isNotBlank()) {
                    try {
                        val subjectsQuery = GraphQlQuery(
                            operationName = "GetAcademicSubjects",
                            query = """
                                query GetAcademicSubjects(${'$'}programId: String!, ${'$'}phase_id: String!) {
                                  academicProgram(id: ${'$'}programId, show_subject_progress_bar: true, phase_id: ${'$'}phase_id) {
                                    subjects {
                                      code
                                      color_code
                                      display_bn
                                      icon
                                    }
                                    subjects_progress_bar {
                                      completed_chapters
                                      total_chapters
                                      code
                                      percentage
                                    }
                                    trial_subject_list
                                  }
                                }
                            """.trimIndent(),
                            variables = mapOf(
                                "programId" to programId,
                                "phase_id" to currentPhaseId
                            )
                        )
                        val response = apiService.getAcademicSubjects(subjectsQuery)
                        val academicProgram = response.data?.academicProgram
                        rawSubjects = academicProgram?.subjects ?: emptyList()
                        progressBars = academicProgram?.subjects_progress_bar ?: emptyList()
                    } catch (_: Exception) {}
                }

                if (rawSubjects.isEmpty()) {
                    try {
                        val fallbackSubjectsQuery = GraphQlQuery(
                            operationName = "GetAcademicSubjects",
                            query = """
                                query GetAcademicSubjects(${'$'}programId: String!) {
                                  academicProgram(id: ${'$'}programId, show_subject_progress_bar: true) {
                                    subjects {
                                      code
                                      color_code
                                      display_bn
                                      icon
                                    }
                                    subjects_progress_bar {
                                      completed_chapters
                                      total_chapters
                                      code
                                      percentage
                                    }
                                    trial_subject_list
                                  }
                                }
                            """.trimIndent(),
                            variables = mapOf("programId" to programId)
                        )
                        val response = apiService.getAcademicSubjects(fallbackSubjectsQuery)
                        val academicProgram = response.data?.academicProgram
                        rawSubjects = academicProgram?.subjects ?: emptyList()
                        progressBars = academicProgram?.subjects_progress_bar ?: emptyList()
                    } catch (_: Exception) {}
                }

                val subjectWithProgressList = rawSubjects.map { subject ->
                    val pb = progressBars.find { it.code.equals(subject.code, ignoreCase = true) }
                    SubjectWithProgress(subject = subject, progressBar = pb)
                }

                _uiState.update {
                    it.copy(
                        subjects = subjectWithProgressList,
                        isSubjectsLoading = false,
                        subjectsErrorMessage = if (subjectWithProgressList.isEmpty()) "কোনো বিষয় পাওয়া যায়নি" else null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSubjectsLoading = false,
                        subjectsErrorMessage = "বিষয় লোড করা যায়নি: ${e.localizedMessage ?: "নেটওয়ার্ক সমস্যা"}"
                    )
                }
            }
        }
    }

    fun selectSubject(subjectCode: String, subjectTitle: String, subjectColor: String) {
        _uiState.update {
            it.copy(
                selectedSubjectCode = subjectCode,
                selectedSubjectTitle = subjectTitle,
                selectedSubjectColor = subjectColor
            )
        }
    }

    fun loadChaptersForSubject(
        subjectCode: String,
        subjectTitle: String? = null,
        subjectColor: String? = null,
        phaseId: String? = null
    ) {
        val currentProgId = _uiState.value.programId
        val progId = if (currentProgId.isNotBlank()) currentProgId else (sessionManager.getActiveProgramId() ?: "")
        val targetPhaseId = phaseId ?: _uiState.value.activePhaseId
        
        val matchedPhase = _uiState.value.phases.find { it.id == targetPhaseId }
            ?: _uiState.value.selectedPhase
            ?: _uiState.value.phases.firstOrNull { it.is_current == true }
            ?: _uiState.value.phases.firstOrNull()

        val cacheKey = "${progId}_${subjectCode}_${matchedPhase?.id ?: targetPhaseId}"
        val cachedChapters = chaptersCache[cacheKey]

        _uiState.update {
            it.copy(
                programId = progId,
                selectedSubjectCode = subjectCode,
                selectedSubjectTitle = subjectTitle ?: it.selectedSubjectTitle,
                selectedSubjectColor = subjectColor ?: it.selectedSubjectColor,
                selectedPhase = matchedPhase,
                activePhaseId = matchedPhase?.id ?: targetPhaseId,
                activePhaseTitle = matchedPhase?.title ?: it.activePhaseTitle,
                chapters = cachedChapters ?: emptyList(),
                isChaptersLoading = cachedChapters == null,
                chaptersErrorMessage = null
            )
        }

        viewModelScope.launch {
            try {
                // Ensure phases are available for the tab bar
                var currentPhases = _uiState.value.phases
                var effectivePhaseId = _uiState.value.activePhaseId

                if (currentPhases.isEmpty() || _uiState.value.programId != progId) {
                    try {
                        val phaseQuery = GraphQlQuery(
                            operationName = "ProgramPhasesByStudent",
                            query = """
                                query ProgramPhasesByStudent(${'$'}program_id: String!) {
                                  programPhasesByStudent(program_id: ${'$'}program_id) {
                                    data {
                                      id
                                      academic_program_id
                                      title
                                      status
                                      is_current
                                      has_enrolment
                                      has_free_trial_enrolment
                                      course_progress_percentage
                                      start_date
                                      end_date
                                    }
                                  }
                                }
                            """.trimIndent(),
                            variables = mapOf("program_id" to progId)
                        )
                        val phaseRes = apiService.getProgramPhases(phaseQuery)
                        val fetchedPhases = phaseRes.data?.programPhasesByStudent?.data ?: emptyList()
                        if (fetchedPhases.isNotEmpty()) {
                            currentPhases = fetchedPhases
                            val activePhase = if (targetPhaseId.isNotBlank()) {
                                fetchedPhases.find { it.id == targetPhaseId }
                            } else null
                                ?: fetchedPhases.firstOrNull { it.is_current == true }
                                ?: fetchedPhases.firstOrNull { it.status.equals("ACTIVE", ignoreCase = true) }
                                ?: fetchedPhases.firstOrNull()

                            effectivePhaseId = activePhase?.id ?: targetPhaseId
                            _uiState.update {
                                it.copy(
                                    programId = progId,
                                    phases = fetchedPhases,
                                    selectedPhase = activePhase,
                                    activePhaseId = effectivePhaseId,
                                    activePhaseTitle = activePhase?.title ?: ""
                                )
                            }
                        }
                    } catch (_: Exception) {}
                }

                // Fetch Chapters for the specific subject and phase
                var chaptersList = emptyList<AcademicChapterItem>()

                // 1. If phaseId / effectivePhaseId is set, fetch phase/quarter-wise chapters first
                if (effectivePhaseId.isNotBlank()) {
                    try {
                        val chaptersQuery = GraphQlQuery(
                            operationName = "PhaseWiseChapters",
                            query = """
                                query PhaseWiseChapters(${'$'}program_id: String!, ${'$'}phase_id: String!, ${'$'}subject_id: String!) {
                                  listAcademicProgramChapters(program_id: ${'$'}program_id, phase_id: ${'$'}phase_id, subject_id: ${'$'}subject_id, show_chapter_progress_bar: true) {
                                    data {
                                      id
                                      batch_id
                                      chapter_id
                                      chapter_name
                                      chapter_no
                                      program_id
                                      status
                                      subject_icon
                                      class_counter
                                      exam_counter
                                      chapters_progress_percentage
                                    }
                                  }
                                }
                            """.trimIndent(),
                            variables = mapOf(
                                "program_id" to progId,
                                "phase_id" to effectivePhaseId,
                                "subject_id" to subjectCode
                            )
                        )

                        val response = apiService.getPhaseWiseChapters(chaptersQuery)
                        chaptersList = response.data?.listAcademicProgramChapters?.data ?: emptyList()
                    } catch (_: Exception) {}
                }

                // 2. If no phase is selected or phase query returned empty, try GetChapters(subject_code)
                if (chaptersList.isEmpty() && (effectivePhaseId.isBlank() || phaseId == null)) {
                    try {
                        val getChaptersQuery = GraphQlQuery(
                            operationName = "GetChapters",
                            query = """
                                query GetChapters(${'$'}subject_code: String!) {
                                  chapters(subject_code: ${'$'}subject_code, filter: { limit: 100 } ) {
                                    data {
                                      id
                                      name
                                      no
                                      all_topics_free
                                      topics {
                                        meta {
                                          count
                                        }
                                      }
                                    }
                                  }
                                }
                            """.trimIndent(),
                            variables = mapOf("subject_code" to subjectCode)
                        )
                        val res = apiService.getPhaseWiseChapters(getChaptersQuery)
                        val fullList = res.data?.chapters?.data ?: emptyList()
                        if (fullList.isNotEmpty()) {
                            chaptersList = fullList
                        }
                    } catch (_: Exception) {}
                }

                // If no phase-specific chapters found on initial entry (phaseId == null) and multiple phases exist,
                // check if chapters exist in another phase (e.g. Quarter 1)
                if (chaptersList.isEmpty() && phaseId == null && currentPhases.size > 1) {
                    for (p in currentPhases) {
                        if (p.id != effectivePhaseId && p.id.isNotBlank()) {
                            try {
                                val altPhaseQuery = GraphQlQuery(
                                    operationName = "PhaseWiseChapters",
                                    query = """
                                        query PhaseWiseChapters(${'$'}program_id: String!, ${'$'}phase_id: String!, ${'$'}subject_id: String!) {
                                          listAcademicProgramChapters(program_id: ${'$'}program_id, phase_id: ${'$'}phase_id, subject_id: ${'$'}subject_id, show_chapter_progress_bar: true) {
                                            data {
                                              id
                                              chapter_id
                                              chapter_name
                                              chapter_no
                                              status
                                              class_counter
                                              exam_counter
                                              chapters_progress_percentage
                                            }
                                          }
                                        }
                                    """.trimIndent(),
                                    variables = mapOf(
                                        "program_id" to progId,
                                        "phase_id" to p.id,
                                        "subject_id" to subjectCode
                                    )
                                )
                                val altRes = apiService.getPhaseWiseChapters(altPhaseQuery)
                                val altList = altRes.data?.listAcademicProgramChapters?.data ?: emptyList()
                                if (altList.isNotEmpty()) {
                                    chaptersList = altList
                                    effectivePhaseId = p.id
                                    _uiState.update {
                                        it.copy(
                                            selectedPhase = p,
                                            activePhaseId = p.id,
                                            activePhaseTitle = p.title ?: ""
                                        )
                                    }
                                    break
                                }
                            } catch (_: Exception) {}
                        }
                    }
                }

                // If still empty or no phase was set, try query without phase_id
                if (chaptersList.isEmpty() && (effectivePhaseId.isBlank() || phaseId == null)) {
                    try {
                        val fallbackQuery = GraphQlQuery(
                            operationName = "AcademicProgramChapters",
                            query = """
                                query AcademicProgramChapters(${'$'}program_id: String!, ${'$'}subject_id: String!) {
                                  listAcademicProgramChapters(program_id: ${'$'}program_id, subject_id: ${'$'}subject_id, show_chapter_progress_bar: true) {
                                    data {
                                      id
                                      chapter_id
                                      chapter_name
                                      chapter_no
                                      status
                                      class_counter
                                      exam_counter
                                      chapters_progress_percentage
                                    }
                                  }
                                }
                            """.trimIndent(),
                            variables = mapOf(
                                "program_id" to progId,
                                "subject_id" to subjectCode
                            )
                        )
                        val fallbackRes = apiService.getPhaseWiseChapters(fallbackQuery)
                        val fallbackList = fallbackRes.data?.listAcademicProgramChapters?.data ?: emptyList()
                        if (fallbackList.isNotEmpty()) {
                            chaptersList = fallbackList
                        }
                    } catch (_: Exception) {}
                }

                if (chaptersList.isEmpty()) {
                    val hierarchyChapters = fetchFallbackHierarchyChapters(subjectCode)
                    if (hierarchyChapters.isNotEmpty()) {
                        chaptersList = hierarchyChapters
                    }
                }

                if (chaptersList.isNotEmpty()) {
                    chaptersCache[cacheKey] = chaptersList
                    // Pre-fetch lessons for chapters in background
                    val pId = progId ?: ""
                    val phId = effectivePhaseId ?: ""
                    chaptersList.take(6).forEach { chapter ->
                        val cid = (chapter.id ?: chapter.chapter_id ?: "").ifBlank { chapter.chapter_id ?: "" }
                        if (cid.isNotBlank() && !lessonsCache.containsKey(cid)) {
                            viewModelScope.launch {
                                try {
                                    val l1 = if (phId.isNotBlank()) fetchLessonsWithPhase(cid, pId, phId) else emptyList()
                                    val l2 = if (l1.isEmpty()) fetchLessonsStandard(cid, pId) else l1
                                    if (l2.isNotEmpty()) {
                                        lessonsCache[cid] = l2
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                    }
                }

                _uiState.update {
                    it.copy(
                        chapters = chaptersList,
                        isChaptersLoading = false,
                        chaptersErrorMessage = if (chaptersList.isEmpty()) "এই বিষয়ে কোনো অধ্যায় পাওয়া যায়নি" else null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isChaptersLoading = false,
                        chaptersErrorMessage = "অধ্যায় লোড করা যায়নি: ${e.localizedMessage ?: "নেটওয়ার্ক সমস্যা"}"
                    )
                }
            }
        }
    }

    fun onSelectPhaseTab(phase: PhaseItem) {
        _uiState.update {
            it.copy(
                selectedPhase = phase,
                activePhaseId = phase.id,
                activePhaseTitle = phase.title ?: ""
            )
        }
        val currentSubjectCode = _uiState.value.selectedSubjectCode
        if (currentSubjectCode.isNotBlank()) {
            loadChaptersForSubject(subjectCode = currentSubjectCode, phaseId = phase.id)
        }
    }

    fun selectChapter(chapterId: String, chapterName: String, chapterStatus: String = "") {
        _uiState.update {
            it.copy(
                selectedChapterId = chapterId,
                selectedChapterName = chapterName,
                selectedChapterStatus = chapterStatus
            )
        }
    }

    fun loadLessonsForChapter(
        chapterId: String,
        altChapterId: String? = null,
        chapterName: String? = null,
        chapterStatus: String? = null
    ) {
        val activeIctProgramId = "6862551806800acba2e22b27"
        val sessionProgId = sessionManager.getActiveProgramId()
        val stateProgId = _uiState.value.programId
        val phaseProgId = _uiState.value.selectedPhase?.academic_program_id
        val enrolledPhaseProgId = _uiState.value.phases.firstOrNull { it.has_enrolment == true }?.academic_program_id

        val candidateProgramIds = listOfNotNull(
            stateProgId,
            phaseProgId,
            enrolledPhaseProgId,
            activeIctProgramId,
            sessionProgId
        ).filter { it.isNotBlank() }.distinct()

        val matchingChapter = _uiState.value.chapters.firstOrNull { it.id == chapterId || it.chapter_id == chapterId }
        val primaryChapterId = chapterId.ifBlank { matchingChapter?.id ?: matchingChapter?.chapter_id ?: "" }
        val secondaryChapterId = altChapterId ?: matchingChapter?.chapter_id?.takeIf { it != primaryChapterId } ?: matchingChapter?.id?.takeIf { it != primaryChapterId }

        val candidateChapterIds = listOfNotNull(primaryChapterId, secondaryChapterId).filter { it.isNotBlank() }.distinct()

        val currentPhaseId = _uiState.value.activePhaseId
        val enrolledPhaseId = _uiState.value.phases.firstOrNull { it.has_enrolment == true }?.id

        val candidatePhaseIds = listOfNotNull(
            enrolledPhaseId,
            currentPhaseId,
            *_uiState.value.phases.map { it.id }.toTypedArray()
        ).filter { it.isNotBlank() }.distinct()

        val cachedLessons = lessonsCache[primaryChapterId]

        _uiState.update {
            it.copy(
                selectedChapterId = primaryChapterId,
                selectedChapterName = chapterName ?: matchingChapter?.chapter_name ?: it.selectedChapterName,
                selectedChapterStatus = chapterStatus ?: matchingChapter?.status ?: it.selectedChapterStatus,
                lessons = cachedLessons ?: emptyList(),
                isLessonsLoading = cachedLessons == null,
                lessonsErrorMessage = null,
                lessonsDiagnosticInfo = null
            )
        }

        viewModelScope.launch {
            try {
                var lessonList = emptyList<StudentLessonItem>()
                val queryAttempts = mutableListOf<String>()

                // Try each candidate chapter ID across candidate program IDs and phases
                searchLoop@ for (cid in candidateChapterIds) {
                    for (pid in candidateProgramIds) {
                        // 1. Try with phase IDs
                        for (phId in candidatePhaseIds) {
                            val attemptKey = "Phase(cid=$cid, pid=$pid, phId=$phId)"
                            queryAttempts.add(attemptKey)
                            val fetchedLessons = fetchLessonsWithPhase(cid, pid, phId)
                            val hasValidStream = fetchedLessons.any { !it.resolvedVideoUrl.isNullOrBlank() }
                            if (fetchedLessons.isNotEmpty()) {
                                lessonList = fetchedLessons
                                _uiState.update {
                                    it.copy(
                                        activePhaseId = phId,
                                        activePhaseTitle = _uiState.value.phases.firstOrNull { p -> p.id == phId }?.title ?: it.activePhaseTitle
                                    )
                                }
                                if (hasValidStream) {
                                    break@searchLoop
                                }
                            }
                        }

                        // 2. Try standard query without phase ID
                        val standardKey = "Standard(cid=$cid, pid=$pid)"
                        queryAttempts.add(standardKey)
                        val fetchedLessons = fetchLessonsStandard(cid, pid)
                        if (fetchedLessons.isNotEmpty()) {
                            lessonList = fetchedLessons
                            if (fetchedLessons.any { !it.resolvedVideoUrl.isNullOrBlank() }) {
                                break@searchLoop
                            }
                        }
                    }
                }

                val diagInfo = if (lessonList.isEmpty()) {
                    "কোর্স আইডি: ${candidateProgramIds.joinToString(", ")}\nঅধ্যায় আইডি: ${candidateChapterIds.joinToString(", ")}\nকোয়ার্টার আইডি: ${candidatePhaseIds.joinToString(", ")}\nঅনুসন্ধান সংখ্যা: ${queryAttempts.size}টি কোয়েরি"
                } else null


                if (lessonList.isNotEmpty()) {
                    lessonsCache[primaryChapterId] = lessonList
                }

                _uiState.update {
                    it.copy(
                        lessons = lessonList,
                        isLessonsLoading = false,
                        lessonsErrorMessage = if (lessonList.isEmpty()) "এই অধ্যায়ে কোনো ক্লাস বা লেকচার পাওয়া যায়নি" else null,
                        lessonsDiagnosticInfo = diagInfo
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLessonsLoading = false,
                        lessonsErrorMessage = "ক্লাস লোড করা যায়নি: ${e.localizedMessage ?: "নেটওয়ার্ক সমস্যা"}",
                        lessonsDiagnosticInfo = "এরর: ${e.javaClass.simpleName} - ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    private suspend fun fetchLessonsWithPhase(
        chapterId: String,
        programId: String,
        phaseId: String
    ): List<StudentLessonItem> {
        try {
            val q1 = GraphQlQuery(
                operationName = "GetUpcomingLessonsPhaseWise",
                query = """
                    query GetUpcomingLessonsPhaseWise(${'$'}chapter_id: String!, ${'$'}program_id: String!, ${'$'}phase_id: String!) {
                      studentSpecificLessons(program_id: ${'$'}program_id, chapter_id: ${'$'}chapter_id, phase_id: ${'$'}phase_id) {
                        data {
                          access_level
                          subject_name
                          start_time
                          end_time
                          content_type
                          id
                          content_id
                          subject_id
                          batch_id
                          chapter_id
                          icon
                          color_code
                          user_activity_state
                          hw_type
                          title
                          live_class {
                            chapter_id
                            chapter_name
                            end_time
                            is_on_going
                            recording_url
                            start_time
                            subject_name
                            topics {
                              id
                              name
                            }
                            subject_id
                            id
                            type
                          }
                          model_test {
                            type
                            exam_category
                            result_publish_time
                          }
                          topics {
                            id
                            name
                          }
                          phase_id
                        }
                      }
                    }
                """.trimIndent(),
                variables = mapOf(
                    "chapter_id" to chapterId,
                    "program_id" to programId,
                    "phase_id" to phaseId
                )
            )
            val res = apiService.getStudentLessons(q1)
            val data = res.data?.studentSpecificLessons?.data
            val detailsLog = data?.joinToString("\n") { item ->
                "[Lesson ID=${item.id}, title=${item.title}, content_id=${item.content_id}, rec_url=${item.live_class?.recording_url}]"
            } ?: "[]"
            return data ?: emptyList()
        } catch (e: Exception) {
            return emptyList()
        }
    }

    private suspend fun fetchLessonsStandard(
        chapterId: String,
        programId: String
    ): List<StudentLessonItem> {
        try {
            val q1 = GraphQlQuery(
                operationName = "GetUpcomingLessons",
                query = """
                    query GetUpcomingLessons(${'$'}chapter_id: String!, ${'$'}program_id: String!) {
                      studentSpecificLessons(program_id: ${'$'}program_id, chapter_id: ${'$'}chapter_id) {
                        data {
                          id
                          title
                          content_id
                          content_type
                          user_activity_state
                          live_class {
                            id
                            recording_url
                            start_time
                            type
                          }
                        }
                      }
                    }
                """.trimIndent(),
                variables = mapOf(
                    "chapter_id" to chapterId,
                    "program_id" to programId
                )
            )
            val res = apiService.getStudentLessons(q1)
            val data = res.data?.studentSpecificLessons?.data
            val detailsLog = data?.joinToString("\n") { item ->
                "[Lesson ID=${item.id}, title=${item.title}, content_id=${item.content_id}, rec_url=${item.live_class?.recording_url}]"
            } ?: "[]"
            return data ?: emptyList()
        } catch (e: Exception) {
            return emptyList()
        }
    }

    private suspend fun fetchFallbackHierarchyChapters(subjectCode: String): List<AcademicChapterItem> {
        try {
            val query = GraphQlQuery(
                operationName = "GetSubjectHierarchyWithQuestionCounts",
                query = """
                    query GetSubjectHierarchyWithQuestionCounts(${'$'}class: ClassEnum, ${'$'}group: StudyGroupTypeEnum) {
                      subjectHierarchyWithQuestionCounts(class: ${'$'}class, group: ${'$'}group) {
                        data {
                          chapters {
                            id
                            name
                            no
                            should_render
                            total_active_questions
                          }
                          should_render
                          display_bn
                          display
                          code
                          icon
                          color_code
                          total_active_questions
                        }
                      }
                    }
                """.trimIndent(),
                variables = mapOf("class" to "HSC", "group" to "Humanities")
            )
            val res = apiService.getSubjectHierarchyWithQuestionCounts(query)
            val subjectsData = res.data?.subjectHierarchyWithQuestionCounts?.data ?: emptyList()
            val matchedSubject = subjectsData.find { it.code == subjectCode }
                ?: subjectsData.find { it.display_bn?.trim() == _uiState.value.selectedSubjectTitle?.trim() }

            val foundChapters = matchedSubject?.chapters?.filter { it.should_render != false }?.map { ch ->
                AcademicChapterItem(
                    id = ch.id,
                    chapter_id = ch.id,
                    chapter_name = ch.name,
                    chapter_no = ch.no,
                    status = "IN_PROGRESS"
                )
            } ?: emptyList()

            return foundChapters
        } catch (e: Exception) {
            return emptyList()
        }
    }

    fun loadAnimatedLessonsForChapter(chapterId: String) {
        _uiState.update {
            it.copy(
                isChapterAnimationsLoading = true,
                chapterAnimatedLessons = emptyList()
            )
        }
        viewModelScope.launch {
            try {
                val topQuery = GraphQlQuery(
                    operationName = "GetTopics",
                    query = """
                        query GetTopics(${'$'}chapter_id: String!, ${'$'}topic_ids: [String]) {
                          topics(chapter_id: ${'$'}chapter_id, topic_ids: ${'$'}topic_ids, filter: { limit: 150 } ) {
                            data {
                              id
                              no
                              name
                              description
                              subscription_type
                              videos {
                                data {
                                  id
                                  playback_url
                                  video_thumbnail_url
                                  category
                                }
                              }
                              header {
                                chapter_id
                                chapter_name
                              }
                            }
                          }
                        }
                    """.trimIndent(),
                    variables = mapOf("chapter_id" to chapterId, "topic_ids" to null)
                )
                val topRes = apiService.getTopics(topQuery)
                val topicsList = topRes.data?.topics?.data ?: emptyList()
                val realAnimatedTopics = topicsList.filter { topic ->
                    topic.videos?.data?.any { !it.playback_url.isNullOrBlank() } == true
                }

                _uiState.update {
                    it.copy(
                        chapterAnimatedLessons = realAnimatedTopics,
                        isChapterAnimationsLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        chapterAnimatedLessons = emptyList(),
                        isChapterAnimationsLoading = false
                    )
                }
            }
        }
    }

    fun loadAnimatedLessonsForSubject() {
        val chaptersList = _uiState.value.chapters
        if (chaptersList.isEmpty()) {
            _uiState.update {
                it.copy(
                    isSubjectAnimationsLoading = false,
                    subjectAnimatedLessons = emptyList()
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                isSubjectAnimationsLoading = true,
                subjectAnimatedLessons = emptyList()
            )
        }

        viewModelScope.launch {
            try {
                val deferreds = chaptersList.map { chapter ->
                    val chId = chapter.id.ifBlank { chapter.chapter_id ?: "" }
                    async {
                        if (chId.isBlank()) return@async emptyList<TopicFullItem>()
                        try {
                            val topQuery = GraphQlQuery(
                                operationName = "GetTopics",
                                query = """
                                    query GetTopics(${'$'}chapter_id: String!, ${'$'}topic_ids: [String]) {
                                      topics(chapter_id: ${'$'}chapter_id, topic_ids: ${'$'}topic_ids, filter: { limit: 150 } ) {
                                        data {
                                          id
                                          no
                                          name
                                          description
                                          subscription_type
                                          videos {
                                            data {
                                              id
                                              playback_url
                                              video_thumbnail_url
                                              category
                                            }
                                          }
                                          header {
                                            chapter_id
                                            chapter_name
                                          }
                                        }
                                      }
                                    }
                                """.trimIndent(),
                                variables = mapOf("chapter_id" to chId, "topic_ids" to null)
                            )
                            val topRes = apiService.getTopics(topQuery)
                            val fetched = topRes.data?.topics?.data ?: emptyList()
                            fetched.filter { topic ->
                                topic.videos?.data?.any { !it.playback_url.isNullOrBlank() } == true
                            }
                        } catch (e: Exception) {
                            emptyList<TopicFullItem>()
                        }
                    }
                }
                val results = deferreds.awaitAll().flatten()
                val finalAnimated = results.distinctBy { it.id }
                _uiState.update {
                    it.copy(
                        subjectAnimatedLessons = finalAnimated,
                        isSubjectAnimationsLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        subjectAnimatedLessons = emptyList(),
                        isSubjectAnimationsLoading = false
                    )
                }
            }
        }
    }
}

class CourseViewModelFactory(
    private val apiService: ShikhoApiService,
    private val sessionManager: SessionManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CourseViewModel::class.java)) {
            return CourseViewModel(apiService, sessionManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
