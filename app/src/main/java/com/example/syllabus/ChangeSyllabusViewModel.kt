package com.example.syllabus

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.MainActivity
import com.example.api.*
import com.example.auth.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Utility function to convert English digits to Bengali digits
 */
fun convertToBengaliDigits(input: String?): String {
    if (input == null) return ""
    val bengaliDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
    return input.map { ch ->
        if (ch in '0'..'9') {
            bengaliDigits[ch - '0']
        } else {
            ch
        }
    }.joinToString("")
}

data class ChangeSyllabusUiState(
    val isLoadingClasses: Boolean = false,
    val isLoadingBatches: Boolean = false,
    val isSubmitting: Boolean = false,
    val isSuccess: Boolean = false,
    val classList: List<ClassItem> = emptyList(),
    val selectedClass: ClassItem? = null,
    val selectedGroup: StudyGroupItem? = null,
    val batchOptions: List<BatchOptionItem> = emptyList(),
    val selectedBatch: BatchOptionItem? = null,
    val errorMessage: String? = null,
    val showConfirmBottomSheet: Boolean = false
) {
    val isGroupRequiredForSelectedClass: Boolean
        get() = selectedClass?.isGroupRequired == true

    val isFormValid: Boolean
        get() {
            if (selectedClass == null) return false
            if (isGroupRequiredForSelectedClass && selectedGroup == null) return false
            if (batchOptions.isNotEmpty() && selectedBatch == null) return false
            return true
        }
}

