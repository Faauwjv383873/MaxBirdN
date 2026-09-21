package com.example.course

import com.example.api.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Encapsulates GraphQL query definitions and remote data fetching methods for Course and Lesson flows.
 */
class CourseRepository(
    private val apiService: ShikhoApiService
) {

    suspend fun getAcademicProgramByEnrollment(
        batchId: String? = null,
        className: String,
        group: String? = null,
        vendor: String = "BD"
    ): AcademicProgramResponse {
        // C5-C8 এর ক্ষেত্রে group সবসময় "None" হতে হবে, C9-C12 এর জন্য Humanities/Science/Business_Studies
        val classUpper = className.uppercase()
        val formattedGroup = if (classUpper in listOf("C5", "C6", "C7", "C8", "C05", "C06", "C07", "C08")) {
            "None"
        } else {
            when (group?.lowercase()) {
                "humanities", "arts", "hum" -> "Humanities"
                "science", "sci" -> "Science"
                "business_studies", "business", "commerce" -> "Business_Studies"
                else -> "None"
            }
        }

        val query = GraphQlQuery(
            operationName = "GetAcademicProgram",
            query = """
                query GetAcademicProgram(${'$'}batch_id: String, ${'$'}className: AcademicProgramClassEnum, ${'$'}group: StudyGroupTypeEnum, ${'$'}vendor: VendorEnum) {
                  listAcademicProgramByEnrollment(batch_id: ${'$'}batch_id, class: ${'$'}className, group: ${'$'}group, vendor: ${'$'}vendor) {
                    enrolled_programs {
                      id
                      classes
                      title_bn
                      facebook_group_url
                      banner_url
                      color
                      course_feature_list
                      has_animated_video
                      is_free
                      phase_pricing
                      trial_enabled
                      trial_duration
                      serial
                      subjects {
                        code
                        display
                        display_bn
                        color_code
                        icon
                      }
                      quarter_discount_price
                      full_program_discount_price
                      enrollment_details {
                        expiry_date
                        type
                        created_at
                        batch_id
                        is_on_installment
                        is_active
                        trial_end_date
                        consumable_resources
                        is_qr
                        tag
                      }
                    }
                    other_programs {
                      id
                      classes
                      title_bn
                      facebook_group_url
                      banner_url
                      phase_pricing
                      is_free
                      has_animated_video
                      full_program_discount_price
                      pricing {
                        sale_price_quarterly
                        sale_price_full
                      }
                      trial_enabled
                      trial_duration
                    }
                  }
                }
            """.trimIndent(),
            variables = mapOf(
                "batch_id" to batchId,
                "className" to className,
                "group" to formattedGroup,
                "vendor" to vendor
            )
        )
        return apiService.getAcademicProgram(query)
    }

    suspend fun getAcademicProgramByEnrollment(className: String): AcademicProgramResponse {
        return getAcademicProgramByEnrollment(null, className, null, "BD")
    }

    suspend fun getProgramPhases(programId: String): List<PhaseItem> {
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
        return phaseRes.data?.programPhasesByStudent?.data ?: emptyList()
    }

    suspend fun getAcademicSubjects(programId: String, phaseId: String? = null): AcademicProgramDetail? {
        val query = if (!phaseId.isNullOrBlank()) {
            GraphQlQuery(
                operationName = "GetAcademicSubjects",
                query = """
                    query GetAcademicSubjects(${'$'}programId: String!, ${'$'}phase_id: String!) {
                      academicProgram(id: ${'$'}programId, show_subject_progress_bar: true, phase_id: ${'$'}phase_id) {
                        subjects {
                          code
                          color_code
                          display_bn
                          icon
                        }
                        subjects_progress_bar {
                          completed_chapters
                          total_chapters
                          code
                          percentage
                        }
                        trial_subject_list
                      }
                    }
                """.trimIndent(),
                variables = mapOf(
                    "programId" to programId,
                    "phase_id" to phaseId
                )
            )
        } else {
            GraphQlQuery(
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
                        subjects_progress_bar {
                          completed_chapters
                          total_chapters
                          code
                          percentage
                        }
                        trial_subject_list
                      }
                    }
                """.trimIndent(),
                variables = mapOf("programId" to programId)
            )
        }
        val response = apiService.getAcademicSubjects(query)
        return response.data?.academicProgram
    }

    suspend fun getPhaseWiseChapters(programId: String, phaseId: String, subjectCode: String): List<AcademicChapterItem> {
        val chaptersQuery = GraphQlQuery(
            operationName = "PhaseWiseChapters",
            query = """
                query PhaseWiseChapters(${'$'}program_id: String!, ${'$'}phase_id: String!, ${'$'}subject_id: String!) {
                  listAcademicProgramChapters(program_id: ${'$'}program_id, phase_id: ${'$'}phase_id, subject_id: ${'$'}subject_id, show_chapter_progress_bar: true) {
                    data {
                      id
                      batch_id
                      chapter_id
                      chapter_name
                      chapter_no
                      program_id
                      status
                      subject_icon
                      class_counter
                      exam_counter
                      chapters_progress_percentage
                    }
                  }
                }
            """.trimIndent(),
            variables = mapOf(
                "program_id" to programId,
                "phase_id" to phaseId,
                "subject_id" to subjectCode
            )
        )
        val response = apiService.getPhaseWiseChapters(chaptersQuery)
        return response.data?.listAcademicProgramChapters?.data ?: emptyList()
    }

    suspend fun getChaptersBySubjectCode(subjectCode: String): List<AcademicChapterItem> {
        val getChaptersQuery = GraphQlQuery(
            operationName = "GetChapters",
            query = """
                query GetChapters(${'$'}subject_code: String!) {
                  chapters(subject_code: ${'$'}subject_code, filter: { limit: 100 } ) {
                    data {
                      id
                      name
                      no
                      all_topics_free
                      topics {
                        meta {
                          count
                        }
                      }
                    }
                  }
                }
            """.trimIndent(),
            variables = mapOf("subject_code" to subjectCode)
        )
        val res = apiService.getPhaseWiseChapters(getChaptersQuery)
        return res.data?.chapters?.data ?: emptyList()
    }

    suspend fun getAcademicProgramChaptersFallback(programId: String, subjectCode: String): List<AcademicChapterItem> {
        val fallbackQuery = GraphQlQuery(
            operationName = "AcademicProgramChapters",
            query = """
                query AcademicProgramChapters(${'$'}program_id: String!, ${'$'}subject_id: String!) {
                  listAcademicProgramChapters(program_id: ${'$'}program_id, subject_id: ${'$'}subject_id, show_chapter_progress_bar: true) {
                    data {
                      id
                      chapter_id
                      chapter_name
                      chapter_no
                      status
                      class_counter
                      exam_counter
                      chapters_progress_percentage
                    }
                  }
                }
            """.trimIndent(),
            variables = mapOf(
                "program_id" to programId,
                "subject_id" to subjectCode
            )
        )
        val fallbackRes = apiService.getPhaseWiseChapters(fallbackQuery)
        return fallbackRes.data?.listAcademicProgramChapters?.data ?: emptyList()
    }

    suspend fun fetchFallbackHierarchyChapters(subjectCode: String, selectedSubjectTitle: String?): List<AcademicChapterItem> {
        try {
            val query = GraphQlQuery(
                operationName = "GetSubjectHierarchyWithQuestionCounts",
                query = """
                    query GetSubjectHierarchyWithQuestionCounts(${'$'}class: ClassEnum, ${'$'}group: StudyGroupTypeEnum) {
                      subjectHierarchyWithQuestionCounts(class: ${'$'}class, group: ${'$'}group) {
                        data {
                          chapters {
                            id
                            name
                            no
                            should_render
                            total_active_questions
                          }
                          should_render
                          display_bn
                          display
                          code
                          icon
                          color_code
                          total_active_questions
                        }
                      }
                    }
                """.trimIndent(),
                variables = mapOf("class" to "HSC", "group" to "Humanities")
            )
            val res = apiService.getSubjectHierarchyWithQuestionCounts(query)
            val subjectsData = res.data?.subjectHierarchyWithQuestionCounts?.data ?: emptyList()
            val matchedSubject = subjectsData.find { it.code == subjectCode }
                ?: subjectsData.find { it.display_bn?.trim() == selectedSubjectTitle?.trim() }

            return matchedSubject?.chapters?.filter { it.should_render != false }?.map { ch ->
                AcademicChapterItem(
                    id = ch.id,
                    chapter_id = ch.id,
                    chapter_name = ch.name,
                    chapter_no = ch.no,
                    status = "IN_PROGRESS"
                )
            } ?: emptyList()
        } catch (e: Exception) {
            return emptyList()
        }
    }

    suspend fun fetchAllLessonsForProgram(
        programId: String,
        phaseId: String? = null,
        batchId: String? = null
    ): List<StudentLessonItem> {
        val collected = mutableListOf<StudentLessonItem>()
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Dhaka"))
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val startCal = (cal.clone() as Calendar).apply { add(Calendar.YEAR, -1) }
        val endCal = (cal.clone() as Calendar).apply { add(Calendar.YEAR, 1) }
        val startDate = dateFormat.format(startCal.time)
        val endDate = dateFormat.format(endCal.time)

        val fragment = """
            id
            title
            content_id
            content_type
            access_level
            start_time
            end_time
            subject_id
            subject_name
            batch_id
            chapter_id
            icon
            color_code
            user_activity_state
            live_class {
              id
              playback_url
              start_time
              end_time
              type
            }
            model_test {
              type
              exam_category
            }
        """.trimIndent()

        val minimalFragment = """
            id
            title
            content_id
            content_type
            access_level
            start_time
            end_time
            subject_id
            subject_name
            batch_id
            chapter_id
            icon
            color_code
            user_activity_state
        """.trimIndent()

        suspend fun runQuery(
            opName: String,
            queryStr: (String) -> String,
            variables: Map<String, Any?>
        ): List<StudentLessonItem> {
            try {
                val q = GraphQlQuery(
                    operationName = opName,
                    query = queryStr(fragment),
                    variables = variables
                )
                val res = apiService.getStudentLessons(q)
                val data = res.data?.studentSpecificLessons?.data
                if (!data.isNullOrEmpty()) return data
            } catch (e: Exception) {
                val errBody = (e as? retrofit2.HttpException)?.response()?.errorBody()?.string()
                android.util.Log.e("CourseRepository", "$opName (rich) failed: ${e.message}, body: $errBody")
            }

            // Fallback to minimal fields
            try {
                val qMin = GraphQlQuery(
                    operationName = opName,
                    query = queryStr(minimalFragment),
                    variables = variables
                )
                val res = apiService.getStudentLessons(qMin)
                return res.data?.studentSpecificLessons?.data ?: emptyList()
            } catch (e: Exception) {
                val errBody = (e as? retrofit2.HttpException)?.response()?.errorBody()?.string()
                android.util.Log.e("CourseRepository", "$opName (minimal) failed: ${e.message}, body: $errBody")
            }
            return emptyList()
        }

        // 1. If phaseId is provided, try with phase_id and date range
        if (!phaseId.isNullOrBlank()) {
            val list = runQuery(
                opName = "GetStudentLessonsPhaseDate",
                queryStr = { f ->
                    """
                        query GetStudentLessonsPhaseDate(${'$'}program_id: String!, ${'$'}phase_id: String!, ${'$'}start_date: String!, ${'$'}end_date: String!) {
                          studentSpecificLessons(program_id: ${'$'}program_id, phase_id: ${'$'}phase_id, start_date: ${'$'}start_date, end_date: ${'$'}end_date) {
                            data { $f }
                          }
                        }
                    """.trimIndent()
                },
                variables = mapOf(
                    "program_id" to programId,
                    "phase_id" to phaseId,
                    "start_date" to startDate,
                    "end_date" to endDate
                )
            )
            collected.addAll(list)
        }

        // 2. If batchId is provided, try with batch_id and date range
        if (!batchId.isNullOrBlank()) {
            val list = runQuery(
                opName = "GetStudentLessonsBatchDate",
                queryStr = { f ->
                    """
                        query GetStudentLessonsBatchDate(${'$'}program_id: String!, ${'$'}batch_id: String!, ${'$'}start_date: String!, ${'$'}end_date: String!) {
                          studentSpecificLessons(program_id: ${'$'}program_id, batch_id: ${'$'}batch_id, start_date: ${'$'}start_date, end_date: ${'$'}end_date) {
                            data { $f }
                          }
                        }
                    """.trimIndent()
                },
                variables = mapOf(
                    "program_id" to programId,
                    "batch_id" to batchId,
                    "start_date" to startDate,
                    "end_date" to endDate
                )
            )
            collected.addAll(list)
        }

        // 3. Try with program_id and date range (without phase or batch)
        val listDateOnly = runQuery(
            opName = "GetStudentLessonsDateOnly",
            queryStr = { f ->
                """
                    query GetStudentLessonsDateOnly(${'$'}program_id: String!, ${'$'}start_date: String!, ${'$'}end_date: String!) {
                      studentSpecificLessons(program_id: ${'$'}program_id, start_date: ${'$'}start_date, end_date: ${'$'}end_date) {
                        data { $f }
                      }
                    }
                """.trimIndent()
            },
            variables = mapOf(
                "program_id" to programId,
                "start_date" to startDate,
                "end_date" to endDate
            )
        )
        collected.addAll(listDateOnly)

        // 4. Try without dates (phase-wise)
        if (!phaseId.isNullOrBlank()) {
            val listPhase = runQuery(
                opName = "GetUpcomingLessonsPhaseWise",
                queryStr = { f ->
                    """
                        query GetUpcomingLessonsPhaseWise(${'$'}program_id: String!, ${'$'}phase_id: String!) {
                          studentSpecificLessons(program_id: ${'$'}program_id, phase_id: ${'$'}phase_id) {
                            data { $f }
                          }
                        }
                    """.trimIndent()
                },
                variables = mapOf("program_id" to programId, "phase_id" to phaseId)
            )
            collected.addAll(listPhase)
        }

        // 5. Try without dates (batch-wise)
        if (!batchId.isNullOrBlank()) {
            val listBatch = runQuery(
                opName = "GetStudentSpecificLessonsWithBatch",
                queryStr = { f ->
                    """
                        query GetStudentSpecificLessonsWithBatch(${'$'}program_id: String!, ${'$'}batch_id: String!) {
                          studentSpecificLessons(program_id: ${'$'}program_id, batch_id: ${'$'}batch_id) {
                            data { $f }
                          }
                        }
                    """.trimIndent()
                },
                variables = mapOf("program_id" to programId, "batch_id" to batchId)
            )
            collected.addAll(listBatch)
        }

        // 6. Try bare program_id
        val listAll = runQuery(
            opName = "GetUpcomingLessons",
            queryStr = { f ->
                """
                    query GetUpcomingLessons(${'$'}program_id: String!) {
                      studentSpecificLessons(program_id: ${'$'}program_id) {
                        data { $f }
                      }
                    }
                """.trimIndent()
            },
            variables = mapOf("program_id" to programId)
        )
        collected.addAll(listAll)

        val distinct = collected.distinctBy { it.id.ifBlank { "${it.content_id}_${it.start_time}_${it.title}" } }
        if (distinct.isNotEmpty()) {
            LessonCacheManager.saveLessons(distinct)
        }
        return distinct
    }

    suspend fun fetchLessonsWithPhase(
        chapterId: String,
        programId: String,
        phaseId: String,
        chapterName: String? = null,
        batchId: String? = null,
        subjectTitle: String? = null
    ): List<StudentLessonItem> {
        val allLessons = fetchAllLessonsForProgram(programId, phaseId, batchId)
        if (chapterId.isBlank() && chapterName.isNullOrBlank()) return allLessons
        return LessonCacheManager.filterLessons(allLessons, listOf(chapterId), chapterName, subjectTitle)
    }

    suspend fun fetchLessonsStandard(
        chapterId: String,
        programId: String,
        chapterName: String? = null,
        batchId: String? = null,
        subjectTitle: String? = null
    ): List<StudentLessonItem> {
        val allLessons = fetchAllLessonsForProgram(programId, null, batchId)
        if (chapterId.isBlank() && chapterName.isNullOrBlank()) return allLessons
        return LessonCacheManager.filterLessons(allLessons, listOf(chapterId), chapterName, subjectTitle)
    }

    suspend fun fetchChapterLessons(
        chapterId: String,
        altChapterId: String? = null,
        chapterName: String? = null,
        subjectTitle: String? = null,
        programId: String? = null,
        phaseId: String? = null,
        batchId: String? = null
    ): List<StudentLessonItem> {
        val collectedLessons = mutableListOf<StudentLessonItem>()

        // 1. Fetch scheduled or live lessons for this program / phase
        val chapterIdsToTry = listOfNotNull(chapterId.ifBlank { null }, altChapterId?.ifBlank { null }).distinct()
        if (!programId.isNullOrBlank()) {
            for (chId in chapterIdsToTry) {
                try {
                    val programLessons = if (!phaseId.isNullOrBlank()) {
                        fetchLessonsWithPhase(chId, programId, phaseId, chapterName, batchId, subjectTitle)
                    } else {
                        fetchLessonsStandard(chId, programId, chapterName, batchId, subjectTitle)
                    }
                    if (programLessons.isNotEmpty()) {
                        collectedLessons.addAll(programLessons)
                        break
                    }
                } catch (e: Exception) {
                    android.util.Log.e("CourseRepository", "Error fetching program lessons for chapter $chId: ${e.message}")
                }
            }
        }

        // 2. Deduplicate
        val distinct = collectedLessons.distinctBy { it.id.ifBlank { "${it.content_id}_${it.title}" } }
        if (distinct.isNotEmpty()) {
            LessonCacheManager.saveLessons(distinct)
            return distinct
        }

        // 4. Try from local cache
        val cached = LessonCacheManager.findLessonsForChapter(listOfNotNull(chapterId, altChapterId), chapterName, subjectTitle)
        if (cached.isNotEmpty()) return cached

        return emptyList()
    }

    suspend fun getLiveClassDetails(liveClassId: String): AcademicProgramLiveClassItem? {
        val query = GraphQlQuery(
            operationName = "academicProgramLiveClass",
            query = """
                query academicProgramLiveClass(${'$'}id: String!) {
                  academicProgramLiveClass(id: ${'$'}id) {
                    academic_program_id
                    batch_ids
                    chapter {
                      id
                      name
                      no
                      __typename
                    }
                    class_type
                    end_time
                    id
                    on_going
                    playback_url
                    start_time
                    study_materials {
                      file_url
                      id
                      name
                    }
                    subject {
                      attr
                      class
                      code
                      color_code
                      display
                      display_bn
                      group
                      icon
                      __typename
                    }
                    __typename
                  }
                }
            """.trimIndent(),
            variables = mapOf("id" to liveClassId)
        )
        val res = apiService.getAcademicLiveClassDetails(query)
        return res.data?.academicProgramLiveClass
    }

    suspend fun getTeacherDetails(teacherId: String): TeacherItem? {
        val tQuery = GraphQlQuery(
            operationName = "GetTeacherDetails",
            query = """
                query GetTeacherDetails(${'$'}teacher_id: String!) {
                  teacher(teacher_id: ${'$'}teacher_id) {
                    id
                    first_name
                    last_name
                    avatar
                    marketing_avatar
                    university_degree
                    marketing_points
                    bio
                    cover_photo
                    subjects_taken {
                      code
                      icon
                      display_bn
                    }
                    color_code
                    teacher_experience
                    total_students_taught
                    consumed_video_hours
                  }
                }
            """.trimIndent(),
            variables = mapOf("teacher_id" to teacherId)
        )
        val tRes = apiService.getTeacherDetails(tQuery)
        return tRes.data?.teacher
    }

    suspend fun getTopics(chapterId: String, topicIds: List<String>? = null): List<TopicFullItem> {
        val topQuery = if (topicIds.isNullOrEmpty()) {
            GraphQlQuery(
                operationName = "GetTopics",
                query = """
                    query GetTopics(${'$'}chapter_id: String!) {
                      topics(chapter_id: ${'$'}chapter_id, filter: { limit: 150 } ) {
                        data {
                          id
                          no
                          name
                          description
                          subscription_type
                          session {
                            progress
                          }
                          videos {
                            data {
                              id
                              playback_url
                              video_thumbnail_url
                              category
                            }
                          }
                          header {
                            chapter_id
                            chapter_name
                          }
                        }
                      }
                    }
                """.trimIndent(),
                variables = mapOf("chapter_id" to chapterId)
            )
        } else {
            GraphQlQuery(
                operationName = "GetTopics",
                query = """
                    query GetTopics(${'$'}chapter_id: String!, ${'$'}topic_ids: [String]) {
                      topics(chapter_id: ${'$'}chapter_id, topic_ids: ${'$'}topic_ids, filter: { limit: 150 } ) {
                        data {
                          id
                          no
                          name
                          description
                          subscription_type
                          session {
                            progress
                          }
                          videos {
                            data {
                              id
                              playback_url
                              video_thumbnail_url
                              category
                            }
                          }
                          header {
                            chapter_id
                            chapter_name
                          }
                        }
                      }
                    }
                """.trimIndent(),
                variables = mapOf("chapter_id" to chapterId, "topic_ids" to topicIds)
            )
        }
        val topRes = apiService.getTopics(topQuery)
        return topRes.data?.topics?.data ?: emptyList()
    }

    suspend fun joinLiveClass(liveClassId: String, lessonId: String): JoinLiveClassPayload? {
        val joinQuery = GraphQlQuery(
            operationName = "JoinLiveClass",
            query = """
                mutation JoinLiveClass(${'$'}id: String!, ${'$'}lesson_id: String) {
                  joinLiveCLass(id: ${'$'}id, lesson_id: ${'$'}lesson_id) {
                    join_link
                    provider
                    hms_room_id
                    __typename
                  }
                }
            """.trimIndent(),
            variables = mapOf(
                "id" to liveClassId,
                "lesson_id" to lessonId
            )
        )
        val response = apiService.joinLiveClass(joinQuery)
        return response.data?.joinLiveCLass
    }

    suspend fun getHmsToken(roomId: String): HmsTokenResponse {
        return apiService.getHmsToken(HmsTokenRequest(room_id = roomId, type = "android"))
    }
}
