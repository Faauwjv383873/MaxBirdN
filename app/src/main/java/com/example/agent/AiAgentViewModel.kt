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
    val githubConfig: GitHubExtensionConfig = GitHubExtensionConfig(),
    val githubRepos: List<GitHubRepoSummary> = emptyList(),
    val workflowRuns: List<GitHubWorkflowRunInfo> = emptyList(),
    val telegramConfig: TelegramExtensionConfig = TelegramExtensionConfig(),
    val enrolledPrograms: List<EnrolledProgram> = emptyList(),
    val selectedProgramId: String = "",
    val selectedProgramTitle: String = "",
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
                    description = "Detailed, helpful response in Bengali discussing the plan, answering the user's question, or confirming the triggered background action."
                ),
                "action_type" to Schema.enumeration(
                    values = listOf(
                        "DISCUSS",
                        "PROPOSE_PLAN",
                        "START_UPLOAD_TASK",
                        "CREATE_SCHEDULE",
                        "CREATE_TELEGRAM_CHANNEL"
                    ),
                    description = "Action to take: DISCUSS for conversation, PROPOSE_PLAN to show an interactive execution card before starting, START_UPLOAD_TASK when user confirms or asks to start uploading now, CREATE_SCHEDULE for daily/recurring 24/7 routines, CREATE_TELEGRAM_CHANNEL to create a channel immediately."
                ),
                "task_title" to Schema.string(description = "Short Bengali title for the task or schedule"),
                "subject_code" to Schema.string(description = "Target subject code if user specified a subject, or empty string for all subjects"),
                "subject_title" to Schema.string(description = "Target subject title in Bengali, or empty string for full course"),
                "target_channel_title" to Schema.string(description = "Suggested Telegram channel or group name"),
                "video_quality" to Schema.enumeration(
                    values = listOf("1080p", "720p", "480p", "360p"),
                    description = "Video resolution quality"
                ),
                "include_videos" to Schema.boolean(description = "Whether to upload lecture videos"),
                "include_slides" to Schema.boolean(description = "Whether to upload PDF lecture slides and notes"),
                "create_new_channel" to Schema.boolean(description = "Whether to create a new Telegram channel/group automatically"),
                "use_forum_topics" to Schema.boolean(description = "True for Telegram Supergroup with Topics, False for standard broadcast Channel"),
                "schedule_time_bn" to Schema.string(description = "Human-readable Bengali schedule time, e.g., প্রতিদিন সকাল ০৮:০০"),
                "cron_expression" to Schema.string(description = "Standard 5-field UTC cron expression for recurring schedules, e.g. 0 2 * * * for 8:00 AM BD time")
            ),
            optionalProperties = listOf(
                "subject_code",
                "subject_title",
                "target_channel_title",
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

    fun initializeAgentWorkspace() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingInitial = true,
                    githubConfig = repository.getGitHubConfig(),
                    telegramConfig = repository.getTelegramConfig(),
                    deviceVaultId = repository.getCurrentDeviceHash(),
                    selectedProgramId = sessionManager.getActiveProgramId().orEmpty(),
                    selectedProgramTitle = sessionManager.getActiveProgramTitleBn() ?: "সক্রিয় কোর্স"
                )
            }

            // Initial greeting message if chat is empty
            if (_uiState.value.messages.isEmpty()) {
                val activeTitle = sessionManager.getActiveProgramTitleBn() ?: "আপনার সক্রিয় কোর্স"
                val welcomeMsg = AiAgentChatMessage(
                    id = "welcome_msg",
                    role = "model",
                    text = "আসসালামু আলাইকুম বস! 🤖 আমি আপনার **MaxBird Autonomous AI Agent**।\n\n" +
                        "আমার সাথে আপনার **GitHub Actions (24/7 Cloud Worker)**, **Telegram Personal Account** এবং **Shikho Course Engine** যুক্ত রয়েছে।\n\n" +
                        "আপনি আমার সাথে যেকোনো কাজ শুরুর আগে আলোচনা করে প্ল্যান সাজাতে পারেন, অথবা সরাসরি নির্দেশ দিতে পারেন:\n" +
                        "• *\"$activeTitle-এর সব ভিডিও ও লেকচার স্লাইড কতগুলো আছে দেখো এবং টেলিগ্রামে আপলোডের প্ল্যান দাও\"*\n" +
                        "• *\"এই কোর্সের সব ক্লাস ও স্লাইড টেলিগ্রামে নতুন চ্যানেল খুলে এক এক করে আপলোড শুরু করো\"*\n" +
                        "• *\"প্রতিদিন সকাল ৮টায় নতুন ক্লাস ও রুটিন চেক করে টেলিগ্রামে অটো-আপলোড করার রুটিন চালু করো\"*"
                )
                _uiState.update { it.copy(messages = listOf(welcomeMsg)) }
            }

            // 1. Restore & sync Device-Bound Cloud Vault from Supabase (recovers GitHub + Telegram after app reinstall)
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

            // 2. Load courses, tasks, schedules, and inspect active course
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
                    inspectCourse(activeId, activeTitle)
                }
            }
            refreshTasksAndSchedules()
            startLiveTaskPolling()
            _uiState.update { it.copy(isLoadingInitial = false) }
        }
    }

    fun selectProgramForAgent(programId: String, programTitle: String) {
        _uiState.update {
            it.copy(
                selectedProgramId = programId,
                selectedProgramTitle = programTitle,
                selectedSubjectCode = "",
                selectedSubjectTitle = ""
            )
        }
        inspectCourse(programId, programTitle)
    }

    fun selectSubjectFilter(subjectCode: String, subjectTitle: String) {
        _uiState.update {
            it.copy(
                selectedSubjectCode = subjectCode,
                selectedSubjectTitle = subjectTitle
            )
        }
    }

    fun inspectCourse(programId: String, programTitle: String) {
        if (programId.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isInspectingCourse = true) }
            val summary = repository.inspectProgramContent(
                programId = programId,
                programTitle = programTitle,
                phaseId = sessionManager.getActiveProgramPhaseId()
            )
            _uiState.update {
                it.copy(
                    activeInspection = summary,
                    isInspectingCourse = false
                )
            }
        }
    }

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
            text = "চিন্তা করছি এবং কোর্সের তথ্য বিশ্লেষণ করছি...",
            isStreaming = true
        )

        _uiState.update {
            it.copy(
                brainState = AgentBrainState.Thinking,
                messages = it.messages + userMsg + thinkingMsg
            )
        }

        viewModelScope.launch {
            try {
                val state = _uiState.value
                val inspection = state.activeInspection ?: run {
                    if (state.selectedProgramId.isNotBlank()) {
                        repository.inspectProgramContent(
                            programId = state.selectedProgramId,
                            programTitle = state.selectedProgramTitle,
                            phaseId = sessionManager.getActiveProgramPhaseId()
                        ).also { summary ->
                            _uiState.update { s -> s.copy(activeInspection = summary) }
                        }
                    } else null
                }

                val subjectBreakdownText = inspection?.subjectBreakdown?.joinToString("\n") { sub ->
                    "- ${sub.subjectTitle} (Code: ${sub.subjectCode}): ${sub.lessonsCount} লেকচার, ${sub.videosCount} ভিডিও, ${sub.slidesCount} পিডিএফ স্লাইড"
                } ?: "কোনো বিষয় তালিকা লোড হয়নি"

                val recentHistoryText = state.messages
                    .filterNot { it.isStreaming }
                    .takeLast(6)
                    .joinToString("\n") { m -> "${m.role.uppercase()}: ${m.text}" }

                val fullContextPrompt = """
                    You are MaxBird Autonomous AI Agent inside the MaxBird Android App.
                    You have full control over:
                    1. GitHub Actions 24/7 Cloud Worker (Connected: ${state.githubConfig.isVerified}, Repo: ${state.githubConfig.owner}/${state.githubConfig.repo})
                    2. Personal Telegram Account via Telethon MTProto (Connected: ${state.telegramConfig.isConnected}, Account: ${state.telegramConfig.accountDisplayName}, Synced Channels/Groups: ${state.telegramConfig.dialogs.size})
                    3. Shikho Course Engine:
                       - Currently Selected Course: ${state.selectedProgramTitle} (ID: ${state.selectedProgramId})
                       - Selected Subject Filter: ${state.selectedSubjectTitle.ifBlank { "সকল বিষয় (All Subjects)" }} (${state.selectedSubjectCode})
                       - Total Lessons in Course: ${inspection?.totalLessonsCount ?: 0}
                       - Total Videos: ${inspection?.videoLessonsCount ?: 0}
                       - Total Lecture Slides (PDF): ${inspection?.slideAttachmentsCount ?: 0}
                       - Subject Breakdown:
                       $subjectBreakdownText
                    4. Active Background Tasks: ${state.tasks.count { it.status in listOf("QUEUED", "RUNNING", "RESUMING") }} running, ${state.tasks.count { it.status == "COMPLETED" }} completed.
                    5. Active 24/7 Schedules: ${state.schedules.count { it.isEnabled }} enabled.

                    Rules for choosing `action_type`:
                    - If the user is greeting you (e.g., "হাই", "কেমন আছেন", "সালাম"), asking a general question, or chatting normally: choose `DISCUSS` and answer naturally, warmly, and intelligently in Bengali! Do NOT propose an upload plan if they are just chatting or asking how you are.
                    - If the user asks for an upload plan, course analysis, or wants to review before starting: choose `PROPOSE_PLAN`.
                    - If the user explicitly commands to start uploading videos/slides now (e.g., "শুরু করো", "আপলোড করো", "টাস্ক চালু করো"): choose `START_UPLOAD_TASK`.
                    - If the user commands to set up a daily/recurring schedule (e.g., "প্রতিদিন সকাল ৮টায়...", "রুটিন অনুযায়ী প্রতিদিন..."): choose `CREATE_SCHEDULE`.
                    - If the user asks to only create a Telegram channel/group right now: choose `CREATE_TELEGRAM_CHANNEL`.

                    Return strictly a valid JSON object with these keys:
                    {
                      "reply_bn": "Your natural, helpful Bengali response to the user",
                      "action_type": "DISCUSS" | "PROPOSE_PLAN" | "START_UPLOAD_TASK" | "CREATE_SCHEDULE" | "CREATE_TELEGRAM_CHANNEL",
                      "task_title": "Short Bengali title if action_type != DISCUSS",
                      "subject_code": "",
                      "subject_title": "",
                      "target_channel_title": "${state.selectedProgramTitle}",
                      "video_quality": "720p",
                      "include_videos": true,
                      "include_slides": true,
                      "create_new_channel": true,
                      "use_forum_topics": false,
                      "schedule_time_bn": "প্রতিদিন সকাল ০৮:০০",
                      "cron_expression": "0 2 * * *"
                    }

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
                    Log.w(TAG, "Firebase.ai unavailable (${aiErr.message}); switching to Real Cloud LLM Engine...")
                    repository.generateCloudLlmAgentResponse(fullContextPrompt, cleanPrompt)
                        ?: generateSmartAgentDecision(cleanPrompt, state, inspection)
                }

                val replyBn = json.optString("reply_bn", "আপনার নির্দেশ বুঝতে পেরেছি।")
                val actionType = json.optString("action_type", "DISCUSS").uppercase()
                val taskTitle = json.optString("task_title", "${state.selectedProgramTitle} — টেলিগ্রাম আপলোড")
                val subjCode = json.optString("subject_code", state.selectedSubjectCode).trim()
                val subjTitle = json.optString("subject_title", state.selectedSubjectTitle).trim()
                val channelTitle = json.optString(
                    "target_channel_title",
                    if (subjTitle.isNotBlank()) "${state.selectedProgramTitle} - $subjTitle" else state.selectedProgramTitle
                ).trim()
                val quality = json.optString("video_quality", "720p")
                val incVideos = json.optBoolean("include_videos", true)
                val incSlides = json.optBoolean("include_slides", true)
                val createNewChan = json.optBoolean("create_new_channel", true)
                val useTopics = json.optBoolean("use_forum_topics", false)
                val schedTimeBn = json.optString("schedule_time_bn", "প্রতিদিন সকাল ০৮:০০")
                val cronExpr = json.optString("cron_expression", "0 2 * * *")

                val matchedSubject = inspection?.subjectBreakdown?.firstOrNull {
                    it.subjectCode.equals(subjCode, ignoreCase = true) ||
                        (subjTitle.isNotBlank() && it.subjectTitle.contains(subjTitle, ignoreCase = true))
                }

                val proposedPlan = if (actionType != "DISCUSS" && actionType != "NONE") {
                    ProposedAgentPlan(
                        actionType = actionType,
                        taskTitle = taskTitle.ifBlank { "${state.selectedProgramTitle} আপলোড" },
                        programId = state.selectedProgramId,
                        programTitle = state.selectedProgramTitle,
                        phaseId = sessionManager.getActiveProgramPhaseId().orEmpty(),
                        subjectCode = matchedSubject?.subjectCode ?: subjCode,
                        subjectTitle = matchedSubject?.subjectTitle ?: subjTitle,
                        targetChannelTitle = channelTitle.ifBlank { state.selectedProgramTitle },
                        targetChatId = state.telegramConfig.defaultChatId,
                        videoQuality = quality,
                        includeVideos = incVideos,
                        includeSlides = incSlides,
                        createNewChannel = createNewChan,
                        useForumTopics = useTopics,
                        scheduleTimeBn = schedTimeBn,
                        cronExpression = cronExpr,
                        estimatedVideos = matchedSubject?.videosCount ?: (inspection?.videoLessonsCount ?: 0),
                        estimatedSlides = matchedSubject?.slidesCount ?: (inspection?.slideAttachmentsCount ?: 0)
                    )
                } else null

                val finalModelMsg = AiAgentChatMessage(
                    id = thinkingMsgId,
                    role = "model",
                    text = replyBn,
                    proposedPlan = proposedPlan,
                    isStreaming = false
                )

                _uiState.update { current ->
                    current.copy(
                        brainState = AgentBrainState.Success(replyBn),
                        messages = current.messages.map { if (it.id == thinkingMsgId) finalModelMsg else it }
                    )
                }

                // If the user explicitly commanded immediate execution, execute the plan right away!
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

    /**
     * Offline Fallback Decision Engine (used only if all Cloud LLMs are unreachable).
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

        val matchedSub = inspection?.subjectBreakdown?.firstOrNull { sub ->
            val t = sub.subjectTitle.lowercase()
            val c = sub.subjectCode.lowercase()
            (t.isNotBlank() && p.contains(t)) ||
                (c.isNotBlank() && c != "gen" && p.contains(c)) ||
                (t.contains("বাংলা") && p.contains("বাংলা")) ||
                (t.contains("ইংরেজি") && (p.contains("ইংরেজি") || p.contains("english"))) ||
                (t.contains("তথ্য") && (p.contains("আইসিটি") || p.contains("ict"))) ||
                (t.contains("ইতিহাস") && p.contains("ইতিহাস")) ||
                (t.contains("অর্থনীতি") && p.contains("অর্থনীতি")) ||
                (t.contains("পৌরনীতি") && p.contains("পৌরনীতি")) ||
                (t.contains("যুক্তিবিদ্যা") && p.contains("যুক্তিবিদ্যা")) ||
                (t.contains("সমাজবিজ্ঞান") && p.contains("সমাজবিজ্ঞান")) ||
                (t.contains("সমাজকর্ম") && p.contains("সমাজকর্ম")) ||
                (t.contains("ভূগোল") && p.contains("ভূগোল")) ||
                (t.contains("ইসলাম") && p.contains("ইসলাম")) ||
                (t.contains("ওরিয়েন্টেশন") && p.contains("ওরিয়েন্টেশন"))
        } ?: inspection?.subjectBreakdown?.firstOrNull {
            state.selectedSubjectCode.isNotBlank() && it.subjectCode == state.selectedSubjectCode
        }

        val subjCode = matchedSub?.subjectCode ?: state.selectedSubjectCode
        val subjTitle = matchedSub?.subjectTitle ?: state.selectedSubjectTitle
        val targetVideos = matchedSub?.videosCount ?: totalVideos
        val targetSlides = matchedSub?.slidesCount ?: totalSlides

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
        val useForum = p.contains("গ্রুপ") || p.contains("টপিক") || p.contains("group")

        val ghStatus = if (state.githubConfig.isVerified) {
            "🟢 কানেক্টেড (@${state.githubConfig.owner}/${state.githubConfig.repo})"
        } else {
            "🟠 কানেক্ট করা নেই"
        }
        val tgStatus = if (state.telegramConfig.isConnected) {
            "🟢 কানেক্টেড (${state.telegramConfig.accountDisplayName.ifBlank { "Telegram Userbot" }})"
        } else {
            "🟠 কানেক্ট করা নেই"
        }

        val isAskingPlanOnly = p.contains("প্ল্যান") || p.contains("plan") || p.contains("দেখাও") ||
            p.contains("কতগুলো") || p.contains("কয়টি") || p.contains("আলোচনা")
        val isScheduleCmd = (p.contains("রুটিন") || p.contains("প্রতিদিন") || p.contains("সকাল") || p.contains("রাত") || p.contains("schedule")) &&
            (p.contains("চালু") || p.contains("করো") || p.contains("সেট") || p.contains("অটো"))
        val isCreateChannelOnly = (p.contains("চ্যানেল তৈরি") || p.contains("চ্যানেল খোল") || p.contains("গ্রুপ তৈরি") || p.contains("গ্রুপ খোল")) &&
            !p.contains("আপলোড") && !p.contains("ভিডিও")
        val isImmediateUploadCmd = !isAskingPlanOnly && !isScheduleCmd && !isCreateChannelOnly && (
            p.contains("আপলোড করো") || p.contains("আপলোড শুরু") || p.contains("শুরু করো") ||
                p.contains("টেলিগ্রামে দাও") || p.contains("পাঠাও") || p.contains("টাস্ক চালু") ||
                p.contains("upload") || p.contains("start")
            )

        val channelTitle = if (subjTitle.isNotBlank()) "$courseTitle — $subjTitle" else courseTitle
        val subjectListText = inspection?.subjectBreakdown?.take(8)?.joinToString("\n") { s ->
            "• **${s.subjectTitle}**: 🎬 ${s.videosCount}টি ভিডিও • 📄 ${s.slidesCount}টি স্লাইড"
        } ?: "• সকল লেকচার ও স্লাইড প্রস্তুত"

        val actionType: String
        val replyBn: String
        val taskTitle: String

        when {
            isCreateChannelOnly -> {
                actionType = "CREATE_TELEGRAM_CHANNEL"
                taskTitle = "$channelTitle — নতুন চ্যানেল তৈরি"
                replyBn = "বস, আপনার নির্দেশ অনুযায়ী **$channelTitle** নামে একটি নতুন টেলিগ্রাম ${if (useForum) "সুপারগ্রুপ" else "চ্যানেল"} তৈরি করার কাজ এখনই GitHub Actions-এ পাঠিয়ে দিচ্ছি! 🚀"
            }
            isScheduleCmd -> {
                actionType = "CREATE_SCHEDULE"
                taskTitle = "$channelTitle — ২৪/৭ অটো রুটিন"
                replyBn = "⏰ বস, **$channelTitle**-এর জন্য **২৪/৭ অটোমেটিক রুটিন** চালু করে দিচ্ছি!\n\n" +
                    "এখন থেকে প্রতিদিন নির্ধারিত সময়ে এজেন্ট নিজে নিজে নতুন ক্লাস ভিডিও ও লেকচার স্লাইড চেক করে আপনার টেলিগ্রামে আপলোড করে দেবে এবং শেষ হলে আপনাকে নোটিফিকেশনে জানিয়ে দেবে।"
            }
            isImmediateUploadCmd -> {
                actionType = "START_UPLOAD_TASK"
                taskTitle = "$channelTitle — টেলিগ্রাম আপলোড"
                replyBn = "🚀 জি বস! এখনই **$channelTitle**-এর **${targetVideos}টি ভিডিও ($quality)** এবং **${targetSlides}টি লেকচার স্লাইড (PDF)** আপনার টেলিগ্রামে নতুন ${if (useForum) "গ্রুপ" else "চ্যানেল"} খুলে এক এক করে আপলোড করার ব্যাকগ্রাউন্ড টাস্ক চালু করে দিচ্ছি!"
            }
            isAskingPlanOnly || matchedSub != null -> {
                actionType = "PROPOSE_PLAN"
                taskTitle = "$channelTitle — ফুল কোর্স আপলোড"
                replyBn = "📚 **$courseTitle** বিশ্লেষণ:\n" +
                    "• মোট ক্লাস ভিডিও: **${targetVideos}টি**\n" +
                    "• মোট লেকচার স্লাইড (PDF): **${targetSlides}টি**\n\n" +
                    "📂 **বিষয়ভিত্তিক তালিকা:**\n$subjectListText\n\n" +
                    "নিচে আপনার জন্য আপলোড প্ল্যান তৈরি করে দিয়েছি:"
            }
            else -> {
                actionType = "DISCUSS"
                taskTitle = ""
                replyBn = "আলহামদুলিল্লাহ বস, আমি একদম ভালো আছি এবং আপনার সেবায় প্রস্তুত! 😊\n\n" +
                    "আপনার **GitHub ($ghStatus)** এবং **Telegram ($tgStatus)** আমার সাথে যুক্ত আছে। আপনি চাইলে এখান থেকে যেকোনো নির্দেশ দিতে পারেন, অথবা যেকোনো অধ্যায় বা ক্লাসের ভেতরে ঢুকে **'✈️ এই ভিডিও টেলিগ্রামে পাঠান'** বাটনে ক্লিক করে আপনার যেকোনো চ্যানেল, গ্রুপ বা নির্দিষ্ট টপিকে সরাসরি ভিডিও ও লেকচার স্লাইড পাঠাতে পারেন!"
            }
        }

        return JSONObject().apply {
            put("reply_bn", replyBn)
            put("action_type", actionType)
            put("task_title", taskTitle)
            put("subject_code", subjCode)
            put("subject_title", subjTitle)
            put("target_channel_title", channelTitle)
            put("video_quality", quality)
            put("include_videos", incVideos)
            put("include_slides", incSlides)
            put("create_new_channel", true)
            put("use_forum_topics", useForum)
            put("schedule_time_bn", "প্রতিদিন সকাল ০৮:০০")
            put("cron_expression", "0 2 * * *")
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
    }

    fun executeProposedPlan(plan: ProposedAgentPlan) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDispatchingTask = true) }
            try {
                if (plan.actionType == "CREATE_SCHEDULE") {
                    val newSchedule = AiAgentSchedule(
                        id = "sched_${System.currentTimeMillis()}",
                        title = plan.taskTitle.ifBlank { "${plan.programTitle} — ২৪/৭ অটো সিঙ্ক" },
                        scheduleType = "DAILY_COURSE_SYNC",
                        cronExpression = plan.cronExpression.ifBlank { "0 2 * * *" },
                        timeDisplayBn = plan.scheduleTimeBn.ifBlank { "প্রতিদিন সকাল ০৮:০০" },
                        programId = plan.programId.ifBlank { _uiState.value.selectedProgramId },
                        programTitle = plan.programTitle.ifBlank { _uiState.value.selectedProgramTitle },
                        phaseId = plan.phaseId.ifBlank { sessionManager.getActiveProgramPhaseId().orEmpty() },
                        subjectCode = plan.subjectCode,
                        targetChatId = plan.targetChatId.ifBlank { _uiState.value.telegramConfig.defaultChatId },
                        targetChannelTitle = plan.targetChannelTitle.ifBlank { plan.programTitle },
                        includeVideos = plan.includeVideos,
                        includeSlides = plan.includeSlides,
                        includeDailyRoutine = true,
                        isEnabled = true,
                        lastRunStatus = "সক্রিয় — নির্ধারিত সময়ে GitHub Actions-এ চলবে ✅"
                    )
                    repository.saveSchedule(newSchedule)
                    refreshTasksAndSchedules()
                    appendSystemConfirmationMessage(
                        "⏰ **২৪/৭ রুটিন সফলভাবে চালু করা হয়েছে!**\n" +
                            "• রুটিন: **${newSchedule.title}**\n" +
                            "• সময়: **${newSchedule.timeDisplayBn}**\n" +
                            "GitHub Actions প্রতিদিন নির্ধারিত সময়ে নতুন ভিডিও ও স্লাইড চেক করে টেলিগ্রামে আপলোড করবে।"
                    )
                } else {
                    val progId = plan.programId.ifBlank { _uiState.value.selectedProgramId }
                    val progTitle = plan.programTitle.ifBlank { _uiState.value.selectedProgramTitle }
                    val phaseId = plan.phaseId.ifBlank { sessionManager.getActiveProgramPhaseId().orEmpty() }

                    val (lessonCount, bundledLessons) = if (plan.actionType == "CREATE_TELEGRAM_CHANNEL") {
                        Pair(1, null)
                    } else {
                        repository.buildBundledLessonsJson(
                            programId = progId,
                            phaseId = phaseId,
                            subjectCode = plan.subjectCode,
                            chapterId = plan.chapterId
                        )
                    }

                    val task = AiAgentTask(
                        id = "task_${System.currentTimeMillis()}",
                        title = plan.taskTitle.ifBlank {
                            if (plan.subjectTitle.isNotBlank()) "$progTitle (${plan.subjectTitle})" else progTitle
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
                        targetChatId = plan.targetChatId.ifBlank { _uiState.value.telegramConfig.defaultChatId },
                        targetTopicId = plan.targetTopicId,
                        targetTopicTitle = plan.targetTopicTitle,
                        totalItems = lessonCount.coerceAtLeast(plan.estimatedVideos),
                        completedItems = 0,
                        statusMessage = "GitHub Actions ক্লাউড ওয়ার্কারে পাঠানো হচ্ছে..."
                    )

                    val dispatchResult = repository.dispatchTaskToGitHubActions(task, bundledLessons)
                    refreshTasksAndSchedules()

                    dispatchResult.fold(
                        onSuccess = { msg ->
                            appendSystemConfirmationMessage(
                                "🚀 **ব্যাকগ্রাউন্ড টাস্ক চালু হয়েছে!**\n" +
                                    "• টাস্ক: **${task.title}**\n" +
                                    "• মোট লেকচার: **${task.totalItems}টি** (ভিডিও + স্লাইড)\n" +
                                    "• চ্যানেল: **${task.targetChannelTitle}**\n\n" +
                                    "$msg আপনি এখন অ্যাপ বন্ধ করলেও GitHub Actions ২৪/৭ ব্যাকগ্রাউন্ডে আপলোড চালিয়ে যাবে এবং শেষ হলে আপনাকে নোটিফিকেশন দেবে!"
                            )
                        },
                        onFailure = { err ->
                            appendSystemConfirmationMessage(
                                "ℹ️ **টাস্ক কিউতে সংরক্ষিত হয়েছে:** ${err.message}"
                            )
                        }
                    )
                }
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
            repeat(80) { // Poll every 2.5s for up to 3.5 minutes while GitHub worker executes Telethon OTP flow
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
                if (latestTg.authState == "ERROR" && prevState != "ERROR") {
                    _uiState.update {
                        it.copy(statusBannerMessage = latestTg.authMessage.ifBlank { "❌ টেলিগ্রাম ওটিপি প্রক্রিয়ায় ত্রুটি ঘটেছে।" })
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
                isConnected = (cleanApiId.isNotBlank() && cleanApiHash.isNotBlank() && sessionString.isNotBlank()) || botToken.isNotBlank()
            )
            repository.saveTelegramConfig(cfg)
            repository.syncAgentConfigWithSupabase()
            _uiState.update {
                it.copy(
                    telegramConfig = repository.getTelegramConfig(),
                    statusBannerMessage = if (cfg.isConnected) {
                        "✅ আপনার টেলিগ্রাম তথ্য ডিভাইসের আন্ডারে ক্লাউড ভল্টে সেভ হয়েছে (অ্যাপ ডিলিট করলেও অটো ফিরে আসবে)!"
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
                phaseId = task.phaseId,
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

    fun clearBannerMessage() {
        _uiState.update { it.copy(statusBannerMessage = null) }
    }

    fun refreshTasksAndSchedules() {
        viewModelScope.launch {
            val fetchedTasks = repository.fetchTasks()
            val fetchedSchedules = repository.fetchSchedules()
            val runs = repository.fetchRecentGitHubWorkflowRuns()

            // Fire local notification if any task newly transitioned to COMPLETED
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
                    workflowRuns = runs
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
