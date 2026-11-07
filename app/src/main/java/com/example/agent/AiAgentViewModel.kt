package com.example.agent

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.MainActivity
import com.example.R
import com.example.api.EnrolledProgram
import com.example.api.ShikhoApiService
import com.example.auth.SessionManager
import com.example.notification.ShikhoNotificationManager
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.UUID
import kotlin.random.Random

private const val TAG = "AiAgentViewModel"

sealed interface AgentBrainState {
    data object Idle : AgentBrainState
    data object Thinking : AgentBrainState
    data class Success(val replyText: String) : AgentBrainState
    data class Error(val message: String) : AgentBrainState
}

data class AiAgentUiState(
    val isLoadingInitial: Boolean = false,
    val brainState: AgentBrainState = AgentBrainState.Idle,
    val messages: List<AiAgentChatMessage> = emptyList(),
    val chatSessions: List<AiChatSession> = emptyList(),
    val activeSessionId: String = "",
    val githubConfig: GitHubExtensionConfig = GitHubExtensionConfig(),
    val githubRepos: List<GitHubRepoSummary> = emptyList(),
    val workflowRuns: List<GitHubWorkflowRunInfo> = emptyList(),
    val telegramConfig: TelegramExtensionConfig = TelegramExtensionConfig(),
    val enrolledPrograms: List<EnrolledProgram> = emptyList(),
    val selectedProgramId: String = "",
    val selectedProgramTitle: String = "",
    val selectedPhaseId: String = "", // "" = All Quarters (সব কোয়ার্টার)
    val selectedPhaseTitle: String = "সব কোয়ার্টার",
    val selectedSubjectCode: String = "",
    val selectedSubjectTitle: String = "",
    val activeInspection: CourseInspectionSummary? = null,
    val isInspectingCourse: Boolean = false,
    val tasks: List<AiAgentTask> = emptyList(),
    val schedules: List<AiAgentSchedule> = emptyList(),
    val statusBannerMessage: String? = null,
    val isVerifyingGitHub: Boolean = false,
    val isDeployingWorkflow: Boolean = false,
    val isProcessingTelegramAuth: Boolean = false,
    val isDispatchingTask: Boolean = false,
    val isResolvingLessonMedia: Boolean = false,
    val pendingAutoPlayLesson: AgentPlayableLesson? = null,
    val deviceVaultId: String = ""
)

