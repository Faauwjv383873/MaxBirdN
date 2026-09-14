package com.example.quiz

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.api.*
import com.example.auth.SessionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PracticeQuizViewModel(
    private val repository: PracticeQuizRepository
) : ViewModel() {

    companion object {
        private const val TAG = "PracticeQuizVM"
    }

    private val _uiState = MutableStateFlow(PracticeQuizUiState())
    val uiState: StateFlow<PracticeQuizUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var saveAnswerJob: Job? = null

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
                val matchedChapter = if (cleanTargetId != null || cleanTargetName != null) {
                    chapters.find { ch ->
                        (cleanTargetId != null && (ch.id == cleanTargetId || ch.id.contains(cleanTargetId) || cleanTargetId.contains(ch.id))) ||
                        (cleanTargetName != null && ch.name?.trim()?.equals(cleanTargetName, ignoreCase = true) == true)
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

    /**
     * In Quiz: Select Option & debounce save
     */
    fun selectOption(questionId: String, optionNo: String) {
        _uiState.update {
            it.copy(userAnswers = it.userAnswers + (questionId to optionNo))
        }

        // Debounced save progress in background
        saveAnswerJob?.cancel()
        saveAnswerJob = viewModelScope.launch {
            delay(400L)
            try {
                val sessionId = _uiState.value.sessionId
                if (sessionId.isNotBlank()) {
                    val answersList = _uiState.value.userAnswers.map { (qId, ans) ->
                        mapOf(
                            "id" to qId,
                            "given_ans" to ans,
                            "is_submitted" to true
                        )
                    }
                    repository.submitPracticeQuizSession(
                        sessionId = sessionId,
                        isFinal = false,
                        isTimeout = false,
                        questionAnswers = answersList
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Background answer progress save error", e)
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
    fun submitFinalQuiz(isTimeout: Boolean = false, onSuccess: (sessionId: String) -> Unit) {
        timerJob?.cancel()
        saveAnswerJob?.cancel()

        val sessionId = _uiState.value.sessionId
        if (sessionId.isBlank()) return

        _uiState.update { it.copy(isSubmitting = true, submitError = null) }

        viewModelScope.launch {
            try {
                val answersList = _uiState.value.userAnswers.map { (qId, ans) ->
                    mapOf(
                        "id" to qId,
                        "given_ans" to ans,
                        "is_submitted" to true
                    )
                }

                repository.submitPracticeQuizSession(
                    sessionId = sessionId,
                    isFinal = true,
                    isTimeout = isTimeout,
                    questionAnswers = answersList
                )

                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        isQuizFinished = true
                    )
                }

                onSuccess(sessionId)
            } catch (e: Exception) {
                Log.e(TAG, "Error submitting final quiz", e)
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        submitError = e.message ?: "সাবমিট করতে সমস্যা হয়েছে। পুনরায় চেষ্টা করুন।"
                    )
                }
            }
        }
    }

    /**
     * Step 4: Load Quiz Result
     */
    fun loadQuizResult(sessionId: String) {
        _uiState.update {
            it.copy(
                sessionId = sessionId,
                isResultLoading = true,
                resultError = null
            )
        }

        viewModelScope.launch {
            try {
                val summary = repository.getQuizResultSummary(sessionId)
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
            return
        }

        _uiState.update {
            it.copy(
                bookmarkedQuestionIds = it.bookmarkedQuestionIds + questionId,
                bookmarkMessage = "প্রশ্নটি বুকমার্ক করা হয়েছে"
            )
        }

        viewModelScope.launch {
            try {
                repository.createSavedQuestion(sessionId, questionId)
            } catch (e: Exception) {
                Log.w(TAG, "Error creating saved question", e)
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
    private val sessionManager: SessionManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PracticeQuizViewModel::class.java)) {
            val repository = PracticeQuizRepository(apiService, sessionManager)
            return PracticeQuizViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
