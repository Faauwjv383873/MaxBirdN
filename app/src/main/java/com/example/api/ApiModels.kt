package com.example.api

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class UserCheckRequest(
    val phone: String,
    val type: String = "student"
)

@JsonClass(generateAdapter = true)
data class UserCheckResponse(
    val code: Int? = null,
    val message: String? = null,
    val pin_exist: Boolean? = false
)

@JsonClass(generateAdapter = true)
data class SendSmsRequest(
    val phone: String,
    val type: String = "student",
    val auth_type: String,
    val vendor: String = "shikho",
    val google_ads_id: String
)

@JsonClass(generateAdapter = true)
data class SendSmsResponse(
    val code: Int? = null,
    val message: String? = null,
    val error: String? = null
)

@JsonClass(generateAdapter = true)
data class VerifyOtpRequest(
    val phone: String,
    val otp: String,
    val type: String = "student"
)

@JsonClass(generateAdapter = true)
data class VerifyOtpResponse(
    val code: Int? = null,
    val message: String? = null
)

@JsonClass(generateAdapter = true)
data class ProfileDevice(
    val device_id: String
)

@JsonClass(generateAdapter = true)
data class LoginRequest(
    val phone: String,
    val otp: String,
    val type: String = "student",
    val profile: ProfileDevice,
    val google_ads_id: String
)

@JsonClass(generateAdapter = true)
data class LoginTokens(
    val access_token: String,
    val refresh_token: String?,
    val user_id: String
)

@JsonClass(generateAdapter = true)
data class LoginResponse(
    val tokens: LoginTokens
)

@JsonClass(generateAdapter = true)
data class LogoutResponse(
    val message: String? = null,
    val code: Int? = null
)

// GraphQL generic request payload
@JsonClass(generateAdapter = true)
data class GraphQlQuery(
    val operationName: String,
    val query: String,
    val variables: Map<String, Any?> = emptyMap()
)

@JsonClass(generateAdapter = true)
data class SetPinData(
    val setUserPin: SetUserPinResponse?
)

@JsonClass(generateAdapter = true)
data class SetUserPinResponse(
    val message: String?
)

@JsonClass(generateAdapter = true)
data class SetPinResponse(
    val data: SetPinData?
)

// ==========================================
// 1. Profile Models
// ==========================================
@JsonClass(generateAdapter = true)
data class ProfileResponse(
    val data: ProfileData?
)

@JsonClass(generateAdapter = true)
data class ProfileData(
    val profile: UserProfile?
)

@JsonClass(generateAdapter = true)
data class UserProfile(
    val id: String?,
    val first_name: String?,
    val last_name: String?,
    val avatar: String?,
    val gender: String?,
    val dob: String?,
    val study_group: String?,
    val `class`: ClassInfo?,
    val school: SchoolInfo?,
    val user: UserInfo?
)

@JsonClass(generateAdapter = true)
data class ClassInfo(
    val code: String?,
    val display: String?
)

@JsonClass(generateAdapter = true)
data class SchoolInfo(
    val id: String?,
    val name: String?
)

@JsonClass(generateAdapter = true)
data class UserInfo(
    val phone: String?,
    val email: String?
)

// ==========================================
// 2. Enrolled Academic Programs & Course Switcher
// ==========================================
@JsonClass(generateAdapter = true)
data class AcademicProgramResponse(
    val data: AcademicProgramData?
)

@JsonClass(generateAdapter = true)
data class AcademicProgramData(
    val listAcademicProgramByEnrollment: ListAcademicProgramByEnrollment?
)

