package com.example.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.database.NotificationHistoryEntity
import com.example.database.NotificationHistoryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NotificationHistoryUiState(
    val notifications: List<NotificationHistoryEntity> = emptyList(),
    val unreadCount: Int = 0,
    val isLoading: Boolean = false
)

class NotificationHistoryViewModel(
    private val repository: NotificationHistoryRepository
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
    private val repository: NotificationHistoryRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NotificationHistoryViewModel::class.java)) {
            return NotificationHistoryViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
