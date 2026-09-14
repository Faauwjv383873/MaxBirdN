package com.example.smartnotes

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.api.AcademicChapterItem
import com.example.api.AttachmentDataItem
import com.example.api.ShikhoApiService
import com.example.api.TaggableResourceItem
import com.example.auth.SessionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SmartNotesUiState(
    val subjectCode: String = "",
    val subjectTitle: String = "",
    val subjectColor: String = "",
    val phaseId: String = "",
    val programId: String = "",
    val selectedTab: Int = 0, // 0: সাবজেক্ট রিসোর্সেস, 1: চ্যাপ্টার রিসোর্সেস
    
    // Subject-level resources
    val isSubjectResourcesLoading: Boolean = false,
    val subjectResources: List<TaggableResourceItem> = emptyList(),
    
    // Chapter list for Tab 2
    val isChaptersLoading: Boolean = false,
    val chapters: List<AcademicChapterItem> = emptyList(),
    
    // Chapter-level resources (when viewing a specific chapter)
    val selectedChapterId: String = "",
    val selectedChapterName: String = "",
    val isChapterResourcesLoading: Boolean = false,
    val chapterResources: List<TaggableResourceItem> = emptyList(),
    
    // PDF Opening state
    val isOpeningPdf: Boolean = false,
    val openingTagId: String? = null,
    val activeAttachment: AttachmentDataItem? = null,
    
    val errorMessage: String? = null
)

sealed class SmartNotesUiEvent {
    data class OpenPdfUrl(val url: String, val title: String) : SmartNotesUiEvent()
    data class ShowToast(val message: String) : SmartNotesUiEvent()
}

