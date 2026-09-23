package com.example.notification

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.auth.SessionManager
import com.example.database.NotificationHistoryEntity
import com.example.database.NotificationHistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotificationHistoryUiState(
    val notifications: List<NotificationHistoryEntity> = emptyList(),
    val unreadCount: Int = 0,
    val isLoading: Boolean = false
)

class NotificationHistoryViewModel(
    private val repository: NotificationHistoryRepository,
    private val context: Context,
    private val sessionManager: SessionManager
) : ViewModel() {

    val uiState: StateFlow<NotificationHistoryUiState> = combine(
        repository.getAll(),
        repository.getUnreadCount()
    ) { notifications, unreadCount ->
        NotificationHistoryUiState(
            notifications = notifications,
            unreadCount = unreadCount,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = NotificationHistoryUiState(isLoading = true)
    )

    private val _diagnosticState = MutableStateFlow(
        FcmDiagnosticState(isChecking = true)
    )
    val diagnosticState: StateFlow<FcmDiagnosticState> = _diagnosticState.asStateFlow()

    init {
        refreshDiagnostics()
    }

    fun refreshDiagnostics() {
        viewModelScope.launch {
            _diagnosticState.update { it.copy(isChecking = true) }
            val result = FcmTopicManager.runDiagnostics(context, sessionManager)
            _diagnosticState.value = result
        }
    }

    fun sendTestNotification() {
        viewModelScope.launch {
            val (_, msg) = FcmTopicManager.sendTestNotification(context, repository)
            _diagnosticState.update { it.copy(testNotificationMessage = msg) }
        }
    }

    fun clearTestMessage() {
        _diagnosticState.update { it.copy(testNotificationMessage = null) }
    }

    fun markAsRead(id: Long) {
        viewModelScope.launch {
            repository.markAsRead(id)
        }
    }

    fun markAsClicked(id: Long) {
        viewModelScope.launch {
            repository.markAsClicked(id)
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            repository.markAllAsRead()
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }

    fun deleteNotification(id: Long) {
        viewModelScope.launch {
            repository.deleteById(id)
        }
    }
}

class NotificationHistoryViewModelFactory(
    private val repository: NotificationHistoryRepository,
    private val context: Context,
    private val sessionManager: SessionManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NotificationHistoryViewModel::class.java)) {
            return NotificationHistoryViewModel(repository, context.applicationContext, sessionManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
