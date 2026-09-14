package com.example.quiz

import android.util.Log
import com.example.api.*
import com.example.auth.SessionManager

class PracticeQuizRepository(
    private val apiService: ShikhoApiService,
    private val sessionManager: SessionManager
) {
    companion object {
        private const val TAG = "PracticeQuizRepo"
    }

    /**
     * 1. Get Subject Hierarchy with Question Counts
     * Edge-cases handled:
     * - Filter out corrupt items (id.isBlank(), should_render != true, negative question count)
     * - Dynamic class and group mapping from user session
     */
    suspend fun getSubjectChaptersWithQuestionCounts(
        subjectCode: String,
        subjectTitle: String?
    ): Pair<SubjectHierarchyItem?, List<HierarchyChapterItem>> {
        val rawClass = sessionManager.getUserClassName() ?: "C11"
        val rawGroup = sessionManager.getUserGroup() ?: "Humanities"

        val classEnumVal = when (rawClass.uppercase()) {
            "C11", "C12", "HSC" -> "HSC"
            "C9", "C10", "SSC" -> "SSC"
            "C6" -> "C6"
            "C7" -> "C7"
            "C8" -> "C8"
            else -> rawClass
        }

        val groupEnumVal = when (rawGroup.lowercase()) {
            "science", "sci" -> "Science"
            "humanities", "arts", "hum" -> "Humanities"
            "business_studies", "business", "commerce" -> "Business_Studies"
            "none", "" -> "None"
            else -> if (classEnumVal in listOf("C6", "C7", "C8")) "None" else rawGroup
        }

        val queryStr = """
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
        """.trimIndent()

        // Attempt with dynamic class and group, with graceful fallback to alternative combinations if empty
        val attempts = listOf(
            mapOf("class" to classEnumVal, "group" to groupEnumVal),
            mapOf("class" to "HSC", "group" to "Humanities"),
            mapOf("class" to classEnumVal, "group" to "None")
        ).distinct()

        for (vars in attempts) {
            try {
                val query = GraphQlQuery(
                    operationName = "GetSubjectHierarchyWithQuestionCounts",
                    query = queryStr,
                    variables = vars
                )
                val response = apiService.getSubjectHierarchyWithQuestionCounts(query)
                val subjectList = response.data?.subjectHierarchyWithQuestionCounts?.data ?: emptyList()

                val matchedSubject = subjectList.find { it.code.equals(subjectCode, ignoreCase = true) }
                    ?: subjectList.find { !subjectTitle.isNullOrBlank() && it.display_bn?.trim() == subjectTitle.trim() }
                    ?: subjectList.find { !subjectTitle.isNullOrBlank() && it.display?.trim() == subjectTitle.trim() }

                if (matchedSubject != null) {
                    val validChapters = matchedSubject.chapters?.filter { ch ->
                        ch.id.isNotBlank() &&
                                ch.should_render == true &&
                                (ch.total_active_questions ?: 0) >= 0
                    } ?: emptyList()

                    if (validChapters.isNotEmpty() || (matchedSubject.total_active_questions ?: 0) > 0) {
                        return Pair(matchedSubject, validChapters)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Hierarchy attempt failed with vars: $vars", e)
            }
        }

        return Pair(null, emptyList())
    }

    /**
     * Check practice quiz access limits
     */
    suspend fun checkPracticeQuizAccess(): CustomPracticeLimits? {
        return try {
            val query = GraphQlQuery(
                operationName = "getPracticeQuizAccess",
                query = """
                    query getPracticeQuizAccess {
                      getPracticeQuizAccess {
                        custom_practice_limits {
                          has_limit
                          limit_per_day
                          used_today
                        }
                      }
                    }
                """.trimIndent()
            )
            val res = apiService.getPracticeQuizAccess(query)
            res.data?.getPracticeQuizAccess?.custom_practice_limits
        } catch (e: Exception) {
            Log.w(TAG, "checkPracticeQuizAccess error", e)
            null
        }
    }

    /**
     * Start user-defined Practice Quiz session
     */
    suspend fun startPracticeQuizSession(
        subjectIds: List<String>?,
        chapterIds: List<String>,
        totalCount: Int
    ): PracticeQuizSessionItem {
        val queryStr = """
            mutation StartPracticeQuizMcqSession(
              ${'$'}subject_ids: [String], ${'$'}chapter_ids: [String], ${'$'}topic_ids: [String],
              ${'$'}live_class_id: String, ${'$'}mcq_quiz_type: McqQuizTypeEnum,
              ${'$'}total_mcq_count: Int, ${'$'}difficulty_level: StartUserDefinedMcqExamDifficultyLevelInfo
            ) {
              startPracticeQuizMcqSession(
                subject_ids: ${'$'}subject_ids, chapter_ids: ${'$'}chapter_ids, topic_ids: ${'$'}topic_ids,
                live_class_id: ${'$'}live_class_id, mcq_quiz_type: ${'$'}mcq_quiz_type,
                total_mcq_count: ${'$'}total_mcq_count, difficulty_level: ${'$'}difficulty_level
              ) {
                message
                session {
                  id
                  start_time
                  expiry_time
                  is_final_submitted
                  is_started
                  is_timeout
                  last_submission_time
                  last_submitted_index
                  quiz_type
                  set_id
                  user_id
                  exam_id
                  question_answer { id given_ans is_submitted start_time submit_time }
                  questions {
                    id question_no title description difficulty_level allocated_marks
                    allocated_time has_math_equation markdown_version question_type source u_code
                    mcq_options { no description }
                  }
                }
              }
            }
        """.trimIndent()

        val variables = mutableMapOf<String, Any?>(
            "chapter_ids" to chapterIds,
            "mcq_quiz_type" to "UserDefinedMcq",
            "total_mcq_count" to totalCount
        )
        if (!subjectIds.isNullOrEmpty()) {
            variables["subject_ids"] = subjectIds
        }

        val query = GraphQlQuery(
            operationName = "StartPracticeQuizMcqSession",
            query = queryStr,
            variables = variables
        )

        val response = apiService.startPracticeQuizMcqSession(query)
        val session = response.data?.startPracticeQuizMcqSession?.session
            ?: throw IllegalStateException(response.data?.startPracticeQuizMcqSession?.message ?: "কুইজ সেশন শুরু করা সম্ভব হয়নি")
        return session
    }

    /**
     * Get or Resume existing MCQ session
     */
    suspend fun getMcqSession(sessionId: String): PracticeQuizSessionItem? {
        val queryStr = """
            query GetMcqSession(${'$'}session_id: String!) {
              getMcqSession(session_id: ${'$'}session_id) {
                message
                session {
                  exam_id expiry_time id is_final_submitted last_submitted_index
                  question_answer { given_ans id is_submitted submit_time }
                  questions { id mcq_options { description no } title }
                  title quiz_type
                }
              }
            }
        """.trimIndent()

        val query = GraphQlQuery(
            operationName = "GetMcqSession",
            query = queryStr,
            variables = mapOf("session_id" to sessionId)
        )

        val response = apiService.getMcqSession(query)
        return response.data?.getMcqSession?.session
    }

    /**
     * Submit answer progress (isFinal = false) or Final Submission (isFinal = true)
     */
    suspend fun submitPracticeQuizSession(
        sessionId: String,
        isFinal: Boolean,
        isTimeout: Boolean,
        questionAnswers: List<Map<String, Any?>>?
    ): PracticeQuizSessionPayload? {
        Log.d(TAG, "submitPracticeQuizSession: sessionId=$sessionId, isFinal=$isFinal, isTimeout=$isTimeout, answersCount=${questionAnswers?.size ?: 0}")

        val queryStr = """
            mutation SubmitPracticeQuizMcqSession(
              ${'$'}id: String!, ${'$'}is_final_submitted: Boolean, ${'$'}is_timeout: Boolean,
              ${'$'}question_answer: [UpdateQuizMcqSessionsQuestionAnswer]
            ) {
              submitPracticeQuizMcqSession(
                id: ${'$'}id, is_final_submitted: ${'$'}is_final_submitted,
                is_timeout: ${'$'}is_timeout, question_answer: ${'$'}question_answer
              ) {
                message
                session {
                  exam_id expiry_time id is_final_submitted last_submitted_index
                  question_answer { given_ans id is_submitted submit_time }
                  questions { id }
                }
              }
            }
        """.trimIndent()

        val variables = mutableMapOf<String, Any?>(
            "id" to sessionId,
            "is_final_submitted" to isFinal,
            "is_timeout" to isTimeout
        )
        if (questionAnswers != null) {
            variables["question_answer"] = questionAnswers
        }

        val query = GraphQlQuery(
            operationName = "SubmitPracticeQuizMcqSession",
            query = queryStr,
            variables = variables
        )

        val response = apiService.submitPracticeQuizMcqSession(query)
        if (!response.errors.isNullOrEmpty()) {
            Log.e(TAG, "SubmitPracticeQuizMcqSession GraphQL errors: ${response.errors}")
        }
        val result = response.data?.submitPracticeQuizMcqSession
        Log.d(TAG, "submitPracticeQuizSession completed, message=${result?.message}, sessionIsFinal=${result?.session?.is_final_submitted}")
        return result
    }

    /**
     * Get quiz result summary (Badge, score, breakdown)
     */
    suspend fun getQuizResultSummary(sessionId: String): QuizResultSummaryPayload {
        val queryStr = """
            query GetQuizResult(${'$'}id: String!) {
              getQuizResultSummery(id: ${'$'}id) {
                badge id badge_image_base_url badge_image_name quiz_type
                total_correct total_incorrect total_questions parent_id
                chapters { id }
                topics { id }
                subjects { code icon }
                subject_results { code proficiency title title_bn total_correct total_in_correct total_questions }
                total_spent_time
              }
            }
        """.trimIndent()

        val query = GraphQlQuery(
            operationName = "GetQuizResult",
            query = queryStr,
            variables = mapOf("id" to sessionId)
        )

        val response = apiService.getQuizResultSummary(query)
        return response.data?.getQuizResultSummery
            ?: throw IllegalStateException("ফলাফল লোড করা সম্ভব হয়নি")
    }

    /**
     * Get full MCQ session feedback (Questions + correct answers + solution/explanations)
     */
    suspend fun getMcqSessionFeedback(sessionId: String): PracticeQuizSessionItem {
        val queryStr = """
            query getMcqSessionFeedback(${'$'}sessionId: String!) {
              getMcqSessionFeedback(session_id: ${'$'}sessionId) {
                message
                session {
                  exam_id expiry_time id init_time is_final_submitted is_started is_timeout
                  last_submission_time last_submitted_index
                  question_answer { correct_ans given_ans id is_correct is_submitted submit_time is_saved }
                  questions {
                    allocated_marks allocated_time
                    chapter { id name no }
                    class correct_option created_at description difficulty_level given_ans
                    has_math_equation id is_active markdown_version
                    mcq_options { description no }
                  }
                }
              }
            }
        """.trimIndent()

        val query = GraphQlQuery(
            operationName = "getMcqSessionFeedback",
            query = queryStr,
            variables = mapOf("sessionId" to sessionId)
        )

        val response = apiService.getMcqSessionFeedback(query)
        return response.data?.getMcqSessionFeedback?.session
            ?: throw IllegalStateException("সলিউশন লোড করা সম্ভব হয়নি")
    }

    /**
     * Bookmark / Save a question
     */
    suspend fun createSavedQuestion(sessionId: String, questionId: String): Boolean {
        val queryStr = """
            mutation CreateSavedQuestion(${'$'}session_id: String = "", ${'$'}question_id: String = "") {
              createSavedQuestion(question_id: ${'$'}question_id, session_id: ${'$'}session_id) {
                allocated_marks allocated_time
                chapter { id name no }
                class correct_option description created_at difficulty_level given_answer
                has_math_equation id is_correct is_deleted is_submitted markdown_version
                mcq_options { description no }
                question_id question_type session_id solution source
                subject { code display display_bn id }
                title updated_at
              }
            }
        """.trimIndent()

        val query = GraphQlQuery(
            operationName = "CreateSavedQuestion",
            query = queryStr,
            variables = mapOf(
                "session_id" to sessionId,
                "question_id" to questionId
            )
        )

        val response = apiService.createSavedQuestion(query)
        return response.data?.createSavedQuestion?.id != null
    }
}
