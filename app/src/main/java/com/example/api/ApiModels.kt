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
    val last_name: String? = null,
    val avatar: String? = null,
    val gender: String? = null,
    val dob: String? = null,
    val shift: String? = null,
    val guardian_name: String? = null,
    val guardian_mobile: String? = null,
    val ssc_board_name: String? = null,
    val hsc_board_name: String? = null,
    val board_roll_number: String? = null,
    val hsc_board_roll_number: String? = null,
    val board_reg_number: String? = null,
    val other_tutoring_source: List<String>? = null,
    val study_group: String? = null,
    val `class`: ClassInfo? = null,
    val school: SchoolInfo? = null,
    val user: UserInfo? = null,
    val passing_year: String? = null
)

@JsonClass(generateAdapter = true)
data class ClassInfo(
    val code: String?,
    val display: String?
)

@JsonClass(generateAdapter = true)
data class SchoolInfo(
    val id: String?,
    val name: String?,
    val address: SchoolAddressInfo? = null
)

@JsonClass(generateAdapter = true)
data class SchoolAddressInfo(
    val division: DivisionDistrictInfo? = null,
    val district: DivisionDistrictInfo? = null
)

@JsonClass(generateAdapter = true)
data class DivisionDistrictInfo(
    val code: String? = null,
    val display: String? = null
)

@JsonClass(generateAdapter = true)
data class UserInfo(
    val phone: String?,
    val email: String?
)

// Address API Models (/address)
@JsonClass(generateAdapter = true)
data class AddressListResponse(
    val body: List<AddressItem>? = null,
    val code: Int? = null
)

@JsonClass(generateAdapter = true)
data class AddressItem(
    val code: String? = null,
    val display: String? = null,
    val ref: String? = null,
    val _key: String? = null
)

// School Search Models (query GetSchools / searchSchoolV1)
@JsonClass(generateAdapter = true)
data class SchoolSearchResponse(
    val data: SchoolSearchData? = null
)

@JsonClass(generateAdapter = true)
data class SchoolSearchData(
    val searchSchoolV1: SchoolSearchResult? = null
)

@JsonClass(generateAdapter = true)
data class SchoolSearchResult(
    val data: List<SchoolItem>? = null
)

@JsonClass(generateAdapter = true)
data class SchoolItem(
    val id: String? = null,
    val name: String? = null
)

// Update Profile & School Mutations
@JsonClass(generateAdapter = true)
data class UpdateProfileResponse(
    val data: UpdateProfileData? = null,
    val errors: List<GraphQlError>? = null
)

@JsonClass(generateAdapter = true)
data class UpdateProfileData(
    val updateProfile: UserProfile? = null
)

@JsonClass(generateAdapter = true)
data class UpdateSchoolResponse(
    val data: UpdateSchoolData? = null,
    val errors: List<GraphQlError>? = null
)

@JsonClass(generateAdapter = true)
data class UpdateSchoolData(
    val updateProfile: UpdateSchoolProfileResult? = null
)

