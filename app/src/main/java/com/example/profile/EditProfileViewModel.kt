package com.example.profile

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
import java.text.SimpleDateFormat
import java.util.*

data class EditProfileUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,

    // Account & Basic Info (from Profile)
    val userId: String = "",
    val phone: String = "",
    val email: String = "",
    val avatarUrl: String? = null,
    val userClassDisplay: String = "",
    val userClassCode: String = "",
    val userGroup: String = "",
    val passingYear: String = "",

    // Form fields
    val firstName: String = "",
    val gender: String = "Male", // "Male" or "Female"
    val dobIso: String = "",     // e.g. "2008-01-16T00:00:00Z"
    val dobDisplay: String = "", // e.g. "16 January, 2008"
    val guardianName: String = "",
    val guardianMobile: String = "",

    // Board details
    val sscBoardName: String = "Dhaka",
    val sscRollNumber: String = "",
    val boardRegNumber: String = "",
    val hscBoardName: String = "Dhaka",
    val hscRollNumber: String = "",
    val shift: String = "NA", // "Morning", "Day", "NA"

    // Institution / School Selection
    val selectedDivisionCode: String = "",
    val selectedDivisionName: String = "",
    val selectedDistrictCode: String = "",
    val selectedDistrictName: String = "",
    val selectedSchoolId: String = "",
    val selectedSchoolName: String = "",

    // Divisions & Districts list from API
    val divisions: List<AddressItem> = emptyList(),
    val districts: List<AddressItem> = emptyList(),
    val isDistrictsLoading: Boolean = false,

    // School search
    val schoolSearchResults: List<SchoolItem> = emptyList(),
    val isSchoolSearching: Boolean = false,
    val showSchoolSearchDialog: Boolean = false
)

