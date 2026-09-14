package com.example.course

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.api.AcademicChapterItem
import com.example.api.ShikhoApiService
import com.example.api.TopicFullItem
import com.example.auth.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AnimatedChaptersUiState(
    val isLoading: Boolean = false,
    val subjectCode: String = "",
    val subjectTitle: String = "",
    val chapters: List<AcademicChapterItem> = emptyList(),
    val errorMessage: String? = null
)

data class AnimatedTopicsUiState(
    val isLoading: Boolean = false,
    val chapterId: String = "",
    val chapterName: String = "",
    val subjectCode: String = "",
    val topics: List<TopicFullItem> = emptyList(),
    val errorMessage: String? = null
)

class AnimatedLessonsViewModel(
    private val repository: CourseRepository
) : ViewModel() {

    private val _chaptersUiState = MutableStateFlow(AnimatedChaptersUiState())
    val chaptersUiState: StateFlow<AnimatedChaptersUiState> = _chaptersUiState.asStateFlow()

    private val _topicsUiState = MutableStateFlow(AnimatedTopicsUiState())
    val topicsUiState: StateFlow<AnimatedTopicsUiState> = _topicsUiState.asStateFlow()

    fun loadAnimatedChapters(subjectCode: String, subjectTitle: String) {
        viewModelScope.launch {
            _chaptersUiState.value = AnimatedChaptersUiState(
                isLoading = true,
                subjectCode = subjectCode,
                subjectTitle = subjectTitle
            )
            try {
                val chapters = repository.getChaptersBySubjectCode(subjectCode)
                _chaptersUiState.value = _chaptersUiState.value.copy(
                    isLoading = false,
                    chapters = chapters
                )
            } catch (e: Exception) {
                _chaptersUiState.value = _chaptersUiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "অধ্যায় লোড করতে ব্যর্থ হয়েছে"
                )
            }
        }
    }

    fun loadAnimatedTopics(chapterId: String, chapterName: String, subjectCode: String) {
        viewModelScope.launch {
            _topicsUiState.value = AnimatedTopicsUiState(
                isLoading = true,
                chapterId = chapterId,
                chapterName = chapterName,
                subjectCode = subjectCode
            )
            try {
                var topics = if (chapterId.isNotBlank()) {
                    repository.getTopics(chapterId)
                } else emptyList()

                // If chapterId was blank or returned empty, try resolving via GetChapters(subjectCode)
                if (topics.isEmpty() && subjectCode.isNotBlank()) {
                    val subjectChapters = repository.getChaptersBySubjectCode(subjectCode)
                    val matchedChapter = subjectChapters.find { ch ->
                        chapterId.isNotBlank() && (ch.id == chapterId || ch.chapter_id == chapterId)
                    } ?: subjectChapters.find { ch ->
                        chapterName.isNotBlank() && (
                            ch.effectiveName.equals(chapterName, ignoreCase = true) ||
                            ch.effectiveName.contains(chapterName, ignoreCase = true) ||
                            chapterName.contains(ch.effectiveName, ignoreCase = true)
                        )
                    }

                    if (matchedChapter != null && matchedChapter.id.isNotBlank()) {
                        topics = repository.getTopics(matchedChapter.id)
                        _topicsUiState.value = _topicsUiState.value.copy(
                            chapterId = matchedChapter.id,
                            chapterName = if (chapterName.isBlank()) matchedChapter.effectiveName else chapterName
                        )
                    }
                }

                _topicsUiState.value = _topicsUiState.value.copy(
                    isLoading = false,
                    topics = topics
                )
            } catch (e: Exception) {
                _topicsUiState.value = _topicsUiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "টপিক লোড করতে ব্যর্থ হয়েছে"
                )
            }
        }
    }
}

class AnimatedLessonsViewModelFactory(
    private val apiService: ShikhoApiService,
    private val sessionManager: SessionManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AnimatedLessonsViewModel::class.java)) {
            val repository = CourseRepository(apiService)
            return AnimatedLessonsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