@JsonClass(generateAdapter = true)
data class UpdateSchoolProfileResult(
    val school: SchoolItem? = null
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
    val content_id: String? = null,
    val content_type: String?, // "LiveClass", "LiveExam", "RecordedClass"
    val access_level: String? = null,
    val start_time: String? = null,
    val end_time: String? = null,
    val subject_id: String? = null,
    val subject_name: String? = null,
    val color_code: String? = null,
    val icon: String? = null,
    val user_activity_state: String?, // "UPCOMING", "ATTENDED", "MISSED", "COMPLETED"
    val live_class: LiveClassDetails? = null,
    val model_test: ModelTestDetails? = null,
    val slide_url: String? = null,
    val attachments: List<LessonAttachmentItem>? = null,
    val video_url: String? = null,
    val stream_url: String? = null,
    val recording_url: String? = null,
    val is_free: Boolean? = false,
    val is_locked: Boolean? = false
) {
    val isFree: Boolean
        get() = is_free == true || access_level.equals("FREE", ignoreCase = true) || live_class?.is_free == true

    val isLocked: Boolean
        get() = is_locked == true || access_level.equals("LOCKED", ignoreCase = true)
    val candidateStreamUrls: List<String>
        get() {
            val list = mutableListOf<String>()
            val direct = live_class?.resolvedVideoUrl
                ?: recording_url?.takeIf { it.isNotBlank() && it != "null" }
                ?: video_url?.takeIf { it.isNotBlank() && it != "null" }
                ?: stream_url?.takeIf { it.isNotBlank() && it != "null" }
            if (!direct.isNullOrBlank()) {
                list.add(direct)
            }
            live_class?.candidateStreamUrls?.let { list.addAll(it) }

            val lcId = live_class?.id ?: id
            if (!lcId.isNullOrBlank()) {
                list.add("https://shikho-stream2.tenbytecdn.com/$lcId/playlist.m3u8")
            }
            if (!content_id.isNullOrBlank()) {
                list.add("https://shikho-stream2.tenbytecdn.com/$content_id/playlist.m3u8")
            }

            return list.distinct()
        }

    val resolvedVideoUrl: String?
        get() = candidateStreamUrls.firstOrNull()

    val resolvedSlideUrl: String?
        get() = live_class?.lectureSlideUrl
            ?: slide_url
            ?: attachments?.firstOrNull { it.file_type.equals("pdf", ignoreCase = true) || it.downloadUrl?.contains(".pdf", ignoreCase = true) == true }?.downloadUrl
            ?: attachments?.firstOrNull()?.downloadUrl

    val allAttachments: List<LessonAttachmentItem>
        get() {
            val list = mutableListOf<LessonAttachmentItem>()
            live_class?.attachments?.let { list.addAll(it) }
            live_class?.attachment_list?.let { list.addAll(it) }
            attachments?.let { list.addAll(it) }
            val directSlide = live_class?.slide_url ?: slide_url
            if (!directSlide.isNullOrBlank() && list.none { it.downloadUrl == directSlide }) {
                list.add(0, LessonAttachmentItem(title = "লেকচার স্লাইড (PDF)", url = directSlide, file_type = "pdf"))
            }
            return list.distinctBy { it.downloadUrl }
        }
}

@JsonClass(generateAdapter = true)
data class TopicItem(
    val id: String? = null,
    val title: String? = null,
    val name: String? = null,
    val description: String? = null
) {
    val displayTitle: String
        get() = title ?: name ?: description ?: ""
}

@JsonClass(generateAdapter = true)
data class TeacherItem(
    val id: String? = null,
    val name: String? = null,
    val first_name: String? = null,
    val last_name: String? = null,
    val avatar: String? = null,
    val image: String? = null,
    val subject: String? = null,
    val designation: String? = null
) {
    val displayName: String
        get() = name ?: listOfNotNull(first_name, last_name).joinToString(" ").ifBlank { "শিক্ষক" }

    val displayAvatar: String?
        get() = avatar ?: image
}

@JsonClass(generateAdapter = true)
data class LessonAttachmentItem(
    val id: String? = null,
    val title: String? = null,
    val name: String? = null,
    val url: String? = null,
    val link: String? = null,
    val file_url: String? = null,
    val path: String? = null,
    val file_type: String? = null,
    val is_solution_sheet: Boolean? = null
) {
    val downloadUrl: String?
        get() = url ?: link ?: file_url ?: path

    val displayTitle: String
        get() = title ?: name ?: if (is_solution_sheet == true) "সমাধান শিট" else "লেকচার স্লাইড"
}

