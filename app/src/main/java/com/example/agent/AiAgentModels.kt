package com.example.agent

const val DEFAULT_TELEGRAM_API_ID = "38634154"
const val DEFAULT_TELEGRAM_API_HASH = "a4194e506db4ad97f1dba20ec28ac383"

data class GitHubExtensionConfig(
    val personalAccessToken: String = "",
    val owner: String = "",
    val repo: String = "",
    val branch: String = "main",
    val connectedUsername: String = "",
    val avatarUrl: String = "",
    val isVerified: Boolean = false,
    val isWorkflowDeployed: Boolean = false
)

data class TelegramForumTopic(
    val id: Int,
    val title: String
)

data class TelegramDialogDestination(
    val id: String,
    val title: String,
    val username: String = "",
    val isChannel: Boolean = true,
    val isMegagroup: Boolean = false,
    val isForum: Boolean = false,
    val canPost: Boolean = true,
    val topics: List<TelegramForumTopic> = emptyList()
)

data class TelegramExtensionConfig(
    val apiId: String = DEFAULT_TELEGRAM_API_ID,
    val apiHash: String = DEFAULT_TELEGRAM_API_HASH,
    val phone: String = "",
    val sessionString: String = "",
    val botToken: String = "",
    val defaultChatId: String = "",
    val accountDisplayName: String = "",
    val authState: String = "IDLE", // IDLE, SENDING_CODE, WAITING_FOR_OTP, VERIFYING_OTP, NEED_2FA, CONNECTED, ERROR
    val authMessage: String = "",
    val isConnected: Boolean = false,
    val dialogs: List<TelegramDialogDestination> = emptyList()
)

data class GitHubRepoSummary(
    val fullName: String,
    val owner: String,
    val name: String,
    val defaultBranch: String,
    val isPrivate: Boolean
)

data class GitHubWorkflowRunInfo(
    val id: Long,
    val name: String,
    val status: String, // queued, in_progress, completed
    val conclusion: String?, // success, failure, cancelled, null
    val htmlUrl: String,
    val createdAt: String
)

data class ProposedAgentPlan(
    val actionType: String, // START_UPLOAD_TASK, CREATE_SCHEDULE, CREATE_TELEGRAM_CHANNEL, NONE
    val taskTitle: String = "",
    val programId: String = "",
    val programTitle: String = "",
    val phaseId: String = "",
    val subjectCode: String = "",
    val subjectTitle: String = "",
    val chapterId: String = "",
    val chapterTitle: String = "",
    val targetChannelTitle: String = "",
    val targetChatId: String = "",
    val targetTopicId: Int = 0,
    val targetTopicTitle: String = "",
    val videoQuality: String = "720p",
    val includeVideos: Boolean = true,
    val includeSlides: Boolean = true,
    val createNewChannel: Boolean = true,
    val useForumTopics: Boolean = false,
    val scheduleTimeBn: String = "প্রতিদিন সকাল ০৮:০০",
    val cronExpression: String = "0 2 * * *",
    val estimatedVideos: Int = 0,
    val estimatedSlides: Int = 0
)

data class AiAgentChatMessage(
    val id: String,
    val role: String, // "user", "model", "system"
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val proposedPlan: ProposedAgentPlan? = null,
    val isStreaming: Boolean = false,
    val isError: Boolean = false
)

data class AiAgentTask(
    val id: String,
    val title: String,
    val taskType: String = "COURSE_TELEGRAM_UPLOAD",
    val status: String = "QUEUED", // QUEUED, RUNNING, RESUMING, COMPLETED, FAILED, CANCELLED
    val programId: String = "",
    val programTitle: String = "",
    val phaseId: String = "",
    val subjectCode: String = "",
    val subjectTitle: String = "",
    val chapterId: String = "",
    val chapterTitle: String = "",
    val videoQuality: String = "720p",
    val includeVideos: Boolean = true,
    val includeSlides: Boolean = true,
    val createNewChannel: Boolean = true,
    val useForumTopics: Boolean = false,
    val targetChannelTitle: String = "",
    val targetChatId: String = "",
    val targetTopicId: Int = 0,
    val targetTopicTitle: String = "",
    val telegramInviteLink: String = "",
    val totalItems: Int = 0,
    val completedItems: Int = 0,
    val currentItemTitle: String = "",
    val statusMessage: String = "কাজের জন্য প্রস্তুত...",
    val githubRunId: String = "",
    val createdAt: String = "",
    val updatedAt: String = ""
) {
    val progressFraction: Float
        get() = if (totalItems > 0) (completedItems.toFloat() / totalItems.toFloat()).coerceIn(0f, 1f) else 0f
}

data class AiAgentSchedule(
    val id: String,
    val title: String,
    val scheduleType: String = "DAILY_COURSE_SYNC",
    val cronExpression: String = "0 2 * * *",
    val timeDisplayBn: String = "প্রতিদিন সকাল ০৮:০০",
    val programId: String = "",
    val programTitle: String = "",
    val phaseId: String = "",
    val subjectCode: String = "",
    val targetChatId: String = "",
    val targetChannelTitle: String = "",
    val includeVideos: Boolean = true,
    val includeSlides: Boolean = true,
    val includeDailyRoutine: Boolean = true,
    val isEnabled: Boolean = true,
    val lastRunAt: String = "",
    val lastRunStatus: String = "অপেক্ষমাণ"
)

data class CourseInspectionSummary(
    val programId: String,
    val programTitle: String,
    val phaseId: String,
    val subjectsCount: Int,
    val totalLessonsCount: Int,
    val videoLessonsCount: Int,
    val slideAttachmentsCount: Int,
    val subjectBreakdown: List<SubjectInspectionItem>
)

data class SubjectInspectionItem(
    val subjectCode: String,
    val subjectTitle: String,
    val lessonsCount: Int,
    val videosCount: Int,
    val slidesCount: Int
)
