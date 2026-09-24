package com.example.course

import android.util.Log
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

class CourseViewModel(
    private val apiService: ShikhoApiService,
    private val sessionManager: SessionManager,
    val repository: CourseRepository = CourseRepository(apiService),
    private val completedItemRepository: com.example.database.CompletedItemRepository? = null
) : ViewModel() {

    suspend fun getTopics(chapterId: String, topicIds: List<String>? = null): List<TopicFullItem> {
        return try {
            repository.getTopics(chapterId, topicIds)
        } catch (e: Exception) {
            android.util.Log.e("CourseViewModel", "Error fetching topics for $chapterId", e)
            emptyList()
        }
    }

    suspend fun getPhaseWiseChapters(programId: String, phaseId: String, subjectCode: String): List<AcademicChapterItem> {
        return try {
            val effectiveProgId = programId.ifBlank { "6864d3a806800acba2e27099" }
            val effectivePhaseId = phaseId.ifBlank { "6864d62506800acba2e27111" }
            val list = repository.getPhaseWiseChapters(effectiveProgId, effectivePhaseId, subjectCode)
            if (list.isNotEmpty()) {
                list
            } else {
                repository.getChaptersBySubjectCode(subjectCode)
            }
        } catch (e: Exception) {
            android.util.Log.e("CourseViewModel", "Error fetching chapters for $subjectCode", e)
            try {
                repository.getChaptersBySubjectCode(subjectCode)
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    // Caching for instant sub-second loading
    private val chaptersCache = java.util.concurrent.ConcurrentHashMap<String, List<AcademicChapterItem>>()
    private val lessonsCache = java.util.concurrent.ConcurrentHashMap<String, List<StudentLessonItem>>()

    private val _uiState = MutableStateFlow(CourseUiState())
    val uiState: StateFlow<CourseUiState> = _uiState.asStateFlow()

    // 100ms Live Class Interactive WebSocket Manager
    val liveSocketManager = com.example.player.HmsLiveSocketManager()

    fun connectLiveSocket(token: String, roomId: String?) {
        val userName = sessionManager.getUserFullName()
            ?: sessionManager.getUserFirstName()
            ?: "Student"
        liveSocketManager.connect(token, roomId, userName)
    }

    fun disconnectLiveSocket() {
        liveSocketManager.disconnect()
    }

    override fun onCleared() {
        super.onCleared()
        liveSocketManager.disconnect()
    }

    init {
        fetchEnrolledPrograms()
        loadSubjects()
        completedItemRepository?.let { repo ->
            viewModelScope.launch {
                repo.completedIdsState.collect { completedSet ->
                    if (_uiState.value.lessons.isNotEmpty()) {
                        val enriched = LessonCacheManager.enrichWithCompletedState(_uiState.value.lessons, completedSet)
                        _uiState.update { it.copy(lessons = enriched) }
                    }
                }
            }
        }
    }

    fun openCourse(program: EnrolledProgram) {
        val title = program.title_bn ?: "প্রোগ্রাম"
        _uiState.update { 
            it.copy(
                selectedCourseProgram = program,
                hasAnimatedVideo = program.has_animated_video == true
            ) 
        }
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
                val batchId = sessionManager.getActiveProgramBatchId() ?: sessionManager.getUserBatchId()
                val userClassName = sessionManager.getUserClassName() ?: "C11"
                val group = sessionManager.getUserGroup() ?: "Humanities"
                val vendor = sessionManager.getUserVendor() ?: "BD"

                val response = repository.getAcademicProgramByEnrollment(
                    batchId = batchId,
                    className = userClassName,
                    group = group,
                    vendor = vendor
                )
                val enrolled = response.data?.listAcademicProgramByEnrollment?.enrolled_programs ?: emptyList()
                val otherAll = response.data?.listAcademicProgramByEnrollment?.other_programs ?: emptyList()

                // সার্ভার থেকে আসা other_programs কেও enrolled হিসেবে প্রমোট করে সব কোর্স আনলক রাখা
                val promotedEnrolled = otherAll.map { it.toEnrolledProgram() }
                val allEnrolled = (enrolled + promotedEnrolled).distinctBy { it.id }

                val freeList = otherAll.filter { it.is_free == true }
                val paidOtherList = otherAll.filter { it.is_free != true }

                _uiState.update {
                    it.copy(
                        enrolledPrograms = allEnrolled,
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
                            enrolledPrograms = CourseFallbackDataProvider.getFallbackEnrolledPrograms(userClassName, group),
                            freePrograms = if (current.freePrograms.isEmpty()) CourseFallbackDataProvider.getFallbackFreePrograms(userClassName, group) else current.freePrograms,
                            otherPrograms = if (current.otherPrograms.isEmpty()) CourseFallbackDataProvider.getFallbackOtherPrograms(userClassName, group) else current.otherPrograms,
                            isProgramsLoading = false
                        )
                    } else {
                        current.copy(isProgramsLoading = false)
                    }
                }
            }
        }
    }

    fun selectLesson(lesson: StudentLessonItem) {
        val hasDirectStream = lesson.candidateStreamUrls.any { it.isNotBlank() && it != "null" }
        val liveClassId = lesson.live_class?.id?.takeIf { it.isNotBlank() }
            ?: lesson.content_id?.takeIf { it.isNotBlank() }
            ?: lesson.id
        val needsFetch = liveClassId.isNotBlank() && (!hasDirectStream || lesson.attachments.isNullOrEmpty())

        _uiState.update {
            it.copy(
                selectedLesson = lesson,
                isLessonDetailLoading = needsFetch
            )
        }

        android.util.Log.d("LectureDebug", "selectLesson: ${lesson.title}, liveClassId: $liveClassId, hasDirectStream: $hasDirectStream, needsFetch: $needsFetch")

        if (liveClassId.isNotBlank()) {
            viewModelScope.launch {
                try {
                    val liveClassData = repository.getLiveClassDetails(liveClassId)
                    android.util.Log.d("LectureDebug", "api response: $liveClassData")
                    android.util.Log.d("LectureDebug", "playback_url: ${liveClassData?.playback_url}")

                    if (liveClassData != null) {
                        val pbUrl = liveClassData.playback_url?.takeIf { it.isNotBlank() }
                            ?: liveClassData.recording_url?.takeIf { it.isNotBlank() }
                            ?: liveClassData.stream_url?.takeIf { it.isNotBlank() }
                            ?: liveClassData.video_url?.takeIf { it.isNotBlank() }
                            ?: liveClassData.url?.takeIf { it.isNotBlank() }
                            ?: liveClassData.hls_url?.takeIf { it.isNotBlank() }

                        val updatedLiveClass = (lesson.live_class ?: LiveClassDetails()).copy(
                            id = liveClassData.id ?: lesson.live_class?.id,
                            playback_url = pbUrl ?: lesson.live_class?.playback_url,
                            recording_url = pbUrl ?: lesson.live_class?.recording_url,
                            stream_url = liveClassData.stream_url ?: lesson.live_class?.stream_url,
                            video_url = liveClassData.video_url ?: lesson.live_class?.video_url,
                            start_time = liveClassData.start_time ?: lesson.live_class?.start_time,
                            end_time = liveClassData.end_time ?: lesson.live_class?.end_time,
                            teacher = liveClassData.teacher ?: liveClassData.instructor ?: lesson.live_class?.teacher,
                            topics = if (!liveClassData.topics.isNullOrEmpty()) liveClassData.topics else lesson.live_class?.topics
                        )

                        val newAttachments = mutableListOf<LessonAttachmentItem>()
                        liveClassData.study_materials?.forEach { mat ->
                            if (!mat.file_url.isNullOrBlank()) {
                                newAttachments.add(LessonAttachmentItem(id = mat.id, title = mat.name ?: "লেকচার স্লাইড (PDF)", url = mat.file_url, file_type = "pdf"))
                            }
                        }
                        lesson.attachments?.let { newAttachments.addAll(it) }

                        val chapterIdResolved = lesson.chapter_id?.takeIf { it.isNotBlank() }
                            ?: liveClassData.chapter?.id?.takeIf { it.isNotBlank() }

                        val updatedLesson = lesson.copy(
                            live_class = updatedLiveClass,
                            attachments = newAttachments.distinctBy { it.downloadUrl },
                            chapter_id = chapterIdResolved,
                            topics = if (!liveClassData.topics.isNullOrEmpty()) liveClassData.topics else lesson.topics
                        )

                        var currentLessonState = updatedLesson
                        android.util.Log.d("LectureDebug", "resolved video url: ${currentLessonState.resolvedVideoUrl}")

                        // 1. Fetch Teacher Details if teacher_id exists
                        val teacherId = liveClassData.teacher?.id
                        if (!teacherId.isNullOrBlank()) {
                            try {
                                val fullTeacher = repository.getTeacherDetails(teacherId)
                                if (fullTeacher != null) {
                                    val withTeacher = currentLessonState.live_class?.copy(teacher = fullTeacher)
                                    currentLessonState = currentLessonState.copy(live_class = withTeacher)
                                }
                            } catch (_: Exception) {}
                        }

                        val cur = _uiState.value.selectedLesson
                        if (cur == null || cur.id == lesson.id || cur.content_id == lesson.content_id || cur.live_class?.id == liveClassId) {
                            _uiState.update { 
                                it.copy(
                                    selectedLesson = currentLessonState,
                                    isLessonDetailLoading = false
                                ) 
                            }
                        }

                        // 2. Attempt to fetch live room & meeting link via JoinLiveClass mutation
                        joinLiveClass(currentLessonState)
                    } else {
                        _uiState.update { it.copy(isLessonDetailLoading = false) }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("LectureDebug", "Error in selectLesson: ${e.message}", e)
                    _uiState.update { it.copy(isLessonDetailLoading = false) }
                }
            }
        } else {
            _uiState.update { it.copy(isLessonDetailLoading = false) }
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
                val payload = repository.joinLiveClass(liveClassId, lessonId)
                if (payload != null) {
                    var hmsToken: String? = null
                    var isChatBlocked: Boolean? = false

                    // Extract room_id (from hms_room_id field or parsed from join_link)
                    val effectiveRoomId = payload.hms_room_id?.trim()
                        ?: payload.join_link?.substringAfterLast("/meeting/")?.substringAfterLast("/")?.trim()

                    if (!effectiveRoomId.isNullOrBlank()) {
                        try {
                            val tokenResp = repository.getHmsToken(effectiveRoomId)
                            hmsToken = tokenResp.token
                            isChatBlocked = tokenResp.blocked_chat
                        } catch (_: Exception) {
                            // Non-fatal if token call fails, fallback to join_link
                        }
                    }

                    val currentSelected = _uiState.value.selectedLesson
                    if (currentSelected != null && (currentSelected.id == lesson.id || currentSelected.content_id == lesson.content_id || currentSelected.live_class?.id == liveClassId)) {
                        val updatedLiveClass = (currentSelected.live_class ?: LiveClassDetails()).copy(
                            join_link = payload.join_link ?: currentSelected.live_class?.join_link,
                            provider = payload.provider ?: currentSelected.live_class?.provider,
                            hms_room_id = effectiveRoomId ?: currentSelected.live_class?.hms_room_id,
                            hms_token = hmsToken ?: currentSelected.live_class?.hms_token,
                            blocked_chat = isChatBlocked ?: currentSelected.live_class?.blocked_chat
                        )
                        _uiState.update {
                            it.copy(selectedLesson = currentSelected.copy(live_class = updatedLiveClass))
                        }

                        if (!hmsToken.isNullOrBlank()) {
                            connectLiveSocket(hmsToken, effectiveRoomId)
                        }
                    }
                }
                onResult?.invoke(payload)
            } catch (_: Exception) {
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
        chaptersCache.clear()
        lessonsCache.clear()
        val title = if (!newProgramTitle.isNullOrBlank()) newProgramTitle else "এইচএসসি কোর্স"
        sessionManager.saveActiveProgram(
            programId = newProgramId,
            titleBn = title,
            batchId = batchId,
            classCode = classCode
        )
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
            
            val prog = _uiState.value.enrolledPrograms.find { it.id == programId }
                ?: _uiState.value.freePrograms.find { it.id == programId }?.toEnrolledProgram()
                ?: _uiState.value.otherPrograms.find { it.id == programId }?.toEnrolledProgram()
            // DISABLED: Animated lessons disabled per user request
            val hasAnimated = false

            _uiState.update { 
                it.copy(
                    programId = programId,
                    programTitle = programTitle,
                    hasAnimatedVideo = hasAnimated,
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
                        val fetchedPhases = repository.getProgramPhases(programId).map { 
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
                    } catch (e: Exception) {
                        Log.e("CourseViewModel", "Error fetching phases for programId=$programId: ${e.message}", e)
                    }
                }

                // 2. Fetch Academic Subjects
                var rawSubjects: List<AcademicSubjectItem> = emptyList()
                var progressBars: List<SubjectProgressBarItem> = emptyList()

                if (currentPhaseId.isNotBlank()) {
                    try {
                        val academicProgram = repository.getAcademicSubjects(programId, currentPhaseId)
                        rawSubjects = academicProgram?.subjects ?: emptyList()
                        progressBars = academicProgram?.subjects_progress_bar ?: emptyList()
                    } catch (e: Exception) {
                        Log.e("CourseViewModel", "Error fetching subjects with phaseId=$currentPhaseId: ${e.message}", e)
                    }
                }

                if (rawSubjects.isEmpty()) {
                    try {
                        val academicProgram = repository.getAcademicSubjects(programId, null)
                        rawSubjects = academicProgram?.subjects ?: emptyList()
                        progressBars = academicProgram?.subjects_progress_bar ?: emptyList()
                    } catch (e: Exception) {
                        Log.e("CourseViewModel", "Error fetching subjects fallback: ${e.message}", e)
                    }
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
                Log.e("CourseViewModel", "Fatal error in loadSubjects: ${e.message}", e)
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
        
        val matchedPhase = if (targetPhaseId.isNotBlank()) {
            _uiState.value.phases.find { it.id == targetPhaseId }
        } else {
            _uiState.value.selectedPhase
                ?: _uiState.value.phases.firstOrNull { it.is_current == true }
                ?: _uiState.value.phases.firstOrNull()
        }

        val effectivePhaseId = matchedPhase?.id ?: targetPhaseId

        val cacheKey = "${progId}_${subjectCode}_${effectivePhaseId}"
        val cachedChapters = chaptersCache[cacheKey]

        _uiState.update {
            it.copy(
                programId = progId,
                selectedSubjectCode = subjectCode,
                selectedSubjectTitle = subjectTitle ?: it.selectedSubjectTitle,
                selectedSubjectColor = subjectColor ?: it.selectedSubjectColor,
                selectedPhase = matchedPhase,
                activePhaseId = effectivePhaseId,
                activePhaseTitle = matchedPhase?.title ?: it.activePhaseTitle,
                chapters = cachedChapters ?: emptyList(),
                isChaptersLoading = cachedChapters == null,
                chaptersErrorMessage = null
            )
        }

        viewModelScope.launch {
            try {
                var currentPhases = _uiState.value.phases
                var currentEffectivePhaseId = effectivePhaseId

                if (currentPhases.isEmpty() || _uiState.value.programId != progId) {
                    try {
                        val fetchedPhases = repository.getProgramPhases(progId)
                        if (fetchedPhases.isNotEmpty()) {
                            currentPhases = fetchedPhases
                            val activePhase = if (targetPhaseId.isNotBlank()) {
                                fetchedPhases.find { it.id == targetPhaseId }
                            } else {
                                fetchedPhases.firstOrNull { it.is_current == true }
                                    ?: fetchedPhases.firstOrNull { it.status.equals("ACTIVE", ignoreCase = true) }
                                    ?: fetchedPhases.firstOrNull()
                            }

                            currentEffectivePhaseId = activePhase?.id ?: targetPhaseId
                            _uiState.update {
                                it.copy(
                                    programId = progId,
                                    phases = fetchedPhases,
                                    selectedPhase = activePhase,
                                    activePhaseId = currentEffectivePhaseId,
                                    activePhaseTitle = activePhase?.title ?: ""
                                )
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("CourseViewModel", "Error fetching phases in loadChaptersForSubject: ${e.message}", e)
                    }
                }

                var chaptersList = emptyList<AcademicChapterItem>()
                var queryError: Exception? = null

                // 1. If phaseId / effectivePhaseId is set, fetch phase/quarter-wise chapters strictly for this phase
                if (currentEffectivePhaseId.isNotBlank()) {
                    try {
                        chaptersList = repository.getPhaseWiseChapters(progId, currentEffectivePhaseId, subjectCode)
                    } catch (e: Exception) {
                        queryError = e
                        Log.e("CourseViewModel", "Error fetching phase-wise chapters for phase=$currentEffectivePhaseId: ${e.message}", e)
                    }
                }

                // 2. Only if the program has NO phases at all, try query without phase_id
                if (chaptersList.isEmpty() && currentPhases.isEmpty()) {
                    try {
                        val fallbackList = repository.getAcademicProgramChaptersFallback(progId, subjectCode)
                        if (fallbackList.isNotEmpty()) {
                            chaptersList = fallbackList
                        }
                    } catch (e: Exception) {
                        queryError = e
                        Log.e("CourseViewModel", "Error fetching academic program chapters fallback: ${e.message}", e)
                    }
                }

                if (chaptersList.isNotEmpty()) {
                    val finalCacheKey = "${progId}_${subjectCode}_${currentEffectivePhaseId}"
                    chaptersCache[finalCacheKey] = chaptersList
                }

                val finalError = if (chaptersList.isEmpty()) {
                    if (queryError != null) "অধ্যায় লোড করতে সমস্যা হয়েছে: ${queryError.localizedMessage ?: "নেটওয়ার্ক সমস্যা"}"
                    else null
                } else null

                _uiState.update {
                    it.copy(
                        chapters = chaptersList,
                        isChaptersLoading = false,
                        chaptersErrorMessage = finalError
                    )
                }

                // Eagerly prefetch all lessons for this phase in background so chapter opening is instant (0ms)
                if (currentEffectivePhaseId.isNotBlank()) {
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            val batchId = sessionManager.getActiveProgramBatchId() ?: sessionManager.getUserBatchId()
                            repository.fetchAllLessonsForProgram(progId, currentEffectivePhaseId, batchId)
                        } catch (_: Exception) {}
                    }
                }
            } catch (e: Exception) {
                Log.e("CourseViewModel", "Fatal error in loadChaptersForSubject: ${e.message}", e)
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
        val currentSubjectCode = _uiState.value.selectedSubjectCode
        _uiState.update {
            it.copy(
                selectedPhase = phase,
                activePhaseId = phase.id,
                activePhaseTitle = phase.title ?: "",
                isChaptersLoading = true,
                chapters = emptyList(),
                chaptersErrorMessage = null
            )
        }
        if (currentSubjectCode.isNotBlank()) {
            loadChaptersForSubject(
                subjectCode = currentSubjectCode,
                subjectTitle = _uiState.value.selectedSubjectTitle,
                subjectColor = _uiState.value.selectedSubjectColor,
                phaseId = phase.id
            )
        }
    }

    fun selectChapter(chapterId: String, chapterName: String, chapterStatus: String = "") {
        val progId = _uiState.value.programId.ifBlank { sessionManager.getActiveProgramId() ?: "" }
        val matchingChapter = _uiState.value.chapters.firstOrNull { it.id == chapterId || it.chapter_id == chapterId }
        val effChapterId = chapterId.ifBlank { matchingChapter?.chapter_id ?: matchingChapter?.id ?: "" }
        val effChapterName = chapterName.ifBlank { matchingChapter?.effectiveName ?: "" }
        val subjectTitle = _uiState.value.selectedSubjectTitle

        val candidateChapterIds = listOfNotNull(
            effChapterId,
            chapterId,
            matchingChapter?.id,
            matchingChapter?.chapter_id
        ).filter { it.isNotBlank() }.distinct()

        val otherChapters = _uiState.value.chapters.filter { ch ->
            val cid1 = ch.id.trim()
            val cid2 = (ch.chapter_id ?: "").trim()
            !candidateChapterIds.contains(cid1) && !candidateChapterIds.contains(cid2)
        }
        val otherChapterIds = otherChapters.flatMap { 
            listOfNotNull(it.id.takeIf { s -> s.isNotBlank() }, it.chapter_id?.takeIf { s -> s.isNotBlank() })
        }.distinct()
        val otherChapterNames = otherChapters.mapNotNull { it.effectiveName.takeIf { s -> s.isNotBlank() } }
        val chapterNoStr = matchingChapter?.effectiveNo?.toString()

        val cachedLessons = LessonCacheManager.findLessonsForChapter(
            candidateChapterIds = candidateChapterIds,
            chapterName = effChapterName,
            subjectTitle = subjectTitle,
            otherChapterIds = otherChapterIds,
            otherChapterNames = otherChapterNames,
            chapterNo = chapterNoStr,
            programId = progId
        ).ifEmpty {
            lessonsCache["${progId}_${effChapterId}"] ?: emptyList()
        }

        _uiState.update {
            it.copy(
                selectedChapterId = effChapterId,
                selectedChapterName = effChapterName,
                selectedChapterStatus = chapterStatus,
                lessons = cachedLessons,
                isLessonsLoading = cachedLessons.isEmpty(),
                lessonsErrorMessage = null,
                lessonsDiagnosticInfo = null
            )
        }
    }

    fun loadLessonsForChapter(
        chapterId: String,
        altChapterId: String? = null,
        chapterName: String? = null,
        chapterStatus: String? = null
    ) {
        val progId = _uiState.value.programId.ifBlank { sessionManager.getActiveProgramId() ?: "" }
        val matchingChapter = _uiState.value.chapters.firstOrNull { it.id == chapterId || it.chapter_id == chapterId }
        val effChapterId = chapterId.ifBlank { altChapterId ?: matchingChapter?.chapter_id ?: matchingChapter?.id ?: "" }
        val effChapterName = chapterName ?: matchingChapter?.chapter_name ?: matchingChapter?.effectiveName
        val subjectTitle = _uiState.value.selectedSubjectTitle

        val candidateChapterIds = listOfNotNull(
            effChapterId,
            chapterId,
            altChapterId,
            matchingChapter?.id,
            matchingChapter?.chapter_id
        ).filter { it.isNotBlank() }.distinct()

        // Chapters in this quarter other than the selected chapter
        val otherChapters = _uiState.value.chapters.filter { ch ->
            val cid1 = ch.id.trim()
            val cid2 = (ch.chapter_id ?: "").trim()
            !candidateChapterIds.contains(cid1) && !candidateChapterIds.contains(cid2)
        }
        val otherChapterIds = otherChapters.flatMap { 
            listOfNotNull(it.id.takeIf { s -> s.isNotBlank() }, it.chapter_id?.takeIf { s -> s.isNotBlank() })
        }.distinct()
        val otherChapterNames = otherChapters.mapNotNull { it.effectiveName.takeIf { s -> s.isNotBlank() } }
        val chapterNoStr = matchingChapter?.effectiveNo?.toString()

        val cachedFromManager = LessonCacheManager.findLessonsForChapter(
            candidateChapterIds = candidateChapterIds,
            chapterName = effChapterName,
            subjectTitle = subjectTitle,
            otherChapterIds = otherChapterIds,
            otherChapterNames = otherChapterNames,
            chapterNo = chapterNoStr,
            programId = progId
        )
        val initialCached = if (cachedFromManager.isNotEmpty()) {
            cachedFromManager
        } else {
            lessonsCache["${progId}_${effChapterId}"]?.let { rawList ->
                LessonCacheManager.filterLessons(
                    lessons = rawList,
                    candidateChapterIds = candidateChapterIds,
                    chapterName = effChapterName,
                    subjectTitle = subjectTitle,
                    otherChapterIds = otherChapterIds,
                    otherChapterNames = otherChapterNames,
                    chapterNo = chapterNoStr,
                    programId = progId
                )
            }
        }

        val hasCurrentLessons = _uiState.value.selectedChapterId == effChapterId && _uiState.value.lessons.isNotEmpty()
        val effectiveInitial = initialCached ?: (if (hasCurrentLessons) _uiState.value.lessons else emptyList())

        _uiState.update {
            it.copy(
                selectedChapterId = effChapterId,
                selectedChapterName = effChapterName ?: it.selectedChapterName,
                selectedChapterStatus = chapterStatus ?: matchingChapter?.status ?: it.selectedChapterStatus,
                lessons = effectiveInitial,
                isLessonsLoading = effectiveInitial.isEmpty(),
                lessonsErrorMessage = null,
                lessonsDiagnosticInfo = null
            )
        }

        viewModelScope.launch {
            try {
                val phaseId = _uiState.value.activePhaseId.ifBlank {
                    _uiState.value.phases.firstOrNull { it.is_current == true }?.id
                        ?: _uiState.value.phases.firstOrNull { it.status.equals("ACTIVE", ignoreCase = true) }?.id
                        ?: _uiState.value.phases.firstOrNull()?.id
                        ?: ""
                }

                val batchId = sessionManager.getActiveProgramBatchId() ?: sessionManager.getUserBatchId()
                val allProgramLessons = repository.fetchAllLessonsForProgram(progId, phaseId.ifBlank { null }, batchId)
                
                var matched = LessonCacheManager.filterLessons(
                    lessons = allProgramLessons,
                    candidateChapterIds = candidateChapterIds,
                    chapterName = effChapterName,
                    subjectTitle = subjectTitle,
                    otherChapterIds = otherChapterIds,
                    otherChapterNames = otherChapterNames,
                    chapterNo = chapterNoStr,
                    programId = progId
                )

                if (matched.isEmpty() && effectiveInitial.isNotEmpty()) {
                    matched = effectiveInitial
                }

                if (matched.isNotEmpty()) {
                    lessonsCache["${progId}_${effChapterId}"] = matched
                    LessonCacheManager.saveLessons(matched, programId = progId)
                }

                val completedSet = completedItemRepository?.completedIdsState?.value ?: sessionManager.getCompletedLessonIds()
                val enrichedMatched = LessonCacheManager.enrichWithCompletedState(matched, completedSet)

                _uiState.update {
                    it.copy(
                        lessons = enrichedMatched,
                        isLessonsLoading = false,
                        lessonsErrorMessage = if (enrichedMatched.isEmpty()) "এই অধ্যায়ে কোনো ক্লাস পাওয়া যায়নি" else null,
                        lessonsDiagnosticInfo = null
                    )
                }
            } catch (e: Exception) {
                val completedSet = completedItemRepository?.completedIdsState?.value ?: sessionManager.getCompletedLessonIds()
                val enrichedInitial = LessonCacheManager.enrichWithCompletedState(effectiveInitial, completedSet)
                _uiState.update {
                    it.copy(
                        lessons = enrichedInitial,
                        isLessonsLoading = false,
                        lessonsErrorMessage = if (enrichedInitial.isEmpty()) "ক্লাস লোড করতে সমস্যা হয়েছে: ${e.localizedMessage ?: "নেটওয়ার্ক সমস্যা"}" else null
                    )
                }
            }
        }
    }
}

class CourseViewModelFactory(
    private val apiService: ShikhoApiService,
    private val sessionManager: SessionManager,
    private val completedItemRepository: com.example.database.CompletedItemRepository? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CourseViewModel::class.java)) {
            return CourseViewModel(apiService, sessionManager, completedItemRepository = completedItemRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
