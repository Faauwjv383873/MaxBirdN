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
                // 1. Fetch Phases if not present to ensure we have a valid phase_id
                var currentPhaseId = _uiState.value.activePhaseId
                var phasesList = _uiState.value.phases

                if (phasesList.isEmpty() || currentPhaseId.isBlank() || forceRefresh) {
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
                        ?: fetchedPhases.firstOrNull { it.status == "ACTIVE" }
                        ?: fetchedPhases.firstOrNull()

                    currentPhaseId = activePhase?.id ?: ""
                    _uiState.update {
                        it.copy(
                            phases = phasesList,
                            selectedPhase = activePhase,
                            activePhaseId = currentPhaseId,
                            activePhaseTitle = activePhase?.title ?: ""
                        )
                    }
                }

                // 2. Fetch Academic Subjects
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
                val rawSubjects = academicProgram?.subjects ?: emptyList()
                val progressBars = academicProgram?.subjects_progress_bar ?: emptyList()

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
        val progId = _uiState.value.programId.ifBlank { sessionManager.getActiveProgramId() ?: "" }
        val targetPhaseId = phaseId ?: _uiState.value.activePhaseId
        
        _uiState.update {
            it.copy(
                selectedSubjectCode = subjectCode,
                selectedSubjectTitle = subjectTitle ?: it.selectedSubjectTitle,
                selectedSubjectColor = subjectColor ?: it.selectedSubjectColor,
                activePhaseId = targetPhaseId,
                isChaptersLoading = true,
                chaptersErrorMessage = null
            )
        }

        viewModelScope.launch {
            try {
                // Ensure phases are available for the tab bar
                if (_uiState.value.phases.isEmpty()) {
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
                    val activePhase = fetchedPhases.find { it.id == targetPhaseId }
                        ?: fetchedPhases.firstOrNull { it.is_current == true }
                        ?: fetchedPhases.firstOrNull()

                    _uiState.update {
                        it.copy(
                            phases = fetchedPhases,
                            selectedPhase = activePhase,
                            activePhaseId = activePhase?.id ?: targetPhaseId,
                            activePhaseTitle = activePhase?.title ?: ""
                        )
                    }
                }

                val effectivePhaseId = _uiState.value.activePhaseId.ifBlank { targetPhaseId }

                // Fetch Chapters
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
                val chaptersList = response.data?.listAcademicProgramChapters?.data ?: emptyList()

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
        val progId = _uiState.value.programId.ifBlank { sessionManager.getActiveProgramId() ?: "" }
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
                val lessonsQuery = GraphQlQuery(
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
                              subject_id
                              subject_name
                              color_code
                              icon
                              live_class {
                                id
                                recording_url
                                chapter_name
                                is_on_going
                                start_time
                                end_time
                                type
                                slide_url
                                topics {
                                  id
                                  title
                                  name
                                }
                                teacher {
                                  id
                                  name
                                  first_name
                                  last_name
                                  avatar
                                  subject
                                }
                                instructor {
                                  id
                                  name
                                  first_name
                                  last_name
                                  avatar
                                  subject
                                }
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
                        "program_id" to progId,
                        "phase_id" to phaseId
                    )
                )

                val response = apiService.getStudentLessons(lessonsQuery)
                val lessonList = response.data?.studentSpecificLessons?.data ?: emptyList()

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
