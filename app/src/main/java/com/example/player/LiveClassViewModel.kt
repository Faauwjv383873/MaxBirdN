package com.example.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.api.StudentLessonItem
import com.example.auth.SessionManager
import com.example.course.CourseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LiveClassUiState(
    val isLoading: Boolean = false,
    val roomId: String? = null,
    val token: String? = null,
    val isChatBlocked: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Dedicated ViewModel for managing Live Class interactive session,
 * token retrieval, and 100ms WebSocket connection lifecycle.
 */
class LiveClassViewModel(
    private val repository: CourseRepository,
    private val sessionManager: SessionManager,
    val socketManager: HmsLiveSocketManager = HmsLiveSocketManager(),
    private val completedItemRepository: com.example.database.CompletedItemRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(LiveClassUiState())
    val uiState: StateFlow<LiveClassUiState> = _uiState.asStateFlow()

    fun joinAndConnect(lesson: StudentLessonItem) {
        val liveClassId = lesson.live_class?.id ?: lesson.content_id ?: lesson.id
        val lessonId = lesson.id.ifBlank { lesson.content_id ?: liveClassId }

        viewModelScope.launch {
            // Mark completed in database and session manager
            sessionManager.markLessonCompleted(lessonId)
            if (lesson.id.isNotBlank()) sessionManager.markLessonCompleted(lesson.id)
            if (!lesson.content_id.isNullOrBlank()) sessionManager.markLessonCompleted(lesson.content_id)
            completedItemRepository?.markCompleted(
                itemId = lessonId,
                itemType = "LIVE_CLASS",
                title = lesson.title ?: "",
                subjectId = lesson.subject_id ?: "",
                programId = lesson.program_id ?: "",
                chapterId = lesson.chapter_id ?: ""
            )

            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val payload = repository.joinLiveClass(liveClassId, lessonId)
                val effectiveRoomId = payload?.hms_room_id?.trim()
                    ?: lesson.live_class?.hms_room_id?.trim()
                    ?: payload?.join_link?.substringAfterLast("/meeting/")?.substringAfterLast("/")?.trim()

                var token = lesson.live_class?.hms_token
                var blockedChat = false

                if (!effectiveRoomId.isNullOrBlank()) {
                    try {
                        val tokenResp = repository.getHmsToken(effectiveRoomId)
                        token = tokenResp.token
                        blockedChat = tokenResp.blocked_chat ?: false
                    } catch (_: Exception) {}
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        roomId = effectiveRoomId,
                        token = token,
                        isChatBlocked = blockedChat
                    )
                }

                if (!token.isNullOrBlank()) {
                    val userName = sessionManager.getUserFullName()
                        ?: sessionManager.getUserFirstName()
                        ?: "Student"
                    socketManager.connect(token, effectiveRoomId, userName)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "লাইভ ক্লাসে যোগ দিতে ব্যর্থ হয়েছে"
                    )
                }
            }
        }
    }

    fun connectSocket(token: String, roomId: String?) {
        val userName = sessionManager.getUserFullName()
            ?: sessionManager.getUserFirstName()
            ?: "Student"
        socketManager.connect(token, roomId, userName)
    }

    fun disconnect() {
        socketManager.disconnect()
    }

    override fun onCleared() {
        super.onCleared()
        socketManager.disconnect()
    }
}

class LiveClassViewModelFactory(
    private val repository: CourseRepository,
    private val sessionManager: SessionManager,
    private val completedItemRepository: com.example.database.CompletedItemRepository? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LiveClassViewModel::class.java)) {
            return LiveClassViewModel(repository, sessionManager, completedItemRepository = completedItemRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