@JsonClass(generateAdapter = true)
data class LiveClassDetails(
    val id: String? = null,
    val recording_url: String? = null,
    val stream_url: String? = null,
    val video_url: String? = null,
    val url: String? = null,
    val playback_url: String? = null,
    val hls_url: String? = null,
    val chapter_name: String? = null,
    val is_on_going: Boolean? = false,
    val start_time: String? = null,
    val end_time: String? = null,
    val type: String? = null,
    val topics: List<TopicItem>? = emptyList(),
    val teacher: TeacherItem? = null,
    val instructor: TeacherItem? = null,
    val attachments: List<LessonAttachmentItem>? = emptyList(),
    val attachment_list: List<LessonAttachmentItem>? = emptyList(),
    val slide_url: String? = null,
    val is_free: Boolean? = false,
    val is_locked: Boolean? = false
) {
    val candidateStreamUrls: List<String>
        get() {
            val list = mutableListOf<String>()
            val direct = recording_url?.takeIf { it.isNotBlank() && it != "null" }
                ?: stream_url?.takeIf { it.isNotBlank() && it != "null" }
                ?: video_url?.takeIf { it.isNotBlank() && it != "null" }
                ?: playback_url?.takeIf { it.isNotBlank() && it != "null" }
                ?: hls_url?.takeIf { it.isNotBlank() && it != "null" }
                ?: url?.takeIf { it.isNotBlank() && it != "null" }
            if (!direct.isNullOrBlank()) list.add(direct)

            if (!id.isNullOrBlank()) {
                list.add("https://shikho-stream2.tenbytecdn.com/$id/playlist.m3u8")
            }

            return list.distinct()
        }

    val resolvedVideoUrl: String?
        get() = candidateStreamUrls.firstOrNull()

    val teacherName: String?
        get() = teacher?.displayName ?: instructor?.displayName

    val teacherAvatar: String?
        get() = teacher?.displayAvatar ?: instructor?.displayAvatar

    val lectureSlideUrl: String?
        get() = slide_url
            ?: attachments?.firstOrNull { it.file_type.equals("pdf", ignoreCase = true) || it.downloadUrl?.contains(".pdf", ignoreCase = true) == true }?.downloadUrl
            ?: attachment_list?.firstOrNull { it.file_type.equals("pdf", ignoreCase = true) || it.downloadUrl?.contains(".pdf", ignoreCase = true) == true }?.downloadUrl
            ?: attachments?.firstOrNull()?.downloadUrl
            ?: attachment_list?.firstOrNull()?.downloadUrl
}

@JsonClass(generateAdapter = true)
data class ModelTestDetails(
    val exam_category: String?,
    val type: String?
)

// ==========================================
// 3.5. Academic Subjects & Progress (Tier 1)
// ==========================================
@JsonClass(generateAdapter = true)
data class AcademicSubjectsResponse(
    val data: AcademicSubjectsData? = null
)

@JsonClass(generateAdapter = true)
data class AcademicSubjectsData(
    val academicProgram: AcademicProgramDetail? = null
)

