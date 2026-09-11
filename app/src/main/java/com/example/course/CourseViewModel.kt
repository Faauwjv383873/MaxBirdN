package com.example.course

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.api.*
import com.example.auth.SessionManager
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
        _uiState.update {
            it.copy(selectedLesson = lesson)
        }
    }

    fun loadSubjects(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val programId = sessionManager.getActiveProgramId() ?: ""
            val programTitle = sessionManager.getActiveProgramTitleBn() ?: "এইচএসসি কোর্স"
            
            _uiState.update { 
                it.copy(
                    programId = programId,
                    programTitle = programTitle,
                    isSubjectsLoading = true,
                    subjectsErrorMessage = null
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
                var currentPhaseId = _uiState.value.activePhaseId
                var phasesList = _uiState.value.phases

                if (phasesList.isEmpty() || currentPhaseId.isBlank() || _uiState.value.programId != programId || forceRefresh) {
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
                        val activePhase = fetchedPhases.firstOrNull { it.is_current == true }
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
        chapterName: String? = null,
        chapterStatus: String? = null
    ) {
        val progId = sessionManager.getActiveProgramId() ?: _uiState.value.programId
        val phaseId = _uiState.value.activePhaseId

        _uiState.update {
            it.copy(
                selectedChapterId = chapterId,
                selectedChapterName = chapterName ?: it.selectedChapterName,
                selectedChapterStatus = chapterStatus ?: it.selectedChapterStatus,
                isLessonsLoading = true,
                lessonsErrorMessage = null
            )
        }

        viewModelScope.launch {
            try {
                var lessonList = emptyList<StudentLessonItem>()

                // 1. If phaseId is non-blank, try phase-wise lessons query
                if (phaseId.isNotBlank()) {
                    try {
                        val phaseWiseQuery = GraphQlQuery(
                            operationName = "GetUpcomingLessonsPhaseWise",
                            query = """
                                query GetUpcomingLessonsPhaseWise(${'$'}chapter_id: String!, ${'$'}program_id: String!, ${'$'}phase_id: String!) {
                                  studentSpecificLessons(program_id: ${'$'}program_id, chapter_id: ${'$'}chapter_id, phase_id: ${'$'}phase_id) {
                                    data {
                                      id
                                      title
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
                                "program_id" to progId,
                                "phase_id" to phaseId
                            )
                        )
                        val response = apiService.getStudentLessons(phaseWiseQuery)
                        lessonList = response.data?.studentSpecificLessons?.data ?: emptyList()
                    } catch (_: Exception) {
                        // Fallback to standard query if phase-wise query throws HTTP 400 or fails
                    }
                }

                // 2. If phase-wise query was not used or failed/returned empty, query without phase_id
                if (lessonList.isEmpty()) {
                    try {
                        val standardQuery = GraphQlQuery(
                            operationName = "GetUpcomingLessons",
                            query = """
                                query GetUpcomingLessons(${'$'}chapter_id: String!, ${'$'}program_id: String!) {
                                  studentSpecificLessons(program_id: ${'$'}program_id, chapter_id: ${'$'}chapter_id) {
                                    data {
                                      id
                                      title
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
                                "program_id" to progId
                            )
                        )
                        val standardRes = apiService.getStudentLessons(standardQuery)
                        val standardList = standardRes.data?.studentSpecificLessons?.data ?: emptyList()
                        if (standardList.isNotEmpty()) {
                            lessonList = standardList
                        }
                    } catch (_: Exception) {}
                }

                _uiState.update {
                    it.copy(
                        lessons = lessonList,
                        isLessonsLoading = false,
                        lessonsErrorMessage = if (lessonList.isEmpty()) "এই অধ্যায়ে কোনো ক্লাস বা লেকচার পাওয়া যায়নি" else null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLessonsLoading = false,
                        lessonsErrorMessage = "ক্লাস লোড করা যায়নি: ${e.localizedMessage ?: "নেটওয়ার্ক সমস্যা"}"
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
