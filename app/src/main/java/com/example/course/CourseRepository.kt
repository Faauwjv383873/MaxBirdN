package com.example.course

import com.example.api.*

/**
 * Encapsulates GraphQL query definitions and remote data fetching methods for Course and Lesson flows.
 */
class CourseRepository(
    private val apiService: ShikhoApiService
) {

    suspend fun getAcademicProgramByEnrollment(className: String): AcademicProgramResponse {
        val query = GraphQlQuery(
            operationName = "GetEnrolledAcademicProgram",
            query = """
                query GetEnrolledAcademicProgram(${'$'}className: AcademicProgramClassEnum, ${'$'}group: StudyGroupTypeEnum) {
                  listAcademicProgramByEnrollment(class: ${'$'}className, group: ${'$'}group) {
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
                "className" to className,
                "group" to null
            )
        )
        return apiService.getAcademicProgram(query)
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

    suspend fun fetchLessonsWithPhase(
        chapterId: String,
        programId: String,
        phaseId: String
    ): List<StudentLessonItem> {
        try {
            val q1 = GraphQlQuery(
                operationName = "GetUpcomingLessonsPhaseWise",
                query = """
                    query GetUpcomingLessonsPhaseWise(${'$'}chapter_id: String!, ${'$'}program_id: String!, ${'$'}phase_id: String!) {
                      studentSpecificLessons(program_id: ${'$'}program_id, chapter_id: ${'$'}chapter_id, phase_id: ${'$'}phase_id) {
                        data {
                          access_level
                          subject_name
                          start_time
                          end_time
                          content_type
                          id
                          content_id
                          subject_id
                          batch_id
                          chapter_id
                          icon
                          color_code
                          user_activity_state
                          hw_type
                          title
                          live_class {
                            chapter_id
                            chapter_name
                            end_time
                            is_on_going
                            recording_url
                            start_time
                            subject_name
                            topics {
                              id
                              name
                            }
                            subject_id
                            id
                            type
                          }
                          model_test {
                            type
                            exam_category
                            result_publish_time
                          }
                          topics {
                            id
                            name
                          }
                          phase_id
                        }
                      }
                    }
                """.trimIndent(),
                variables = mapOf(
                    "chapter_id" to chapterId,
                    "program_id" to programId,
                    "phase_id" to phaseId
                )
            )
            val res = apiService.getStudentLessons(q1)
            return res.data?.studentSpecificLessons?.data ?: emptyList()
        } catch (e: Exception) {
            return emptyList()
        }
    }

    suspend fun fetchLessonsStandard(
        chapterId: String,
        programId: String
    ): List<StudentLessonItem> {
        try {
            val q1 = GraphQlQuery(
                operationName = "GetUpcomingLessons",
                query = """
                    query GetUpcomingLessons(${'$'}chapter_id: String!, ${'$'}program_id: String!) {
                      studentSpecificLessons(program_id: ${'$'}program_id, chapter_id: ${'$'}chapter_id) {
                        data {
                          access_level
                          subject_name
                          start_time
                          end_time
                          content_type
                          id
                          content_id
                          subject_id
                          batch_id
                          chapter_id
                          icon
                          color_code
                          user_activity_state
                          hw_type
                          title
                          live_class {
                            chapter_id
                            chapter_name
                            end_time
                            is_on_going
                            recording_url
                            start_time
                            subject_name
                            topics {
                              id
                              name
                            }
                            subject_id
                            id
                            type
                          }
                          model_test {
                            type
                            exam_category
                            result_publish_time
                          }
                          topics {
                            id
                            name
                          }
                          phase_id
                        }
                      }
                    }
                """.trimIndent(),
                variables = mapOf(
                    "chapter_id" to chapterId,
                    "program_id" to programId
                )
            )
            val res = apiService.getStudentLessons(q1)
            return res.data?.studentSpecificLessons?.data ?: emptyList()
        } catch (e: Exception) {
            return emptyList()
        }
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
