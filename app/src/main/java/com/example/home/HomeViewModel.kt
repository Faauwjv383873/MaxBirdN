package com.example.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.api.*
import com.example.auth.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val enrolledPrograms: List<EnrolledProgram> = emptyList(),
    val activeProgram: EnrolledProgram? = null,
    val showCourseSwitcher: Boolean = false,
    val userProfile: UserProfile? = null,
    val userName: String = "",
    val userFirstName: String = "",
    val userAvatar: String? = null,
    val userClass: String = "",
    val userGroup: String = "",
    val userSchool: String = "",
    val isPremium: Boolean = false,
    val errorMessage: String? = null
)

class HomeViewModel(
    private val apiService: ShikhoApiService,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        HomeUiState(
            isLoading = true,
            userName = sessionManager.getUserFullName() ?: "শিক্ষার্থী",
            userFirstName = sessionManager.getUserFirstName() ?: (sessionManager.getUserFullName()?.split(" ")?.firstOrNull() ?: "শিক্ষার্থী"),
            userAvatar = sessionManager.getUserAvatar(),
            userClass = sessionManager.getUserClassDisplay() ?: sessionManager.getUserClassName() ?: "একাদশ শ্রেণি",
            userGroup = sessionManager.getUserGroup() ?: "মানবিক",
            userSchool = sessionManager.getUserSchoolName() ?: ""
        )
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun setCourseSwitcherVisible(visible: Boolean) {
        _uiState.value = _uiState.value.copy(showCourseSwitcher = visible)
    }

    fun switchActiveCourse(program: EnrolledProgram) {
        sessionManager.saveActiveProgram(program.id, program.title_bn)
        _uiState.value = _uiState.value.copy(
            activeProgram = program,
            showCourseSwitcher = false
        )
    }

    fun loadData(isRefresh: Boolean = false) {
        viewModelScope.launch {
            if (isRefresh) {
                _uiState.value = _uiState.value.copy(isRefreshing = true, errorMessage = null)
            } else {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            }

            try {
                // 1. Fetch Profile if possible
                fetchUserProfile()

                // 2. Fetch Academic Programs
                fetchAcademicPrograms()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    errorMessage = e.localizedMessage ?: "কোর্স লোড করতে সমস্যা হয়েছে"
                )
            }
        }
    }

    private suspend fun fetchUserProfile() {
        try {
            val userId = sessionManager.getUserId() ?: ""
            val profileQuery = GraphQlQuery(
                operationName = "GetProfile",
                query = "query GetProfile(\$user_id: String, \$type: String!) { profile(user_id: \$user_id, type: \$type) { id first_name last_name avatar gender dob study_group class { code display } school { id name } user { phone email } } }",
                variables = mapOf(
                    "user_id" to userId,
                    "type" to "student"
                )
            )
            val response = apiService.getProfile(profileQuery)
            val profile = response.data?.profile
            if (profile != null) {
                val firstName = profile.first_name?.trim() ?: ""
                val lastName = profile.last_name?.trim() ?: ""
                val fullName = when {
                    firstName.isNotBlank() && lastName.isNotBlank() -> "$firstName $lastName"
                    firstName.isNotBlank() -> firstName
                    lastName.isNotBlank() -> lastName
                    else -> ""
                }
                val first = when {
                    firstName.isNotBlank() -> firstName
                    fullName.isNotBlank() -> fullName.split(" ").firstOrNull() ?: fullName
                    else -> "শিক্ষার্থী"
                }

                sessionManager.saveUserProfile(
                    firstName = firstName.ifBlank { first },
                    lastName = lastName,
                    avatar = profile.avatar,
                    schoolName = profile.school?.name,
                    classDisplay = profile.`class`?.display ?: profile.`class`?.code
                )
                sessionManager.saveUserAcademicInfo(
                    batchId = "HSC 2027",
                    className = profile.`class`?.code ?: "C11",
                    group = profile.study_group ?: "Humanities",
                    vendor = "BD"
                )

                _uiState.value = _uiState.value.copy(
                    userProfile = profile,
                    userName = fullName.ifBlank { first },
                    userFirstName = first,
                    userAvatar = profile.avatar,
                    userClass = profile.`class`?.display ?: profile.`class`?.code ?: "একাদশ শ্রেণি",
                    userGroup = profile.study_group ?: "মানবিক",
                    userSchool = profile.school?.name ?: ""
                )
            }
        } catch (_: Exception) {
            // Profile fetch failed; fallback to cached session values
        }
    }

    private suspend fun fetchAcademicPrograms() {
        val batchId = sessionManager.getUserBatchId() ?: "HSC 2027"
        val className = sessionManager.getUserClassName() ?: "C11"
        val group = sessionManager.getUserGroup() ?: "Humanities"
        val vendor = sessionManager.getUserVendor() ?: "BD"

        val query = GraphQlQuery(
            operationName = "GetAcademicProgram",
            query = """
                query GetAcademicProgram(${'$'}batch_id: String, ${'$'}className: AcademicProgramClassEnum, ${'$'}group: StudyGroupTypeEnum, ${'$'}vendor: VendorEnum, ${'$'}classes: [AcademicProgramClassEnum]) {
                  listAcademicProgramByEnrollment(batch_id: ${'$'}batch_id, class: ${'$'}className, group: ${'$'}group, vendor: ${'$'}vendor, classes: ${'$'}classes) {
                    enrolled_programs {
                      id
                      title_bn
                      banner_url
                      color
                      is_free
                      trial_enabled
                      enrollment_details {
                        batch_id
                        is_active
                        trial_end_date
                        type
                        expiry_date
                      }
                    }
                  }
                }
            """.trimIndent(),
            variables = mapOf(
                "batch_id" to batchId,
                "className" to className,
                "group" to group,
                "vendor" to vendor
            )
        )

        val response = apiService.getAcademicProgram(query)
        val programs = response.data?.listAcademicProgramByEnrollment?.enrolled_programs ?: emptyList()

        // Determine active program
        val savedProgramId = sessionManager.getActiveProgramId()
        val active = programs.firstOrNull { it.id == savedProgramId }
            ?: programs.firstOrNull()

        if (active != null) {
            sessionManager.saveActiveProgram(active.id, active.title_bn)
        }

        val hasActiveEnrollment = active?.enrollment_details?.is_active == true ||
                programs.any { it.enrollment_details?.is_active == true }

        _uiState.value = _uiState.value.copy(
            enrolledPrograms = programs,
            activeProgram = active,
            isPremium = hasActiveEnrollment,
            isLoading = false,
            isRefreshing = false,
            errorMessage = if (programs.isEmpty()) "কোনো সক্রিয় কোর্স পাওয়া যায়নি" else null
        )
    }
}

class HomeViewModelFactory(
    private val apiService: ShikhoApiService,
    private val sessionManager: SessionManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            return HomeViewModel(apiService, sessionManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