class AiAgentViewModel(
    private val appContext: Context,
    private val repository: AiAgentRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiAgentUiState())
    val uiState: StateFlow<AiAgentUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null
    private var tgAuthPollingJob: Job? = null

    private val agentResponseSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "reply_bn" to Schema.string(
                    description = "Detailed, helpful response in Bengali. Can include markdown code blocks (``` ... ```) for structured summaries, tables, or command previews."
                ),
                "action_type" to Schema.enumeration(
                    values = listOf(
                        "DISCUSS",
                        "ASK_CONFIRMATION",
                        "PROPOSE_PLAN",
                        "START_UPLOAD_TASK",
                        "PLAY_VIDEO",
                        "SHOW_CHANNELS",
                        "CREATE_SCHEDULE",
                        "CREATE_TELEGRAM_CHANNEL"
                    ),
                    description = "Action: DISCUSS for chat, ASK_CONFIRMATION / PROPOSE_PLAN to ask permission & show channel/group picker before uploading, START_UPLOAD_TASK when user confirms (হ্যাঁ/শুরু করো) or demands immediate start, PLAY_VIDEO when user asks to play/watch a class video or open slides, SHOW_CHANNELS to show user's Telegram channels/groups, CREATE_SCHEDULE for 24/7 routine, CREATE_TELEGRAM_CHANNEL to create a channel."
                ),
                "task_title" to Schema.string(description = "Short Bengali title for the task or schedule"),
                "phase_id" to Schema.string(description = "Target Quarter/Phase ID if user specified a Quarter (e.g. কোয়ার্টার ১), or empty string for all quarters"),
                "phase_title" to Schema.string(description = "Target Quarter/Phase title in Bengali, or empty string"),
                "subject_code" to Schema.string(description = "Target subject code if user specified a subject, or empty string for all subjects"),
                "subject_title" to Schema.string(description = "Target subject title in Bengali, or empty string"),
                "chapter_title" to Schema.string(description = "Target chapter title in Bengali if specified, or empty string"),
                "video_search_query" to Schema.string(description = "Keywords or lesson title to search and play when action_type is PLAY_VIDEO"),
                "auto_play_video" to Schema.boolean(description = "True if the user explicitly asked to play/launch the video immediately"),
                "target_channel_title" to Schema.string(description = "Suggested or user-requested Telegram channel or group name"),
                "target_chat_id" to Schema.string(description = "Matched Telegram chat ID from user's synced dialogs if user named an existing group/channel, or empty string"),
                "target_topic_id" to Schema.integer(description = "Matched Telegram forum topic ID if user named a specific topic, or 0"),
                "target_topic_title" to Schema.string(description = "Matched Telegram forum topic title if specified, or empty string"),
                "video_quality" to Schema.enumeration(
                    values = listOf("1080p", "720p", "480p", "360p"),
                    description = "Video resolution quality"
                ),
                "include_videos" to Schema.boolean(description = "Whether to upload lecture videos"),
                "include_slides" to Schema.boolean(description = "Whether to upload PDF lecture slides and notes"),
                "create_new_channel" to Schema.boolean(description = "True to create a new Telegram channel/group, False if uploading to an existing group/channel"),
                "use_forum_topics" to Schema.boolean(description = "True for Telegram Supergroup with Topics, False for standard broadcast Channel"),
                "schedule_time_bn" to Schema.string(description = "Human-readable Bengali schedule time, e.g., প্রতিদিন সকাল ০৮:০০"),
                "cron_expression" to Schema.string(description = "Standard 5-field UTC cron expression, e.g. 0 2 * * *")
            ),
            optionalProperties = listOf(
                "phase_id",
                "phase_title",
                "subject_code",
                "subject_title",
                "chapter_title",
                "video_search_query",
                "auto_play_video",
                "target_channel_title",
                "target_chat_id",
                "target_topic_id",
                "target_topic_title",
                "schedule_time_bn",
                "cron_expression"
            )
        )
    }

    private val generativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = agentResponseSchema
            }
        )
    }

    init {
        initializeAgentWorkspace()
    }

    private fun buildDefaultWelcomeMessage(courseTitle: String): AiAgentChatMessage {
        return AiAgentChatMessage(
            id = "welcome_${UUID.randomUUID()}",
            role = "model",
            text = "আসসালামু আলাইকুম বস! 🤖 আমি আপনার **MaxBird AI Agent (Gemini 3.8 Flash)**।\n\n" +
                "আমার সাথে আপনার **GitHub 24/7 Cloud Worker**, **Telegram Account** এবং **$courseTitle (সকল কোয়ার্টার)** যুক্ত রয়েছে।\n\n" +
                "```text\n" +
                "⚡ আমি আপনার জন্য যা যা করতে পারি:\n" +
                "১. যেকোনো কোয়ার্টারের যেকোনো ক্লাস ভিডিও সরাসরি চ্যাট থেকে প্লে (▶️) ও স্লাইড ওপেন\n" +
                "২. আপনার যেকোনো টেলিগ্রাম গ্রুপ/চ্যানেল বা নির্দিষ্ট টপিকে ১-২ মিনিটে সুপার-ফাস্ট ভিডিও ও স্লাইড আপলোড\n" +
                "৩. যেকোনো নাম দিয়ে নতুন টেলিগ্রাম চ্যানেল বা সুপারগ্রুপ তৈরি\n" +
                "৪. প্রতিদিন নির্ধারিত সময়ে ২৪/৭ অটোমেটিক ক্লাস সিঙ্ক রুটিন\n" +
                "```\n\n" +
                "আপনি আমাকে সরাসরি বলুন—কোন ভিডিওটি প্লে করব নাকি কোন গ্রুপে ক্লাস আপলোড করব?"
        )
    }

    fun initializeAgentWorkspace() {
        viewModelScope.launch {
            val activeProgramId = sessionManager.getActiveProgramId().orEmpty()
            val activeProgramTitle = sessionManager.getActiveProgramTitleBn() ?: "সক্রিয় কোর্স"

            // Load persisted Gemini-style chat sessions
            val savedSessions = repository.getChatSessions().toMutableList()
            val savedActiveId = repository.getActiveChatSessionId()
            val currentSession: AiChatSession = savedSessions.firstOrNull { it.id == savedActiveId }
                ?: savedSessions.firstOrNull()
                ?: AiChatSession(
                    id = "session_${System.currentTimeMillis()}",
                    title = "নতুন কথোপকথন",
                    programId = activeProgramId,
                    programTitle = activeProgramTitle,
                    messages = listOf(buildDefaultWelcomeMessage(activeProgramTitle))
                ).also { newSess ->
                    repository.saveChatSession(newSess)
                    savedSessions.add(0, newSess)
                }

            repository.setActiveChatSessionId(currentSession.id)

            _uiState.update {
                it.copy(
                    isLoadingInitial = true,
                    chatSessions = savedSessions,
                    activeSessionId = currentSession.id,
                    messages = currentSession.messages.ifEmpty {
                        listOf(buildDefaultWelcomeMessage(activeProgramTitle))
                    },
                    githubConfig = repository.getGitHubConfig(),
                    telegramConfig = repository.getTelegramConfig(),
                    deviceVaultId = repository.getCurrentDeviceHash(),
                    selectedProgramId = activeProgramId,
                    selectedProgramTitle = activeProgramTitle
                )
            }

            // 1. Restore & sync Device-Bound Cloud Vault from Supabase
            repository.syncAgentConfigWithSupabase()
            val syncedGh = repository.getGitHubConfig()
            val syncedTg = repository.getTelegramConfig()
            _uiState.update {
                it.copy(
                    githubConfig = syncedGh,
                    telegramConfig = syncedTg
                )
            }
            if (syncedTg.authState in listOf("SENDING_CODE", "VERIFYING_OTP")) {
                startTelegramAuthPolling()
            }

            // 2. Load courses, tasks, schedules, and inspect all quarters of active course
            launch {
                val programs = repository.fetchEnrolledProgramsForAgent()
                val activeId = _uiState.value.selectedProgramId.ifBlank {
                    programs.firstOrNull()?.id.orEmpty()
                }
                val activeTitle = _uiState.value.selectedProgramTitle.takeIf { it.isNotBlank() && it != "সক্রিয় কোর্স" }
                    ?: programs.firstOrNull { it.id == activeId }?.title_bn
                    ?: programs.firstOrNull()?.title_bn
                    ?: "সক্রিয় কোর্স"

                _uiState.update {
                    it.copy(
                        enrolledPrograms = programs,
                        selectedProgramId = activeId,
                        selectedProgramTitle = activeTitle,
                        githubConfig = repository.getGitHubConfig(),
                        telegramConfig = repository.getTelegramConfig()
                    )
                }
                if (activeId.isNotBlank()) {
                    inspectCourse(activeId, activeTitle, _uiState.value.selectedPhaseId.ifBlank { null })
                }
            }
            refreshTasksAndSchedules()
            startLiveTaskPolling()
            _uiState.update { it.copy(isLoadingInitial = false) }
        }
    }

    // =========================================================================
    // MULTI-SESSION CHAT HISTORY MANAGEMENT (GEMINI-STYLE SESSIONS)
    // =========================================================================

    fun startNewChatSession() {
        val courseTitle = _uiState.value.selectedProgramTitle.ifBlank { "সক্রিয় কোর্স" }
        val welcome = buildDefaultWelcomeMessage(courseTitle)
        val now = System.currentTimeMillis()
        val newSession = AiChatSession(
            id = "session_$now",
            title = "নতুন চ্যাট (${courseTitle.take(18)})",
            createdAt = now,
            updatedAt = now,
            programId = _uiState.value.selectedProgramId,
            programTitle = courseTitle,
            messages = listOf(welcome)
        )
        repository.saveChatSession(newSession)
        val updatedList = repository.getChatSessions()
        _uiState.update {
            it.copy(
                activeSessionId = newSession.id,
                chatSessions = updatedList,
                messages = newSession.messages,
                brainState = AgentBrainState.Idle
            )
        }
    }

    fun selectChatSession(sessionId: String) {
        val target = _uiState.value.chatSessions.firstOrNull { it.id == sessionId }
            ?: repository.getChatSessions().firstOrNull { it.id == sessionId }
            ?: return
        repository.setActiveChatSessionId(target.id)
        _uiState.update {
            it.copy(
                activeSessionId = target.id,
                messages = target.messages.ifEmpty {
                    listOf(buildDefaultWelcomeMessage(it.selectedProgramTitle))
                },
                brainState = AgentBrainState.Idle
            )
        }
    }

    fun deleteChatSession(sessionId: String) {
        val remaining = repository.deleteChatSession(sessionId)
        if (remaining.isEmpty()) {
            startNewChatSession()
        } else {
            val nextActive = if (_uiState.value.activeSessionId == sessionId) {
                remaining.first()
            } else {
                remaining.firstOrNull { it.id == _uiState.value.activeSessionId } ?: remaining.first()
            }
            repository.setActiveChatSessionId(nextActive.id)
            _uiState.update {
                it.copy(
                    chatSessions = remaining,
                    activeSessionId = nextActive.id,
                    messages = nextActive.messages
                )
            }
        }
    }

    private fun persistCurrentSessionMessages(updatedMessages: List<AiAgentChatMessage>) {
        val state = _uiState.value
        val cleanMessages = updatedMessages.filterNot { it.isStreaming }
        if (cleanMessages.isEmpty()) return

        val now = System.currentTimeMillis()
        val sessionId = state.activeSessionId.ifBlank { "session_$now" }
        val existing = state.chatSessions.firstOrNull { it.id == sessionId }
        val firstUserText = cleanMessages.firstOrNull { it.role == "user" }?.text?.trim().orEmpty()
        val computedTitle = when {
            firstUserText.isNotBlank() -> firstUserText.replace("\n", " ").take(42)
            existing != null && existing.title.isNotBlank() -> existing.title
            else -> "নতুন কথোপকথন"
        }

        val sessionObj = AiChatSession(
            id = sessionId,
            title = computedTitle,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            programId = state.selectedProgramId,
            programTitle = state.selectedProgramTitle,
            messages = cleanMessages
        )
        repository.saveChatSession(sessionObj)
        val refreshedSessions = repository.getChatSessions()
        _uiState.update {
            it.copy(
                activeSessionId = sessionId,
                chatSessions = refreshedSessions
            )
        }
    }

    // =========================================================================
    // COURSE, QUARTER & SUBJECT SELECTION
    // =========================================================================

    fun selectProgramForAgent(programId: String, programTitle: String) {
        _uiState.update {
            it.copy(
                selectedProgramId = programId,
                selectedProgramTitle = programTitle,
                selectedPhaseId = "",
                selectedPhaseTitle = "সব কোয়ার্টার",
                selectedSubjectCode = "",
                selectedSubjectTitle = ""
            )
        }
        inspectCourse(programId, programTitle, null)
    }

    fun selectQuarterFilter(phaseId: String, phaseTitle: String) {
        val cleanTitle = phaseTitle.ifBlank { "সব কোয়ার্টার" }
        _uiState.update {
            it.copy(
                selectedPhaseId = phaseId,
                selectedPhaseTitle = cleanTitle,
                selectedSubjectCode = "",
                selectedSubjectTitle = ""
            )
        }
        val progId = _uiState.value.selectedProgramId
        val progTitle = _uiState.value.selectedProgramTitle
        if (progId.isNotBlank()) {
            inspectCourse(progId, progTitle, phaseId.ifBlank { null })
        }
    }

    fun selectSubjectFilter(subjectCode: String, subjectTitle: String) {
        _uiState.update {
            it.copy(
                selectedSubjectCode = subjectCode,
                selectedSubjectTitle = subjectTitle
            )
        }
    }

    fun inspectCourse(programId: String, programTitle: String, phaseId: String? = null) {
        if (programId.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isInspectingCourse = true) }
            val summary = repository.inspectProgramContent(
                programId = programId,
                programTitle = programTitle,
                phaseId = phaseId?.takeIf { it.isNotBlank() }
            )
            _uiState.update {
                it.copy(
                    activeInspection = summary,
                    isInspectingCourse = false
                )
            }
        }
    }

    // =========================================================================
    // IN-CHAT VIDEO PLAYER & SLIDE OPENER
    // =========================================================================

    fun clearPendingAutoPlayLesson() {
        _uiState.update { it.copy(pendingAutoPlayLesson = null) }
    }

    fun resolveAndPlayLesson(
        lesson: AgentPlayableLesson,
        onReady: (AgentPlayableLesson) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isResolvingLessonMedia = true) }
            try {
                val resolved = if (lesson.videoUrl.isNotBlank() && lesson.videoUrl.startsWith("http")) {
                    lesson
                } else {
                    repository.resolvePlayableLessonUrls(lesson)
                }
                _uiState.update { it.copy(isResolvingLessonMedia = false) }
                if (resolved.videoUrl.isBlank() && resolved.slideUrl.isBlank()) {
                    _uiState.update {
                        it.copy(statusBannerMessage = "⚠️ এই ক্লাসের রেকর্ডিং ভিডিও বা স্লাইড এখনো যুক্ত হয়নি।")
                    }
                } else {
                    onReady(resolved)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isResolvingLessonMedia = false,
                        statusBannerMessage = "⚠️ ক্লাস লোড করতে সমস্যা হয়েছে: ${e.message}"
                    )
                }
            }
        }
    }

    private fun searchPlayableLessonsInCatalog(
        catalog: List<AgentPlayableLesson>,
        query: String,
        subjectCode: String = "",
        phaseId: String = ""
    ): List<AgentPlayableLesson> {
        if (catalog.isEmpty()) return emptyList()
        val cleanQuery = query.trim().lowercase()
        val stopWords = setOf(
            "ভিডিও", "প্লে", "করো", "চালু", "দাও", "দেখাও", "ক্লাস", "লেকচার", "স্লাইড",
            "টা", "টি", "করে", "দেন", "ভাই", "এজেন্ট", "অমুক", "play", "video", "class", "open"
        )
        val tokens = cleanQuery
            .split(Regex("[\\s,।?!:;\\-]+"))
            .map { it.trim() }
            .filter { it.length >= 2 && it !in stopWords }

        val filteredByScope = catalog.filter { item ->
            val phaseOk = phaseId.isBlank() || item.phaseId == phaseId
            val subjOk = subjectCode.isBlank() || item.subjectCode.equals(subjectCode, ignoreCase = true)
            phaseOk && subjOk
        }.ifEmpty { catalog }

        if (tokens.isEmpty()) {
            return filteredByScope.take(5)
        }

        val scored = filteredByScope.mapNotNull { lesson ->
            val haystack = "${lesson.title} ${lesson.chapterTitle} ${lesson.subjectTitle} ${lesson.phaseTitle}".lowercase()
            var score = 0
            if (cleanQuery.isNotBlank() && haystack.contains(cleanQuery)) {
                score += 50
            }
            for (token in tokens) {
                if (lesson.title.lowercase().contains(token)) score += 15
                if (lesson.chapterTitle.lowercase().contains(token)) score += 10
                if (lesson.subjectTitle.lowercase().contains(token)) score += 8
                if (lesson.phaseTitle.lowercase().contains(token)) score += 5
            }
            if (score > 0) Pair(lesson, score) else null
        }.sortedByDescending { it.second }.map { it.first }

        return scored.ifEmpty { filteredByScope.take(4) }.take(6)
    }

    // =========================================================================
    // CONVERSATIONAL AI AGENT BRAIN (GEMINI 3.8 FLASH + MULTI-QUARTER + CONFIRMATION)
    // =========================================================================

    fun sendPromptToAgent(userPrompt: String) {
        val cleanPrompt = userPrompt.trim()
        if (cleanPrompt.isBlank()) return

        val userMsg = AiAgentChatMessage(
            id = UUID.randomUUID().toString(),
            role = "user",
            text = cleanPrompt
        )
        val thinkingMsgId = UUID.randomUUID().toString()
        val thinkingMsg = AiAgentChatMessage(
            id = thinkingMsgId,
            role = "model",
            text = "Gemini 3.8 Flash বিশ্লেষণ করছে...",
            isStreaming = true
        )

        _uiState.update {
            it.copy(
                brainState = AgentBrainState.Thinking,
                messages = it.messages + userMsg + thinkingMsg
            )
        }
        persistCurrentSessionMessages(_uiState.value.messages)

        viewModelScope.launch {
            try {
                val state = _uiState.value
                val inspection = state.activeInspection ?: run {
                    if (state.selectedProgramId.isNotBlank()) {
                        repository.inspectProgramContent(
                            programId = state.selectedProgramId,
                            programTitle = state.selectedProgramTitle,
                            phaseId = state.selectedPhaseId.takeIf { it.isNotBlank() }
                        ).also { summary ->
                            _uiState.update { s -> s.copy(activeInspection = summary) }
                        }
                    } else null
                }

                // Check if user is confirming a previously proposed plan in the same session!
                val previousProposedPlan = state.messages
                    .asReversed()
                    .firstOrNull { it.role == "model" && it.proposedPlan != null }
                    ?.proposedPlan

                val lowerPrompt = cleanPrompt.lowercase()
                val isShortConfirmation = previousProposedPlan != null && (
                    lowerPrompt in setOf("হ্যাঁ", "হ্যা", "জি", "জ্বী", "ওকে", "শুরু করো", "শুরু করেন", "পাঠাও", "পাঠান", "অনুমতি দিলাম", "yes", "ok", "start", "confirm") ||
                        (lowerPrompt.length <= 36 && (
                            lowerPrompt.contains("হ্যাঁ") ||
                                lowerPrompt.contains("অনুমতি") ||
                                lowerPrompt.contains("শুরু কর") ||
                                lowerPrompt.contains("ওকে") ||
                                lowerPrompt.contains("পাঠিয়ে দাও")
                            ))
                    )

                if (isShortConfirmation && previousProposedPlan != null) {
                    val confirmedPlan = previousProposedPlan.copy(actionType = "START_UPLOAD_TASK")
                    val confirmReply = "```text\n" +
                        "✅ অনুমতি নিশ্চিত হয়েছে!\n" +
                        "• টাস্ক: ${confirmedPlan.taskTitle}\n" +
                        "• গন্তব্য: ${confirmedPlan.targetChannelTitle}${if (confirmedPlan.targetTopicTitle.isNotBlank()) " -> ${confirmedPlan.targetTopicTitle}" else ""}\n" +
                        "• মোড: আল্ট্রা-ফাস্ট প্যারালাল আপলোড (১-২ মিনিট/ভিডিও)\n" +
                        "```\n" +
                        "🚀 বস, আপনার অনুমতির সাথে সাথেই আমি GitHub Actions-এ টাস্কটি চালু করে দিচ্ছি!"

                    val finalMsg = AiAgentChatMessage(
                        id = thinkingMsgId,
                        role = "model",
                        text = confirmReply,
                        proposedPlan = confirmedPlan,
                        isStreaming = false
                    )
                    _uiState.update { current ->
                        current.copy(
                            brainState = AgentBrainState.Success(confirmReply),
                            messages = current.messages.map { if (it.id == thinkingMsgId) finalMsg else it }
                        )
                    }
                    persistCurrentSessionMessages(_uiState.value.messages)
                    executeProposedPlan(confirmedPlan)
                    return@launch
                }

                val quartersText = inspection?.quarters?.joinToString("\n") { q ->
                    "- ${q.phaseTitle} (ID: ${q.phaseId}): ${q.lessonsCount} লেকচার, ${q.videosCount} ভিডিও, ${q.slidesCount} স্লাইড"
                }?.ifBlank { "একক ফেজ কোর্স" } ?: "লোড হয়নি"

                val subjectBreakdownText = inspection?.subjectBreakdown?.joinToString("\n") { sub ->
                    "- ${sub.subjectTitle} (Code: ${sub.subjectCode}): ${sub.lessonsCount} লেকচার, ${sub.videosCount} ভিডিও, ${sub.slidesCount} পিডিএফ স্লাইড"
                } ?: "কোনো বিষয় তালিকা লোড হয়নি"

                val telegramDialogsText = state.telegramConfig.dialogs.take(25).joinToString("\n") { d ->
                    val kind = when {
                        d.isForum -> "Forum Supergroup"
                        d.isMegagroup -> "Supergroup"
                        else -> "Channel"
                    }
                    val topicsInfo = if (d.topics.isNotEmpty()) {
                        " | Topics: " + d.topics.joinToString(", ") { "${it.title} (topicId=${it.id})" }
                    } else ""
                    "- ${d.title} (chatId=${d.id}, type=$kind$topicsInfo)"
                }.ifBlank { "কোনো পূর্ববর্তী চ্যানেল/গ্রুপ সিঙ্ক করা নেই (নতুন চ্যানেল খোলা যাবে)" }

                val candidateLessonsForPrompt = searchPlayableLessonsInCatalog(
                    catalog = inspection?.allLessonsCatalog.orEmpty(),
                    query = cleanPrompt,
                    subjectCode = state.selectedSubjectCode,
                    phaseId = state.selectedPhaseId
                )
                val lessonsPreviewText = candidateLessonsForPrompt.joinToString("\n") { l ->
                    "- [${l.phaseTitle}] ${l.subjectTitle} -> ${l.chapterTitle} -> ${l.title} (lessonId=${l.id})"
                }.ifBlank { "কোনো ক্লাস পাওয়া যায়নি" }

                val recentHistoryText = state.messages
                    .filterNot { it.isStreaming }
                    .takeLast(8)
                    .joinToString("\n") { m -> "${m.role.uppercase()}: ${m.text}" }

                val fullContextPrompt = """
                    You are MaxBird Autonomous AI Agent powered by Gemini 3.8 Flash inside the MaxBird Android App.
                    You speak fluent, smart, respectful Bengali (addressing the user as "বস") and you format structured data, plans, and status blocks using clean Markdown code blocks (```text ... ```) so the message looks super organized and coder-style!

                    Connected Ecosystem:
                    1. GitHub Actions 24/7 Cloud Worker: Connected=${state.githubConfig.isVerified}, Repo=${state.githubConfig.owner}/${state.githubConfig.repo}
                    2. Personal Telegram Account (Telethon MTProto): Connected=${state.telegramConfig.isConnected}, Account=${state.telegramConfig.accountDisplayName}, AuthState=${state.telegramConfig.authState}
                    3. User's Synced Telegram Channels, Supergroups & Forum Topics:
                    $telegramDialogsText
                    4. Active Course in Shikho Engine:
                       - Course: ${state.selectedProgramTitle} (ID: ${state.selectedProgramId})
                       - Active Quarter Filter: ${state.selectedPhaseTitle} (ID: ${state.selectedPhaseId.ifBlank { "ALL" }})
                       - Active Subject Filter: ${state.selectedSubjectTitle.ifBlank { "সকল বিষয়" }} (${state.selectedSubjectCode})
                       - Total Lessons across Quarters: ${inspection?.totalLessonsCount ?: 0} (Videos: ${inspection?.videoLessonsCount ?: 0}, Slides: ${inspection?.slideAttachmentsCount ?: 0})
                       - Quarters Breakdown (All 5+ Quarters):
                       $quartersText
                       - Subject Breakdown:
                       $subjectBreakdownText
                       - Matching/Sample Lessons from Course Catalog:
                       $lessonsPreviewText

                    CRITICAL BEHAVIOR RULES:
                    1. PLAY VIDEO / OPEN SLIDES (`action_type = "PLAY_VIDEO"`):
                       - If the user asks to play a video, watch a class, or find a specific lecture (e.g. "অমুক ভিডিওটা প্লে করে দাও", "ডেটাবেজ ম্যানেজমেন্ট ক্লাসটা চালাও", "বাংলা ১ম ক্লাস প্লে করো"), choose `PLAY_VIDEO`, set `video_search_query` to the topic/lesson keywords, and set `auto_play_video = true` if they asked to play it now.
                    2. PUBLIC GROUP WITH FORUM TOPICS (`use_forum_topics = true`):
                       - When the user asks to create a "গ্রুপ", "পাবলিক গ্রুপ", "সুপারগ্রুপ" or mentions creating a "টপিক" (e.g. "পাবলিক গ্রুপ তৈরি করে তার মধ্যে বাংলা ১ম পত্র নামে একটি টপিক তৈরি করে আপলোড করো"):
                         You MUST set `use_forum_topics = true` and `create_new_channel = true`!
                         Also set `target_topic_title` to the subject or topic title requested by the user (e.g. "বাংলা ১ম পত্র")!
                    3. ASK PERMISSION BEFORE UPLOADING (`action_type = "ASK_CONFIRMATION"` or `"PROPOSE_PLAN"`):
                       - Format a neat ```text ... ``` summary block showing the Course, Quarter, Subject, Video/Slide count, Target Group/Channel name, whether it's a Supergroup with Topics, and the Topic Name, and politely ask: "আপনি কি এই গ্রুপে/টপিকে আপলোড শুরু করার অনুমতি দিচ্ছেন?"
                    4. IMMEDIATE UPLOAD (`action_type = "START_UPLOAD_TASK"`):
                       - Only choose `START_UPLOAD_TASK` if the user explicitly confirms permission or says "এখনই আপলোড শুরু করো / অনুমতি দিলাম".
                    5. 24/7 RECURRING SCHEDULE (`action_type = "CREATE_SCHEDULE"`):
                       - If the user asks for automated recurring uploads at a specific time (e.g. "প্রতিদিন রাত ১টায় নতুন ক্লাস আপলোড করার রুটিন সেট করো", "সারাজীবন চেক করে নতুন ক্লাস আপলোড করবে"), choose `CREATE_SCHEDULE`, set `schedule_time_bn` to their chosen time (e.g. "প্রতিদিন রাত ০১:০০"), and compute the UTC `cron_expression` (e.g. 1:00 AM BD is 19:00 UTC previous day -> "0 19 * * *").
                    6. SHOW CHANNELS (`action_type = "SHOW_CHANNELS"`):
                       - If the user asks to see their connected Telegram groups, channels, or topics, choose `SHOW_CHANNELS`.

                    Recent Conversation History:
                    $recentHistoryText

                    User's New Message:
                    $cleanPrompt
                """.trimIndent()

                val json: JSONObject = try {
                    val response = withTimeout(30_000L) {
                        generativeModel.generateContent(fullContextPrompt)
                    }
                    val rawJson = response.text ?: throw IllegalStateException("Empty AI response")
                    JSONObject(rawJson)
                } catch (aiErr: Exception) {
                    if (aiErr is CancellationException && aiErr !is TimeoutCancellationException) {
                        throw aiErr
                    }
                    Log.w(TAG, "Firebase.ai fallback (${aiErr.message}); using Cloud LLM / Smart Engine...")
                    repository.generateCloudLlmAgentResponse(fullContextPrompt, cleanPrompt)
                        ?: generateSmartAgentDecision(cleanPrompt, state, inspection)
                }

                val promptLowerForAll = cleanPrompt.lowercase()
                val hasUploadOrTelegramIntent = promptLowerForAll.contains("আপলোড") ||
                    promptLowerForAll.contains("টেলিগ্রাম") ||
                    promptLowerForAll.contains("telegram") ||
                    promptLowerForAll.contains("গ্রুপ") ||
                    promptLowerForAll.contains("গুরুপ") ||
                    promptLowerForAll.contains("চ্যানেল") ||
                    promptLowerForAll.contains("টপিক") ||
                    promptLowerForAll.contains("রুটিন") ||
                    promptLowerForAll.contains("পাঠাও") ||
                    promptLowerForAll.contains("পাঠান") ||
                    promptLowerForAll.contains("সিঙ্ক") ||
                    promptLowerForAll.contains("sync") ||
                    promptLowerForAll.contains("upload")

                val rawActionType = json.optString("action_type", "DISCUSS").uppercase()
                val effectiveJson = if (hasUploadOrTelegramIntent && rawActionType in setOf("PLAY_VIDEO", "DISCUSS", "")) {
                    generateSmartAgentDecision(cleanPrompt, state, inspection)
                } else {
                    json
                }

                val replyBn = effectiveJson.optString("reply_bn", "আপনার নির্দেশ বুঝতে পেরেছি।")
                val actionType = effectiveJson.optString("action_type", "DISCUSS").uppercase()
                val taskTitle = effectiveJson.optString("task_title", "${state.selectedProgramTitle} — টেলিগ্রাম আপলোড")
                val reqPhaseId = effectiveJson.optString("phase_id", state.selectedPhaseId).trim()
                val reqPhaseTitle = effectiveJson.optString("phase_title", state.selectedPhaseTitle).trim()
                val subjCode = effectiveJson.optString("subject_code", state.selectedSubjectCode).trim()
                val subjTitle = effectiveJson.optString("subject_title", state.selectedSubjectTitle).trim()
                val chapTitle = effectiveJson.optString("chapter_title", "").trim()
                val videoQuery = effectiveJson.optString("video_search_query", cleanPrompt).trim()
                val autoPlayVideo = effectiveJson.optString("action_type", "").uppercase() == "PLAY_VIDEO" &&
                    effectiveJson.optBoolean("auto_play_video", true)

                val wantsAllQuarters = promptLowerForAll.contains("সব কোয়ার্টার") ||
                    promptLowerForAll.contains("সব কোয়ার্টার") ||
                    promptLowerForAll.contains("সকল কোয়ার্টার") ||
                    promptLowerForAll.contains("যতগুলো ভিডিও") ||
                    promptLowerForAll.contains("যতগুলো ক্লাস") ||
                    promptLowerForAll.contains("সব ভিডিও") ||
                    promptLowerForAll.contains("সকল ভিডিও")
                val wantsAllSubjects = (promptLowerForAll.contains("প্রতিটা সাবজেক্ট") ||
                    promptLowerForAll.contains("প্রতিটি সাবজেক্ট") ||
                    promptLowerForAll.contains("সাবজেক্ট ওয়াইজ") ||
                    promptLowerForAll.contains("সাবজেক্ট ওয়াইজ") ||
                    promptLowerForAll.contains("সব সাবজেক্ট") ||
                    promptLowerForAll.contains("সকল সাবজেক্ট") ||
                    promptLowerForAll.contains("সব বিষয়") ||
                    promptLowerForAll.contains("সকল বিষয়"))

                val matchedQuarter = if (wantsAllQuarters) null else inspection?.quarters?.firstOrNull {
                    it.phaseId == reqPhaseId ||
                        (reqPhaseTitle.isNotBlank() && reqPhaseTitle != "সব কোয়ার্টার" && it.phaseTitle.contains(reqPhaseTitle, ignoreCase = true))
                }
                val finalPhaseId = if (wantsAllQuarters) "" else (matchedQuarter?.phaseId ?: state.selectedPhaseId)
                val finalPhaseTitle = if (wantsAllQuarters) "সব কোয়ার্টার" else (matchedQuarter?.phaseTitle ?: state.selectedPhaseTitle)

                val matchedSubject = if (wantsAllSubjects) null else matchSubjectFromPromptOrHint(
                    userPrompt = cleanPrompt,
                    hintCode = subjCode,
                    hintTitle = subjTitle,
                    breakdown = inspection?.subjectBreakdown.orEmpty(),
                    fallbackSelectedCode = state.selectedSubjectCode
                )

                // Match existing Telegram channel/group if user mentioned one
                val reqChatId = effectiveJson.optString("target_chat_id", "").trim()
                val reqChannelTitle = effectiveJson.optString("target_channel_title", "").trim()
                val matchedDialog = state.telegramConfig.dialogs.firstOrNull { d ->
                    (reqChatId.isNotBlank() && d.id == reqChatId) ||
                        (reqChannelTitle.isNotBlank() && d.title.contains(reqChannelTitle, ignoreCase = true)) ||
                        (d.title.length >= 4 && cleanPrompt.contains(d.title, ignoreCase = true))
                }
                val matchedTopic = matchedDialog?.topics?.firstOrNull { t ->
                    t.id == effectiveJson.optInt("target_topic_id", 0) ||
                        (effectiveJson.optString("target_topic_title").isNotBlank() && t.title.contains(effectiveJson.optString("target_topic_title"), ignoreCase = true)) ||
                        (t.title.length >= 3 && cleanPrompt.contains(t.title, ignoreCase = true))
                }

                val promptLower = cleanPrompt.lowercase()
                val promptWantsGroup = promptLower.contains("গ্রুপ") ||
                    promptLower.contains("গুরুপ") ||
                    promptLower.contains("পাবলিক") ||
                    promptLower.contains("সুপারগ্রুপ") ||
                    promptLower.contains("group")
                val promptWantsTopic = promptLower.contains("টপিক") ||
                    promptLower.contains("topic") ||
                    promptLower.contains("সাবজেক্ট ওয়াইজ") ||
                    promptLower.contains("সাবজেক্ট ওয়াইজ") ||
                    promptLower.contains("সাবজেক্ট টপিক")
                val promptWantsLifetimeSync = promptLower.contains("প্রতিদিন") ||
                    promptLower.contains("রুটিন") ||
                    promptLower.contains("সারাজীবন") ||
                    promptLower.contains("নতুন ক্লাস") ||
                    promptLower.contains("রাত ১") ||
                    promptLower.contains("রাত 1") ||
                    promptLower.contains("২৪/৭") ||
                    promptLower.contains("24/7")

                val useTopics = effectiveJson.optBoolean("use_forum_topics", false) ||
                    promptWantsGroup ||
                    promptWantsTopic ||
                    matchedDialog?.isForum == true

                val finalChatId = if (promptWantsGroup && promptLower.contains("নতুন")) {
                    ""
                } else {
                    matchedDialog?.id ?: reqChatId.ifBlank { state.telegramConfig.defaultChatId }
                }
                val createNewChan = if (promptWantsGroup && promptLower.contains("নতুন")) {
                    true
                } else if (matchedDialog != null) {
                    false
                } else {
                    effectiveJson.optBoolean("create_new_channel", finalChatId.isBlank())
                }

                // When creating a Forum Supergroup (`useTopics = true`), the Group name is the Course Title,
                // and the Subject is the Forum Topic inside it!
                val finalChannelTitle = matchedDialog?.title ?: run {
                    if (useTopics) {
                        state.selectedProgramTitle
                    } else if (reqChannelTitle.isNotBlank()) {
                        reqChannelTitle
                    } else if (matchedSubject != null) {
                        "${state.selectedProgramTitle} — ${matchedSubject.subjectTitle}"
                    } else {
                        state.selectedProgramTitle
                    }
                }

                val quality = effectiveJson.optString("video_quality", "720p")
                val incVideos = effectiveJson.optBoolean("include_videos", true)
                val incSlides = effectiveJson.optBoolean("include_slides", true)
                val targetTopicName = if (wantsAllSubjects) {
                    ""
                } else {
                    matchedTopic?.title
                        ?: matchedSubject?.subjectTitle?.takeIf { useTopics }
                        ?: effectiveJson.optString("target_topic_title", "").trim()
                }

                val (parsedTimeBn, parsedCron) = parseBengaliScheduleTime(cleanPrompt)
                val schedTimeBn = if (promptWantsLifetimeSync || actionType == "CREATE_SCHEDULE") {
                    parsedTimeBn
                } else {
                    effectiveJson.optString("schedule_time_bn", parsedTimeBn).ifBlank { parsedTimeBn }
                }
                val cronExpr = if (promptWantsLifetimeSync || actionType == "CREATE_SCHEDULE") {
                    parsedCron
                } else {
                    effectiveJson.optString("cron_expression", parsedCron).ifBlank { parsedCron }
                }

                // Handle PLAY_VIDEO action: find matching lessons and resolve the top lesson's stream URL!
                val playableMatches: List<AgentPlayableLesson> = if (actionType == "PLAY_VIDEO") {
                    val rawMatches = searchPlayableLessonsInCatalog(
                        catalog = inspection?.allLessonsCatalog.orEmpty(),
                        query = videoQuery.ifBlank { cleanPrompt },
                        subjectCode = matchedSubject?.subjectCode ?: subjCode,
                        phaseId = finalPhaseId
                    )
                    if (rawMatches.isNotEmpty()) {
                        val firstResolved = repository.resolvePlayableLessonUrls(rawMatches.first())
                        listOf(firstResolved) + rawMatches.drop(1)
                    } else emptyList()
                } else emptyList()

                val proposedPlan = if (actionType in setOf("ASK_CONFIRMATION", "PROPOSE_PLAN", "START_UPLOAD_TASK", "CREATE_SCHEDULE", "CREATE_TELEGRAM_CHANNEL")) {
                    ProposedAgentPlan(
                        actionType = if (actionType == "ASK_CONFIRMATION") "PROPOSE_PLAN" else actionType,
                        taskTitle = taskTitle.ifBlank { "${state.selectedProgramTitle} আপলোড" },
                        programId = state.selectedProgramId,
                        programTitle = state.selectedProgramTitle,
                        phaseId = finalPhaseId,
                        phaseTitle = finalPhaseTitle,
                        subjectCode = if (wantsAllSubjects) "" else (matchedSubject?.subjectCode ?: subjCode),
                        subjectTitle = if (wantsAllSubjects) "" else (matchedSubject?.subjectTitle ?: subjTitle),
                        chapterTitle = chapTitle,
                        targetChannelTitle = finalChannelTitle.ifBlank { state.selectedProgramTitle },
                        targetChatId = finalChatId,
                        targetTopicId = if (wantsAllSubjects) 0 else (matchedTopic?.id ?: effectiveJson.optInt("target_topic_id", 0)),
                        targetTopicTitle = targetTopicName,
                        videoQuality = quality,
                        includeVideos = incVideos,
                        includeSlides = incSlides,
                        createNewChannel = createNewChan,
                        useForumTopics = useTopics,
                        enableLifetimeSync = promptWantsLifetimeSync || actionType == "CREATE_SCHEDULE",
                        scheduleTimeBn = schedTimeBn,
                        cronExpression = cronExpr,
                        estimatedVideos = if (wantsAllSubjects) (inspection?.videoLessonsCount ?: 0) else (matchedSubject?.videosCount ?: (inspection?.videoLessonsCount ?: 0)),
                        estimatedSlides = if (wantsAllSubjects) (inspection?.slideAttachmentsCount ?: 0) else (matchedSubject?.slidesCount ?: (inspection?.slideAttachmentsCount ?: 0))
                    )
                } else null

                val finalModelMsg = AiAgentChatMessage(
                    id = thinkingMsgId,
                    role = "model",
                    text = replyBn,
                    proposedPlan = proposedPlan,
                    playableLessons = playableMatches,
                    autoPlayFirstLesson = actionType == "PLAY_VIDEO" && autoPlayVideo && playableMatches.isNotEmpty(),
                    showChannelCatalog = actionType == "SHOW_CHANNELS",
                    isStreaming = false
                )

                val autoPlayTarget = if (actionType == "PLAY_VIDEO" && autoPlayVideo) {
                    playableMatches.firstOrNull { it.videoUrl.isNotBlank() }
                } else null

                _uiState.update { current ->
                    current.copy(
                        brainState = AgentBrainState.Success(replyBn),
                        messages = current.messages.map { if (it.id == thinkingMsgId) finalModelMsg else it },
                        pendingAutoPlayLesson = autoPlayTarget
                    )
                }
                persistCurrentSessionMessages(_uiState.value.messages)

                if (proposedPlan != null && actionType in setOf("START_UPLOAD_TASK", "CREATE_SCHEDULE", "CREATE_TELEGRAM_CHANNEL")) {
                    executeProposedPlan(proposedPlan)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI Agent generation failed", e)
                val errMsg = e.localizedMessage ?: "AI এজেন্টের সাথে সংযোগে ত্রুটি হয়েছে।"
                updateMessageAsError(thinkingMsgId, errMsg)
            }
        }
    }

    private fun normalizeSubjectTextForMatching(text: String): String {
        return text.lowercase()
            .replace("1", "১")
            .replace("2", "২")
            .replace("প্রথম", "১ম")
            .replace("দ্বিতীয়", "২য়")
            .replace("দ্বিতীয়", "২য়")
            .replace("পএ", "পত্র")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun matchSubjectFromPromptOrHint(
        userPrompt: String,
        hintCode: String,
        hintTitle: String,
        breakdown: List<SubjectInspectionItem>,
        fallbackSelectedCode: String
    ): SubjectInspectionItem? {
        if (breakdown.isEmpty()) return null
        val normPrompt = normalizeSubjectTextForMatching(userPrompt)
        val normHint = normalizeSubjectTextForMatching(hintTitle)

        // 1. Direct code match
        if (hintCode.isNotBlank()) {
            breakdown.firstOrNull { it.subjectCode.equals(hintCode, ignoreCase = true) }?.let { return it }
        }
        // 2. Normalized title match from prompt or hint
        breakdown.firstOrNull { sub ->
            val normSub = normalizeSubjectTextForMatching(sub.subjectTitle)
            (normHint.isNotBlank() && (normSub.contains(normHint) || normHint.contains(normSub))) ||
                (normSub.isNotBlank() && normPrompt.contains(normSub))
        }?.let { return it }

        // 3. Keyword + paper number match (e.g. "বাংলা 1 ম পএ" -> "বাংলা ১ম পত্র")
        val wantsSecondPaper = normPrompt.contains("২য়") || normPrompt.contains("2য়") || normPrompt.contains("second")
        val wantsFirstPaper = normPrompt.contains("১ম") || normPrompt.contains("1ম") || normPrompt.contains("first")

        val subjectKeywords = listOf(
            "বাংলা" to listOf("বাংলা", "bangla"),
            "ইংরেজি" to listOf("ইংরেজি", "english"),
            "তথ্য" to listOf("আইসিটি", "ict", "তথ্য", "ডেটাবেজ"),
            "ইতিহাস" to listOf("ইতিহাস", "history"),
            "অর্থনীতি" to listOf("অর্থনীতি", "economics"),
            "পৌরনীতি" to listOf("পৌরনীতি", "civics"),
            "যুক্তিবিদ্যা" to listOf("যুক্তিবিদ্যা", "logic"),
            "সমাজবিজ্ঞান" to listOf("সমাজবিজ্ঞান", "sociology"),
            "সমাজকর্ম" to listOf("সমাজকর্ম", "social work"),
            "ভূগোল" to listOf("ভূগোল", "geography"),
            "ইসলাম" to listOf("ইসলাম", "islam"),
            "পদার্থ" to listOf("পদার্থ", "physics"),
            "রসায়ন" to listOf("রসায়ন", "রসায়ন", "chemistry"),
            "জীববিজ্ঞান" to listOf("জীববিজ্ঞান", "বায়োলজি", "biology"),
            "গণিত" to listOf("উচ্চতর গণিত", "গণিত", "math")
        )

        for ((subPrefix, triggers) in subjectKeywords) {
            if (triggers.any { normPrompt.contains(it) || normHint.contains(it) }) {
                val candidates = breakdown.filter { normalizeSubjectTextForMatching(it.subjectTitle).contains(subPrefix) }
                if (candidates.isNotEmpty()) {
                    if (wantsSecondPaper) {
                        candidates.firstOrNull { normalizeSubjectTextForMatching(it.subjectTitle).contains("২য়") }?.let { return it }
                    }
                    if (wantsFirstPaper) {
                        candidates.firstOrNull { normalizeSubjectTextForMatching(it.subjectTitle).contains("১ম") }?.let { return it }
                    }
                    return candidates.first()
                }
            }
        }

        // 4. Fallback to selected subject chip in UI
        if (fallbackSelectedCode.isNotBlank()) {
            return breakdown.firstOrNull { it.subjectCode.equals(fallbackSelectedCode, ignoreCase = true) }
        }
        return null
    }

    /**
     * Parses any Bengali or English time from the prompt (e.g. "রাত ১ টা", "রাত 1 টা", "রাত ২ টা", "সকাল ৮ টা")
     * and returns `(timeDisplayBn, utcCronExpression)`. Defaults to "প্রতিদিন রাত ০১:০০ (1:00 AM)" -> "0 19 * * *".
     */
    fun parseBengaliScheduleTime(prompt: String): Pair<String, String> {
        val bnToEnDigits = mapOf(
            '০' to '0', '১' to '1', '২' to '2', '৩' to '3', '৪' to '4',
            '৫' to '5', '৬' to '6', '৭' to '7', '৮' to '8', '৯' to '9'
        )
        val asciiPrompt = buildString {
            for (ch in prompt) {
                append(bnToEnDigits[ch] ?: ch)
            }
        }.lowercase()

        val regex = Regex("(রাত|সকাল|দুপুর|বিকাল|বিকেল|সন্ধ্যা)?\\s*(\\d{1,2})(?:[:.](\\d{1,2}))?\\s*(টা|টায়|টায়|am|pm)?")
        val match = regex.findAll(asciiPrompt).firstOrNull { m ->
            val period = m.groupValues[1]
            val suffix = m.groupValues[4]
            period.isNotBlank() || suffix.isNotBlank()
        }

        var bdHour24 = 1 // Default: 1:00 AM (রাত ১:০০ টা)
        var minute = 0
        var periodLabel = "রাত"

        if (match != null) {
            val period = match.groupValues[1]
            val rawHour = match.groupValues[2].toIntOrNull()?.coerceIn(0, 23) ?: 1
            minute = match.groupValues[3].toIntOrNull()?.coerceIn(0, 59) ?: 0
            val suffix = match.groupValues[4]

            when {
                period == "রাত" -> {
                    periodLabel = "রাত"
                    bdHour24 = when {
                        rawHour == 12 -> 0
                        rawHour in 1..6 -> rawHour
                        rawHour in 7..11 -> rawHour + 12
                        else -> rawHour
                    }
                }
                period == "সকাল" -> {
                    periodLabel = "সকাল"
                    bdHour24 = if (rawHour == 12) 0 else rawHour
                }
                period == "দুপুর" -> {
                    periodLabel = "দুপুর"
                    bdHour24 = if (rawHour < 12) rawHour + 12 else rawHour
                }
                period == "বিকাল" || period == "বিকেল" -> {
                    periodLabel = "বিকাল"
                    bdHour24 = if (rawHour < 12) rawHour + 12 else rawHour
                }
                period == "সন্ধ্যা" -> {
                    periodLabel = "সন্ধ্যা"
                    bdHour24 = if (rawHour < 12) rawHour + 12 else rawHour
                }
                suffix == "pm" -> {
                    periodLabel = if (rawHour in 6..11) "রাত" else "বিকাল"
                    bdHour24 = if (rawHour < 12) rawHour + 12 else rawHour
                }
                suffix == "am" -> {
                    periodLabel = if (rawHour in 1..5 || rawHour == 12) "রাত" else "সকাল"
                    bdHour24 = if (rawHour == 12) 0 else rawHour
                }
            }
        }

        val utcHour = (bdHour24 - 6 + 24) % 24
        val cron = "$minute $utcHour * * *"
        val hour12 = when {
            bdHour24 == 0 -> 12
            bdHour24 > 12 -> bdHour24 - 12
            else -> bdHour24
        }
        val enToBnDigits = mapOf(
            '0' to '০', '1' to '১', '2' to '২', '3' to '৩', '4' to '৪',
            '5' to '৫', '6' to '৬', '7' to '৭', '8' to '৮', '9' to '৯'
        )
        val timeStrEn = String.format("%02d:%02d", hour12, minute)
        val timeStrBn = timeStrEn.map { enToBnDigits[it] ?: it }.joinToString("")
        val amPm = if (bdHour24 < 12) "AM" else "PM"
        val displayBn = "প্রতিদিন $periodLabel $timeStrBn ($hour12:${String.format("%02d", minute)} $amPm)"
        return Pair(displayBn, cron)
    }

    /**
     * Smart Decision Engine when offline or fallback.
     */
    private fun generateSmartAgentDecision(
        userPrompt: String,
        state: AiAgentUiState,
        inspection: CourseInspectionSummary?
    ): JSONObject {
        val p = userPrompt.lowercase()
        val courseTitle = state.selectedProgramTitle.ifBlank { "সক্রিয় কোর্স" }
        val totalLessons = inspection?.totalLessonsCount ?: 0
        val totalVideos = inspection?.videoLessonsCount ?: totalLessons
        val totalSlides = inspection?.slideAttachmentsCount ?: totalLessons

        val hasUploadOrTelegramIntent = p.contains("আপলোড") ||
            p.contains("টেলিগ্রাম") ||
            p.contains("telegram") ||
            p.contains("গ্রুপ") ||
            p.contains("গুরুপ") ||
            p.contains("চ্যানেল") ||
            p.contains("টপিক") ||
            p.contains("রুটিন") ||
            p.contains("পাঠাও") ||
            p.contains("পাঠান") ||
            p.contains("সিঙ্ক") ||
            p.contains("sync") ||
            p.contains("upload")

        // Check if user wants to play a video (ONLY when NOT asking for Telegram upload / group / topic / schedule!)
        val isPlayRequest = !hasUploadOrTelegramIntent && (
            p.contains("প্লে") ||
                p.contains("play") ||
                p.contains("ভিডিও চালাও") ||
                p.contains("ক্লাস চালাও") ||
                p.contains("ভিডিও চালু") ||
                p.contains("ক্লাস চালু") ||
                p.contains("প্লেয়ার চালু") ||
                (p.contains("ভিডিও") && (p.contains("দেখাও") || p.contains("দেখব") || p.contains("চালাও")))
            )

        val matchedQuarter = inspection?.quarters?.firstOrNull { q ->
            p.contains(q.phaseTitle.lowercase()) ||
                (p.contains("কোয়ার্টার ১") && q.phaseTitle.contains("১")) ||
                (p.contains("কোয়ার্টার ২") && q.phaseTitle.contains("২")) ||
                (p.contains("কোয়ার্টার ৩") && q.phaseTitle.contains("৩")) ||
                (p.contains("কোয়ার্টার ৪") && q.phaseTitle.contains("৪")) ||
                (p.contains("কোয়ার্টার ৫") && q.phaseTitle.contains("৫")) ||
                (p.contains("quarter 1") && (q.phaseTitle.contains("1") || q.phaseTitle.contains("১"))) ||
                (p.contains("quarter 2") && (q.phaseTitle.contains("2") || q.phaseTitle.contains("২")))
        }

        val wantsAllQuarters = p.contains("সব কোয়ার্টার") ||
            p.contains("সব কোয়ার্টার") ||
            p.contains("সকল কোয়ার্টার") ||
            p.contains("যতগুলো ভিডিও") ||
            p.contains("যতগুলো ক্লাস") ||
            p.contains("সব ভিডিও") ||
            p.contains("সকল ভিডিও")
        val wantsAllSubjects = p.contains("প্রতিটা সাবজেক্ট") ||
            p.contains("প্রতিটি সাবজেক্ট") ||
            p.contains("সাবজেক্ট ওয়াইজ") ||
            p.contains("সাবজেক্ট ওয়াইজ") ||
            p.contains("সব সাবজেক্ট") ||
            p.contains("সকল সাবজেক্ট") ||
            p.contains("সব বিষয়") ||
            p.contains("সকল বিষয়")

        val matchedSub = if (wantsAllSubjects) null else matchSubjectFromPromptOrHint(
            userPrompt = userPrompt,
            hintCode = "",
            hintTitle = "",
            breakdown = inspection?.subjectBreakdown.orEmpty(),
            fallbackSelectedCode = state.selectedSubjectCode
        )

        val matchedDialog = state.telegramConfig.dialogs.firstOrNull { d ->
            d.title.length >= 3 && p.contains(d.title.lowercase())
        }

        val subjCode = if (wantsAllSubjects) "" else (matchedSub?.subjectCode ?: state.selectedSubjectCode)
        val subjTitle = if (wantsAllSubjects) "" else (matchedSub?.subjectTitle ?: state.selectedSubjectTitle)
        val targetVideos = if (wantsAllSubjects) totalVideos else (matchedSub?.videosCount ?: totalVideos)
        val targetSlides = if (wantsAllSubjects) totalSlides else (matchedSub?.slidesCount ?: totalSlides)

        val quality = when {
            p.contains("1080") -> "1080p"
            p.contains("480") -> "480p"
            p.contains("360") -> "360p"
            else -> "720p"
        }
        val onlyVideo = p.contains("শুধু ভিডিও") || p.contains("স্লাইড বাদে")
        val onlySlide = p.contains("শুধু স্লাইড") || p.contains("শুধু পিডিএফ") || p.contains("ভিডিও বাদে")
        val incVideos = !onlySlide
        val incSlides = !onlyVideo
        val useForum = p.contains("গ্রুপ") || p.contains("গুরুপ") || p.contains("পাবলিক") || p.contains("টপিক") || p.contains("group") || p.contains("topic")
        val (schedTimeBn, cronExpr) = parseBengaliScheduleTime(userPrompt)

        val isShowChannels = (p.contains("চ্যানেল") || p.contains("গ্রুপ") || p.contains("গুরুপ")) &&
            (p.contains("তালিকা") || p.contains("দেখাও") || p.contains("কি কি আছে") || p.contains("লিস্ট")) &&
            !p.contains("আপলোড")

        val isScheduleCmd = (p.contains("রুটিন") || p.contains("প্রতিদিন") || p.contains("সারাজীবন") || p.contains("রাত ১") || p.contains("রাত 1") || p.contains("schedule")) &&
            (p.contains("চালু") || p.contains("করো") || p.contains("সেট") || p.contains("অটো") || p.contains("আপলোড"))

        val isCreateChannelOnly = (p.contains("চ্যানেল তৈরি") || p.contains("চ্যানেল খোল") || p.contains("গ্রুপ তৈরি") || p.contains("গুরুপ তৈরি")) &&
            !p.contains("আপলোড") && !p.contains("ভিডিও") && !p.contains("ক্লাস")

        val isUploadMention = p.contains("আপলোড") || p.contains("টেলিগ্রাম") || p.contains("পাঠাও") ||
            p.contains("পাঠান") || p.contains("প্ল্যান") || p.contains("upload") || matchedSub != null || matchedDialog != null

        val channelTitle = matchedDialog?.title ?: if (useForum) courseTitle else if (subjTitle.isNotBlank()) "$courseTitle — $subjTitle" else courseTitle
        val topicTitle = if (useForum && subjTitle.isNotBlank() && !wantsAllSubjects) subjTitle else ""
        val quartersSummary = inspection?.quarters?.joinToString("\n") { q ->
            "• ${q.phaseTitle}: ${q.videosCount}টি ভিডিও, ${q.slidesCount}টি স্লাইড"
        }?.ifBlank { "• সকল লেকচার প্রস্তুত" } ?: "• সকল লেকচার প্রস্তুত"

        val subjectsTopicsSummary = inspection?.subjectBreakdown
            ?.filter { it.videosCount > 0 || it.slidesCount > 0 }
            ?.joinToString("\n") { s ->
                "  ├─ 📁 ${s.subjectTitle}: ${s.videosCount}টি ভিডিও, ${s.slidesCount}টি স্লাইড"
            }.orEmpty()

        val actionType: String
        val replyBn: String
        val taskTitle: String

        when {
            isPlayRequest -> {
                actionType = "PLAY_VIDEO"
                taskTitle = ""
                val matchedLessons = searchPlayableLessonsInCatalog(
                    catalog = inspection?.allLessonsCatalog.orEmpty(),
                    query = userPrompt,
                    subjectCode = subjCode,
                    phaseId = matchedQuarter?.phaseId ?: state.selectedPhaseId
                )
                val firstLesson = matchedLessons.firstOrNull()
                replyBn = if (firstLesson != null) {
                    "🎬 বস, আপনার নির্দেশ অনুযায়ী **${firstLesson.title}** ক্লাসটি খুঁজে বের করেছি!\n\n" +
                        "```text\n" +
                        "কোর্স     : $courseTitle\n" +
                        "কোয়ার্টার : ${firstLesson.phaseTitle.ifBlank { "সক্রিয় ফেজ" }}\n" +
                        "বিষয়     : ${firstLesson.subjectTitle}\n" +
                        "অধ্যায়    : ${firstLesson.chapterTitle}\n" +
                        "লেকচার   : ${firstLesson.title}\n" +
                        "```\n" +
                        "আমি এখনই ভিডিও প্লেয়ার চালু করে দিচ্ছি। আপনি চাইলে নিচের কার্ড থেকে স্লাইড দেখতে বা সরাসরি টেলিগ্রামেও পাঠাতে পারবেন!"
                } else {
                    "🎬 বস, আপনার কোর্সের লেকচার তালিকা নিচে যুক্ত করেছি। যেকোনো ক্লাসের **▶️ ভিডিও প্লে** বাটনে ক্লিক করলেই ক্লাসটি চালু হয়ে যাবে!"
                }
            }
            isShowChannels -> {
                actionType = "SHOW_CHANNELS"
                taskTitle = ""
                val dialogLines = state.telegramConfig.dialogs.take(15).joinToString("\n") { d ->
                    val kind = if (d.isForum) "Forum Group" else if (d.isMegagroup) "Group" else "Channel"
                    val tCount = if (d.topics.isNotEmpty()) " (${d.topics.size}টি টপিক)" else ""
                    "• ${d.title} [$kind]$tCount"
                }.ifBlank { "• এখনো কোনো চ্যানেল সিঙ্ক হয়নি" }
                replyBn = "📡 বস, আপনার টেলিগ্রাম একাউন্টের সিঙ্ক করা চ্যানেল ও গ্রুপগুলোর তালিকা নিচে কোড আকারে সাজিয়ে দিলাম:\n\n" +
                    "```text\n" +
                    "$dialogLines\n" +
                    "```\n" +
                    "আপনি যেকোনো গ্রুপ বা চ্যানেলের নাম বলে সেখানে ভিডিও ও স্লাইড আপলোড করতে বলতে পারেন!"
            }
            isCreateChannelOnly -> {
                actionType = "CREATE_TELEGRAM_CHANNEL"
                taskTitle = "$channelTitle — নতুন ${if (useForum) "পাবলিক গ্রুপ" else "চ্যানেল"} তৈরি"
                replyBn = "```text\n" +
                    "অ্যাকশন : নতুন টেলিগ্রাম ${if (useForum) "পাবলিক ফোরাম গ্রুপ (টপিকসহ)" else "চ্যানেল"} তৈরি\n" +
                    "নাম     : $channelTitle\n" +
                    "```\n" +
                    "বস, আপনার নির্দেশ অনুযায়ী **$channelTitle** তৈরি করার কাজ এখনই GitHub Actions-এ পাঠিয়ে দিচ্ছি! 🚀"
            }
            isUploadMention -> {
                val directApproved = p.contains("অনুমতি দিলাম") || p.contains("সরাসরি শুরু")
                actionType = if (directApproved) "START_UPLOAD_TASK" else "ASK_CONFIRMATION"
                taskTitle = if (subjTitle.isNotBlank()) "$courseTitle — $subjTitle" else "$courseTitle — সম্পূর্ণ কোর্স ও ২৪/৭ সিঙ্ক"
                val targetDesc = if (matchedDialog != null && !(useForum && p.contains("নতুন"))) {
                    "নির্বাচিত গ্রুপ/চ্যানেল: ${matchedDialog.title} (ID: ${matchedDialog.id})"
                } else if (useForum) {
                    "নতুন পাবলিক ফোরাম গ্রুপ: $channelTitle"
                } else {
                    "নতুন চ্যানেল তৈরি হবে: $channelTitle"
                }
                val topicDesc = if (useForum) {
                    topicTitle.ifBlank { "প্রতিটি সাবজেক্টের নামে আলাদা ফোরাম টপিক" }
                } else "সাধারণ চ্যানেল"
                val effQuarterTitle = if (wantsAllQuarters) "সব কোয়ার্টার (সকল ভিডিও)" else (matchedQuarter?.phaseTitle ?: state.selectedPhaseTitle)
                val topicsBreakdownBlock = if (wantsAllSubjects && subjectsTopicsSummary.isNotBlank()) {
                    "\n📂 সাবজেক্ট টপিক তালিকা:\n$subjectsTopicsSummary"
                } else ""
                replyBn = "বস, আমি আপনার সিলেক্ট করা **$courseTitle** কোর্সের লাইভ ডেটা বিশ্লেষণ করে আপলোড ও ২৪/৭ অটো-সিঙ্ক প্ল্যানটি সাজিয়ে নিয়েছি:\n\n" +
                    "```text\n" +
                    "📚 কোর্স        : $courseTitle\n" +
                    "🗂️ কোয়ার্টার    : $effQuarterTitle\n" +
                    "📖 বিষয়        : ${subjTitle.ifBlank { "সকল বিষয় (${inspection?.subjectsCount ?: 0}টি সাবজেক্ট)" }}\n" +
                    "📌 ফোরাম টপিক   : $topicDesc\n" +
                    "🎬 মোট ভিডিও   : ${targetVideos}টি ($quality)\n" +
                    "📄 লেকচার স্লাইড: ${targetSlides}টি (PDF)\n" +
                    "🎯 গন্তব্য      : $targetDesc\n" +
                    "⏰ ডেইলি রুটিন  : $schedTimeBn (নতুন ক্লাস অটো-আপলোড)\n" +
                    "⚡ স্পিড ইঞ্জিন : ৪৮-থ্রেড প্রি-ফেচ + ১৬-লেন টেলিগ্রাম আপলোড$topicsBreakdownBlock\n" +
                    "```\n\n" +
                    "❓ **আপনি কি '$channelTitle' নামে নতুন পাবলিক ফোরাম গ্রুপ খুলে প্রতিটি সাবজেক্টের টপিকে সব ভিডিও ও স্লাইড আপলোড এবং ২৪/৭ রুটিন চালু করার অনুমতি দিচ্ছেন?**\n" +
                    "নিচে **'হ্যাঁ, অনুমতি দিলাম'** বাটনে চাপলেই সাথে সাথে GitHub Actions-এ আপলোড ও রুটিন চালু হয়ে যাবে!"
            }
            isScheduleCmd -> {
                actionType = "CREATE_SCHEDULE"
                taskTitle = "$courseTitle — ২৪/৭ লাইফটাইম অটো-সিঙ্ক"
                replyBn = "```text\n" +
                    "রুটিন   : ২৪/৭ লাইফটাইম অটোমেটিক ক্লাস সিঙ্ক (সাবজেক্ট টপিক ওয়াইজ)\n" +
                    "কোর্স    : $courseTitle (সকল কোয়ার্টার)\n" +
                    "গন্তব্য  : $channelTitle\n" +
                    "সময়     : $schedTimeBn\n" +
                    "```\n" +
                    "⏰ বস, আপনার জন্য **$courseTitle**-এর সম্পূর্ণ কোর্স আপলোড ও প্রতিদিন **$schedTimeBn** সময়ে ২৪/৭ অটোমেটিক রুটিন চালু করে দিচ্ছি!"
            }
            else -> {
                actionType = "DISCUSS"
                taskTitle = ""
                replyBn = "আলহামদুলিল্লাহ বস, আমি **Gemini 3.8 Flash** ইঞ্জিনের সাথে সম্পূর্ণ প্রস্তুত! 😊\n\n" +
                    "```text\n" +
                    "📊 আপনার কোর্সের সকল কোয়ার্টারের সামারি:\n" +
                    "$quartersSummary\n" +
                    "মোট ভিডিও: ${totalVideos}টি | মোট স্লাইড: ${totalSlides}টি\n" +
                    "```\n\n" +
                    "আপনি আমাকে বলতে পারেন:\n" +
                    "• *\"নতুন পাবলিক গ্রুপ তৈরি করে বাংলা ১ম পত্র নামে টপিক খুলে সব ক্লাস ও স্লাইড পরপর আপলোড করো\"*\n" +
                    "• *\"এই কোর্সের সব কোয়ার্টার সাবজেক্ট টপিক ওয়াইজ আপলোড করো এবং প্রতিদিন রাত ১টায় অটো সিঙ্ক চালু রাখো\"*"
            }
        }

        return JSONObject().apply {
            put("reply_bn", replyBn)
            put("action_type", actionType)
            put("task_title", taskTitle)
            put("phase_id", if (wantsAllQuarters) "" else (matchedQuarter?.phaseId ?: state.selectedPhaseId))
            put("phase_title", if (wantsAllQuarters) "সব কোয়ার্টার" else (matchedQuarter?.phaseTitle ?: state.selectedPhaseTitle))
            put("subject_code", subjCode)
            put("subject_title", subjTitle)
            put("video_search_query", userPrompt)
            put("auto_play_video", actionType == "PLAY_VIDEO")
            put("target_channel_title", channelTitle)
            put("target_chat_id", if (useForum && p.contains("নতুন")) "" else (matchedDialog?.id ?: ""))
            put("target_topic_title", topicTitle)
            put("video_quality", quality)
            put("include_videos", incVideos)
            put("include_slides", incSlides)
            put("create_new_channel", (useForum && p.contains("নতুন")) || matchedDialog == null)
            put("use_forum_topics", useForum)
            put("schedule_time_bn", schedTimeBn)
            put("cron_expression", cronExpr)
        }
    }

    private fun updateMessageAsError(msgId: String, errorText: String) {
        _uiState.update { current ->
            current.copy(
                brainState = AgentBrainState.Error(errorText),
                messages = current.messages.map {
                    if (it.id == msgId) {
                        it.copy(
                            text = "⚠️ ত্রুটি: $errorText",
                            isStreaming = false,
                            isError = true
                        )
                    } else it
                }
            )
        }
        persistCurrentSessionMessages(_uiState.value.messages)
    }

    fun sendSingleLessonToTelegramFromChat(
        lesson: AgentPlayableLesson,
        targetChatId: String,
        targetChannelTitle: String,
        targetTopicId: Int = 0,
        targetTopicTitle: String = "",
        createNewChannel: Boolean = false
    ) {
        val plan = ProposedAgentPlan(
            actionType = "START_UPLOAD_TASK",
            taskTitle = "${lesson.subjectTitle} • ${lesson.title}",
            programId = _uiState.value.selectedProgramId,
            programTitle = _uiState.value.selectedProgramTitle,
            phaseId = lesson.phaseId,
            phaseTitle = lesson.phaseTitle,
            subjectCode = lesson.subjectCode,
            subjectTitle = lesson.subjectTitle,
            chapterId = lesson.chapterId,
            chapterTitle = lesson.chapterTitle,
            specificLessonIds = listOf(lesson.id),
            targetChannelTitle = targetChannelTitle.ifBlank { _uiState.value.selectedProgramTitle.ifBlank { lesson.subjectTitle } },
            targetChatId = if (createNewChannel) "" else targetChatId,
            targetTopicId = targetTopicId,
            targetTopicTitle = targetTopicTitle.ifBlank { if (createNewChannel) lesson.subjectTitle else "" },
            videoQuality = "720p",
            includeVideos = true,
            includeSlides = true,
            createNewChannel = createNewChannel,
            useForumTopics = createNewChannel || targetTopicId > 0 || targetTopicTitle.isNotBlank(),
            estimatedVideos = 1,
            estimatedSlides = 1
        )
        executeProposedPlan(plan)
    }

    /**
     * One-click helper to start a Full-Course Subject-Topic-Wise Upload across all quarters (1 to 5+)
     * AND configure a permanent 24/7 Daily Routine at the user's chosen time (e.g. রাত ০১:০০ টা).
     * Works forever even if the user later switches syllabus (HSC 27 -> HSC 28 / SSC) or logs out/in!
     */
    fun createCourseLifetimeSyncRoutine(
        programId: String = _uiState.value.selectedProgramId,
        programTitle: String = _uiState.value.selectedProgramTitle,
        targetChatId: String = "",
        targetChannelTitle: String = "",
        timeDisplayBn: String = "প্রতিদিন রাত ০১:০০ (1:00 AM)",
        cronExpression: String = "0 19 * * *",
        videoQuality: String = "720p",
        startImmediateFullUpload: Boolean = true
    ) {
        val cleanProgId = programId.ifBlank { _uiState.value.selectedProgramId }
        val cleanProgTitle = programTitle.ifBlank { _uiState.value.selectedProgramTitle.ifBlank { "সক্রিয় কোর্স" } }
        if (cleanProgId.isBlank()) {
            _uiState.update { it.copy(statusBannerMessage = "⚠️ অনুগ্রহ করে আগে একটি কোর্স সিলেক্ট করুন।") }
            return
        }
        val plan = ProposedAgentPlan(
            actionType = if (startImmediateFullUpload) "START_UPLOAD_TASK" else "CREATE_SCHEDULE",
            taskTitle = "$cleanProgTitle — সম্পূর্ণ কোর্স ও ২৪/৭ অটো-সিঙ্ক",
            programId = cleanProgId,
            programTitle = cleanProgTitle,
            phaseId = "", // All Quarters (1 -> 2 -> 3 -> 4 -> 5)
            phaseTitle = "সব কোয়ার্টার",
            subjectCode = "", // All Subjects (Subject-Topic-Wise)
            subjectTitle = "",
            targetChannelTitle = targetChannelTitle.ifBlank { cleanProgTitle },
            targetChatId = targetChatId,
            targetTopicId = 0,
            targetTopicTitle = "",
            videoQuality = videoQuality,
            includeVideos = true,
            includeSlides = true,
            createNewChannel = targetChatId.isBlank(),
            useForumTopics = true,
            enableLifetimeSync = true,
            scheduleTimeBn = timeDisplayBn,
            cronExpression = cronExpression,
            estimatedVideos = _uiState.value.activeInspection?.videoLessonsCount ?: 50,
            estimatedSlides = _uiState.value.activeInspection?.slideAttachmentsCount ?: 50
        )
        executeProposedPlan(plan)
    }

    fun triggerScheduleNow(schedule: AiAgentSchedule) {
        val plan = ProposedAgentPlan(
            actionType = "START_UPLOAD_TASK",
            taskTitle = schedule.title,
            programId = schedule.programId,
            programTitle = schedule.programTitle.ifBlank { schedule.title },
            phaseId = schedule.phaseId,
            subjectCode = schedule.subjectCode,
            targetChannelTitle = schedule.targetChannelTitle.ifBlank { schedule.programTitle },
            targetChatId = schedule.targetChatId,
            videoQuality = schedule.videoQuality,
            includeVideos = schedule.includeVideos,
            includeSlides = schedule.includeSlides,
            createNewChannel = schedule.targetChatId.isBlank(),
            useForumTopics = schedule.useForumTopics,
            enableLifetimeSync = false
        )
        executeProposedPlan(plan)
    }

    fun executeProposedPlan(plan: ProposedAgentPlan) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDispatchingTask = true) }
            try {
                val progId = plan.programId.ifBlank { _uiState.value.selectedProgramId }
                val progTitle = plan.programTitle.ifBlank { _uiState.value.selectedProgramTitle }
                val phaseId = if (plan.phaseTitle.contains("সব কোয়ার্টার") || plan.phaseId.isBlank()) {
                    plan.phaseId
                } else {
                    plan.phaseId
                }

                // 1. If user requested 24/7 Lifetime Sync OR actionType == CREATE_SCHEDULE, save the persistent schedule
                if (plan.actionType == "CREATE_SCHEDULE" || plan.enableLifetimeSync) {
                    val newSchedule = AiAgentSchedule(
                        id = "sched_${progId.ifBlank { System.currentTimeMillis().toString() }}_${plan.subjectCode.ifBlank { "ALL" }}",
                        title = "${progTitle}${if (plan.subjectTitle.isNotBlank()) " (${plan.subjectTitle})" else ""} — ২৪/৭ অটো সিঙ্ক",
                        scheduleType = "DAILY_COURSE_SYNC",
                        cronExpression = plan.cronExpression.ifBlank { "0 19 * * *" },
                        timeDisplayBn = plan.scheduleTimeBn.ifBlank { "প্রতিদিন রাত ০১:০০ (1:00 AM)" },
                        programId = progId,
                        programTitle = progTitle,
                        phaseId = if (plan.enableLifetimeSync) "" else phaseId, // Empty = checks all quarters 1..5+
                        subjectCode = plan.subjectCode,
                        targetChatId = if (plan.createNewChannel) "" else plan.targetChatId.ifBlank { _uiState.value.telegramConfig.defaultChatId },
                        targetChannelTitle = plan.targetChannelTitle.ifBlank { progTitle },
                        useForumTopics = plan.useForumTopics || plan.enableLifetimeSync,
                        videoQuality = plan.videoQuality,
                        includeVideos = plan.includeVideos,
                        includeSlides = plan.includeSlides,
                        includeDailyRoutine = true,
                        isEnabled = true,
                        lastRunStatus = "সক্রিয় — প্রতিদিন ${plan.scheduleTimeBn} সময়ে অটো-চেক ও আপলোড চলবে ✅"
                    )
                    repository.saveSchedule(newSchedule)
                    refreshTasksAndSchedules()
                    _uiState.update {
                        it.copy(statusBannerMessage = "⏰ ২৪/৭ রুটিন সেভ হয়েছে (${newSchedule.timeDisplayBn})!")
                    }
                    if (plan.actionType == "CREATE_SCHEDULE" && !plan.enableLifetimeSync) {
                        appendSystemConfirmationMessage(
                            "⏰ **২৪/৭ লাইফটাইম অটো-সিঙ্ক রুটিন সফলভাবে চালু হয়েছে!**\n" +
                                "• কোর্স: **${newSchedule.programTitle}** (সিলেবাস পরিবর্তন বা লগআউট করলেও সচল থাকবে)\n" +
                                "• সময়: **${newSchedule.timeDisplayBn}**\n" +
                                "GitHub Actions প্রতিদিন নির্ধারিত সময়ে নতুন ভিডিও ও স্লাইড চেক করে সাবজেক্ট টপিক ওয়াইজ টেলিগ্রামে আপলোড করবে।"
                        )
                        return@launch
                    }
                }

                // 2. Build complete lessons bundle and dispatch immediate upload task to GitHub Actions
                val (lessonCount, bundledLessons) = if (plan.actionType == "CREATE_TELEGRAM_CHANNEL") {
                    Pair(1, null)
                } else {
                    repository.buildBundledLessonsJson(
                        programId = progId,
                        phaseId = phaseId.takeIf { it.isNotBlank() },
                        subjectCode = plan.subjectCode,
                        chapterId = plan.chapterId,
                        specificLessonIds = plan.specificLessonIds,
                        subjectTitle = plan.subjectTitle
                    )
                }

                val task = AiAgentTask(
                    id = "task_${System.currentTimeMillis()}",
                    title = plan.taskTitle.ifBlank {
                        if (plan.subjectTitle.isNotBlank()) "$progTitle — ${plan.subjectTitle}" else progTitle
                    },
                    taskType = if (plan.actionType == "CREATE_TELEGRAM_CHANNEL") {
                        "CREATE_CHANNEL_ONLY"
                    } else {
                        "COURSE_TELEGRAM_UPLOAD"
                    },
                    status = "QUEUED",
                    programId = progId,
                    programTitle = progTitle,
                    phaseId = phaseId,
                    subjectCode = plan.subjectCode,
                    subjectTitle = plan.subjectTitle,
                    chapterId = plan.chapterId,
                    chapterTitle = plan.chapterTitle,
                    videoQuality = plan.videoQuality,
                    includeVideos = plan.includeVideos,
                    includeSlides = plan.includeSlides,
                    createNewChannel = plan.createNewChannel,
                    useForumTopics = plan.useForumTopics,
                    targetChannelTitle = plan.targetChannelTitle.ifBlank { progTitle },
                    targetChatId = if (plan.createNewChannel) "" else plan.targetChatId.ifBlank { _uiState.value.telegramConfig.defaultChatId },
                    targetTopicId = plan.targetTopicId,
                    targetTopicTitle = plan.targetTopicTitle,
                    totalItems = if (lessonCount > 0) lessonCount else plan.estimatedVideos.coerceAtLeast(1),
                    completedItems = 0,
                    statusMessage = "GitHub Actions ক্লাউড ওয়ার্কারে পাঠানো হচ্ছে..."
                )

                val dispatchResult = repository.dispatchTaskToGitHubActions(task, bundledLessons)
                refreshTasksAndSchedules()

                val topicInfoLine = when {
                    task.targetTopicTitle.isNotBlank() -> " (${task.targetTopicTitle})"
                    task.useForumTopics -> " (সাবজেক্ট টপিক ওয়াইজ)"
                    else -> ""
                }
                val routineInfoLine = if (plan.enableLifetimeSync) {
                    "\n⏰ ২৪/৭ রুটিন     : ${plan.scheduleTimeBn} (নতুন ক্লাস অটো-আপলোড সচল)"
                } else ""

                dispatchResult.fold(
                    onSuccess = { msg ->
                        appendSystemConfirmationMessage(
                            "```text\n" +
                                "🚀 টাস্ক স্ট্যাটাস : RUNNING (GitHub Actions)\n" +
                                "📦 মোট লেকচার    : ${task.totalItems}টি\n" +
                                "🎯 গন্তব্য গ্রুপ/চ্যানেল: ${task.targetChannelTitle}$topicInfoLine\n" +
                                "⚡ স্পিড মোড     : 48x HLS Prefetch + 16x MTProto Parallel$routineInfoLine\n" +
                                "```\n" +
                                "$msg আপনি এখন অ্যাপ বন্ধ করলেও বা সিলেবাস পরিবর্তন করলেও ব্যাকগ্রাউন্ডে আপলোড চলবে এবং শেষ হলে নোটিফিকেশন পাবেন!"
                        )
                    },
                    onFailure = { err ->
                        appendSystemConfirmationMessage(
                            "ℹ️ **টাস্ক কিউতে সংরক্ষিত হয়েছে:** ${err.message}"
                        )
                    }
                )
            } finally {
                _uiState.update { it.copy(isDispatchingTask = false) }
            }
        }
    }

    private fun appendSystemConfirmationMessage(text: String) {
        val msg = AiAgentChatMessage(
            id = UUID.randomUUID().toString(),
            role = "model",
            text = text
        )
        _uiState.update { it.copy(messages = it.messages + msg) }
        persistCurrentSessionMessages(_uiState.value.messages)
    }

    // =========================================================================
    // EXTENSION CONFIGURATION ACTIONS
    // =========================================================================

    fun connectAndVerifyGitHub(token: String, ownerOverride: String = "", repoOverride: String = "", branchOverride: String = "main") {
        viewModelScope.launch {
            _uiState.update { it.copy(isVerifyingGitHub = true, statusBannerMessage = null) }
            val current = repository.getGitHubConfig()
            repository.saveGitHubConfig(
                current.copy(
                    personalAccessToken = token.trim(),
                    owner = ownerOverride.trim(),
                    repo = repoOverride.trim(),
                    branch = branchOverride.trim().ifBlank { "main" }
                )
            )
            val result = repository.verifyGitHubTokenAndFetchRepos(token)
            result.fold(
                onSuccess = { (verifiedConfig, repos) ->
                    val finalConfig = verifiedConfig.copy(
                        owner = ownerOverride.trim().ifBlank { verifiedConfig.owner },
                        repo = repoOverride.trim().ifBlank { verifiedConfig.repo },
                        branch = branchOverride.trim().ifBlank { verifiedConfig.branch }
                    )
                    repository.saveGitHubConfig(finalConfig)
                    val runs = repository.fetchRecentGitHubWorkflowRuns()
                    _uiState.update {
                        it.copy(
                            isVerifyingGitHub = false,
                            githubConfig = finalConfig,
                            githubRepos = repos,
                            workflowRuns = runs,
                            statusBannerMessage = "✅ GitHub অ্যাকাউন্ট (@${finalConfig.connectedUsername}) সফলভাবে কানেক্ট হয়েছে!"
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isVerifyingGitHub = false,
                            statusBannerMessage = "❌ ${err.message}"
                        )
                    }
                }
            )
        }
    }

    fun selectGitHubRepository(repo: GitHubRepoSummary) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDeployingWorkflow = true) }
            val updated = _uiState.value.githubConfig.copy(
                owner = repo.owner,
                repo = repo.name,
                branch = repo.defaultBranch,
                isWorkflowDeployed = false
            )
            repository.saveGitHubConfig(updated)
            repository.syncAgentConfigWithSupabase()
            val deployRes = repository.deployAgentWorkflowToGitHub(updated)
            val runs = repository.fetchRecentGitHubWorkflowRuns()
            _uiState.update {
                it.copy(
                    githubConfig = repository.getGitHubConfig(),
                    workflowRuns = runs,
                    isDeployingWorkflow = false,
                    statusBannerMessage = deployRes.getOrElse {
                        "✅ GitHub রিপোজিটরি '${repo.fullName}' সিলেক্ট ও ডিভাইসের ক্লাউড ভল্টে সেভ হয়েছে!"
                    }
                )
            }
        }
    }

    fun deployWorkflowToGitHubRepo() {
        viewModelScope.launch {
            _uiState.update { it.copy(isDeployingWorkflow = true) }
            val res = repository.deployAgentWorkflowToGitHub()
            _uiState.update {
                it.copy(
                    githubConfig = repository.getGitHubConfig(),
                    isDeployingWorkflow = false,
                    statusBannerMessage = res.getOrElse { err -> "❌ ${err.message}" }
                )
            }
        }
    }

    fun requestTelegramOtpCode(
        apiId: String,
        apiHash: String,
        phone: String,
        twoFaPassword: String = ""
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessingTelegramAuth = true) }
            val result = repository.requestTelegramOtpCode(apiId, apiHash, phone, twoFaPassword)
            _uiState.update {
                it.copy(
                    isProcessingTelegramAuth = false,
                    telegramConfig = repository.getTelegramConfig(),
                    statusBannerMessage = result.getOrElse { err -> "❌ ${err.message}" }
                )
            }
            if (result.isSuccess) {
                startTelegramAuthPolling()
            }
        }
    }

    fun verifyTelegramOtpAndCreateSession(otpCode: String, twoFaPassword: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessingTelegramAuth = true) }
            val result = repository.verifyTelegramOtpAndCreateSession(otpCode, twoFaPassword)
            val updatedTg = repository.getTelegramConfig()
            _uiState.update {
                it.copy(
                    isProcessingTelegramAuth = false,
                    telegramConfig = updatedTg,
                    statusBannerMessage = result.getOrElse { err -> "❌ ${err.message}" }
                )
            }
            if (result.isSuccess && !updatedTg.isConnected) {
                startTelegramAuthPolling()
            }
        }
    }

    private fun startTelegramAuthPolling() {
        tgAuthPollingJob?.cancel()
        tgAuthPollingJob = viewModelScope.launch {
            repeat(80) {
                delay(2_500L)
                val prevState = _uiState.value.telegramConfig.authState
                val latestTg = repository.pollTelegramAuthStatusFromSupabase()
                _uiState.update { it.copy(telegramConfig = latestTg) }

                if (latestTg.authState == "WAITING_FOR_OTP" && prevState != "WAITING_FOR_OTP") {
                    _uiState.update {
                        it.copy(statusBannerMessage = "📲 আপনার টেলিগ্রামে ৫ সংখ্যার ওটিপি (OTP) কোড গেছে! নিচে কোডটি লিখে ভেরিফাই চাপুন।")
                    }
                }
                if (latestTg.authState == "NEED_2FA" && prevState != "NEED_2FA") {
                    _uiState.update {
                        it.copy(statusBannerMessage = "🔒 আপনার টেলিগ্রামে 2FA পাসওয়ার্ড দেওয়া আছে। পাসওয়ার্ডটি লিখে ভেরিফাই চাপুন।")
                    }
                }
                if (latestTg.authState == "CONNECTED" && latestTg.sessionString.isNotBlank()) {
                    _uiState.update {
                        it.copy(
                            statusBannerMessage = "🎉 আপনার টেলিগ্রাম একাউন্ট (${latestTg.accountDisplayName}) সফলভাবে কানেক্ট হয়েছে ও ডিভাইসের আন্ডারে স্থায়ীভাবে সেভ হয়েছে!"
                        )
                    }
                    return@launch
                }
                if (latestTg.authState in listOf("ERROR", "SESSION_EXPIRED") && prevState != latestTg.authState) {
                    _uiState.update {
                        it.copy(statusBannerMessage = latestTg.authMessage.ifBlank { "❌ টেলিগ্রাম সেশন ত্রুটি ঘটেছে। পুনরায় ওটিপি দিয়ে কানেক্ট করুন।" })
                    }
                    return@launch
                }
            }
        }
    }

    fun saveTelegramAccountConfig(
        apiId: String,
        apiHash: String,
        phone: String = "",
        sessionString: String,
        botToken: String,
        defaultChatId: String,
        accountDisplayName: String
    ) {
        viewModelScope.launch {
            val cleanApiId = apiId.trim().ifBlank { DEFAULT_TELEGRAM_API_ID }
            val cleanApiHash = apiHash.trim().ifBlank { DEFAULT_TELEGRAM_API_HASH }
            val cfg = TelegramExtensionConfig(
                apiId = cleanApiId,
                apiHash = cleanApiHash,
                phone = phone.trim().ifBlank { _uiState.value.telegramConfig.phone },
                sessionString = sessionString.trim(),
                botToken = botToken.trim(),
                defaultChatId = defaultChatId.trim(),
                accountDisplayName = accountDisplayName.trim().ifBlank { "Personal Telegram Account" },
                authState = if (sessionString.isNotBlank()) "CONNECTED" else _uiState.value.telegramConfig.authState,
                authMessage = _uiState.value.telegramConfig.authMessage,
                isConnected = (cleanApiId.isNotBlank() && cleanApiHash.isNotBlank() && sessionString.isNotBlank()) || botToken.isNotBlank(),
                dialogs = _uiState.value.telegramConfig.dialogs
            )
            repository.saveTelegramConfig(cfg)
            repository.syncAgentConfigWithSupabase()
            _uiState.update {
                it.copy(
                    telegramConfig = repository.getTelegramConfig(),
                    statusBannerMessage = if (cfg.isConnected) {
                        "✅ আপনার টেলিগ্রাম তথ্য ডিভাইসের আন্ডারে ক্লাউড ভল্টে সেভ হয়েছে!"
                    } else {
                        "ℹ️ টেলিগ্রাম তথ্য আপডেট ও ডিভাইসের আন্ডারে সেভ করা হয়েছে।"
                    }
                )
            }
        }
    }

    fun toggleSchedule(schedule: AiAgentSchedule, enabled: Boolean) {
        viewModelScope.launch {
            val updated = schedule.copy(isEnabled = enabled)
            repository.saveSchedule(updated)
            refreshTasksAndSchedules()
        }
    }

    fun deleteSchedule(scheduleId: String) {
        viewModelScope.launch {
            repository.deleteSchedule(scheduleId)
            refreshTasksAndSchedules()
        }
    }

    fun retryOrResumeTask(task: AiAgentTask) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDispatchingTask = true) }
            val (_, bundled) = repository.buildBundledLessonsJson(
                programId = task.programId,
                phaseId = task.phaseId.takeIf { it.isNotBlank() },
                subjectCode = task.subjectCode,
                chapterId = task.chapterId
            )
            val result = repository.dispatchTaskToGitHubActions(
                task.copy(status = "RESUMING", statusMessage = "পুনরায় GitHub Actions-এ চালু করা হচ্ছে..."),
                bundled
            )
            refreshTasksAndSchedules()
            _uiState.update {
                it.copy(
                    isDispatchingTask = false,
                    statusBannerMessage = result.getOrElse { err -> "⚠️ ${err.message}" }
                )
            }
        }
    }

    fun cancelRunningWorkflow(runId: Long) {
        viewModelScope.launch {
            val ok = repository.cancelGitHubWorkflowRun(runId)
            val runs = repository.fetchRecentGitHubWorkflowRuns()
            _uiState.update {
                it.copy(
                    workflowRuns = runs,
                    statusBannerMessage = if (ok) "🛑 GitHub Workflow Run বাতিল করা হয়েছে।" else "বাতিল করা যায়নি।"
                )
            }
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            repository.deleteTask(taskId)
            refreshTasksAndSchedules()
            _uiState.update {
                it.copy(statusBannerMessage = "🗑️ টাস্ক ডাটাবেজ থেকে সফলভাবে ডিলিট করা হয়েছে।")
            }
        }
    }

    fun clearAllCompletedTasks() {
        viewModelScope.launch {
            repository.clearCompletedTasks()
            refreshTasksAndSchedules()
            _uiState.update {
                it.copy(statusBannerMessage = "🧹 সকল সম্পন্ন হওয়া পুরনো টাস্ক মুছে ডাটাবেজ ক্লিয়ার করা হয়েছে।")
            }
        }
    }

    fun clearBannerMessage() {
        _uiState.update { it.copy(statusBannerMessage = null) }
    }

    fun refreshTasksAndSchedules() {
        viewModelScope.launch {
            val fetchedTasks = repository.fetchTasks()
            val fetchedSchedules = repository.fetchSchedules()
            val runs = repository.fetchRecentGitHubWorkflowRuns()
            val latestTg = repository.pollTelegramAuthStatusFromSupabase()

            fetchedTasks.forEach { task ->
                if (task.status.equals("COMPLETED", ignoreCase = true) &&
                    !repository.isTaskCompletionAlreadyNotified(task.id)
                ) {
                    repository.markTaskCompletionNotified(task.id)
                    sendLocalTaskCompletionNotification(task)
                }
            }

            _uiState.update {
                it.copy(
                    tasks = fetchedTasks,
                    schedules = fetchedSchedules,
                    workflowRuns = runs,
                    telegramConfig = latestTg
                )
            }
        }
    }

    private fun startLiveTaskPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (true) {
                delay(12_000L)
                refreshTasksAndSchedules()
            }
        }
    }

    private fun sendLocalTaskCompletionNotification(task: AiAgentTask) {
        try {
            ShikhoNotificationManager.createNotificationChannels(appContext)
            val intent = Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("OPEN_SCREEN", "AI_AGENT")
                if (task.telegramInviteLink.isNotBlank()) {
                    data = Uri.parse(task.telegramInviteLink)
                }
            }
            val pendingIntent = PendingIntent.getActivity(
                appContext,
                Random.nextInt(1000, 99999),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(
                appContext,
                ShikhoNotificationManager.CHANNEL_PUSH_NOTIFICATIONS
            )
                .setSmallIcon(R.drawable.ic_shikho_notification_small)
                .setContentTitle("✅ AI এজেন্ট টাস্ক সম্পন্ন: ${task.title}")
                .setContentText(task.statusMessage.ifBlank { "সকল ভিডিও ও স্লাইড টেলিগ্রামে আপলোড সম্পন্ন হয়েছে!" })
                .setStyle(NotificationCompat.BigTextStyle().bigText(task.statusMessage))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(Random.nextInt(20000, 89999), notification)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to post local completion notification: ${e.message}")
        }
    }
}

class AiAgentViewModelFactory(
    private val context: Context,
    private val apiService: ShikhoApiService,
    private val sessionManager: SessionManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val appCtx = context.applicationContext
        val repo = AiAgentRepository(appCtx, apiService, sessionManager)
        return AiAgentViewModel(appCtx, repo, sessionManager) as T
    }
}
