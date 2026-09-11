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

    private val _uiState = MutableStateFlow(CourseUiState())
    val uiState: StateFlow<CourseUiState> = _uiState.asStateFlow()

    init {
        loadSubjects()
    }

    fun selectLesson(lesson: StudentLessonItem) {
        val recUrl = lesson.resolvedVideoUrl ?: lesson.live_class?.resolvedVideoUrl ?: lesson.live_class?.recording_url ?: ""
        val isEnrolled = recUrl.isNotBlank()
        DebugTerminalManager.log(
            "SELECT_LESSON",
            "ক্লাস সিলেক্ট করা হয়েছে: '${lesson.title}' (ID: ${lesson.id})\n- স্ট্রিমিং URL: ${if (isEnrolled) recUrl else "নেই (ফ্রি বা আনএনরোল্ড কোর্স, ভিডিও প্লে হবে না)"}",
            if (isEnrolled) LogType.SUCCESS else LogType.ERROR
        )
        _uiState.update {
            it.copy(selectedLesson = lesson)
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
        sessionManager.saveActiveProgram(newProgramId, title, batchId, classCode)
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
            val programId = sessionManager.getActiveProgramId() ?: _uiState.value.programId
            val programTitle = sessionManager.getActiveProgramTitleBn() ?: _uiState.value.programTitle.ifBlank { "এইচএসসি কোর্স" }

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
                                      code
                                      percentage
                                      total_chapters
                                      completed_chapters
                                    }
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
                                      code
                                      percentage
                                      total_chapters
                                      completed_chapters
                                    }
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
        val progId = sessionManager.getActiveProgramId() ?: _uiState.value.programId
        val targetPhaseId = phaseId ?: _uiState.value.activePhaseId
        
        val matchedPhase = _uiState.value.phases.find { it.id == targetPhaseId }
            ?: _uiState.value.selectedPhase
            ?: _uiState.value.phases.firstOrNull { it.is_current == true }
            ?: _uiState.value.phases.firstOrNull()

        _uiState.update {
            it.copy(
                programId = progId,
                selectedSubjectCode = subjectCode,
                selectedSubjectTitle = subjectTitle ?: it.selectedSubjectTitle,
                selectedSubjectColor = subjectColor ?: it.selectedSubjectColor,
                selectedPhase = matchedPhase,
                activePhaseId = matchedPhase?.id ?: targetPhaseId,
                activePhaseTitle = matchedPhase?.title ?: it.activePhaseTitle,
                isChaptersLoading = true,
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
        val sessionProgId = sessionManager.getActiveProgramId()
        val stateProgId = _uiState.value.programId
        val phaseProgId = _uiState.value.selectedPhase?.academic_program_id
        val candidateProgramIds = listOfNotNull(phaseProgId, sessionProgId, stateProgId).filter { it.isNotBlank() }.distinct()

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

        _uiState.update {
            it.copy(
                selectedChapterId = primaryChapterId,
                selectedChapterName = chapterName ?: matchingChapter?.chapter_name ?: it.selectedChapterName,
                selectedChapterStatus = chapterStatus ?: matchingChapter?.status ?: it.selectedChapterStatus,
                isLessonsLoading = true,
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
        // Attempt 1: Rich query
        try {
            val q1 = GraphQlQuery(
                operationName = "GetUpcomingLessonsPhaseWise",
                query = """
                    query GetUpcomingLessonsPhaseWise(${'$'}chapter_id: String!, ${'$'}program_id: String!, ${'$'}phase_id: String!) {
                      studentSpecificLessons(program_id: ${'$'}program_id, chapter_id: ${'$'}chapter_id, phase_id: ${'$'}phase_id) {
                        data {
                          id
                          title
                          content_id
                          content_type
                          user_activity_state
                          start_time
                          end_time
                          slide_url
                          live_class {
                            id
                            recording_url
                            stream_url
                            video_url
                            playback_url
                            url
                            hls_url
                            start_time
                            end_time
                            type
                            slide_url
                            attachments {
                              id
                              title
                              name
                              url
                              link
                              file_type
                            }
                            topics {
                              id
                              title
                              description
                            }
                            teacher {
                              id
                              name
                              avatar
                            }
                          }
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
            if (!data.isNullOrEmpty()) return data
        } catch (_: Exception) {}

        // Attempt 2: Query with slide_url & attachments
        try {
            val q2 = GraphQlQuery(
                operationName = "GetUpcomingLessonsPhaseWise",
                query = """
                    query GetUpcomingLessonsPhaseWise(${'$'}chapter_id: String!, ${'$'}program_id: String!, ${'$'}phase_id: String!) {
                      studentSpecificLessons(program_id: ${'$'}program_id, chapter_id: ${'$'}chapter_id, phase_id: ${'$'}phase_id) {
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
                            slide_url
                            attachments {
                              id
                              title
                              url
                              file_type
                            }
                          }
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
            val res = apiService.getStudentLessons(q2)
            val data = res.data?.studentSpecificLessons?.data
            if (!data.isNullOrEmpty()) return data
        } catch (_: Exception) {}

        // Attempt 3: Query with slide_url only
        try {
            val q3 = GraphQlQuery(
                operationName = "GetUpcomingLessonsPhaseWise",
                query = """
                    query GetUpcomingLessonsPhaseWise(${'$'}chapter_id: String!, ${'$'}program_id: String!, ${'$'}phase_id: String!) {
                      studentSpecificLessons(program_id: ${'$'}program_id, chapter_id: ${'$'}chapter_id, phase_id: ${'$'}phase_id) {
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
                            slide_url
                          }
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
            val res = apiService.getStudentLessons(q3)
            val data = res.data?.studentSpecificLessons?.data
            if (!data.isNullOrEmpty()) return data
        } catch (_: Exception) {}

        // Attempt 4: Base fallback query
        try {
            val q4 = GraphQlQuery(
                operationName = "GetUpcomingLessonsPhaseWise",
                query = """
                    query GetUpcomingLessonsPhaseWise(${'$'}chapter_id: String!, ${'$'}program_id: String!, ${'$'}phase_id: String!) {
                      studentSpecificLessons(program_id: ${'$'}program_id, chapter_id: ${'$'}chapter_id, phase_id: ${'$'}phase_id) {
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
                    "program_id" to programId,
                    "phase_id" to phaseId
                )
            )
            val res = apiService.getStudentLessons(q4)
            return res.data?.studentSpecificLessons?.data ?: emptyList()
        } catch (_: Exception) {
            return emptyList()
        }
    }

    private suspend fun fetchLessonsStandard(
        chapterId: String,
        programId: String
    ): List<StudentLessonItem> {
        // Attempt 1: Rich standard query
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
                          start_time
                          end_time
                          slide_url
                          live_class {
                            id
                            recording_url
                            stream_url
                            video_url
                            playback_url
                            url
                            hls_url
                            start_time
                            end_time
                            type
                            slide_url
                            attachments {
                              id
                              title
                              name
                              url
                              link
                              file_type
                            }
                            topics {
                              id
                              title
                              description
                            }
                            teacher {
                              id
                              name
                              avatar
                            }
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
            if (!data.isNullOrEmpty()) return data
        } catch (_: Exception) {}

        // Attempt 2: Standard with slide_url & attachments
        try {
            val q2 = GraphQlQuery(
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
                            slide_url
                            attachments {
                              id
                              title
                              url
                              file_type
                            }
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
            val res = apiService.getStudentLessons(q2)
            val data = res.data?.studentSpecificLessons?.data
            if (!data.isNullOrEmpty()) return data
        } catch (_: Exception) {}

        // Attempt 3: Base standard query
        try {
            val q3 = GraphQlQuery(
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
            val res = apiService.getStudentLessons(q3)
            return res.data?.studentSpecificLessons?.data ?: emptyList()
        } catch (_: Exception) {
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
