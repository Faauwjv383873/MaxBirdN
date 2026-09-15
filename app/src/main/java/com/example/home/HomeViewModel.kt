package com.example.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.api.*
import com.example.auth.SessionManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isRoutineLoading: Boolean = false,
    val enrolledPrograms: List<EnrolledProgram> = emptyList(),
    val activeProgram: EnrolledProgram? = null,
    val showCourseSwitcher: Boolean = false,
    val weeklyRoutine: List<StudentLessonItem> = emptyList(),
    val userProfile: UserProfile? = null,
    val userName: String = "",
    val userFirstName: String = "",
    val userAvatar: String? = null,
    val userClass: String = "",
    val userGroup: String = "",
    val userSchool: String = "",
    val isPremium: Boolean = false,
    val errorMessage: String? = null,
    // Subject Filter / Customizer State
    val showSubjectFilterDialog: Boolean = false,
    val courseSubjects: List<AcademicSubjectItem> = emptyList(),
    val isCourseSubjectsLoading: Boolean = false,
    val selectedSubjectCodes: Set<String> = emptySet(),
    val isSavingSubjectFilter: Boolean = false,
    val programPhases: List<PhaseItem> = emptyList()
) {
    /**
     * Filtered weekly routine containing only lessons matching the selected subjects
     * If no subjects are explicitly selected, shows all routine lessons by default.
     */
    val filteredWeeklyRoutine: List<StudentLessonItem>
        get() {
            if (selectedSubjectCodes.isEmpty()) return weeklyRoutine
            return weeklyRoutine.filter { lesson ->
                val code = lesson.subject_id ?: ""
                val name = lesson.subject_name ?: ""
                // Match by subject code, subject_id, or subject name / display_bn
                selectedSubjectCodes.any { selected ->
                    selected.equals(code, ignoreCase = true) ||
                    selected.equals(name, ignoreCase = true) ||
                    courseSubjects.any { sub -> 
                        (sub.code.equals(selected, ignoreCase = true) || sub.display_bn.equals(selected, ignoreCase = true)) &&
                        (sub.code.equals(code, ignoreCase = true) || sub.display_bn.equals(name, ignoreCase = true) || (sub.display_bn != null && name.contains(sub.display_bn, ignoreCase = true)))
                    }
                }
            }
        }
}

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
        // Load saved subject filter for this course if any
        val savedSubjects = sessionManager.getSelectedSubjectCodes(program.id) ?: emptySet()
        val initialSubjects = program.subjects?.map { 
            AcademicSubjectItem(code = it.code, color_code = it.color_code, display_bn = it.display_bn, icon = it.icon)
        } ?: emptyList()

        _uiState.value = _uiState.value.copy(
            activeProgram = program,
            showCourseSwitcher = false,
            selectedSubjectCodes = savedSubjects,
            courseSubjects = initialSubjects
        )
        fetchWeeklyRoutine(program)
        loadCourseSubjects(program)
    }

    fun openSubjectFilterDialog() {
        val program = _uiState.value.activeProgram ?: return
        val saved = sessionManager.getSelectedSubjectCodes(program.id) ?: emptySet()
        _uiState.value = _uiState.value.copy(
            showSubjectFilterDialog = true,
            selectedSubjectCodes = saved
        )
        loadCourseSubjects(program)
    }

    fun dismissSubjectFilterDialog() {
        _uiState.value = _uiState.value.copy(showSubjectFilterDialog = false)
    }

    fun toggleSubjectSelection(subjectCode: String) {
        val current = _uiState.value.selectedSubjectCodes.toMutableSet()
        if (current.contains(subjectCode)) {
            current.remove(subjectCode)
        } else {
            current.add(subjectCode)
        }
        _uiState.value = _uiState.value.copy(selectedSubjectCodes = current)
    }

    fun selectAllSubjects() {
        val allCodes = _uiState.value.courseSubjects.mapNotNull { it.code }.toSet()
        _uiState.value = _uiState.value.copy(selectedSubjectCodes = allCodes)
    }

    fun clearAllSubjectSelection() {
        _uiState.value = _uiState.value.copy(selectedSubjectCodes = emptySet())
    }

    fun saveSubjectFilter() {
        val program = _uiState.value.activeProgram ?: return
        val selected = _uiState.value.selectedSubjectCodes
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSavingSubjectFilter = true)
            // 1. Save locally to SessionManager for instant persistence
            sessionManager.saveSelectedSubjectCodes(program.id, selected)

            // 2. Optionally sync with backend if mutation is supported
            try {
                if (selected.isNotEmpty()) {
                    val upsertQuery = GraphQlQuery(
                        operationName = "UpsertUserPrioritySubjects",
                        query = """
                            mutation UpsertUserPrioritySubjects(${'$'}program_id: String!, ${'$'}subjects: [String]!) {
                              upsertUserPrioritySubjects(academic_program_id: ${'$'}program_id, subjects: ${'$'}subjects) {
                                id
                                academic_program_id
                              }
                            }
                        """.trimIndent(),
                        variables = mapOf(
                            "program_id" to program.id,
                            "subjects" to selected.toList()
                        )
                    )
                    apiService.upsertPrioritySubjects(upsertQuery)
                }
            } catch (_: Exception) {
                // Ignore API error and rely on local storage
            } finally {
                _uiState.value = _uiState.value.copy(
                    isSavingSubjectFilter = false,
                    showSubjectFilterDialog = false
                )
            }
        }
    }

    fun loadCourseSubjects(program: EnrolledProgram) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCourseSubjectsLoading = true)
            val subjectsList = mutableListOf<AcademicSubjectItem>()
            
            // 1. Start with subjects already attached to EnrolledProgram if any
            program.subjects?.let { subs ->
                subjectsList.addAll(subs.map {
                    AcademicSubjectItem(code = it.code, color_code = it.color_code, display_bn = it.display_bn, icon = it.icon)
                })
            }

            // 2. Try fetching full subjects via GetAcademicSubjects
            try {
                val subjectsQuery = GraphQlQuery(
                    operationName = "GetAcademicSubjects",
                    query = """
                        query GetAcademicSubjects(${'$'}programId: String!) {
                          academicProgram(id: ${'$'}programId, show_subject_progress_bar: true) {
                            subjects {
                              code
                              color_code
                              display_bn
                              icon
                            }
                          }
                        }
                    """.trimIndent(),
                    variables = mapOf("programId" to program.id)
                )
                val res = apiService.getAcademicSubjects(subjectsQuery)
                val fetched = res.data?.academicProgram?.subjects
                if (!fetched.isNullOrEmpty()) {
                    subjectsList.clear()
                    subjectsList.addAll(fetched)
                }
            } catch (_: Exception) {}

            // 3. Try fallback to GetPrioritySubjects or SubjectHierarchy
            if (subjectsList.isEmpty()) {
                try {
                    val pQuery = GraphQlQuery(
                        operationName = "UserPrioritySubjects",
                        query = """
                            query UserPrioritySubjects(${'$'}academic_program_id: String!) {
                              userPrioritySubjects(academic_program_id: ${'$'}academic_program_id) {
                                subjects {
                                  code
                                  color_code
                                  display
                                  display_bn
                                  icon
                                }
                              }
                            }
                        """.trimIndent(),
                        variables = mapOf("academic_program_id" to program.id)
                    )
                    val pRes = apiService.getPrioritySubjects(pQuery)
                    val pSubs = pRes.data?.userPrioritySubjects?.subjects
                    if (!pSubs.isNullOrEmpty()) {
                        pSubs.forEach { p ->
                            subjectsList.add(AcademicSubjectItem(code = p.code, color_code = p.color_code, display_bn = p.display_bn ?: p.display, icon = p.icon))
                        }
                    }
                } catch (_: Exception) {}
            }

            // Deduplicate by code
            val distinctSubjects = subjectsList.distinctBy { it.code ?: it.display_bn }
            
            // Load saved preference
            val savedSelection = sessionManager.getSelectedSubjectCodes(program.id) ?: emptySet()

            _uiState.value = _uiState.value.copy(
                courseSubjects = distinctSubjects,
                isCourseSubjectsLoading = false,
                selectedSubjectCodes = if (savedSelection.isNotEmpty()) savedSelection else _uiState.value.selectedSubjectCodes
            )
        }
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
                
                // 3. Fetch Weekly Routine
                val active = _uiState.value.activeProgram
                if (active != null) {
                    fetchWeeklyRoutine(active)
                }
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
            "businessstudies" -> "BusinessStudies"
            "commerce" -> "Commerce"
            "business" -> "Business"
            "business_studies", "business studies" -> "Business_Studies"
            else -> group
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
                        other_programs {
                          id
                          classes
                          title_bn
                          banner_url
                          phase_pricing
                          is_free
                          has_animated_video
                          full_program_discount_price
                          trial_enabled
                          trial_duration
                        }
                      }
                    }
                """.trimIndent()
            )
            val response = apiService.getAcademicProgram(allEnrollmentsQuery)
            val list = response.data?.listAcademicProgramByEnrollment?.enrolled_programs ?: emptyList()
            val otherList = response.data?.listAcademicProgramByEnrollment?.other_programs ?: emptyList()
            val promoted = otherList.map { it.toEnrolledProgram() }
            val merged = (list + promoted).distinctBy { it.id }
            if (merged.isNotEmpty()) {
                programs = merged
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
                            other_programs {
                              id
                              classes
                              title_bn
                              banner_url
                              phase_pricing
                              is_free
                              has_animated_video
                              full_program_discount_price
                              trial_enabled
                              trial_duration
                            }
                          }
                        }
                    """.trimIndent(),
                    variables = mutableMapOf<String, Any?>(
                        "batch_id" to batchId,
                        "className" to className,
                        "group" to null, // Fetch ALL groups under this class/batch
                        "vendor" to vendor
                    )
                )
                val response = apiService.getAcademicProgram(queryWithBatch)
                val list = response.data?.listAcademicProgramByEnrollment?.enrolled_programs ?: emptyList()
                val otherList = response.data?.listAcademicProgramByEnrollment?.other_programs ?: emptyList()
                val promoted = otherList.map { it.toEnrolledProgram() }
                programs = (list + promoted).distinctBy { it.id }
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
                            other_programs {
                              id
                              classes
                              title_bn
                              banner_url
                              phase_pricing
                              is_free
                              has_animated_video
                              full_program_discount_price
                              trial_enabled
                              trial_duration
                            }
                          }
                        }
                    """.trimIndent(),
                    variables = mapOf(
                        "className" to className,
                        "group" to null, // Fetch ALL groups under this class
                        "vendor" to vendor
                    )
                )
                val fallbackResponse = apiService.getAcademicProgram(fallbackQuery)
                val list = fallbackResponse.data?.listAcademicProgramByEnrollment?.enrolled_programs ?: emptyList()
                val otherList = fallbackResponse.data?.listAcademicProgramByEnrollment?.other_programs ?: emptyList()
                val promoted = otherList.map { it.toEnrolledProgram() }
                programs = (list + promoted).distinctBy { it.id }
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
                            other_programs {
                              id
                              classes
                              title_bn
                              banner_url
                              phase_pricing
                              is_free
                              has_animated_video
                              full_program_discount_price
                              trial_enabled
                              trial_duration
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
                val list = classOnlyResponse.data?.listAcademicProgramByEnrollment?.enrolled_programs ?: emptyList()
                val otherList = classOnlyResponse.data?.listAcademicProgramByEnrollment?.other_programs ?: emptyList()
                val promoted = otherList.map { it.toEnrolledProgram() }
                programs = (list + promoted).distinctBy { it.id }
            } catch (_: Exception) {
                // Final fallback
            }
        }

        // Filter/Prioritize programs based on user group so that only the selected department/group and common/skills are shown
        val groupLower = group.lowercase()
        val keywords = when {
            groupLower.contains("science") || groupLower.contains("বিজ্ঞান") -> listOf("বিজ্ঞান", "science")
            groupLower.contains("humanities") || groupLower.contains("মানবিক") || groupLower.contains("hum") -> listOf("মানবিক", "humanities")
            groupLower.contains("business") || groupLower.contains("commerce") || groupLower.contains("ব্যবসায়") || groupLower.contains("ব্যাবসা") -> listOf("ব্যবসায়", "ব্যাবসা", "business", "commerce", "studies")
            else -> emptyList()
        }

        val otherKeywords = when {
            groupLower.contains("science") || groupLower.contains("বিজ্ঞান") -> listOf("মানবিক", "humanities", "ব্যবসায়", "ব্যাবসা", "business", "commerce", "studies")
            groupLower.contains("humanities") || groupLower.contains("মানবিক") || groupLower.contains("hum") -> listOf("বিজ্ঞান", "science", "ব্যবসায়", "ব্যাবসা", "business", "commerce")
            groupLower.contains("business") || groupLower.contains("commerce") || groupLower.contains("ব্যবসায়") || groupLower.contains("ব্যাবসা") -> listOf("বিজ্ঞান", "science", "মানবিক", "humanities")
            else -> emptyList()
        }

        programs = programs.filter { program ->
            val t = program.title_bn?.lowercase() ?: ""
            var matchesOther = false
            for (okw in otherKeywords) {
                if (t.contains(okw)) {
                    matchesOther = true
                    break
                }
            }
            !matchesOther
        }.sortedWith(compareByDescending { program ->
            val title = program.title_bn?.lowercase() ?: ""
            var score = 0
            for (kw in keywords) {
                if (title.contains(kw)) {
                    score += 10
                }
            }
            if (title.contains("কমন") || title.contains("common") || title.contains("আবশ্যিক")) {
                score += 5
            }
            score
        })

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

        val activeSavedSubjects = if (active != null) sessionManager.getSelectedSubjectCodes(active.id) ?: emptySet() else emptySet()
        val activeInitialSubjects = active?.subjects?.map {
            AcademicSubjectItem(code = it.code, color_code = it.color_code, display_bn = it.display_bn, icon = it.icon)
        } ?: emptyList()

        _uiState.value = _uiState.value.copy(
            enrolledPrograms = programs,
            activeProgram = active,
            selectedSubjectCodes = activeSavedSubjects,
            courseSubjects = activeInitialSubjects,
            isPremium = hasActiveEnrollment,
            isLoading = false,
            isRefreshing = false,
            errorMessage = if (programs.isEmpty()) "কোনো সক্রিয় কোর্স পাওয়া যায়নি" else null
        )

        if (active != null) {
            loadCourseSubjects(active)
        }
    }
    fun fetchMonthlyRoutine(
        year: Int,
        monthZeroIndexed: Int,
        program: EnrolledProgram? = _uiState.value.activeProgram
    ) {
        val targetProgram = program ?: return
        fetchRoutineForMonth(targetProgram, year, monthZeroIndexed)
    }

    private fun fetchWeeklyRoutine(program: EnrolledProgram) {
        val dhakaZone = TimeZone.getTimeZone("Asia/Dhaka")
        val now = Calendar.getInstance(dhakaZone)
        fetchRoutineForMonth(program, now.get(Calendar.YEAR), now.get(Calendar.MONTH))
    }

    private fun fetchRoutineForMonth(
        program: EnrolledProgram,
        year: Int,
        monthZeroIndexed: Int
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRoutineLoading = true)
            try {
                val programId = program.id
                var fetchedPhases = emptyList<PhaseItem>()

                // 1. Fetch active and all phases for the selected program (Science, Business, Humanities, etc.)
                try {
                    val phaseQuery = GraphQlQuery(
                        operationName = "ProgramPhasesByStudent",
                        query = """
                            query ProgramPhasesByStudent(${'$'}program_id: String!) {
                              programPhasesByStudent(program_id: ${'$'}program_id) {
                                data {
                                  id
                                  academic_program_id
                                  title
                                  status
                                  is_current
                                  has_enrolment
                                  has_free_trial_enrolment
                                  course_progress_percentage
                                  start_date
                                  end_date
                                }
                              }
                            }
                        """.trimIndent(),
                        variables = mapOf("program_id" to programId)
                    )
                    val phaseRes = apiService.getProgramPhases(phaseQuery)
                    fetchedPhases = phaseRes.data?.programPhasesByStudent?.data ?: emptyList()
                    if (fetchedPhases.isNotEmpty()) {
                        _uiState.value = _uiState.value.copy(programPhases = fetchedPhases)
                    }
                } catch (_: Exception) {}

                // Fallback: fetch programPhases if programPhasesByStudent returned empty
                if (fetchedPhases.isEmpty()) {
                    try {
                        val fallbackPhaseQuery = GraphQlQuery(
                            operationName = "GetProgramPhases",
                            query = """
                                query GetProgramPhases(${'$'}program_id: String!) {
                                  programPhases(program_id: ${'$'}program_id) {
                                    data {
                                      id
                                      academic_program_id
                                      title
                                      status
                                      is_current
                                    }
                                  }
                                }
                            """.trimIndent(),
                            variables = mapOf("program_id" to programId)
                        )
                        val fallbackRes = apiService.getProgramPhases(fallbackPhaseQuery)
                        fetchedPhases = fallbackRes.data?.programPhases?.data ?: emptyList()
                        if (fetchedPhases.isNotEmpty()) {
                            _uiState.value = _uiState.value.copy(programPhases = fetchedPhases)
                        }
                    } catch (_: Exception) {}
                }

                // Calculate exact month range in Asia/Dhaka (1st day 00:00:00 to last day 23:59:59)
                val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Dhaka"))
                cal.set(Calendar.YEAR, year)
                cal.set(Calendar.MONTH, monthZeroIndexed)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)

                val startCal = cal.clone() as Calendar

                val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                cal.set(Calendar.DAY_OF_MONTH, maxDay)
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                val endCal = cal.clone() as Calendar

                val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }

                val startDate = dateFormat.format(startCal.time)
                val endDate = dateFormat.format(endCal.time)

                val collectedLessons = mutableListOf<StudentLessonItem>()

                // 2. Query lessons for each phase of this course
                val validPhases = fetchedPhases.mapNotNull { it.id.ifBlank { null } }.distinct()
                if (validPhases.isNotEmpty()) {
                    for (phaseId in validPhases) {
                        try {
                            val query = GraphQlQuery(
                                operationName = "GetStudentSpecificLessons",
                                query = """
                                    query GetStudentSpecificLessons(${'$'}program_id: String!, ${'$'}phase_id: String!, ${'$'}start_date: String!, ${'$'}end_date: String!) {
                                      studentSpecificLessons(program_id: ${'$'}program_id, phase_id: ${'$'}phase_id, start_date: ${'$'}start_date, end_date: ${'$'}end_date) {
                                        data {
                                          access_level
                                          hw_type
                                          start_time
                                          subject_id
                                          subject_name
                                          title
                                          end_time
                                          icon
                                          id
                                          content_id
                                          content_type
                                          user_activity_state
                                          batch_id
                                          chapter_id
                                          live_class {
                                            chapter_id
                                            chapter_name
                                            end_time
                                            is_on_going
                                            recording_url
                                            start_time
                                            subject_name
                                            subject_id
                                            id
                                            type
                                          }
                                          model_test {
                                            result_publish_time
                                            type
                                            exam_category
                                          }
                                          topics {
                                            id
                                            name
                                          }
                                          color_code
                                          phase_id
                                        }
                                      }
                                    }
                                """.trimIndent(),
                                variables = mapOf(
                                    "program_id" to programId,
                                    "phase_id" to phaseId,
                                    "start_date" to startDate,
                                    "end_date" to endDate
                                )
                            )
                            val response = apiService.getStudentLessons(query)
                            val phaseLessons = response.data?.studentSpecificLessons?.data ?: emptyList()
                            collectedLessons.addAll(phaseLessons)
                        } catch (_: Exception) {}
                    }
                }

                // 3. If still empty or no phases found, query without phase_id filter
                if (collectedLessons.isEmpty()) {
                    try {
                        val noPhaseQuery = GraphQlQuery(
                            operationName = "GetStudentSpecificLessonsNoPhase",
                            query = """
                                query GetStudentSpecificLessonsNoPhase(${'$'}program_id: String!, ${'$'}start_date: String!, ${'$'}end_date: String!) {
                                  studentSpecificLessons(program_id: ${'$'}program_id, start_date: ${'$'}start_date, end_date: ${'$'}end_date) {
                                    data {
                                      access_level
                                      hw_type
                                      start_time
                                      subject_id
                                      subject_name
                                      title
                                      end_time
                                      icon
                                      id
                                      content_id
                                      content_type
                                      user_activity_state
                                      batch_id
                                      chapter_id
                                      live_class {
                                        chapter_id
                                        chapter_name
                                        end_time
                                        is_on_going
                                        recording_url
                                        start_time
                                        subject_name
                                        subject_id
                                        id
                                        type
                                      }
                                      model_test {
                                        result_publish_time
                                        type
                                        exam_category
                                      }
                                      topics {
                                        id
                                        name
                                      }
                                      color_code
                                      phase_id
                                    }
                                  }
                                }
                            """.trimIndent(),
                            variables = mapOf(
                                "program_id" to programId,
                                "start_date" to startDate,
                                "end_date" to endDate
                            )
                        )
                        val response = apiService.getStudentLessons(noPhaseQuery)
                        val directLessons = response.data?.studentSpecificLessons?.data ?: emptyList()
                        collectedLessons.addAll(directLessons)
                    } catch (_: Exception) {}
                }

                // 4. If still empty, query without date range restriction across all phases
                if (collectedLessons.isEmpty() && validPhases.isNotEmpty()) {
                    for (phaseId in validPhases) {
                        try {
                            val allPhaseLessonsQuery = GraphQlQuery(
                                operationName = "GetStudentSpecificLessonsPhaseAll",
                                query = """
                                    query GetStudentSpecificLessonsPhaseAll(${'$'}program_id: String!, ${'$'}phase_id: String!) {
                                      studentSpecificLessons(program_id: ${'$'}program_id, phase_id: ${'$'}phase_id) {
                                        data {
                                          access_level
                                          hw_type
                                          start_time
                                          subject_id
                                          subject_name
                                          title
                                          end_time
                                          icon
                                          id
                                          content_id
                                          content_type
                                          user_activity_state
                                          batch_id
                                          chapter_id
                                          live_class {
                                            chapter_id
                                            chapter_name
                                            end_time
                                            is_on_going
                                            recording_url
                                            start_time
                                            subject_name
                                            subject_id
                                            id
                                            type
                                          }
                                          model_test {
                                            result_publish_time
                                            type
                                            exam_category
                                          }
                                          topics {
                                            id
                                            name
                                          }
                                          color_code
                                          phase_id
                                        }
                                      }
                                    }
                                """.trimIndent(),
                                variables = mapOf(
                                    "program_id" to programId,
                                    "phase_id" to phaseId
                                )
                            )
                            val response = apiService.getStudentLessons(allPhaseLessonsQuery)
                            val phaseLessons = response.data?.studentSpecificLessons?.data ?: emptyList()
                            collectedLessons.addAll(phaseLessons)
                        } catch (_: Exception) {}
                    }
                }

                // 5. If still empty, query without phase_id AND without date range
                if (collectedLessons.isEmpty()) {
                    try {
                        val noPhaseNoDateQuery = GraphQlQuery(
                            operationName = "GetStudentSpecificLessonsNoPhaseAll",
                            query = """
                                query GetStudentSpecificLessonsNoPhaseAll(${'$'}program_id: String!) {
                                  studentSpecificLessons(program_id: ${'$'}program_id) {
                                    data {
                                      access_level
                                      hw_type
                                      start_time
                                      subject_id
                                      subject_name
                                      title
                                      end_time
                                      icon
                                      id
                                      content_id
                                      content_type
                                      user_activity_state
                                      batch_id
                                      chapter_id
                                      live_class {
                                        chapter_id
                                        chapter_name
                                        end_time
                                        is_on_going
                                        recording_url
                                        start_time
                                        subject_name
                                        subject_id
                                        id
                                        type
                                      }
                                      model_test {
                                        result_publish_time
                                        type
                                        exam_category
                                      }
                                      topics {
                                        id
                                        name
                                      }
                                      color_code
                                      phase_id
                                    }
                                  }
                                }
                            """.trimIndent(),
                            variables = mapOf("program_id" to programId)
                        )
                        val response = apiService.getStudentLessons(noPhaseNoDateQuery)
                        val directLessons = response.data?.studentSpecificLessons?.data ?: emptyList()
                        collectedLessons.addAll(directLessons)
                    } catch (_: Exception) {}
                }

                // Deduplicate by ID and sort chronologically
                val distinctLessons = collectedLessons
                    .distinctBy { it.id.ifBlank { "${it.content_id}_${it.start_time}" } }
                    .sortedBy { it.start_time ?: "" }

                _uiState.value = _uiState.value.copy(
                    weeklyRoutine = distinctLessons,
                    isRoutineLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isRoutineLoading = false)
            }
        }
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