@JsonClass(generateAdapter = true)
data class AcademicProgramDetail(
    val id: String? = null,
    val subjects: List<AcademicSubjectItem>? = emptyList(),
    val subjects_progress_bar: List<SubjectProgressBarItem>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class AcademicSubjectItem(
    val code: String? = null,
    val color_code: String? = null,
    val display_bn: String? = null,
    val icon: String? = null
)

@JsonClass(generateAdapter = true)
data class SubjectProgressBarItem(
    val code: String? = null,
    val percentage: Double? = null,
    val total_chapters: Int? = null,
    val completed_chapters: Int? = null
)

// ==========================================
// 3.6. Academic Program Chapters (Tier 2)
// ==========================================
@JsonClass(generateAdapter = true)
data class AcademicChaptersResponse(
    val data: AcademicChaptersData? = null
)

@JsonClass(generateAdapter = true)
data class AcademicChaptersData(
    val listAcademicProgramChapters: ListAcademicProgramChaptersPayload? = null
)

@JsonClass(generateAdapter = true)
data class ListAcademicProgramChaptersPayload(
    val data: List<AcademicChapterItem>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class AcademicChapterItem(
    val id: String = "",
    val chapter_id: String? = null,
    val chapter_name: String? = null,
    val chapter_no: Any? = null,
    val status: String? = null, // e.g. "IN_PROGRESS", "COMPLETED", "ON_GOING", "UPCOMING"
    val class_counter: Int? = null,
    val exam_counter: Int? = null,
    val chapters_progress_percentage: Double? = null
) {
    val displayProgress: Int
        get() = chapters_progress_percentage?.toInt() ?: 0

    val isCompleted: Boolean
        get() = status?.equals("COMPLETED", ignoreCase = true) == true || displayProgress >= 100

    val isInProgress: Boolean
        get() = status?.equals("IN_PROGRESS", ignoreCase = true) == true ||
                status?.equals("ON_GOING", ignoreCase = true) == true ||
                (displayProgress in 1..99)
}

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

// ==========================================
// 8. Syllabus Change, Class List & Batch Models
// ==========================================
@JsonClass(generateAdapter = true)
data class ClassListResponse(
    val classes: List<ClassItem>? = null,
    val data: List<ClassItem>? = null,
    val code: Int? = null,
    val status: String? = null,
    val message: String? = null
)

@JsonClass(generateAdapter = true)
data class ClassItem(
    val serial: Int? = null,
    val code: String = "",
    val name_en: String? = null,
    val name_bn: String? = null,
    val title_bn: String? = null,
    val title_en: String? = null,
    val is_group_required: Boolean? = null,
    val has_batch_selection: Boolean? = null,
    val show_on_boarding: Boolean? = null,
    val parent_name: String? = null,
    val parent_name_bn: String? = null,
    val is_active: Boolean? = true,
    val vendor: String? = "BD",
    val groups: List<StudyGroupItem>? = null
) {
    val displayNameBn: String
        get() = when (code.uppercase()) {
            "C5", "C05" -> "ক্লাস ৫"
            "C6", "C06" -> "ক্লাস ৬"
            "C7", "C07" -> "ক্লাস ৭"
            "C8", "C08" -> "ক্লাস ৮"
            "C9", "C09" -> "ক্লাস ৯"
            "C10" -> "ক্লাস ১০"
            "C11" -> "এইচএসসি"
            "C12" -> "এডমিশন"
            else -> name_bn ?: title_bn ?: code
        }

    val displaySubtitle: String
        get() = when (code.uppercase()) {
            "C5", "C05" -> "Class 5"
            "C6", "C06" -> "Class 6"
            "C7", "C07" -> "Class 7"
            "C8", "C08" -> "Class 8"
            "C9", "C09" -> "Class 9"
            "C10" -> "Class 10"
            "C11" -> "HSC"
            "C12" -> "Admission"
            else -> name_en ?: title_en ?: ""
        }

    val isGroupRequired: Boolean
        get() = is_group_required == true || code.uppercase() in listOf("C9", "C09", "C10", "C11", "C12")
}

@JsonClass(generateAdapter = true)
data class StudyGroupItem(
    val code: String = "",
    val name_en: String? = null,
    val name_bn: String? = null,
    val title_bn: String? = null,
    val title_en: String? = null
) {
    val displayNameBn: String
        get() = when (code.lowercase()) {
            "science" -> "বিজ্ঞান"
            "humanities", "hum" -> "মানবিক"
            "business_studies", "business", "business studies", "commerce" -> "ব্যবসায় শিক্ষা"
            else -> name_bn ?: title_bn ?: code
        }
}

@JsonClass(generateAdapter = true)
data class BatchOptionsResponse(
    val data: BatchOptionsData? = null,
    val errors: List<GraphQlError>? = null
)

@JsonClass(generateAdapter = true)
data class BatchOptionsData(
    val batchOptions: BatchOptionsPayload? = null
)

@JsonClass(generateAdapter = true)
data class BatchOptionsPayload(
    val classCode: String? = null,
    val date: String? = null,
    val options: List<BatchOptionItem>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class BatchOptionItem(
    val year: Any? = null,
    val label: String? = null
) {
    val yearString: String
        get() = when (val y = year) {
            is Number -> y.toLong().toString()
            is String -> y
            null -> ""
            else -> y.toString()
        }
}

@JsonClass(generateAdapter = true)
data class GraphQlError(
    val message: String? = null
)

@JsonClass(generateAdapter = true)
data class ChangeSyllabusResponse(
    val data: ChangeSyllabusData? = null,
    val errors: List<GraphQlError>? = null
)

@JsonClass(generateAdapter = true)
data class ChangeSyllabusData(
    val changeSyllabus: AuthTokensPayload? = null
)

@JsonClass(generateAdapter = true)
data class AuthTokensPayload(
    val access_token: String? = null,
    val id_token: String? = null,
    val refresh_token: String? = null
)

@JsonClass(generateAdapter = true)
data class UpdateExamYearResponse(
    val data: UpdateExamYearData? = null,
    val errors: List<GraphQlError>? = null
)

@JsonClass(generateAdapter = true)
data class UpdateExamYearData(
    val updateProfile: UpdateProfilePayload? = null
)

@JsonClass(generateAdapter = true)
data class UpdateProfilePayload(
    val passing_year: String? = null
)