class ChangeSyllabusViewModel(
    private val apiService: ShikhoApiService,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChangeSyllabusUiState(isLoadingClasses = true))
    val uiState: StateFlow<ChangeSyllabusUiState> = _uiState.asStateFlow()

    // Standard available study groups for secondary/higher-secondary
    val standardStudyGroups = listOf(
        StudyGroupItem(code = "Science", name_bn = "বিজ্ঞান", name_en = "Science"),
        StudyGroupItem(code = "Humanities", name_bn = "মানবিক", name_en = "Humanities"),
        StudyGroupItem(code = "Business_Studies", name_bn = "ব্যবসায় শিক্ষা", name_en = "Business Studies")
    )

    init {
        loadClassList()
    }

    /**
     * Step 1: REST GET https://api.shikho.com/class_list?vendor=BD&type=syllabus
     */
    fun loadClassList() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoadingClasses = true,
                errorMessage = null
            )
            try {
                val response = apiService.getClassList(vendor = "BD", type = "syllabus")
                // Filter only valid academic syllabus classes (C5, C6, C7, C8, C9, C10, C11, C12)
                val classes = (response.classes ?: response.data ?: emptyList()).filter { item ->
                    item.is_active == true &&
                    item.show_on_boarding == true &&
                    item.code.matches(Regex("(?i)C\\d+"))
                }

                val currentClassCode = sessionManager.getUserClassName()
                val currentGroupCode = sessionManager.getUserGroup()
                val currentBatchId = sessionManager.getUserBatchId()

                val preselectedClass = classes.find { it.code.equals(currentClassCode, ignoreCase = true) }
                    ?: classes.firstOrNull()

                val availableGroups = if (preselectedClass?.groups.isNullOrEmpty()) standardStudyGroups else preselectedClass?.groups!!
                val preselectedGroup = if (preselectedClass?.isGroupRequired == true) {
                    availableGroups.find {
                        it.code.equals(currentGroupCode, ignoreCase = true) ||
                        it.name_en.equals(currentGroupCode, ignoreCase = true) ||
                        it.title_en.equals(currentGroupCode, ignoreCase = true)
                    } ?: availableGroups.firstOrNull()
                } else {
                    null
                }

                _uiState.value = _uiState.value.copy(
                    isLoadingClasses = false,
                    classList = classes,
                    selectedClass = preselectedClass,
                    selectedGroup = preselectedGroup
                )

                // Fetch batch options for preselected class if available
                preselectedClass?.code?.let { code ->
                    fetchBatchOptions(code, currentBatchId)
                }

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingClasses = false,
                    errorMessage = "ক্লাসের তালিকা লোড করা যায়নি: ${e.localizedMessage ?: "নেটওয়ার্ক সমস্যা"}"
                )
            }
        }
    }

    /**
     * Step 2: Dynamic Batch Options GraphQL Query on selecting class
     */
    fun selectClass(classItem: ClassItem) {
        if (_uiState.value.selectedClass?.code == classItem.code) return

        val availableGroups = if (classItem.groups.isNullOrEmpty()) standardStudyGroups else classItem.groups!!
        val defaultGroup = if (classItem.isGroupRequired) {
            val currentGroupCode = sessionManager.getUserGroup()
            availableGroups.find {
                it.code.equals(currentGroupCode, ignoreCase = true) ||
                it.name_en.equals(currentGroupCode, ignoreCase = true)
            } ?: availableGroups.firstOrNull()
        } else {
            null
        }

        _uiState.value = _uiState.value.copy(
            selectedClass = classItem,
            selectedGroup = defaultGroup,
            selectedBatch = null,
            batchOptions = emptyList(),
            errorMessage = null
        )

        fetchBatchOptions(classItem.code)
    }

    private fun fetchBatchOptions(classCode: String, preferredBatchYearOrLabel: String? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingBatches = true)
            try {
                val query = GraphQlQuery(
                    operationName = "BatchOptions",
                    query = """
                        query BatchOptions(${'$'}classCode: ClassEnumCommon) {
                          batchOptions(classCode: ${'$'}classCode) {
                            classCode
                            date
                            options {
                              year
                              label
                            }
                          }
                        }
                    """.trimIndent(),
                    variables = mapOf("classCode" to classCode)
                )

                val response = apiService.getBatchOptions(query)
                val options = response.data?.batchOptions?.options ?: emptyList()

                val selectedBatch = if (!preferredBatchYearOrLabel.isNullOrBlank()) {
                    options.find {
                        it.yearString == preferredBatchYearOrLabel ||
                        it.label?.contains(preferredBatchYearOrLabel, ignoreCase = true) == true
                    } ?: options.firstOrNull()
                } else {
                    options.firstOrNull()
                }

                _uiState.value = _uiState.value.copy(
                    isLoadingBatches = false,
                    batchOptions = options,
                    selectedBatch = selectedBatch
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingBatches = false
                )
            }
        }
    }

    fun selectGroup(group: StudyGroupItem) {
        _uiState.value = _uiState.value.copy(selectedGroup = group)
    }

    fun selectBatch(batch: BatchOptionItem) {
        _uiState.value = _uiState.value.copy(selectedBatch = batch)
    }

    fun showConfirmationDialog() {
        val state = _uiState.value
        if (!state.isFormValid) {
            _uiState.value = state.copy(errorMessage = "অনুগ্রহ করে সকল তথ্য সঠিকভাবে নির্বাচন করো")
            return
        }
        _uiState.value = state.copy(showConfirmBottomSheet = true, errorMessage = null)
    }

    fun dismissConfirmationDialog() {
        _uiState.value = _uiState.value.copy(showConfirmBottomSheet = false)
    }

    /**
     * Steps 3 & 4: Execute ChangeSyllabus mutation, update tokens, execute UpdateExamYear mutation,
     * clear cache and restart the application cleanly.
     */
    fun applySyllabusChange(context: Context) {
        val state = _uiState.value
        val selectedClass = state.selectedClass ?: return
        val selectedGroup = state.selectedGroup
        val selectedBatch = state.selectedBatch

        viewModelScope.launch {
            _uiState.value = state.copy(
                isSubmitting = true,
                errorMessage = null,
                showConfirmBottomSheet = false
            )

            try {
                // Determine StudyGroupTypeEnum formatted value
                val studyGroupEnum = if (selectedClass.isGroupRequired && selectedGroup != null) {
                    when (selectedGroup.code.lowercase()) {
                        "humanities", "hum", "humanities_group" -> "Humanities"
                        "science", "science_group" -> "Science"
                        "business", "business_studies", "commerce" -> "Business_Studies"
                        else -> selectedGroup.code.replace("-", "_")
                    }
                } else {
                    null
                }

                // 1. ChangeSyllabus Mutation
                val variables = mutableMapOf<String, Any?>("userclass" to selectedClass.code)
                if (studyGroupEnum != null) {
                    variables["study_group"] = studyGroupEnum
                }

                val changeSyllabusQuery = GraphQlQuery(
                    operationName = "ChangeSyllabus",
                    query = """
                        mutation ChangeSyllabus(${'$'}study_group: StudyGroupTypeEnum, ${'$'}userclass: ClassEnumCommon) {
                          changeSyllabus(study_group: ${'$'}study_group, class: ${'$'}userclass) {
                            access_token
                            id_token
                            refresh_token
                          }
                        }
                    """.trimIndent(),
                    variables = variables
                )

                val changeResponse = apiService.changeSyllabus(changeSyllabusQuery)
                val tokenPayload = changeResponse.data?.changeSyllabus

                // 2. Replace JWT tokens in encrypted preferences
                if (tokenPayload?.access_token != null) {
                    sessionManager.updateAuthTokens(
                        accessToken = tokenPayload.access_token,
                        refreshToken = tokenPayload.refresh_token,
                        idToken = tokenPayload.id_token
                    )
                }

                // 3. UpdateExamYear Mutation
                val passingYear = selectedBatch?.yearString ?: ""
                if (passingYear.isNotBlank()) {
                    try {
                        val updateExamQuery = GraphQlQuery(
                            operationName = "UpdateExamYear",
                            query = """
                                mutation UpdateExamYear(${'$'}passing_year: String, ${'$'}type: PrimaryUserTypeEnum!) {
                                  updateProfile(passing_year: ${'$'}passing_year, type: ${'$'}type) {
                                    passing_year
                                  }
                                }
                            """.trimIndent(),
                            variables = mapOf(
                                "passing_year" to passingYear,
                                "type" to "student"
                            )
                        )
                        apiService.updateExamYear(updateExamQuery)
                    } catch (_: Exception) {
                        // Safe fallback
                    }
                }

                // 4. Save Academic Info & Clear Local Program Cache
                sessionManager.saveUserAcademicInfo(
                    batchId = selectedBatch?.label ?: selectedBatch?.yearString ?: "",
                    className = selectedClass.code,
                    group = studyGroupEnum ?: "General",
                    vendor = selectedClass.vendor ?: "BD"
                )

                sessionManager.saveUserProfile(
                    firstName = sessionManager.getUserFirstName(),
                    lastName = "",
                    avatar = sessionManager.getUserAvatar(),
                    schoolName = sessionManager.getUserSchoolName(),
                    classDisplay = selectedClass.displayNameBn
                )

                sessionManager.clearActiveProgram()

                _uiState.value = _uiState.value.copy(
                    isSubmitting = false,
                    isSuccess = true
                )

                // 5. Restart application
                restartApp(context)

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSubmitting = false,
                    errorMessage = "সিলেবাস পরিবর্তন ব্যর্থ হয়েছে: ${e.localizedMessage ?: "সার্ভার এরর"}"
                )
            }
        }
    }

    private fun restartApp(context: Context) {
        val packageManager = context.packageManager
        val intent = packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(context, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }
}

class ChangeSyllabusViewModelFactory(
    private val apiService: ShikhoApiService,
    private val sessionManager: SessionManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChangeSyllabusViewModel::class.java)) {
            return ChangeSyllabusViewModel(apiService, sessionManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