@JsonClass(generateAdapter = true)
data class ListAcademicProgramByEnrollment(
    val enrolled_programs: List<EnrolledProgram>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class EnrolledProgram(
    val id: String,
    val classes: List<String>? = emptyList(),
    val title_bn: String?,
    val banner_url: String?,
    val color: String?,
    val is_free: Boolean? = false,
    val trial_enabled: Boolean? = false,
    val enrollment_details: EnrollmentDetails? = null,
    val subjects: List<ProgramSubject>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class EnrollmentDetails(
    val batch_id: String?,
    val is_active: Boolean? = false,
    val trial_end_date: String?,
    val type: String?, // e.g. "FullApTrial", "Paid"
    val expiry_date: String?
)

@JsonClass(generateAdapter = true)
data class ProgramSubject(
    val code: String?,
    val display_bn: String?,
    val color_code: String?,
    val icon: String?
)

// ==========================================
// 3. Student Specific Lessons & Weekly Routine
// ==========================================
@JsonClass(generateAdapter = true)
data class StudentSpecificLessonsResponse(
    val data: StudentSpecificLessonsData?
)

@JsonClass(generateAdapter = true)
data class StudentSpecificLessonsData(
    val studentSpecificLessons: StudentLessonsInnerData?
)

@JsonClass(generateAdapter = true)
data class StudentLessonsInnerData(
    val data: List<StudentLessonItem>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class StudentLessonItem(
    val id: String,
    val title: String?,
    val content_id: String?,
    val content_type: String?, // "LiveClass", "LiveExam", "RecordedClass"
    val access_level: String?,
    val start_time: String?,
    val end_time: String?,
    val subject_id: String?,
    val subject_name: String?,
    val color_code: String?,
    val icon: String?,
    val user_activity_state: String?, // "UPCOMING", "ATTENDED", "MISSED", "COMPLETED"
    val live_class: LiveClassDetails? = null,
    val model_test: ModelTestDetails? = null
)

@JsonClass(generateAdapter = true)
data class LiveClassDetails(
    val chapter_name: String?,
    val is_on_going: Boolean? = false,
    val start_time: String?,
    val end_time: String?,
    val type: String?
)

@JsonClass(generateAdapter = true)
data class ModelTestDetails(
    val exam_category: String?,
    val type: String?
)

// ==========================================
// 4. Program Phases / Course Progress Quarters
// ==========================================
@JsonClass(generateAdapter = true)
data class ProgramPhasesResponse(
    val data: ProgramPhasesData?
)

@JsonClass(generateAdapter = true)
data class ProgramPhasesData(
    val programPhasesByStudent: ProgramPhasesInnerData?
)

@JsonClass(generateAdapter = true)
data class ProgramPhasesInnerData(
    val data: List<PhaseItem>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class PhaseItem(
    val id: String,
    val academic_program_id: String?,
    val title: String?,
    val status: String?, // "ACTIVE", "COMPLETED", "UPCOMING", "UNENROLLED"
    val is_current: Boolean? = false,
    val has_enrolment: Boolean? = false,
    val has_free_trial_enrolment: Boolean? = false,
    val course_progress_percentage: Double? = 0.0,
    val start_date: String?,
    val end_date: String?
)

// ==========================================
// 5. Practice Quiz Limits
// ==========================================
@JsonClass(generateAdapter = true)
data class PracticeQuizAccessResponse(
    val data: PracticeQuizAccessData?
)

@JsonClass(generateAdapter = true)
data class PracticeQuizAccessData(
    val getPracticeQuizAccess: PracticeQuizAccessPayload?
)

@JsonClass(generateAdapter = true)
data class PracticeQuizAccessPayload(
    val custom_practice_limits: CustomPracticeLimits?
)

@JsonClass(generateAdapter = true)
data class CustomPracticeLimits(
    val has_limit: Boolean? = true,
    val limit_per_day: Int? = 3,
    val used_today: Int? = 0
)

// ==========================================
// 6. Quarterly Results / Performance Score REST
// ==========================================
@JsonClass(generateAdapter = true)
data class QuarterlyResultResponse(
    val status: String? = null,
    val total_score_percentage: Double? = null,
    val total_exams: Int? = null,
    val attended_exams: Int? = null,
    val message: String? = null
)

// ==========================================
// 7. Video List Model
// ==========================================
@JsonClass(generateAdapter = true)
data class VideoListResponse(
    val data: VideoListData?
)

@JsonClass(generateAdapter = true)
data class VideoListData(
    val userSpecificVideoList: List<VideoItem>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class VideoItem(
    val id: String? = null,
    val title: String? = null,
    val thumbnail: String? = null,
    val duration: String? = null,
    val teacher_name: String? = null,
    val stream_url: String? = null,
    val subject: String? = null,
    val view_count: String? = null
)
