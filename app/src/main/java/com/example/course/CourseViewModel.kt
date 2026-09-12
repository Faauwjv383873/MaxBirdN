package com.example.course

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.api.*
import com.example.auth.SessionManager
import com.example.ui.components.DebugTerminalManager
import com.example.ui.components.LogType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    // Enrolled / Unlocked Programs list
    val enrolledPrograms: List<EnrolledProgram> = emptyList(),
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
    val selectedLesson: StudentLessonItem? = null
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
            try {
                val query = GraphQlQuery(
                    operationName = "GetAcademicProgram",
                    query = """
                        query GetAcademicProgram {
                          listAcademicProgramByEnrollment {
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
                          }
                        }
                    """.trimIndent()
                )
                val response = apiService.getAcademicProgram(query)
                val list = response.data?.listAcademicProgramByEnrollment?.enrolled_programs
                if (!list.isNullOrEmpty()) {
                    DebugTerminalManager.log(
                        "ENROLLED_PROGS",
                        "কোর্স অপশনের জন্য ${list.size} টি এনরোল করা প্রোগ্রাম লোড করা হয়েছে",
                        LogType.SUCCESS
                    )
                    _uiState.update { it.copy(enrolledPrograms = list) }
                }
            } catch (e: Exception) {
                DebugTerminalManager.log(
                    "ENROLLED_PROGS_ERR",
                    "এনরোল করা কোর্স লোড করতে ত্রুটি: ${e.localizedMessage}",
                    LogType.ERROR
                )
            }
        }
    }

    fun selectLesson(lesson: StudentLessonItem) {
        val recUrl = lesson.resolvedVideoUrl ?: lesson.live_class?.resolvedVideoUrl ?: lesson.live_class?.recording_url ?: ""
        val isEnrolled = recUrl.isNotBlank()
        val sessionId = lesson.live_class?.session_id ?: lesson.session_id ?: "N/A"
        DebugTerminalManager.log(
            "SELECT_LESSON",
            "ক্লাস সিলেক্ট করা হয়েছে: '${lesson.title}' (ID: ${lesson.id})\n- Session ID: $sessionId\n- স্ট্রিমিং URL: ${if (isEnrolled) recUrl else "নেই (ফ্রি বা আনএনরোল্ড কোর্স, ভিডিও প্লে হবে না)"}\n- Candidate URLs Count: ${lesson.candidateStreamUrls.size}",
            if (isEnrolled) LogType.SUCCESS else LogType.ERROR
        )
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
                                    DebugTerminalManager.log(
                                        "TEACHER_DETAILS_RESP",
                                        "Teacher details loaded for $teacherId: ${fullTeacher.displayName}",
                                        LogType.SUCCESS
                                    )
                                }
                            } catch (e: Exception) {
                                DebugTerminalManager.log(
                                    "TEACHER_DETAILS_ERR",
                                    "Error fetching teacher details ($teacherId): ${e.localizedMessage}",
                                    LogType.ERROR
                                )
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
                                    DebugTerminalManager.log(
                                        "TOPICS_RESP",
                                        "Topic video stream loaded for chapter $chapterIdForTopics: $fallbackTopicUrl",
                                        LogType.SUCCESS
                                    )
                                }
                            } catch (e: Exception) {
                                DebugTerminalManager.log(
                                    "TOPICS_ERR",
                                    "Error fetching topic videos ($chapterIdForTopics): ${e.localizedMessage}",
                                    LogType.ERROR
                                )
                            }
                        }

                        if (_uiState.value.selectedLesson?.id == lesson.id) {
                            _uiState.update { it.copy(selectedLesson = currentLessonState) }
                        }
                        DebugTerminalManager.log(
                            "LIVE_CLASS_DETAILS_RESP",
                            "Live Class Details loaded for $liveClassId:\n- Playback URL: ${currentLessonState.resolvedVideoUrl}\n- Teacher: ${currentLessonState.live_class?.teacher?.displayName}\n- Materials: ${liveClassData.study_materials?.size ?: 0}",
                            LogType.SUCCESS
                        )

                    }
                } catch (e: Exception) {
                    DebugTerminalManager.log(
                        "LIVE_CLASS_DETAILS_ERR",
                        "Error fetching live class details for $liveClassId: ${e.localizedMessage}",
                        LogType.ERROR
                    )
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
        classCode: String? = null
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
                activePhaseId = "",
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
        loadSubjects(forceRefresh = true)
    }

    fun loadSubjects(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val currentProgId = _uiState.value.programId
            val programId = if (currentProgId.isNotBlank()) currentProgId else (sessionManager.getActiveProgramId() ?: "")
            val currentProgTitle = _uiState.value.programTitle
            val programTitle = if (currentProgTitle.isNotBlank()) currentProgTitle else (sessionManager.getActiveProgramTitleBn() ?: "এইচএসসি কোর্স")

            val oldProgramId = _uiState.value.programId
            val isProgramChanged = oldProgramId.isNotBlank() && oldProgramId != programId
            
            _uiState.update { 
                it.copy(
                    programId = programId,
                    programTitle = programTitle,
                    isSubjectsLoading = true,
                    subjectsErrorMessage = null,
                    phases = if (isProgramChanged) emptyList() else it.phases,
                    selectedPhase = if (isProgramChanged) null else it.selectedPhase,
                    activePhaseId = if (isProgramChanged) "" else it.activePhaseId,
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
                        val fetchedPhases = phaseRes.data?.programPhasesByStudent?.data ?: emptyList()
                        phasesList = fetchedPhases
                        val activePhase = fetchedPhases.firstOrNull { it.has_enrolment == true }
                            ?: fetchedPhases.firstOrNull { it.has_free_trial_enrolment == true }
                            ?: fetchedPhases.firstOrNull { it.is_current == true }
                            ?: fetchedPhases.firstOrNull { it.status.equals("ACTIVE", ignoreCase = true) }
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
            DebugTerminalManager.log(
                "LOAD_LESSONS",
                "চ্যাপ্টার লেকচার লোড হচ্ছে: '${chapterName ?: matchingChapter?.chapter_name}' (ID: $primaryChapterId)\n- Candidate Chapter IDs: $candidateChapterIds\n- Candidate Program IDs: $candidateProgramIds",
                LogType.INFO
            )
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

                DebugTerminalManager.log(
                    "LOAD_LESSONS_RESULT",
                    "লোড সম্পন্ন: ${lessonList.size}টি ক্লাস পাওয়া গেছে।\n- চেষ্টা করা কোয়েরি: ${queryAttempts.size}টি\n- স্ট্যাটাস: ${if (lessonList.isNotEmpty()) "সফল (Lessons Loaded)" else "ফাঁকা (No lessons found, possible enrollment restriction)"}",
                    if (lessonList.isNotEmpty()) LogType.SUCCESS else LogType.WARNING
                )

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
            DebugTerminalManager.log(
                "GQL_PHASE_RESP",
                "Query PhaseWise (cid=$chapterId, pid=$programId, phId=$phaseId):\nData Count: ${data?.size ?: 0}\nItems:\n$detailsLog",
                if (!data.isNullOrEmpty()) LogType.SUCCESS else LogType.WARNING
            )
            return data ?: emptyList()
        } catch (e: Exception) {
            DebugTerminalManager.log(
                "GQL_PHASE_ERR",
                "Query PhaseWise Error (cid=$chapterId, pid=$programId, phId=$phaseId): ${e.localizedMessage}",
                LogType.ERROR
            )
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
            DebugTerminalManager.log(
                "GQL_STD_RESP",
                "Query Standard (cid=$chapterId, pid=$programId):\nData Count: ${data?.size ?: 0}\nItems:\n$detailsLog",
                if (!data.isNullOrEmpty()) LogType.SUCCESS else LogType.WARNING
            )
            return data ?: emptyList()
        } catch (e: Exception) {
            DebugTerminalManager.log(
                "GQL_STD_ERR",
                "Query Standard Error (cid=$chapterId, pid=$programId): ${e.localizedMessage}",
                LogType.ERROR
            )
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

            DebugTerminalManager.log(
                "HIERARCHY_CHAPTERS_RESP",
                "Hierarchy Fallback (code=$subjectCode): Found ${foundChapters.size} chapters",
                if (foundChapters.isNotEmpty()) LogType.SUCCESS else LogType.WARNING
            )
            return foundChapters
        } catch (e: Exception) {
            DebugTerminalManager.log(
                "HIERARCHY_CHAPTERS_ERR",
                "Hierarchy Fallback Error (code=$subjectCode): ${e.localizedMessage}",
                LogType.ERROR
            )
            return emptyList()
        }
    }

    fun reloadSelectedLesson() {
        val currentChapterId = _uiState.value.selectedChapterId
        val currentLessonId = _uiState.value.selectedLesson?.id
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
