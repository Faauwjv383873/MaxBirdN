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

class CourseViewModel(
    private val apiService: ShikhoApiService,
    private val sessionManager: SessionManager,
    private val repository: CourseRepository = CourseRepository(apiService)
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

                val response = repository.getAcademicProgramByEnrollment(userClassName)
                var enrolled = response.data?.listAcademicProgramByEnrollment?.enrolled_programs ?: emptyList()
                val otherAll = response.data?.listAcademicProgramByEnrollment?.other_programs ?: emptyList()

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
                    enrolled = CourseFallbackDataProvider.getFallbackEnrolledPrograms(userClassName, group)
                }
                if (freeList.isEmpty()) {
                    freeList = CourseFallbackDataProvider.getFallbackFreePrograms(userClassName, group)
                }
                if (paidOtherList.isEmpty()) {
                    paidOtherList = CourseFallbackDataProvider.getFallbackOtherPrograms(userClassName, group)
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
        _uiState.update {
            it.copy(selectedLesson = lesson)
        }

        val liveClassId = lesson.live_class?.id ?: lesson.content_id ?: lesson.id
        if (liveClassId.isNotBlank()) {
            viewModelScope.launch {
                try {
                    val liveClassData = repository.getLiveClassDetails(liveClassId)
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
                                val fullTeacher = repository.getTeacherDetails(teacherId)
                                if (fullTeacher != null) {
                                    val withTeacher = currentLessonState.live_class?.copy(teacher = fullTeacher)
                                    currentLessonState = currentLessonState.copy(live_class = withTeacher)
                                }
                            } catch (_: Exception) {}
                        }

                        // 2. Fetch Topic Videos via GetTopics if playback_url is blank
                        val chapterIdForTopics = lesson.chapter_id ?: liveClassData.chapter?.id
                        val topicIds = liveClassData.topics?.mapNotNull { it.id }?.filter { it.isNotBlank() }
                        if (currentLessonState.resolvedVideoUrl.isNullOrBlank() && !chapterIdForTopics.isNullOrBlank() && !topicIds.isNullOrEmpty()) {
                            try {
                                val topicsList = repository.getTopics(chapterIdForTopics, topicIds)
                                val topicVideos = topicsList.flatMap { it.videos?.data ?: emptyList() }
                                val fallbackTopicUrl = topicVideos.firstOrNull { !it.playback_url.isNullOrBlank() }?.playback_url
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
                            } catch (_: Exception) {}
                        }

                        if (_uiState.value.selectedLesson?.id == lesson.id) {
                            _uiState.update { it.copy(selectedLesson = currentLessonState) }
                        }

                        // 3. Attempt to fetch live room & meeting link via JoinLiveClass mutation
                        joinLiveClass(currentLessonState)
                    }
                } catch (_: Exception) {}
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
        val title = if (!newProgramTitle.isNullOrBlank()) newProgramTitle else "এইচএসসি কোর্স"
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
                    } catch (_: Exception) {}
                }

                // 2. Fetch Academic Subjects
                var rawSubjects: List<AcademicSubjectItem> = emptyList()
                var progressBars: List<SubjectProgressBarItem> = emptyList()

                if (currentPhaseId.isNotBlank()) {
                    try {
                        val academicProgram = repository.getAcademicSubjects(programId, currentPhaseId)
                        rawSubjects = academicProgram?.subjects ?: emptyList()
                        progressBars = academicProgram?.subjects_progress_bar ?: emptyList()
                    } catch (_: Exception) {}
                }

                if (rawSubjects.isEmpty()) {
                    try {
                        val academicProgram = repository.getAcademicSubjects(programId, null)
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
                var currentPhases = _uiState.value.phases
                var effectivePhaseId = _uiState.value.activePhaseId

                if (currentPhases.isEmpty() || _uiState.value.programId != progId) {
                    try {
                        val fetchedPhases = repository.getProgramPhases(progId)
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

                var chaptersList = emptyList<AcademicChapterItem>()

                // 1. If phaseId / effectivePhaseId is set, fetch phase/quarter-wise chapters first
                if (effectivePhaseId.isNotBlank()) {
                    try {
                        chaptersList = repository.getPhaseWiseChapters(progId, effectivePhaseId, subjectCode)
                    } catch (_: Exception) {}
                }

                // 2. If no phase is selected or phase query returned empty, try GetChapters(subject_code)
                if (chaptersList.isEmpty() && (effectivePhaseId.isBlank() || phaseId == null)) {
                    try {
                        val fullList = repository.getChaptersBySubjectCode(subjectCode)
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
                                val altList = repository.getPhaseWiseChapters(progId, p.id, subjectCode)
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
                        val fallbackList = repository.getAcademicProgramChaptersFallback(progId, subjectCode)
                        if (fallbackList.isNotEmpty()) {
                            chaptersList = fallbackList
                        }
                    } catch (_: Exception) {}
                }

                if (chaptersList.isEmpty()) {
                    val hierarchyChapters = repository.fetchFallbackHierarchyChapters(subjectCode, _uiState.value.selectedSubjectTitle)
                    if (hierarchyChapters.isNotEmpty()) {
                        chaptersList = hierarchyChapters
                    }
                }

                if (chaptersList.isNotEmpty()) {
                    chaptersCache[cacheKey] = chaptersList
                    val pId = progId
                    val phId = effectivePhaseId
                    chaptersList.take(6).forEach { chapter ->
                        val cid = (chapter.id ?: chapter.chapter_id ?: "").ifBlank { chapter.chapter_id ?: "" }
                        if (cid.isNotBlank() && !lessonsCache.containsKey(cid)) {
                            viewModelScope.launch {
                                try {
                                    val l1 = if (phId.isNotBlank()) repository.fetchLessonsWithPhase(cid, pId, phId) else emptyList()
                                    val l2 = if (l1.isEmpty()) repository.fetchLessonsStandard(cid, pId) else l1
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
                            val fetchedLessons = repository.fetchLessonsWithPhase(cid, pid, phId)
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
                        val fetchedLessons = repository.fetchLessonsStandard(cid, pid)
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

    fun loadAnimatedLessonsForChapter(chapterId: String) {
        _uiState.update {
            it.copy(
                isChapterAnimationsLoading = true,
                chapterAnimatedLessons = emptyList()
            )
        }
        viewModelScope.launch {
            try {
                val topicsList = repository.getTopics(chapterId)
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
                            val fetched = repository.getTopics(chId)
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
