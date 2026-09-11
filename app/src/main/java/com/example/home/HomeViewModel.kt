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
        sessionManager.saveActiveProgram(
            programId = program.id,
            titleBn = program.title_bn,
            batchId = program.enrollment_details?.batch_id,
            classCode = program.classes?.firstOrNull()
        )
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

                val currentBatchId = sessionManager.getUserBatchId()
                val profileClass = profile.`class`?.code ?: sessionManager.getUserClassName() ?: "C11"
                val profileGroup = profile.study_group ?: sessionManager.getUserGroup() ?: "Humanities"

                sessionManager.saveUserProfile(
                    firstName = firstName.ifBlank { first },
                    lastName = lastName,
                    avatar = profile.avatar,
                    schoolName = profile.school?.name,
                    classDisplay = profile.`class`?.display ?: profile.`class`?.code
                )
                sessionManager.saveUserAcademicInfo(
                    batchId = currentBatchId ?: "HSC 2027",
                    className = profileClass,
                    group = profileGroup,
                    vendor = "BD"
                )

                _uiState.value = _uiState.value.copy(
                    userProfile = profile,
                    userName = fullName.ifBlank { first },
                    userFirstName = first,
                    userAvatar = profile.avatar,
                    userClass = profile.`class`?.display ?: profile.`class`?.code ?: "একাদশ শ্রেণি",
                    userGroup = profileGroup,
                    userSchool = profile.school?.name ?: ""
                )
            }
        } catch (_: Exception) {
            // Profile fetch failed; fallback to cached session values
        }
    }

    private suspend fun fetchAcademicPrograms() {
        val batchId = sessionManager.getActiveProgramBatchId() ?: sessionManager.getUserBatchId()
        val className = sessionManager.getActiveProgramClassCode() ?: sessionManager.getUserClassName() ?: "C11"
        val group = sessionManager.getUserGroup() ?: "Humanities"
        val vendor = sessionManager.getUserVendor() ?: "BD"

        // Map group to standard GraphQL enum formatting
        val formattedGroup = when (group.lowercase()) {
            "humanities", "humanities_group" -> "Humanities"
            "science", "science_group" -> "Science"
            "business", "business_studies", "commerce" -> "Business_Studies"
            else -> group.replace("-", "_")
        }

        var programs: List<EnrolledProgram> = emptyList()

        // 0. Primary query: List all user enrollments directly without narrow restrictive filters
        try {
            val allEnrollmentsQuery = GraphQlQuery(
                operationName = "GetAcademicProgram",
                query = """
                    query GetAcademicProgram {
                      listAcademicProgramByEnrollment {
                        enrolled_programs {
                          id
                          classes
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
                          subjects {
                            code
                            display_bn
                            color_code
                            icon
                          }
                        }
                      }
                    }
                """.trimIndent()
            )
            val response = apiService.getAcademicProgram(allEnrollmentsQuery)
            val list = response.data?.listAcademicProgramByEnrollment?.enrolled_programs
            if (!list.isNullOrEmpty()) {
                programs = list
            }
        } catch (_: Exception) {}

        // 1. Try querying with batch_id if available and no programs found yet
        if (programs.isEmpty() && !batchId.isNullOrBlank()) {
            try {
                val queryWithBatch = GraphQlQuery(
                    operationName = "GetAcademicProgram",
                    query = """
                        query GetAcademicProgram(${'$'}batch_id: String, ${'$'}className: AcademicProgramClassEnum, ${'$'}group: StudyGroupTypeEnum, ${'$'}vendor: VendorEnum, ${'$'}classes: [AcademicProgramClassEnum]) {
                          listAcademicProgramByEnrollment(batch_id: ${'$'}batch_id, class: ${'$'}className, group: ${'$'}group, vendor: ${'$'}vendor, classes: ${'$'}classes) {
                            enrolled_programs {
                              id
                              classes
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
                              subjects {
                                code
                                display_bn
                                color_code
                                icon
                              }
                            }
                          }
                        }
                    """.trimIndent(),
                    variables = mutableMapOf<String, Any?>(
                        "batch_id" to batchId,
                        "className" to className,
                        "group" to formattedGroup,
                        "vendor" to vendor
                    )
                )
                val response = apiService.getAcademicProgram(queryWithBatch)
                programs = response.data?.listAcademicProgramByEnrollment?.enrolled_programs ?: emptyList()
            } catch (_: Exception) {
                // Ignore and try fallback without batch_id
            }
        }

        // 2. Fallback query without batch_id if programs are empty
        if (programs.isEmpty()) {
            try {
                val fallbackQuery = GraphQlQuery(
                    operationName = "GetAcademicProgram",
                    query = """
                        query GetAcademicProgram(${'$'}className: AcademicProgramClassEnum, ${'$'}group: StudyGroupTypeEnum, ${'$'}vendor: VendorEnum, ${'$'}classes: [AcademicProgramClassEnum]) {
                          listAcademicProgramByEnrollment(class: ${'$'}className, group: ${'$'}group, vendor: ${'$'}vendor, classes: ${'$'}classes) {
                            enrolled_programs {
                              id
                              classes
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
                              subjects {
                                code
                                display_bn
                                color_code
                                icon
                              }
                            }
                          }
                        }
                    """.trimIndent(),
                    variables = mapOf(
                        "className" to className,
                        "group" to formattedGroup,
                        "vendor" to vendor
                    )
                )
                val fallbackResponse = apiService.getAcademicProgram(fallbackQuery)
                programs = fallbackResponse.data?.listAcademicProgramByEnrollment?.enrolled_programs ?: emptyList()
            } catch (_: Exception) {
                // Ignore and proceed to class-only fallback
            }
        }

        // 3. Fallback query with class only if still empty
        if (programs.isEmpty()) {
            try {
                val classOnlyQuery = GraphQlQuery(
                    operationName = "GetAcademicProgram",
                    query = """
                        query GetAcademicProgram(${'$'}className: AcademicProgramClassEnum, ${'$'}vendor: VendorEnum) {
                          listAcademicProgramByEnrollment(class: ${'$'}className, vendor: ${'$'}vendor) {
                            enrolled_programs {
                              id
                              classes
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
                        "className" to className,
                        "vendor" to vendor
                    )
                )
                val classOnlyResponse = apiService.getAcademicProgram(classOnlyQuery)
                programs = classOnlyResponse.data?.listAcademicProgramByEnrollment?.enrolled_programs ?: emptyList()
            } catch (_: Exception) {
                // Final fallback
            }
        }

        // Determine active program: prioritize saved program, then actively enrolled program (is_active == true), then first
        val savedProgramId = sessionManager.getActiveProgramId()
        val active = programs.firstOrNull { it.id == savedProgramId }
            ?: programs.firstOrNull { it.enrollment_details?.is_active == true }
            ?: programs.firstOrNull()

        if (active != null) {
            sessionManager.saveActiveProgram(
                programId = active.id,
                titleBn = active.title_bn,
                batchId = active.enrollment_details?.batch_id,
                classCode = active.classes?.firstOrNull()
            )
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
