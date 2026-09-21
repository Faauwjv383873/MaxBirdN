package com.example.quiz

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.api.*
import com.example.auth.SessionManager
import com.example.database.SavedItemEntity
import com.example.database.SavedItemRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PracticeQuizViewModel(
    private val repository: PracticeQuizRepository,
    private val savedItemRepository: SavedItemRepository? = null
) : ViewModel() {

    companion object {
        private const val TAG = "PracticeQuizVM"
    }

    private val _uiState = MutableStateFlow(PracticeQuizUiState())
    val uiState: StateFlow<PracticeQuizUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var saveAnswerJob: Job? = null

    init {
        savedItemRepository?.let { repo ->
            viewModelScope.launch {
                repo.allSavedItemIds.collect { savedIds ->
                    _uiState.update { state ->
                        state.copy(bookmarkedQuestionIds = (state.bookmarkedQuestionIds + savedIds).toSet())
                    }
                }
            }
        }
    }

    /**
     * Step 1: Load subject chapters with question counts
     */
    fun loadSubjectChapters(
        subjectCode: String,
        subjectTitle: String,
        subjectColorHex: String?,
        targetChapterId: String? = null,
        targetChapterName: String? = null
    ) {
        _uiState.update {
            it.copy(
                subjectCode = subjectCode,
                subjectTitle = subjectTitle,
                subjectColorHex = subjectColorHex,
                targetChapterId = targetChapterId?.ifBlank { null },
                targetChapterName = targetChapterName?.ifBlank { null },
                isSingleChapterMode = !targetChapterId.isNullOrBlank(),
                isChaptersLoading = true,
                chaptersError = null
            )
        }

        viewModelScope.launch {
            try {
                val (subjectItem, chapters) = repository.getSubjectChaptersWithQuestionCounts(
                    subjectCode = subjectCode,
                    subjectTitle = subjectTitle
                )

                val limits = repository.checkPracticeQuizAccess()

                val cleanTargetId = targetChapterId?.trim()?.ifBlank { null }
                val cleanTargetName = targetChapterName?.trim()?.ifBlank { null }

                // Find matching chapter if targetChapterId or targetChapterName is specified
                fun normalizeBengali(s: String?): String {
                    if (s.isNullOrBlank()) return ""
                    return s.replace("য়", "y")
                        .replace("য়", "y")
                        .replace("১ম", "1")
                        .replace("২য়", "2")
                        .replace("৩য়", "3")
                        .replace("৪র্থ", "4")
                        .replace("৫ম", "5")
                        .replace("৬ষ্ঠ", "6")
                        .replace("৭ম", "7")
                        .replace("৮ম", "8")
                        .replace("৯ম", "9")
                        .replace("১০ম", "10")
                        .replace("অধ্যায়", "")
                        .replace("অধ্যায়", "")
                        .replace(Regex("[^a-zA-Z0-9\\p{L}]"), "")
                        .lowercase()
                        .trim()
                }

                val matchedChapter = if (cleanTargetId != null || cleanTargetName != null) {
                    val normTargetName = normalizeBengali(cleanTargetName)
                    chapters.find { ch ->
                        val normChName = normalizeBengali(ch.name)
                        (cleanTargetId != null && (ch.id == cleanTargetId || ch.id.contains(cleanTargetId) || cleanTargetId.contains(ch.id))) ||
                        (cleanTargetName != null && normChName.isNotBlank() && normTargetName.isNotBlank() && (normChName.contains(normTargetName) || normTargetName.contains(normChName)))
                    } ?: if (cleanTargetId != null) {
                        HierarchyChapterItem(
                            id = cleanTargetId,
                            name = cleanTargetName ?: "অধ্যায়",
                            should_render = true,
                            total_active_questions = null
                        )
                    } else null
                } else null

                val effectiveChapters = if (matchedChapter != null) {
                    listOf(matchedChapter)
                } else {
                    chapters
                }

                val selectedIds = if (matchedChapter != null) {
                    setOf(matchedChapter.id)
                } else {
                    chapters.map { it.id }.toSet()
                }

                _uiState.update {
                    it.copy(
                        isChaptersLoading = false,
                        chapters = effectiveChapters,
                        allSubjectChapters = chapters,
                        selectedChapterIds = selectedIds,
                        isSingleChapterMode = matchedChapter != null,
                        subjectIcon = subjectItem?.icon,
                        totalActiveQuestionsInSubject = subjectItem?.total_active_questions ?: chapters.sumOf { ch -> ch.total_active_questions ?: 0 },
                        practiceLimits = limits,
                        chaptersError = if (effectiveChapters.isEmpty()) "এই বিষয়ে বর্তমানে কোনো প্র্যাকটিস কুইজ উপলব্ধ নেই" else null
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading subject chapters", e)
                _uiState.update {
                    it.copy(
                        isChaptersLoading = false,
                        chaptersError = "অধ্যায়সমূহ লোড করতে সমস্যা হয়েছে। পুনরায় চেষ্টা করুন।"
                    )
                }
            }
        }
    }

    fun showAllChapters() {
        _uiState.update { state ->
            val all = state.allSubjectChapters
            state.copy(
                chapters = all,
                isSingleChapterMode = false,
                selectedChapterIds = all.map { it.id }.toSet()
            )
        }
    }

    fun toggleChapterSelection(chapterId: String) {
        _uiState.update { state ->
            val updated = state.selectedChapterIds.toMutableSet()
            if (updated.contains(chapterId)) {
                updated.remove(chapterId)
            } else {
                updated.add(chapterId)
            }
            state.copy(selectedChapterIds = updated)
        }
    }

    fun toggleSelectAllChapters() {
        _uiState.update { state ->
            if (state.areAllChaptersSelected) {
                state.copy(selectedChapterIds = emptySet())
            } else {
                state.copy(selectedChapterIds = state.chapters.map { it.id }.toSet())
            }
        }
    }

    /**
     * Step 2: Select Question Count
     */
    fun selectQuestionCount(count: Int) {
        _uiState.update {
            it.copy(
                selectedQuestionCount = count,
                showSummarySheet = true
            )
        }
    }

    fun dismissSummarySheet() {
        _uiState.update { it.copy(showSummarySheet = false) }
    }

    fun openSummarySheet() {
        _uiState.update { it.copy(showSummarySheet = true) }
    }

    /**
     * Step 3: Start Practice Quiz Session
     */
    fun startQuiz(onSuccess: (sessionId: String) -> Unit) {
        val state = _uiState.value
        val chapterIds = state.selectedChapterIds.toList()
        if (chapterIds.isEmpty()) {
            _uiState.update { it.copy(startQuizError = "অনুগ্রহ করে অন্তত একটি অধ্যায় সিলেক্ট করুন") }
            return
        }

        _uiState.update {
            it.copy(
                isStartingQuiz = true,
                startQuizError = null,
                showSummarySheet = false
            )
        }

        viewModelScope.launch {
            try {
                val session = repository.startPracticeQuizSession(
                    subjectIds = listOfNotNull(state.subjectCode.ifBlank { null }),
                    chapterIds = chapterIds,
                    totalCount = state.selectedQuestionCount
                )

                val durationSeconds = (session.questions?.size ?: state.selectedQuestionCount) * 60L

                _uiState.update {
                    it.copy(
                        isStartingQuiz = false,
                        session = session,
                        sessionId = session.id,
                        currentQuestionIndex = 0,
                        userAnswers = emptyMap(),
                        remainingSeconds = durationSeconds,
                        initialTotalSeconds = durationSeconds,
                        isQuizFinished = false
                    )
                }

                startCountdownTimer()
                onSuccess(session.id)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting practice quiz session", e)
                _uiState.update {
                    it.copy(
                        isStartingQuiz = false,
                        startQuizError = e.message ?: "কুইজ শুরু করতে সমস্যা হয়েছে। আবার চেষ্টা করুন।"
                    )
                }
            }
        }
    }

    private fun startCountdownTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_uiState.value.remainingSeconds > 0 && !_uiState.value.isQuizFinished) {
                delay(1000L)
                _uiState.update {
                    val nextSec = it.remainingSeconds - 1
                    it.copy(remainingSeconds = nextSec)
                }
            }

            if (_uiState.value.remainingSeconds <= 0 && !_uiState.value.isQuizFinished) {
                // Auto-submit on timeout
                submitFinalQuiz(isTimeout = true) {}
            }
        }
    }

    private fun getIsoUtcString(millis: Long): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        return sdf.format(java.util.Date(millis))
    }

    /**
     * Helper to build properly structured question_answer payload with session qa IDs
     */
    private fun buildQuestionAnswersPayload(
        session: PracticeQuizSessionItem?,
        userAnswers: Map<String, String>
    ): List<Map<String, Any?>> {
        val nowIso = getIsoUtcString(System.currentTimeMillis())
        val startIso = getIsoUtcString(System.currentTimeMillis() - 15000L)

        return userAnswers.mapNotNull { (qId, ans) ->
            if (ans.isNotBlank()) {
                mapOf(
                    "id" to qId,
                    "given_ans" to ans,
                    "start_time" to startIso,
                    "submit_time" to nowIso
                )
            } else null
        }
    }

    /**
     * In Quiz: Select Option & debounce save
     */
    fun selectOption(questionId: String, optionNo: String) {
        val updatedAnswers = _uiState.value.userAnswers + (questionId to optionNo)
        _uiState.update {
            it.copy(userAnswers = updatedAnswers)
        }

        // Debounced save progress in background
        saveAnswerJob?.cancel()
        saveAnswerJob = viewModelScope.launch {
            delay(400L)
            try {
                val sessionId = _uiState.value.sessionId
                val session = _uiState.value.session
                if (sessionId.isNotBlank()) {
                    val answersList = buildQuestionAnswersPayload(session, updatedAnswers)
                    repository.submitPracticeQuizSession(
                        sessionId = sessionId,
                        isFinal = false,
                        isTimeout = false,
                        questionAnswers = answersList.ifEmpty { null }
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Background answer progress save error: ${e.message}")
            }
        }
    }

    fun goToNextQuestion() {
        val total = _uiState.value.session?.questions?.size ?: 0
        if (_uiState.value.currentQuestionIndex < total - 1) {
            _uiState.update { it.copy(currentQuestionIndex = it.currentQuestionIndex + 1) }
        }
    }

    fun goToPreviousQuestion() {
        if (_uiState.value.currentQuestionIndex > 0) {
            _uiState.update { it.copy(currentQuestionIndex = it.currentQuestionIndex - 1) }
        }
    }

    fun goToQuestionIndex(index: Int) {
        val total = _uiState.value.session?.questions?.size ?: 0
        if (index in 0 until total) {
            _uiState.update { it.copy(currentQuestionIndex = index) }
        }
    }

    /**
     * Submit Final Quiz
     */
    fun submitFinalQuiz(
        explicitSessionId: String? = null,
        isTimeout: Boolean = false,
        onSuccess: (sessionId: String) -> Unit = {}
    ) {
        timerJob?.cancel()
        saveAnswerJob?.cancel()
        val sessionId = explicitSessionId?.ifBlank { null }
            ?: _uiState.value.sessionId.ifBlank { null }
            ?: return

        _uiState.update { it.copy(isSubmitting = true, submitError = null, sessionId = sessionId) }
        viewModelScope.launch {
            try {
                val session = _uiState.value.session
                val answersList = buildQuestionAnswersPayload(session, _uiState.value.userAnswers)

                // ১. উত্তরগুলো সহ ফাইনাল সাবমিশন
                repository.submitPracticeQuizSession(
                    sessionId = sessionId,
                    isFinal = true,
                    isTimeout = isTimeout,
                    questionAnswers = answersList.ifEmpty { null }
                )

                // সার্ভার যাতে এগ্রিগেশন সম্পন্ন করতে পারে তার জন্য সামান্য বাফার ডিলে
                delay(800L)
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        isQuizFinished = true,
                        sessionId = sessionId
                    )
                }
                onSuccess(sessionId)
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error submitting final quiz", e)
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        submitError = e.message ?: "সাবমিট করতে সমস্যা হয়েছে"
                    )
                }
            }
        }
    }

    /**
     * Step 4: Load Quiz Result
     */
    fun loadQuizResult(sessionId: String) {
        Log.d(TAG, "loadQuizResult called for sessionId=$sessionId")
        _uiState.update {
            it.copy(
                sessionId = sessionId,
                isResultLoading = true,
                resultError = null
            )
        }

        viewModelScope.launch {
            try {
                var summary = repository.getQuizResultSummary(sessionId)

                // If result shows 0 correct and 0 incorrect despite user having answered questions,
                // give server backend another 1s to finish aggregation and retry once.
                val answeredCount = _uiState.value.userAnswers.size
                val totalCorrect = summary.correctCount
                val totalIncorrect = summary.incorrectCount
                if (answeredCount > 0 && totalCorrect == 0 && totalIncorrect == 0) {
                    Log.d(TAG, "Result shows 0/0 despite answeredCount=$answeredCount. Retrying getQuizResultSummary in 1000ms...")
                    delay(1000L)
                    try {
                        val retrySummary = repository.getQuizResultSummary(sessionId)
                        val retryCorrect = retrySummary.correctCount
                        val retryIncorrect = retrySummary.incorrectCount
                        if (retryCorrect > 0 || retryIncorrect > 0 || retrySummary.total_spent_time != null) {
                            summary = retrySummary
                        }
                    } catch (retryEx: Exception) {
                        Log.w(TAG, "Retry getQuizResultSummary failed", retryEx)
                    }
                }

                _uiState.update {
                    it.copy(
                        isResultLoading = false,
                        resultSummary = summary
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading quiz result", e)
                _uiState.update {
                    it.copy(
                        isResultLoading = false,
                        resultError = "ফলাফল লোড করা যায়নি। পুনরায় চেষ্টা করুন।"
                    )
                }
            }
        }
    }

    /**
     * Step 5: Load Quiz Feedback / Solutions
     */
    fun loadQuizFeedback(sessionId: String) {
        _uiState.update {
            it.copy(
                sessionId = sessionId,
                isFeedbackLoading = true,
                feedbackError = null
            )
        }

        viewModelScope.launch {
            try {
                val session = repository.getMcqSessionFeedback(sessionId)
                _uiState.update {
                    it.copy(
                        isFeedbackLoading = false,
                        feedbackSession = session
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading quiz feedback", e)
                _uiState.update {
                    it.copy(
                        isFeedbackLoading = false,
                        feedbackError = "ফিডব্যাক লোড করা যায়নি। পুনরায় চেষ্টা করুন।"
                    )
                }
            }
        }
    }

    fun setFeedbackFilter(filter: FeedbackFilter) {
        _uiState.update { it.copy(feedbackFilter = filter) }
    }

    fun toggleBookmark(questionId: String) {
        val sessionId = _uiState.value.sessionId
        val isAlreadyBookmarked = _uiState.value.bookmarkedQuestionIds.contains(questionId)

        if (isAlreadyBookmarked) {
            _uiState.update {
                it.copy(
                    bookmarkedQuestionIds = it.bookmarkedQuestionIds - questionId,
                    bookmarkMessage = "বুকমার্ক সরানো হয়েছে"
                )
            }
            viewModelScope.launch {
                try {
                    savedItemRepository?.deleteSavedItemById(questionId)
                } catch (e: Exception) {
                    Log.w(TAG, "Error deleting saved question from database", e)
                }
            }
            return
        }

        _uiState.update {
            it.copy(
                bookmarkedQuestionIds = it.bookmarkedQuestionIds + questionId,
                bookmarkMessage = "প্রশ্নটি সংরক্ষণ করা হয়েছে"
            )
        }

        viewModelScope.launch {
            // 1. Save question to local Room database
            try {
                val session = _uiState.value.session
                val feedbackSession = _uiState.value.feedbackSession
                val question = session?.questions?.find { it.id == questionId }
                    ?: feedbackSession?.questions?.find { it.id == questionId }

                val correctOpt = question?.correct_option
                    ?: feedbackSession?.question_answer?.find { it.id == questionId }?.correct_ans
                val userGivenAns = _uiState.value.userAnswers[questionId]
                    ?: question?.given_ans
                    ?: feedbackSession?.question_answer?.find { it.id == questionId }?.given_ans

                val explanation = question?.description?.takeIf { it.isNotBlank() }
                val subjectTitle = _uiState.value.subjectTitle.ifBlank { null }
                val chapterName = question?.chapter?.name ?: _uiState.value.targetChapterName

                val optionsList = question?.mcq_options?.map { opt ->
                    (opt.no ?: "") to (opt.description ?: "")
                } ?: emptyList()

                val contentJson = SavedItemEntity.buildQuestionContentJson(
                    options = optionsList,
                    correctOption = correctOpt,
                    userGivenAns = userGivenAns,
                    explanation = explanation,
                    chapterName = chapterName
                )

                val entity = SavedItemEntity(
                    id = questionId,
                    type = SavedItemEntity.TYPE_QUESTION,
                    title = question?.title?.takeIf { it.isNotBlank() } ?: "প্রশ্ন",
                    subtitle = listOfNotNull(subjectTitle, chapterName).filter { it.isNotBlank() }.joinToString(" • ").ifBlank { null },
                    contentJson = contentJson,
                    timestamp = System.currentTimeMillis()
                )

                savedItemRepository?.saveItem(entity)
            } catch (e: Exception) {
                Log.w(TAG, "Error saving question to local database", e)
            }

            // 2. Call backend GraphQL mutation to sync
            try {
                repository.createSavedQuestion(sessionId, questionId)
            } catch (e: Exception) {
                Log.w(TAG, "Error creating saved question on backend", e)
            }
        }
    }

    fun clearBookmarkMessage() {
        _uiState.update { it.copy(bookmarkMessage = null) }
    }

    fun resetQuizFlow() {
        timerJob?.cancel()
        saveAnswerJob?.cancel()
        _uiState.update {
            it.copy(
                session = null,
                sessionId = "",
                currentQuestionIndex = 0,
                userAnswers = emptyMap(),
                remainingSeconds = 0L,
                isSubmitting = false,
                submitError = null,
                isQuizFinished = false,
                resultSummary = null,
                feedbackSession = null
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        saveAnswerJob?.cancel()
    }
}

class PracticeQuizViewModelFactory(
    private val apiService: ShikhoApiService,
    private val sessionManager: SessionManager,
    private val savedItemRepository: SavedItemRepository? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PracticeQuizViewModel::class.java)) {
            val repository = PracticeQuizRepository(apiService, sessionManager)
            return PracticeQuizViewModel(repository, savedItemRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
