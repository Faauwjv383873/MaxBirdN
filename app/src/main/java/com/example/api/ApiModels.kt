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

// GraphQL request & generic payloads
@JsonClass(generateAdapter = true)
data class GraphQlQuery(
    val operationName: String,
    val query: String,
    val variables: Map<String, Any>
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

// Profile Models
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

// Academic Program & Enrolled Courses
@JsonClass(generateAdapter = true)
data class AcademicProgramResponse(
    val data: AcademicProgramData?
)

@JsonClass(generateAdapter = true)
data class AcademicProgramData(
    val academicProgram: AcademicProgram?
)

@JsonClass(generateAdapter = true)
data class AcademicProgram(
    val id: String? = null,
    val title: String? = null,
    val code: String? = null,
    val banner_image: String? = null,
    val icon: String? = null,
    val progress: Int? = 0,
    val subjects: List<SubjectInfo>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class SubjectInfo(
    val id: String? = null,
    val title: String? = null,
    val code: String? = null,
    val icon: String? = null,
    val total_chapters: Int? = 0,
    val completed_chapters: Int? = 0,
    val progress: Int? = 0
)

// Program Phases / Quarters
@JsonClass(generateAdapter = true)
data class ProgramPhasesResponse(
    val data: ProgramPhasesData?
)

@JsonClass(generateAdapter = true)
data class ProgramPhasesData(
    val programPhasesByStudent: List<ProgramPhase>?
)

@JsonClass(generateAdapter = true)
data class ProgramPhase(
    val id: String? = null,
    val name: String? = null,
    val phase_number: Int? = null,
    val is_active: Boolean? = false,
    val progress: Double? = 0.0,
    val start_date: String? = null,
    val end_date: String? = null
)

// Routine & Live Classes
@JsonClass(generateAdapter = true)
data class StudentLessonsResponse(
    val data: StudentLessonsData?
)

@JsonClass(generateAdapter = true)
data class StudentLessonsData(
    val studentSpecificLessons: List<LessonItem>?
)

@JsonClass(generateAdapter = true)
data class LessonItem(
    val id: String? = null,
    val title: String? = null,
    val subject_title: String? = null,
    val chapter_title: String? = null,
    val teacher_name: String? = null,
    val teacher_designation: String? = null,
    val start_time: String? = null,
    val end_time: String? = null,
    val status: String? = null, // "Upcoming", "Live", "Missed", "Completed"
    val join_url: String? = null,
    val is_live: Boolean? = false,
    val date_display: String? = null
)

// Video Library & Recommendations
@JsonClass(generateAdapter = true)
data class VideoListResponse(
    val data: VideoListData?
)

@JsonClass(generateAdapter = true)
data class VideoListData(
    val userSpecificVideoList: List<VideoItem>?
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