class SmartNotesViewModel(
    private val apiService: ShikhoApiService,
    private val sessionManager: SessionManager,
    private val repository: SmartNotesRepository = SmartNotesRepository(apiService)
) : ViewModel() {

    private val _uiState = MutableStateFlow(SmartNotesUiState())
    val uiState: StateFlow<SmartNotesUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SmartNotesUiEvent>()
    val events: SharedFlow<SmartNotesUiEvent> = _events.asSharedFlow()

    private var loadSubjectJob: Job? = null
    private var loadChaptersJob: Job? = null
    private var loadChapterResourcesJob: Job? = null

    fun initialize(
        subjectCode: String,
        subjectTitle: String,
        subjectColor: String?,
        phaseId: String?
    ) {
        val activeProgId = sessionManager.getActiveProgramId() ?: ""
        val effectivePhaseId = phaseId ?: ""
        
        _uiState.update {
            it.copy(
                subjectCode = subjectCode,
                subjectTitle = subjectTitle,
                subjectColor = subjectColor ?: "#2563EB",
                phaseId = effectivePhaseId,
                programId = activeProgId
            )
        }

        loadSubjectResources(subjectCode, effectivePhaseId)
        loadChapters(subjectCode, effectivePhaseId)
    }

    fun setSelectedTab(index: Int) {
        _uiState.update { it.copy(selectedTab = index) }
    }

    fun loadSubjectResources(subjectCode: String, phaseId: String?) {
        loadSubjectJob?.cancel()
        loadSubjectJob = viewModelScope.launch {
            _uiState.update { it.copy(isSubjectResourcesLoading = true, errorMessage = null) }
            try {
                val resources = repository.listSubjectTaggableResources(subjectCode, phaseId)
                _uiState.update {
                    it.copy(
                        isSubjectResourcesLoading = false,
                        subjectResources = resources
                    )
                }
            } catch (e: Exception) {
                Log.e("SmartNotesVM", "Error loading subject resources: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        isSubjectResourcesLoading = false,
                        errorMessage = "রিসোর্স লোড করতে সমস্যা হয়েছে"
                    )
                }
            }
        }
    }

    fun loadChapters(subjectCode: String, phaseId: String?) {
        loadChaptersJob?.cancel()
        loadChaptersJob = viewModelScope.launch {
            _uiState.update { it.copy(isChaptersLoading = true) }
            try {
                val progId = _uiState.value.programId.ifBlank {
                    sessionManager.getActiveProgramId() ?: ""
                }
                val chaptersList = repository.getSubjectChapters(progId, phaseId, subjectCode)
                _uiState.update {
                    it.copy(
                        isChaptersLoading = false,
                        chapters = chaptersList
                    )
                }
            } catch (e: Exception) {
                Log.e("SmartNotesVM", "Error loading chapters: ${e.message}", e)
                _uiState.update {
                    it.copy(isChaptersLoading = false)
                }
            }
        }
    }

    fun loadChapterResources(chapterId: String, phaseId: String?, chapterName: String? = null) {
        loadChapterResourcesJob?.cancel()
        loadChapterResourcesJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    selectedChapterId = chapterId,
                    selectedChapterName = chapterName ?: it.selectedChapterName,
                    isChapterResourcesLoading = true,
                    chapterResources = emptyList()
                )
            }
            try {
                val resources = repository.listChapterTaggableResources(chapterId, phaseId)
                _uiState.update {
                    it.copy(
                        isChapterResourcesLoading = false,
                        chapterResources = resources
                    )
                }
            } catch (e: Exception) {
                Log.e("SmartNotesVM", "Error loading chapter resources: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        isChapterResourcesLoading = false,
                        errorMessage = "অধ্যায়ের রিসোর্স লোড করতে ব্যর্থ হয়েছে"
                    )
                }
            }
        }
    }

    fun openResourcePdf(
        programId: String? = null,
        phaseId: String? = null,
        subjectCode: String,
        chapterId: String? = null,
        tagId: String,
        isSubjectSpecific: Boolean,
        resourceTitle: String = "পিডিএফ নোট",
        onUrlReady: ((String) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val effectiveProgId = programId?.ifBlank { null }
                ?: _uiState.value.programId.ifBlank { null }
                ?: sessionManager.getActiveProgramId() ?: ""
            val effectivePhaseId = phaseId?.ifBlank { null }
                ?: _uiState.value.phaseId.ifBlank { null }

            _uiState.update {
                it.copy(
                    isOpeningPdf = true,
                    openingTagId = tagId,
                    errorMessage = null
                )
            }

            try {
                val chapterIds = if (isSubjectSpecific || chapterId.isNullOrBlank()) {
                    emptyList()
                } else {
                    listOf(chapterId)
                }

                val attachments = repository.getResourceAttachmentsOfChapter(
                    subjectId = subjectCode,
                    moduleId = effectiveProgId,
                    phaseId = effectivePhaseId,
                    chapterIds = chapterIds,
                    resourceTypeTagIds = listOf(tagId),
                    isSubjectSpecific = isSubjectSpecific
                )

                val attachment = attachments.firstOrNull { !it.url.isNullOrBlank() }
                    ?: attachments.firstOrNull()

                if (attachment?.url != null) {
                    val finalUrl = attachment.url
                    val finalTitle = attachment.title ?: resourceTitle
                    _uiState.update {
                        it.copy(
                            isOpeningPdf = false,
                            openingTagId = null,
                            activeAttachment = attachment
                        )
                    }
                    onUrlReady?.invoke(finalUrl)
                    _events.emit(SmartNotesUiEvent.OpenPdfUrl(finalUrl, finalTitle))
                } else {
                    _uiState.update {
                        it.copy(
                            isOpeningPdf = false,
                            openingTagId = null
                        )
                    }
                    _events.emit(SmartNotesUiEvent.ShowToast("এই রিসোর্সের কোন পিডিএফ পাওয়া যায়নি"))
                }
            } catch (e: Exception) {
                Log.e("SmartNotesVM", "Error opening resource PDF: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        isOpeningPdf = false,
                        openingTagId = null,
                        errorMessage = "পিডিএফ লোড করা যায়নি: ${e.localizedMessage}"
                    )
                }
                _events.emit(SmartNotesUiEvent.ShowToast("পিডিএফ লোড করতে ত্রুটি ঘটেছে"))
            }
        }
    }

    fun dismissActiveAttachment() {
        _uiState.update { it.copy(activeAttachment = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}

class SmartNotesViewModelFactory(
    private val apiService: ShikhoApiService,
    private val sessionManager: SessionManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SmartNotesViewModel::class.java)) {
            return SmartNotesViewModel(apiService, sessionManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