class EditProfileViewModel(
    private val apiService: ShikhoApiService,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditProfileUiState())
    val uiState: StateFlow<EditProfileUiState> = _uiState.asStateFlow()

    private var schoolSearchJob: Job? = null

    val educationBoards = listOf(
        "Dhaka", "Chattogram", "Rajshahi", "Cumilla",
        "Jessore", "Barishal", "Sylhet", "Dinajpur",
        "Mymensingh", "Madrasah", "Technical", "Other"
    )

    init {
        loadInitialData()
    }

    fun loadInitialData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val userId = sessionManager.getUserId() ?: ""
                
                // 1. Fetch Profile
                val profileQuery = GraphQlQuery(
                    query = """
                        query GetProfile(${'$'}user_id: String, ${'$'}type: String!) {
                          profile(user_id: ${'$'}user_id, type: ${'$'}type) {
                            first_name
                            last_name
                            avatar
                            gender
                            dob
                            shift
                            guardian_name
                            guardian_mobile
                            ssc_board_name
                            hsc_board_name
                            board_roll_number
                            hsc_board_roll_number
                            board_reg_number
                            study_group
                            passing_year
                            class {
                              code
                              display
                            }
                            school {
                              id
                              name
                              address {
                                district {
                                  code
                                  display
                                }
                                division {
                                  code
                                  display
                                }
                              }
                            }
                            user {
                              email
                              phone
                            }
                          }
                        }
                    """.trimIndent(),
                    operationName = "GetProfile",
                    variables = mapOf("user_id" to userId, "type" to "student")
                )

                val profileRes = apiService.getProfile(profileQuery)
                val profile = profileRes.data?.profile

                // 2. Fetch Divisions
                val divisionsRes = try {
                    apiService.getAddress(countryCode = "BD")
                } catch (_: Exception) {
                    null
                }
                val divisionsList = divisionsRes?.body ?: emptyList()

                val rawDob = profile?.dob ?: ""
                val formattedDob = formatIsoDateToDisplay(rawDob)

                val genderVal = when (profile?.gender?.uppercase()) {
                    "FEMALE", "F" -> "Female"
                    else -> "Male"
                }

                val school = profile?.school
                val schoolAddress = school?.address
                val initialDivCode = schoolAddress?.division?.code ?: ""
                val initialDivName = schoolAddress?.division?.display ?: ""
                val initialDistCode = schoolAddress?.district?.code ?: ""
                val initialDistName = schoolAddress?.district?.display ?: ""
                val initialSchoolId = school?.id ?: ""
                val initialSchoolName = school?.name ?: ""

                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        userId = userId,
                        phone = profile?.user?.phone ?: "",
                        email = profile?.user?.email ?: "",
                        avatarUrl = profile?.avatar ?: sessionManager.getUserAvatar(),
                        userClassDisplay = profile?.`class`?.display ?: sessionManager.getUserClassDisplay() ?: "",
                        userClassCode = profile?.`class`?.code ?: sessionManager.getUserClassName() ?: "C11",
                        userGroup = profile?.study_group ?: sessionManager.getUserGroup() ?: "",
                        passingYear = profile?.passing_year ?: sessionManager.getUserBatchId() ?: "",

                        firstName = profile?.first_name ?: sessionManager.getUserFirstName() ?: "",
                        gender = genderVal,
                        dobIso = rawDob,
                        dobDisplay = formattedDob,
                        guardianName = profile?.guardian_name ?: "",
                        guardianMobile = profile?.guardian_mobile ?: "",

                        sscBoardName = profile?.ssc_board_name?.ifBlank { "Dhaka" } ?: "Dhaka",
                        sscRollNumber = profile?.board_roll_number ?: "",
                        boardRegNumber = profile?.board_reg_number ?: "",
                        hscBoardName = profile?.hsc_board_name?.ifBlank { "Dhaka" } ?: "Dhaka",
                        hscRollNumber = profile?.hsc_board_roll_number ?: "",
                        shift = profile?.shift?.ifBlank { "NA" } ?: "NA",

                        selectedDivisionCode = initialDivCode,
                        selectedDivisionName = initialDivName,
                        selectedDistrictCode = initialDistCode,
                        selectedDistrictName = initialDistName,
                        selectedSchoolId = initialSchoolId,
                        selectedSchoolName = initialSchoolName,

                        divisions = divisionsList
                    )
                }

                // If initial division exists, load its districts
                if (initialDivCode.isNotBlank()) {
                    loadDistricts(initialDivCode)
                }

            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "প্রোফাইল তথ্য লোড করা যায়নি: ${e.localizedMessage ?: "নেটওয়ার্ক সমস্যা"}") }
            }
        }
    }

    fun onAvatarSelected(url: String?) {
        _uiState.update { it.copy(avatarUrl = url) }
    }

    fun onAvatarRemoved() {
        _uiState.update { it.copy(avatarUrl = null) }
    }

    fun onFirstNameChange(name: String) {
        _uiState.update { it.copy(firstName = name) }
    }

    fun onGenderChange(gender: String) {
        _uiState.update { it.copy(gender = gender) }
    }

    fun onDobSelected(year: Int, monthZeroIndexed: Int, dayOfMonth: Int) {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, monthZeroIndexed)
            set(Calendar.DAY_OF_MONTH, dayOfMonth)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val displayFormat = SimpleDateFormat("dd MMMM, yyyy", Locale.US)

        val iso = isoFormat.format(cal.time)
        val display = displayFormat.format(cal.time)

        _uiState.update { it.copy(dobIso = iso, dobDisplay = display) }
    }

    fun onGuardianNameChange(name: String) {
        _uiState.update { it.copy(guardianName = name) }
    }

    fun onGuardianMobileChange(mobile: String) {
        _uiState.update { it.copy(guardianMobile = mobile) }
    }

    fun onSscBoardChange(board: String) {
        _uiState.update { it.copy(sscBoardName = board) }
    }

    fun onSscRollChange(roll: String) {
        _uiState.update { it.copy(sscRollNumber = roll) }
    }

    fun onBoardRegChange(reg: String) {
        _uiState.update { it.copy(boardRegNumber = reg) }
    }

    fun onHscBoardChange(board: String) {
        _uiState.update { it.copy(hscBoardName = board) }
    }

    fun onHscRollChange(roll: String) {
        _uiState.update { it.copy(hscRollNumber = roll) }
    }

    fun onShiftChange(shift: String) {
        _uiState.update { it.copy(shift = shift) }
    }

    fun onSelectDivision(item: AddressItem) {
        val divCode = item.code ?: ""
        val divName = item.display ?: ""
        _uiState.update {
            it.copy(
                selectedDivisionCode = divCode,
                selectedDivisionName = divName,
                selectedDistrictCode = "",
                selectedDistrictName = "",
                selectedSchoolId = "",
                selectedSchoolName = "",
                districts = emptyList()
            )
        }
        if (divCode.isNotBlank()) {
            loadDistricts(divCode)
        }
    }

    fun onSelectDistrict(item: AddressItem) {
        val distCode = item.code ?: ""
        val distName = item.display ?: ""
        _uiState.update {
            it.copy(
                selectedDistrictCode = distCode,
                selectedDistrictName = distName,
                selectedSchoolId = "",
                selectedSchoolName = ""
            )
        }
    }

    private fun loadDistricts(divisionId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDistrictsLoading = true) }
            try {
                val res = apiService.getAddress(countryCode = "", divisionId = divisionId)
                val list = res.body ?: emptyList()
                _uiState.update { it.copy(districts = list, isDistrictsLoading = false) }
            } catch (_: Exception) {
                _uiState.update { it.copy(isDistrictsLoading = false) }
            }
        }
    }

    fun setSchoolSearchDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(showSchoolSearchDialog = visible) }
        if (visible) {
            searchSchools("")
        }
    }

    fun searchSchools(query: String) {
        schoolSearchJob?.cancel()
        schoolSearchJob = viewModelScope.launch {
            delay(150)
            _uiState.update { it.copy(isSchoolSearching = true) }
            try {
                val state = _uiState.value
                val schoolQuery = GraphQlQuery(
                    query = """
                        query GetSchools(${'$'}district: String, ${'$'}division: String, ${'$'}limit: Int, ${'$'}offset: Int, ${'$'}name: String) {
                          searchSchoolV1(district: ${'$'}district, division: ${'$'}division, limit: ${'$'}limit, offset: ${'$'}offset, name: ${'$'}name) {
                            data {
                              id
                              name
                            }
                          }
                        }
                    """.trimIndent(),
                    operationName = "GetSchools",
                    variables = mapOf(
                        "district" to state.selectedDistrictCode.ifBlank { null },
                        "division" to state.selectedDivisionCode.ifBlank { null },
                        "limit" to 100,
                        "offset" to 0,
                        "name" to query
                    )
                )

                val res = apiService.getSchools(schoolQuery)
                val list = res.data?.searchSchoolV1?.data ?: emptyList()
                _uiState.update { it.copy(schoolSearchResults = list, isSchoolSearching = false) }
            } catch (_: Exception) {
                _uiState.update { it.copy(isSchoolSearching = false) }
            }
        }
    }

    fun onSelectSchool(school: SchoolItem) {
        _uiState.update {
            it.copy(
                selectedSchoolId = school.id ?: "",
                selectedSchoolName = school.name ?: "",
                showSchoolSearchDialog = false
            )
        }
    }

    fun saveProfile(onSuccess: () -> Unit) {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null, successMessage = null) }
            try {
                // 1. Update main profile fields
                val updateQuery = GraphQlQuery(
                    query = """
                        mutation UpdateProfileWithoutUseName(
                          ${'$'}avatar: String,
                          ${'$'}dob: String,
                          ${'$'}gender: GenderTypeEnum,
                          ${'$'}shift: ShiftEnum,
                          ${'$'}ssc_board_name: String,
                          ${'$'}hsc_board_name: String,
                          ${'$'}board_roll_number: String,
                          ${'$'}hsc_board_roll_number: String,
                          ${'$'}board_reg_number: String,
                          ${'$'}other_tutoring_source: [OtherTutoringSourceEnum],
                          ${'$'}guardian_name: String,
                          ${'$'}guardian_mobile: String
                        ) {
                          updateProfile(
                            type: student,
                            avatar: ${'$'}avatar,
                            dob: ${'$'}dob,
                            gender: ${'$'}gender,
                            shift: ${'$'}shift,
                            ssc_board_name: ${'$'}ssc_board_name,
                            hsc_board_name: ${'$'}hsc_board_name,
                            board_roll_number: ${'$'}board_roll_number,
                            hsc_board_roll_number: ${'$'}hsc_board_roll_number,
                            board_reg_number: ${'$'}board_reg_number,
                            other_tutoring_source: ${'$'}other_tutoring_source,
                            guardian_name: ${'$'}guardian_name,
                            guardian_mobile: ${'$'}guardian_mobile
                          ) {
                            id
                            first_name
                            avatar
                            dob
                            gender
                            guardian_name
                            guardian_mobile
                            ssc_board_name
                            hsc_board_name
                            board_roll_number
                            hsc_board_roll_number
                            board_reg_number
                            shift
                            school {
                              id
                              name
                            }
                          }
                        }
                    """.trimIndent(),
                    operationName = "UpdateProfileWithoutUseName",
                    variables = mapOf(
                        "avatar" to state.avatarUrl?.ifBlank { null },
                        "dob" to state.dobIso.ifBlank { null },
                        "gender" to state.gender, // "Male" or "Female"
                        "shift" to state.shift.ifBlank { "NA" },
                        "ssc_board_name" to state.sscBoardName.ifBlank { null },
                        "hsc_board_name" to state.hscBoardName.ifBlank { null },
                        "board_roll_number" to state.sscRollNumber.ifBlank { null },
                        "hsc_board_roll_number" to state.hscRollNumber.ifBlank { null },
                        "board_reg_number" to state.boardRegNumber.ifBlank { null },
                        "other_tutoring_source" to listOf("NA"),
                        "guardian_name" to state.guardianName.ifBlank { null },
                        "guardian_mobile" to state.guardianMobile.ifBlank { null }
                    )
                )

                val updateRes = apiService.updateProfile(updateQuery)
                if (updateRes.errors != null && updateRes.errors.isNotEmpty()) {
                    val error = updateRes.errors.first().message ?: "প্রোফাইল আপডেট করা যায়নি"
                    _uiState.update { it.copy(isSaving = false, errorMessage = error) }
                    return@launch
                }

                // 2. If school ID is selected and changed, update school via UpdateUserSchool mutation
                if (state.selectedSchoolId.isNotBlank()) {
                    val schoolMutation = GraphQlQuery(
                        query = """
                            mutation UpdateUserSchool(${'$'}school_id: String, ${'$'}type: PrimaryUserTypeEnum!) {
                              updateProfile(school_id: ${'$'}school_id, type: ${'$'}type) {
                                school {
                                  id
                                  name
                                }
                              }
                            }
                        """.trimIndent(),
                        operationName = "UpdateUserSchool",
                        variables = mapOf(
                            "school_id" to state.selectedSchoolId,
                            "type" to "student"
                        )
                    )
                    apiService.updateUserSchool(schoolMutation)
                }

                // 3. Update SessionManager Cache
                sessionManager.saveUserProfile(
                    firstName = state.firstName.ifBlank { sessionManager.getUserFirstName() },
                    lastName = null,
                    avatar = state.avatarUrl ?: sessionManager.getUserAvatar(),
                    schoolName = state.selectedSchoolName.ifBlank { sessionManager.getUserSchoolName() },
                    classDisplay = state.userClassDisplay
                )

                sessionManager.setAccountComplete(true)
                sessionManager.setJustSignedUp(false)
                _uiState.update { it.copy(isSaving = false, successMessage = "প্রোফাইল সফলভাবে আপডেট করা হয়েছে!") }
                delay(600)
                onSuccess()

            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, errorMessage = "সংরক্ষণ ব্যর্থ হয়েছে: ${e.localizedMessage ?: "নেটওয়ার্ক সমস্যা"}") }
            }
        }
    }

    private fun formatIsoDateToDisplay(iso: String): String {
        if (iso.isBlank()) return ""
        return try {
            val parseFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            val date = parseFormat.parse(iso) ?: return iso
            val displayFormat = SimpleDateFormat("dd MMMM, yyyy", Locale.US)
            displayFormat.format(date)
        } catch (_: Exception) {
            iso
        }
    }
}

class EditProfileViewModelFactory(
    private val apiService: ShikhoApiService,
    private val sessionManager: SessionManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EditProfileViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return EditProfileViewModel(apiService, sessionManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
