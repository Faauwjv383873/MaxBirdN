package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.agent.AgentBrainState
import com.example.agent.AgentPlayableLesson
import com.example.agent.AiAgentChatMessage
import com.example.agent.AiAgentSchedule
import com.example.agent.AiAgentTask
import com.example.agent.AiAgentUiState
import com.example.agent.AiAgentViewModel
import com.example.agent.AiChatSession
import com.example.agent.DEFAULT_TELEGRAM_API_HASH
import com.example.agent.DEFAULT_TELEGRAM_API_ID
import com.example.agent.GitHubRepoSummary
import com.example.agent.ProposedAgentPlan
import com.example.agent.TelegramDialogDestination
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAgentScreen(
    viewModel: AiAgentViewModel,
    onBack: () -> Unit,
    onPlayVideo: (url: String, title: String, subject: String) -> Unit = { _, _, _ -> },
    onOpenPdf: (url: String, title: String, subject: String) -> Unit = { _, _, _ -> }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    var showSettingsSheet by rememberSaveable { mutableStateOf(false) }
    var settingsInitialSection by rememberSaveable { mutableIntStateOf(0) } // 0 = Accounts, 1 = Tasks & 24/7 Routines
    var lessonForTelegramSheet by remember { mutableStateOf<AgentPlayableLesson?>(null) }

    // Auto-launch video player if the user asked the AI Agent to play a video right now
    LaunchedEffect(state.pendingAutoPlayLesson) {
        val pending = state.pendingAutoPlayLesson
        if (pending != null && pending.videoUrl.isNotBlank()) {
            viewModel.clearPendingAutoPlayLesson()
            onPlayVideo(pending.videoUrl, pending.title, pending.subjectTitle)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            GeminiSessionDrawerContent(
                state = state,
                onNewChat = {
                    viewModel.startNewChatSession()
                    scope.launch { drawerState.close() }
                },
                onSelectSession = { sessionId ->
                    viewModel.selectChatSession(sessionId)
                    scope.launch { drawerState.close() }
                },
                onDeleteSession = { sessionId ->
                    viewModel.deleteChatSession(sessionId)
                },
                onOpenAccountsSheet = {
                    settingsInitialSection = 0
                    showSettingsSheet = true
                    scope.launch { drawerState.close() }
                },
                onOpenTasksSheet = {
                    settingsInitialSection = 1
                    showSettingsSheet = true
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                GeminiUnifiedTopBar(
                    state = state,
                    onBack = onBack,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onNewChat = { viewModel.startNewChatSession() },
                    onOpenAccounts = {
                        settingsInitialSection = 0
                        showSettingsSheet = true
                    },
                    onOpenTasks = {
                        settingsInitialSection = 1
                        showSettingsSheet = true
                    },
                    onSelectProgram = { id, title -> viewModel.selectProgramForAgent(id, title) },
                    onSelectQuarter = { phaseId, phaseTitle -> viewModel.selectQuarterFilter(phaseId, phaseTitle) },
                    onSelectSubject = { code, title -> viewModel.selectSubjectFilter(code, title) }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Alert banner if Telegram session was revoked/expired
                if (state.telegramConfig.authState == "SESSION_EXPIRED") {
                    Surface(
                        color = Color(0xFF7F1D1D),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .clickable {
                                settingsInitialSection = 0
                                showSettingsSheet = true
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = "Session Expired",
                                tint = Color(0xFFFCA5A5),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "টেলিগ্রাম সেশনটি পুনরায় কানেক্ট করুন",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "একাধিক রানের কারণে আগের সেশনটি বাতিল হয়েছিল। এখন সিঙ্গেল-আইপি লক যুক্ত করা হয়েছে—১ বার ওটিপি দিলেই আর কখনো নষ্ট হবে না।",
                                    fontSize = 11.sp,
                                    color = Color(0xFFFECACA)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = Color(0xFFEF4444),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "কানেক্ট",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                // Status Banner if present
                AnimatedVisibility(
                    visible = !state.statusBannerMessage.isNullOrBlank(),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = state.statusBannerMessage.orEmpty(),
                                color = Color.White,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.clearBannerMessage() },
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "বন্ধ করুন",
                                    tint = Color.White.copy(alpha = 0.75f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Live Collapsible Active Task Status Bar inside the unified chat view
                val activeOrLatestTask = state.tasks.firstOrNull {
                    it.status.uppercase() in setOf("RUNNING", "QUEUED", "RESUMING")
                } ?: state.tasks.firstOrNull()

                if (activeOrLatestTask != null) {
                    LiveTaskCompactBanner(
                        task = activeOrLatestTask,
                        activeRunsCount = state.workflowRuns.count { it.status == "in_progress" || it.status == "queued" },
                        onOpenTelegramLink = { link ->
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
                            } catch (_: Exception) {
                            }
                        },
                        onRetryTask = { viewModel.retryOrResumeTask(activeOrLatestTask) },
                        onDeleteTask = { viewModel.deleteTask(activeOrLatestTask.id) },
                        onOpenAllTasks = {
                            settingsInitialSection = 1
                            showSettingsSheet = true
                        }
                    )
                }

                // Loading indicator when resolving video/slide URLs
                if (state.isResolvingLessonMedia) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF38BDF8)
                    )
                }

                // Main Unified Gemini Chat Workspace
                GeminiChatWorkspace(
                    state = state,
                    onSendPrompt = { viewModel.sendPromptToAgent(it) },
                    onExecutePlan = { plan -> viewModel.executeProposedPlan(plan) },
                    onPlayLessonVideo = { lesson ->
                        viewModel.resolveAndPlayLesson(lesson) { resolved ->
                            if (resolved.videoUrl.isNotBlank()) {
                                onPlayVideo(resolved.videoUrl, resolved.title, resolved.subjectTitle)
                            } else if (resolved.slideUrl.isNotBlank()) {
                                onOpenPdf(resolved.slideUrl, resolved.title, resolved.subjectTitle)
                            }
                        }
                    },
                    onOpenLessonSlide = { lesson ->
                        viewModel.resolveAndPlayLesson(lesson) { resolved ->
                            if (resolved.slideUrl.isNotBlank()) {
                                onOpenPdf(resolved.slideUrl, resolved.title, resolved.subjectTitle)
                            } else if (resolved.videoUrl.isNotBlank()) {
                                onPlayVideo(resolved.videoUrl, resolved.title, resolved.subjectTitle)
                            }
                        }
                    },
                    onSendLessonToTelegram = { lesson ->
                        lessonForTelegramSheet = lesson
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    // Bottom sheet for sending a specific lesson from chat to any Telegram Channel/Group/Topic
    if (lessonForTelegramSheet != null) {
        val targetLesson = lessonForTelegramSheet!!
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { lessonForTelegramSheet = null },
            sheetState = sheetState,
            containerColor = Color(0xFF0F172A)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                InteractiveConfirmationPlanCard(
                    plan = ProposedAgentPlan(
                        actionType = "START_UPLOAD_TASK",
                        taskTitle = "${targetLesson.subjectTitle} • ${targetLesson.title}",
                        programId = state.selectedProgramId,
                        programTitle = state.selectedProgramTitle,
                        phaseId = targetLesson.phaseId,
                        phaseTitle = targetLesson.phaseTitle,
                        subjectCode = targetLesson.subjectCode,
                        subjectTitle = targetLesson.subjectTitle,
                        chapterId = targetLesson.chapterId,
                        chapterTitle = targetLesson.chapterTitle,
                        specificLessonIds = listOf(targetLesson.id),
                        targetChannelTitle = "${state.selectedProgramTitle} — ${targetLesson.subjectTitle}",
                        estimatedVideos = 1,
                        estimatedSlides = 1
                    ),
                    dialogs = state.telegramConfig.dialogs,
                    isDispatching = state.isDispatchingTask,
                    onConfirmPlan = { confirmedPlan ->
                        viewModel.executeProposedPlan(confirmedPlan)
                        lessonForTelegramSheet = null
                    }
                )
            }
        }
    }

    // Unified Settings & Tasks Bottom Sheet (Accounts + Tasks + 24/7 Schedules)
    if (showSettingsSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            AgentSettingsAndTasksSheetContent(
                state = state,
                initialSection = settingsInitialSection,
                onConnectGitHub = { token, owner, repo, branch ->
                    viewModel.connectAndVerifyGitHub(token, owner, repo, branch)
                },
                onSelectRepo = { repo -> viewModel.selectGitHubRepository(repo) },
                onDeployWorkflow = { viewModel.deployWorkflowToGitHubRepo() },
                onRequestTelegramOtp = { apiId, apiHash, phone, twoFa ->
                    viewModel.requestTelegramOtpCode(apiId, apiHash, phone, twoFa)
                },
                onVerifyTelegramOtp = { otp, twoFa ->
                    viewModel.verifyTelegramOtpAndCreateSession(otp, twoFa)
                },
                onSaveTelegram = { apiId, apiHash, phone, sessionStr, botTok, chatId, displayName ->
                    viewModel.saveTelegramAccountConfig(apiId, apiHash, phone, sessionStr, botTok, chatId, displayName)
                },
                onRetryTask = { viewModel.retryOrResumeTask(it) },
                onCancelRun = { viewModel.cancelRunningWorkflow(it) },
                onDeleteTask = { viewModel.deleteTask(it) },
                onClearCompletedTasks = { viewModel.clearAllCompletedTasks() },
                onToggleSchedule = { sched, enabled -> viewModel.toggleSchedule(sched, enabled) },
                onDeleteSchedule = { viewModel.deleteSchedule(it) },
                onRunScheduleNow = { viewModel.triggerScheduleNow(it) },
                onCreateLifetimeCourseSync = { progId, progTitle, timeBn, cronExpr, startNow ->
                    viewModel.createCourseLifetimeSyncRoutine(
                        programId = progId,
                        programTitle = progTitle,
                        timeDisplayBn = timeBn,
                        cronExpression = cronExpr,
                        startImmediateFullUpload = startNow
                    )
                },
                onClose = { showSettingsSheet = false }
            )
        }
    }
}

// =============================================================================
// GEMINI-STYLE MULTI-SESSION NAVIGATION DRAWER
// =============================================================================

@Composable
private fun GeminiSessionDrawerContent(
    state: AiAgentUiState,
    onNewChat: () -> Unit,
    onSelectSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onOpenAccountsSheet: () -> Unit,
    onOpenTasksSheet: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }

    ModalDrawerSheet(
        modifier = Modifier.width(310.dp),
        drawerContainerColor = Color(0xFF0F172A),
        drawerContentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            // Brand Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF6366F1), Color(0xFFEC4899), Color(0xFF06B6D4))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "MaxBird Gemini",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp,
                        color = Color.White
                    )
                    Text(
                        text = "Gemini 3.8 Flash • চ্যাট হিস্ট্রি",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // New Chat Button (Gemini Style)
            Button(
                onClick = onNewChat,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("drawer_new_chat_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "নতুন চ্যাট শুরু করুন",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "সাম্প্রতিক সেশনসমূহ (${state.chatSessions.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = Color(0xFF94A3B8)
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Session List
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(state.chatSessions, key = { it.id }) { session: AiChatSession ->
                    val isSelected = session.id == state.activeSessionId
                    Surface(
                        color = if (isSelected) Color(0xFF1E293B) else Color.Transparent,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = if (isSelected) Color(0xFF6366F1) else Color.White.copy(alpha = 0.06f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onSelectSession(session.id) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = if (isSelected) Color(0xFF818CF8) else Color(0xFF64748B),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = session.title.ifBlank { "নতুন চ্যাট" },
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${session.messages.size} মেসেজ • ${dateFormat.format(Date(session.updatedAt))}",
                                    fontSize = 10.sp,
                                    color = Color(0xFF94A3B8),
                                    maxLines = 1
                                )
                            }
                            IconButton(
                                onClick = { onDeleteSession(session.id) },
                                modifier = Modifier
                                    .size(30.dp)
                                    .testTag("delete_session_${session.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "সেশন ডিলিট করুন",
                                    tint = Color(0xFFF87171),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 10.dp))

            // Quick Bottom Actions in Drawer
            Surface(
                color = Color(0xFF1E293B),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenTasksSheet() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "চলমান টাস্ক (${state.tasks.size}) ও ২৪/৭ রুটিন (${state.schedules.size})",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = Color(0xFF1E293B),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenAccountsSheet() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = Color(0xFFA78BFA), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "GitHub ও Telegram একাউন্ট সেটিংস",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = Color.White
                        )
                        Text(
                            text = "GitHub: ${if (state.githubConfig.isVerified) "যুক্ত ✅" else "বাকি"} • Telegram: ${if (state.telegramConfig.isConnected) "যুক্ত ✅" else "বাকি"}",
                            fontSize = 10.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }
        }
    }
}

// =============================================================================
// UNIFIED GEMINI TOP BAR WITH COURSE, 5+ QUARTERS & SUBJECT FILTERS
// =============================================================================

@Composable
private fun GeminiUnifiedTopBar(
    state: AiAgentUiState,
    onBack: () -> Unit,
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenTasks: () -> Unit,
    onSelectProgram: (String, String) -> Unit,
    onSelectQuarter: (String, String) -> Unit,
    onSelectSubject: (String, String) -> Unit
) {
    var showCourseDropdown by remember { mutableStateOf(false) }
    var showFiltersExpanded by rememberSaveable { mutableStateOf(true) }

    Surface(
        color = Color(0xFF0F172A),
        shadowElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            // Top Row: Back + Drawer Menu + Title + Gemini 3.8 Flash Badge + New Chat + Settings
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("ai_agent_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "ফিরে যান",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onOpenDrawer,
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("ai_agent_drawer_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "চ্যাট হিস্ট্রি মেনু",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "MaxBird AI",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = Color(0xFF312E81),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.border(1.dp, Color(0xFF818CF8), RoundedCornerShape(50))
                        ) {
                            Text(
                                text = "Gemini 3.8 Flash",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFC7D2FE),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    val inspect = state.activeInspection
                    Text(
                        text = if (state.isInspectingCourse) {
                            "কোর্সের সকল কোয়ার্টার স্ক্যান হচ্ছে..."
                        } else if (inspect != null) {
                            "${inspect.quarters.size.coerceAtLeast(1)}টি কোয়ার্টার • ${inspect.videoLessonsCount} ভিডিও • ${inspect.slideAttachmentsCount} স্লাইড"
                        } else {
                            state.selectedProgramTitle
                        },
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // New Chat Quick Button
                IconButton(
                    onClick = onNewChat,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("top_new_chat_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "নতুন চ্যাট",
                        tint = Color.White
                    )
                }

                // Tasks Badge Button
                IconButton(
                    onClick = onOpenTasks,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "টাস্ক ও রুটিন",
                            tint = Color(0xFF38BDF8)
                        )
                        if (state.tasks.isNotEmpty()) {
                            Badge(
                                containerColor = Color(0xFF10B981),
                                modifier = Modifier.align(Alignment.TopEnd)
                            ) {
                                Text(text = "${state.tasks.size}", fontSize = 8.sp, color = Color.White)
                            }
                        }
                    }
                }

                // Accounts & Setup Button
                IconButton(
                    onClick = onOpenAccounts,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("top_accounts_settings_button")
                ) {
                    val bothConnected = state.githubConfig.isVerified && state.telegramConfig.isConnected
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "একাউন্টস সেটিংস",
                        tint = if (bothConnected) Color(0xFF34D399) else Color(0xFFFBBF24)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Second Row: Course Selector + Connection Status Pills + Expand/Collapse Filters
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.45f), RoundedCornerShape(50))
                            .clickable { showCourseDropdown = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.School,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = state.selectedProgramTitle.ifBlank { "কোর্স নির্বাচন" },
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showCourseDropdown,
                        onDismissRequest = { showCourseDropdown = false }
                    ) {
                        state.enrolledPrograms.forEach { prog ->
                            val progTitle = prog.title_bn ?: prog.id
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = progTitle,
                                        fontSize = 13.sp
                                    )
                                },
                                onClick = {
                                    showCourseDropdown = false
                                    onSelectProgram(prog.id, progTitle)
                                }
                            )
                        }
                    }
                }

                StatusPillChip(
                    label = if (state.githubConfig.isVerified) "GitHub: ${state.githubConfig.repo.ifBlank { "যুক্ত" }}" else "GitHub কানেক্ট",
                    isConnected = state.githubConfig.isVerified,
                    onClick = onOpenAccounts
                )

                StatusPillChip(
                    label = when {
                        state.telegramConfig.authState == "SESSION_EXPIRED" -> "Telegram: পুনরায় কানেক্ট করুন"
                        state.telegramConfig.isConnected -> "Telegram: যুক্ত (${state.telegramConfig.dialogs.size} চ্যানেল)"
                        else -> "Telegram কানেক্ট"
                    },
                    isConnected = state.telegramConfig.isConnected,
                    onClick = onOpenAccounts
                )

                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.clickable { showFiltersExpanded = !showFiltersExpanded }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (showFiltersExpanded) "ফিল্টার লুকান" else "কোয়ার্টার ও বিষয়",
                            fontSize = 10.sp,
                            color = Color(0xFFCBD5E1)
                        )
                        Icon(
                            imageVector = if (showFiltersExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = Color(0xFFCBD5E1),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Expandable Multi-Quarter (কোয়ার্টার ১, ২, ৩, ৪, ৫) & Subject Filter Rows
            AnimatedVisibility(visible = showFiltersExpanded && state.activeInspection != null) {
                val inspection = state.activeInspection
                if (inspection != null) {
                    Column(modifier = Modifier.padding(top = 6.dp)) {
                        // Quarters Row (if course has multiple quarters/phases)
                        if (inspection.quarters.size > 1) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                QuarterChip(
                                    title = "সব কোয়ার্টার (${inspection.quarters.size})",
                                    selected = state.selectedPhaseId.isBlank(),
                                    onClick = { onSelectQuarter("", "সব কোয়ার্টার") }
                                )
                                inspection.quarters.forEach { q ->
                                    QuarterChip(
                                        title = "${q.phaseTitle} (${q.videosCount}V)",
                                        selected = state.selectedPhaseId == q.phaseId,
                                        onClick = { onSelectQuarter(q.phaseId, q.phaseTitle) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        // Subjects Row
                        if (inspection.subjectBreakdown.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                QuarterChip(
                                    title = "সব বিষয় (${inspection.totalLessonsCount})",
                                    selected = state.selectedSubjectCode.isBlank(),
                                    onClick = { onSelectSubject("", "") }
                                )
                                inspection.subjectBreakdown.forEach { sub ->
                                    QuarterChip(
                                        title = "${sub.subjectTitle} (${sub.videosCount}V/${sub.slidesCount}S)",
                                        selected = state.selectedSubjectCode == sub.subjectCode,
                                        onClick = { onSelectSubject(sub.subjectCode, sub.subjectTitle) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuarterChip(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) Color(0xFF4F46E5) else Color(0xFF1E293B),
        shape = RoundedCornerShape(50),
        modifier = Modifier
            .border(
                1.dp,
                if (selected) Color(0xFF818CF8) else Color.White.copy(alpha = 0.12f),
                RoundedCornerShape(50)
            )
            .clickable(onClick = onClick)
    ) {
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun StatusPillChip(
    label: String,
    isConnected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isConnected) Color(0xFF064E3B) else Color(0xFF3B2F1E)
    val borderColor = if (isConnected) Color(0xFF10B981) else Color(0xFFF59E0B)
    val dotColor = if (isConnected) Color(0xFF34D399) else Color(0xFFFBBF24)

    Surface(
        color = bgColor.copy(alpha = 0.78f),
        shape = RoundedCornerShape(50),
        modifier = Modifier
            .border(1.dp, borderColor.copy(alpha = 0.65f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// =============================================================================
// LIVE COMPACT TASK BANNER IN UNIFIED CHAT VIEW
// =============================================================================

@Composable
private fun LiveTaskCompactBanner(
    task: AiAgentTask,
    activeRunsCount: Int,
    onOpenTelegramLink: (String) -> Unit,
    onRetryTask: () -> Unit,
    onDeleteTask: () -> Unit,
    onOpenAllTasks: () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val isRunning = task.status.uppercase() in setOf("RUNNING", "QUEUED", "RESUMING") || activeRunsCount > 0
    val isCompleted = task.status.equals("COMPLETED", ignoreCase = true)
    val progress = if (task.totalItems > 0) {
        (task.completedItems.toFloat() / task.totalItems.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Surface(
        color = if (isCompleted) Color(0xFF064E3B) else Color(0xFF1E1B4B),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .border(
                1.dp,
                if (isCompleted) Color(0xFF10B981).copy(alpha = 0.5f) else Color(0xFF6366F1).copy(alpha = 0.5f),
                RoundedCornerShape(14.dp)
            )
            .clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isRunning && !isCompleted) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF38BDF8)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF34D399),
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${task.title} (${task.completedItems}/${task.totalItems})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${(progress * 100).toInt()}%",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = Color(0xFF38BDF8)
                )
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = onDeleteTask,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "টাস্ক ডিলিট করুন",
                        tint = Color(0xFFFCA5A5),
                        modifier = Modifier.size(15.dp)
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(50)),
                color = if (isCompleted) Color(0xFF10B981) else Color(0xFF38BDF8),
                trackColor = Color.White.copy(alpha = 0.15f)
            )

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    if (task.statusMessage.isNotBlank()) {
                        Text(
                            text = task.statusMessage,
                            fontSize = 11.sp,
                            color = Color(0xFFE2E8F0)
                        )
                    }
                    if (task.currentItemTitle.isNotBlank()) {
                        Text(
                            text = "চলমান: ${task.currentItemTitle}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF93C5FD)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (task.telegramInviteLink.isNotBlank()) {
                            Button(
                                onClick = { onOpenTelegramLink(task.telegramInviteLink) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("গ্রুপ/চ্যানেল ওপেন", fontSize = 11.sp)
                            }
                        }
                        OutlinedButton(
                            onClick = onRetryTask,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("রিস্টার্ট", fontSize = 11.sp, color = Color.White)
                        }
                        OutlinedButton(
                            onClick = onDeleteTask,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFF87171), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("মুছুন", fontSize = 11.sp, color = Color(0xFFF87171))
                        }
                        OutlinedButton(
                            onClick = onOpenAllTasks,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("সব লগ ও রুটিন", fontSize = 11.sp, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// GEMINI UNIFIED CHAT WORKSPACE (CODE BLOCKS + VIDEO CARDS + INTERACTIVE PLAN)
// =============================================================================

@Composable
private fun GeminiChatWorkspace(
    state: AiAgentUiState,
    onSendPrompt: (String) -> Unit,
    onExecutePlan: (ProposedAgentPlan) -> Unit,
    onPlayLessonVideo: (AgentPlayableLesson) -> Unit,
    onOpenLessonSlide: (AgentPlayableLesson) -> Unit,
    onSendLessonToTelegram: (AgentPlayableLesson) -> Unit,
    modifier: Modifier = Modifier
) {
    var inputText by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .navigationBarsPadding()
    ) {
        // Chat Messages List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(state.messages, key = { it.id }) { msg ->
                GeminiChatMessageBubble(
                    message = msg,
                    dialogs = state.telegramConfig.dialogs,
                    isDispatching = state.isDispatchingTask,
                    onExecutePlan = onExecutePlan,
                    onPlayLessonVideo = onPlayLessonVideo,
                    onOpenLessonSlide = onOpenLessonSlide,
                    onSendLessonToTelegram = onSendLessonToTelegram
                )
            }
        }

        // Quick Action Suggestion Chips
        val quickPrompts = listOf(
            "🎬 ডেটাবেজ বা ১ম ক্লাসের ভিডিও প্লে করে দাও",
            "📊 সব কোয়ার্টারের ভিডিও ও স্লাইড আপলোডের প্ল্যান দেখাও",
            "📡 আমার টেলিগ্রাম চ্যানেল ও গ্রুপগুলোর তালিকা দেখাও",
            "⏰ প্রতিদিন সকাল ৮টায় অটো-আপলোড রুটিন চালু করো"
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            quickPrompts.forEach { prompt ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                            RoundedCornerShape(50)
                        )
                        .clickable { onSendPrompt(prompt) }
                ) {
                    Text(
                        text = prompt,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Gemini Prompt Composer Bar
        Surface(
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            text = "Gemini 3.8 Flash-কে বলুন (যেমন: অমুক ভিডিও প্লে করো / অমুক গ্রুপে পাঠাও)...",
                            fontSize = 13.sp
                        )
                    },
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 4,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai_agent_prompt_input")
                )
                Spacer(modifier = Modifier.width(8.dp))
                val isBusy = state.brainState is AgentBrainState.Thinking
                IconButton(
                    onClick = {
                        if (inputText.isNotBlank() && !isBusy) {
                            val toSend = inputText
                            inputText = ""
                            onSendPrompt(toSend)
                        }
                    },
                    enabled = inputText.isNotBlank() && !isBusy,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (inputText.isNotBlank() && !isBusy) Color(0xFF4F46E5)
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .testTag("ai_agent_send_button")
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "পাঠান",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

// =============================================================================
// RICH MESSAGE BUBBLE WITH CODE BLOCKS, PLAYABLE LESSONS & CONFIRMATION PICKER
// =============================================================================

private sealed interface FormattedSegment {
    data class PlainText(val text: String) : FormattedSegment
    data class CodeBlock(val language: String, val code: String) : FormattedSegment
}

private fun parseMessageSegments(rawText: String): List<FormattedSegment> {
    val regex = Regex("```([a-zA-Z0-9_-]*)\\n?([\\s\\S]*?)```")
    val segments = mutableListOf<FormattedSegment>()
    var lastIndex = 0
    for (match in regex.findAll(rawText)) {
        if (match.range.first > lastIndex) {
            val before = rawText.substring(lastIndex, match.range.first).trim()
            if (before.isNotEmpty()) {
                segments.add(FormattedSegment.PlainText(before))
            }
        }
        val lang = match.groupValues[1].trim().ifBlank { "code" }
        val codeContent = match.groupValues[2].trim()
        if (codeContent.isNotEmpty()) {
            segments.add(FormattedSegment.CodeBlock(lang, codeContent))
        }
        lastIndex = match.range.last + 1
    }
    if (lastIndex < rawText.length) {
        val tail = rawText.substring(lastIndex).trim()
        if (tail.isNotEmpty()) {
            segments.add(FormattedSegment.PlainText(tail))
        }
    }
    return segments.ifEmpty { listOf(FormattedSegment.PlainText(rawText)) }
}

@Composable
private fun GeminiChatMessageBubble(
    message: AiAgentChatMessage,
    dialogs: List<TelegramDialogDestination>,
    isDispatching: Boolean,
    onExecutePlan: (ProposedAgentPlan) -> Unit,
    onPlayLessonVideo: (AgentPlayableLesson) -> Unit,
    onOpenLessonSlide: (AgentPlayableLesson) -> Unit,
    onSendLessonToTelegram: (AgentPlayableLesson) -> Unit
) {
    val isUser = message.role == "user"
    val context = LocalContext.current
    val segments = remember(message.text) { parseMessageSegments(message.text) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = when {
                isUser -> Color(0xFF4F46E5)
                message.isError -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surface
            },
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp
            ),
            tonalElevation = if (isUser) 0.dp else 3.dp,
            shadowElevation = 2.dp,
            modifier = Modifier.widthIn(max = 365.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                if (!isUser) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color(0xFF6366F1),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "MaxBird AI • Gemini 3.8 Flash",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = Color(0xFF6366F1)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                segments.forEach { seg ->
                    when (seg) {
                        is FormattedSegment.PlainText -> {
                            Text(
                                text = seg.text.replace("**", ""),
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                color = when {
                                    isUser -> Color.White
                                    message.isError -> MaterialTheme.colorScheme.onErrorContainer
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                        is FormattedSegment.CodeBlock -> {
                            Surface(
                                color = Color(0xFF0F172A),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF1E293B))
                                            .padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Code,
                                            contentDescription = null,
                                            tint = Color(0xFF38BDF8),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = seg.language.uppercase(),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF94A3B8),
                                            modifier = Modifier.weight(1f)
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.clickable {
                                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                cm.setPrimaryClip(ClipData.newPlainText("AI Code", seg.code))
                                                Toast.makeText(context, "কপি করা হয়েছে!", Toast.LENGTH_SHORT).show()
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "কপি",
                                                tint = Color(0xFFCBD5E1),
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = "কপি",
                                                fontSize = 10.sp,
                                                color = Color(0xFFCBD5E1)
                                            )
                                        }
                                    }
                                    Text(
                                        text = seg.code,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        lineHeight = 18.sp,
                                        color = Color(0xFFE2E8F0),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState())
                                            .padding(10.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                if (message.isStreaming) {
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(50))
                    )
                }
            }
        }

        // Playable Video & Slide Cards inside Chat Message
        if (message.playableLessons.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                modifier = Modifier.widthIn(max = 365.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                message.playableLessons.forEach { lesson ->
                    PlayableLessonChatCard(
                        lesson = lesson,
                        onPlayVideo = { onPlayLessonVideo(lesson) },
                        onOpenSlide = { onOpenLessonSlide(lesson) },
                        onSendToTelegram = { onSendLessonToTelegram(lesson) }
                    )
                }
            }
        }

        // Interactive Confirmation & Channel/Group/Topic Picker Card
        if (message.proposedPlan != null) {
            Spacer(modifier = Modifier.height(8.dp))
            InteractiveConfirmationPlanCard(
                plan = message.proposedPlan,
                dialogs = dialogs,
                isDispatching = isDispatching,
                onConfirmPlan = onExecutePlan
            )
        }
    }
}

@Composable
private fun PlayableLessonChatCard(
    lesson: AgentPlayableLesson,
    onPlayVideo: () -> Unit,
    onOpenSlide: () -> Unit,
    onSendToTelegram: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.45f), RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0284C7)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircleFilled,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (lesson.phaseTitle.isNotBlank()) "${lesson.phaseTitle} • ${lesson.subjectTitle}" else lesson.subjectTitle,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8)
                    )
                    Text(
                        text = lesson.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (lesson.chapterTitle.isNotBlank()) {
                        Text(
                            text = "অধ্যায়: ${lesson.chapterTitle}",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = onPlayVideo,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ভিডিও প্লে", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = onOpenSlide,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                ) {
                    Icon(Icons.Default.Description, contentDescription = null, tint = Color(0xFF93C5FD), modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("স্লাইড", fontSize = 11.sp, color = Color.White)
                }

                OutlinedButton(
                    onClick = onSendToTelegram,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("টেলিগ্রাম", fontSize = 11.sp, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun InteractiveConfirmationPlanCard(
    plan: ProposedAgentPlan,
    dialogs: List<TelegramDialogDestination>,
    isDispatching: Boolean,
    onConfirmPlan: (ProposedAgentPlan) -> Unit
) {
    var createNewMode by remember(plan) {
        mutableStateOf(plan.createNewChannel || dialogs.isEmpty())
    }
    var customChannelName by remember(plan) {
        mutableStateOf(plan.targetChannelTitle.ifBlank { plan.programTitle })
    }
    var useForumTopics by remember(plan) {
        mutableStateOf(plan.useForumTopics)
    }
    var customTopicName by remember(plan) {
        mutableStateOf(plan.targetTopicTitle.ifBlank { if (plan.useForumTopics) plan.subjectTitle else "" })
    }
    var enableLifetimeSync by remember(plan) {
        mutableStateOf(plan.enableLifetimeSync || plan.actionType == "CREATE_SCHEDULE")
    }
    var selectedScheduleTimeBn by remember(plan) {
        mutableStateOf(plan.scheduleTimeBn.ifBlank { "প্রতিদিন রাত ০১:০০ (1:00 AM)" })
    }
    var selectedCronExpr by remember(plan) {
        mutableStateOf(plan.cronExpression.ifBlank { "0 19 * * *" })
    }
    var selectedDialogId by remember(plan, dialogs) {
        val matched = dialogs.firstOrNull { d -> d.id == plan.targetChatId }
            ?: dialogs.firstOrNull()
        mutableStateOf(matched?.id.orEmpty())
    }
    val selectedDialog: TelegramDialogDestination? = dialogs.firstOrNull { d -> d.id == selectedDialogId }
    var selectedTopicId by remember(selectedDialogId) {
        mutableIntStateOf(plan.targetTopicId)
    }
    var selectedQuality by remember(plan) {
        mutableStateOf(plan.videoQuality.ifBlank { "720p" })
    }

    val scheduleTimePresets = listOf(
        Triple("রাত ১:০০ টা", "প্রতিদিন রাত ০১:০০ (1:00 AM)", "0 19 * * *"),
        Triple("রাত ২:০০ টা", "প্রতিদিন রাত ০২:০০ (2:00 AM)", "0 20 * * *"),
        Triple("রাত ১২:০০ টা", "প্রতিদিন রাত ১২:০০ (12:00 AM)", "0 18 * * *"),
        Triple("সকাল ৮:০০ টা", "প্রতিদিন সকাল ০৮:০০ (8:00 AM)", "0 2 * * *")
    )

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        modifier = Modifier
            .widthIn(max = 365.dp)
            .border(
                width = 1.5.dp,
                brush = Brush.horizontalGradient(listOf(Color(0xFF6366F1), Color(0xFF06B6D4))),
                shape = RoundedCornerShape(18.dp)
            )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "টেলিগ্রাম গন্তব্য ও অনুমতি নিশ্চিতকরণ",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    color = Color(0xFF38BDF8)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${plan.taskTitle} • 🎬 ${plan.estimatedVideos} ভিডিও • 📄 ${plan.estimatedSlides} স্লাইড",
                fontSize = 12.sp,
                color = Color(0xFFE2E8F0)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Mode Toggle: Existing Channel/Group vs Create New Channel/Group
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !createNewMode && dialogs.isNotEmpty(),
                    onClick = { if (dialogs.isNotEmpty()) createNewMode = false },
                    enabled = dialogs.isNotEmpty(),
                    label = {
                        Text(
                            text = "আমার চ্যানেল/গ্রুপ (${dialogs.size})",
                            fontSize = 11.sp
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF0284C7),
                        selectedLabelColor = Color.White
                    )
                )
                FilterChip(
                    selected = createNewMode,
                    onClick = { createNewMode = true },
                    label = {
                        Text(
                            text = "➕ নতুন গ্রুপ/চ্যানেল খুলুন",
                            fontSize = 11.sp
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF4F46E5),
                        selectedLabelColor = Color.White
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (!createNewMode && dialogs.isNotEmpty()) {
                Text(
                    text = "কোন গ্রুপ বা চ্যানেলে আপলোড করবেন সিলেক্ট করুন:",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8)
                )
                Spacer(modifier = Modifier.height(6.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 150.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    dialogs.forEach { dialog: TelegramDialogDestination ->
                        val isChosen = dialog.id == selectedDialogId
                        Surface(
                            color = if (isChosen) Color(0xFF1E3A8A) else Color(0xFF1E293B),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    1.dp,
                                    if (isChosen) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.08f),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    selectedDialogId = dialog.id
                                    selectedTopicId = 0
                                    if (dialog.isForum) useForumTopics = true
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (dialog.isForum) Icons.Default.Forum else Icons.AutoMirrored.Filled.Send,
                                    contentDescription = null,
                                    tint = if (isChosen) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = dialog.title,
                                    fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (dialog.topics.isNotEmpty()) {
                                    Text(
                                        text = "${dialog.topics.size} টপিক",
                                        fontSize = 10.sp,
                                        color = Color(0xFF38BDF8)
                                    )
                                }
                            }
                        }
                    }
                }

                // Topic Selector if selected group has Forum Topics
                if (selectedDialog != null && selectedDialog.topics.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "গ্রুপের টপিক (Topic) সিলেক্ট করুন:",
                        fontSize = 11.sp,
                        color = Color(0xFF38BDF8)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        QuarterChip(
                            title = "সাবজেক্ট অনুযায়ী অটো টপিক",
                            selected = selectedTopicId == 0,
                            onClick = { selectedTopicId = 0 }
                        )
                        selectedDialog.topics.forEach { topic ->
                            QuarterChip(
                                title = topic.title,
                                selected = selectedTopicId == topic.id,
                                onClick = { selectedTopicId = topic.id }
                            )
                        }
                    }
                }
            } else {
                OutlinedTextField(
                    value = customChannelName,
                    onValueChange = { customChannelName = it },
                    label = {
                        Text(
                            if (useForumTopics) "নতুন পাবলিক ফোরাম গ্রুপের নাম (কোর্সের নাম)" else "নতুন টেলিগ্রাম চ্যানেলের নাম দিন",
                            fontSize = 11.sp
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuarterChip(
                        title = "🗂️ পাবলিক ফোরাম গ্রুপ (টপিকসহ)",
                        selected = useForumTopics,
                        onClick = { useForumTopics = true }
                    )
                    QuarterChip(
                        title = "📢 সাধারণ চ্যানেল",
                        selected = !useForumTopics,
                        onClick = { useForumTopics = false }
                    )
                }

                if (useForumTopics) {
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = customTopicName,
                        onValueChange = { customTopicName = it },
                        label = {
                            Text(
                                "টপিকের নাম (খালি রাখলে প্রতিটি বিষয়ের নামে আলাদা টপিক তৈরি হবে)",
                                fontSize = 10.sp
                            )
                        },
                        placeholder = {
                            Text("যেমন: বাংলা ১ম পত্র (অথবা সব বিষয়ের জন্য খালি রাখুন)", fontSize = 11.sp)
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Video Resolution Selector
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "কোয়ালিটি:",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8)
                )
                listOf("480p", "720p", "1080p").forEach { q ->
                    QuarterChip(
                        title = q,
                        selected = selectedQuality == q,
                        onClick = { selectedQuality = q }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 24/7 Lifetime Auto-Sync Toggle & Custom Time Picker (যেমন: রাত ১:০০ টা)
            Surface(
                color = Color(0xFF1E293B),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        if (enableLifetimeSync) Color(0xFF10B981).copy(alpha = 0.5f) else Color.White.copy(alpha = 0.08f),
                        RoundedCornerShape(12.dp)
                    )
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "🔄 ২৪/৭ লাইফটাইম অটো-সিঙ্ক রুটিন",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Color.White
                            )
                            Text(
                                text = "প্রতিদিন নির্ধারিত সময়ে নতুন ক্লাস চেক করে সাবজেক্ট টপিক ওয়াইজ আপলোড করবে",
                                fontSize = 10.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                        Switch(
                            checked = enableLifetimeSync,
                            onCheckedChange = { enableLifetimeSync = it }
                        )
                    }

                    if (enableLifetimeSync) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            scheduleTimePresets.forEach { (chipLabel, fullBn, cron) ->
                                QuarterChip(
                                    title = "⏰ $chipLabel",
                                    selected = selectedCronExpr == cron,
                                    onClick = {
                                        selectedScheduleTimeBn = fullBn
                                        selectedCronExpr = cron
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val chosenTopicTitle = selectedDialog?.topics?.firstOrNull { t -> t.id == selectedTopicId }?.title.orEmpty()

            Button(
                onClick = {
                    val updatedPlan = plan.copy(
                        actionType = if (plan.actionType == "CREATE_SCHEDULE" && !createNewMode) "CREATE_SCHEDULE" else "START_UPLOAD_TASK",
                        createNewChannel = createNewMode,
                        useForumTopics = useForumTopics || selectedDialog?.isForum == true,
                        targetChatId = if (createNewMode) "" else selectedDialog?.id.orEmpty(),
                        targetChannelTitle = if (createNewMode) {
                            customChannelName.trim().ifBlank { plan.programTitle }
                        } else {
                            selectedDialog?.title ?: plan.targetChannelTitle
                        },
                        targetTopicId = if (createNewMode) 0 else selectedTopicId,
                        targetTopicTitle = if (createNewMode) {
                            if (useForumTopics) customTopicName.trim() else ""
                        } else {
                            chosenTopicTitle.ifBlank { customTopicName.trim() }
                        },
                        videoQuality = selectedQuality,
                        enableLifetimeSync = enableLifetimeSync,
                        scheduleTimeBn = selectedScheduleTimeBn,
                        cronExpression = selectedCronExpr
                    )
                    onConfirmPlan(updatedPlan)
                },
                enabled = !isDispatching,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("execute_agent_plan_button")
            ) {
                if (isDispatching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = "✅ হ্যাঁ, অনুমতি দিলাম — আপলোড শুরু করুন",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color.White
                )
            }
        }
    }
}

// =============================================================================
// UNIFIED MODAL SHEET FOR ACCOUNTS (GITHUB + TELEGRAM) & TASKS / 24/7 ROUTINES
// =============================================================================

@Composable
private fun AgentSettingsAndTasksSheetContent(
    state: AiAgentUiState,
    initialSection: Int,
    onConnectGitHub: (String, String, String, String) -> Unit,
    onSelectRepo: (GitHubRepoSummary) -> Unit,
    onDeployWorkflow: () -> Unit,
    onRequestTelegramOtp: (String, String, String, String) -> Unit,
    onVerifyTelegramOtp: (String, String) -> Unit,
    onSaveTelegram: (String, String, String, String, String, String, String) -> Unit,
    onRetryTask: (AiAgentTask) -> Unit,
    onCancelRun: (Long) -> Unit,
    onDeleteTask: (String) -> Unit,
    onClearCompletedTasks: () -> Unit,
    onToggleSchedule: (AiAgentSchedule, Boolean) -> Unit,
    onDeleteSchedule: (String) -> Unit,
    onRunScheduleNow: (AiAgentSchedule) -> Unit,
    onCreateLifetimeCourseSync: (String, String, String, String, Boolean) -> Unit,
    onClose: () -> Unit
) {
    var selectedSection by rememberSaveable { mutableIntStateOf(initialSection) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.9f)
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "⚙️ MaxBird কন্ট্রোল সেন্টার",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 17.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "বন্ধ করুন")
            }
        }

        // Section Switcher inside Sheet
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { selectedSection = 0 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedSection == 0) Color(0xFF4F46E5) else Color(0xFF1E293B)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "🔌 GitHub ও Telegram",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color.White
                )
            }
            Button(
                onClick = { selectedSection = 1 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedSection == 1) Color(0xFF4F46E5) else Color(0xFF1E293B)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "⚡ টাস্ক (${state.tasks.size}) ও রুটিন (${state.schedules.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color.White
                )
            }
        }

        if (selectedSection == 0) {
            AccountsSetupSection(
                state = state,
                onConnectGitHub = onConnectGitHub,
                onSelectRepo = onSelectRepo,
                onDeployWorkflow = onDeployWorkflow,
                onRequestTelegramOtp = onRequestTelegramOtp,
                onVerifyTelegramOtp = onVerifyTelegramOtp,
                onSaveTelegram = onSaveTelegram
            )
        } else {
            TasksAndRoutinesSection(
                state = state,
                onRetryTask = onRetryTask,
                onCancelRun = onCancelRun,
                onDeleteTask = onDeleteTask,
                onClearCompletedTasks = onClearCompletedTasks,
                onToggleSchedule = onToggleSchedule,
                onDeleteSchedule = onDeleteSchedule,
                onRunScheduleNow = onRunScheduleNow,
                onCreateLifetimeCourseSync = onCreateLifetimeCourseSync,
                onOpenUrl = { url ->
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (_: Exception) {
                    }
                }
            )
        }
    }
}

@Composable
private fun AccountsSetupSection(
    state: AiAgentUiState,
    onConnectGitHub: (String, String, String, String) -> Unit,
    onSelectRepo: (GitHubRepoSummary) -> Unit,
    onDeployWorkflow: () -> Unit,
    onRequestTelegramOtp: (String, String, String, String) -> Unit,
    onVerifyTelegramOtp: (String, String) -> Unit,
    onSaveTelegram: (String, String, String, String, String, String, String) -> Unit
) {
    var ghToken by remember(state.githubConfig.personalAccessToken) {
        mutableStateOf(state.githubConfig.personalAccessToken)
    }
    var ghOwner by remember(state.githubConfig.owner) {
        mutableStateOf(state.githubConfig.owner)
    }
    var ghRepo by remember(state.githubConfig.repo) {
        mutableStateOf(state.githubConfig.repo)
    }
    var showGhToken by remember { mutableStateOf(false) }

    val tgApiId = state.telegramConfig.apiId.ifBlank { DEFAULT_TELEGRAM_API_ID }
    val tgApiHash = state.telegramConfig.apiHash.ifBlank { DEFAULT_TELEGRAM_API_HASH }
    var tgPhone by remember(state.telegramConfig.phone) {
        mutableStateOf(state.telegramConfig.phone.ifBlank { "+880" })
    }
    var tgOtpCode by remember { mutableStateOf("") }
    var tgTwoFaPassword by remember { mutableStateOf("") }
    var tgSessionString by remember(state.telegramConfig.sessionString) {
        mutableStateOf(state.telegramConfig.sessionString)
    }
    var tgChatId by remember(state.telegramConfig.defaultChatId) {
        mutableStateOf(state.telegramConfig.defaultChatId)
    }
    var showTgSecrets by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Telegram Setup Card
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "১. Telegram Account কানেক্ট (সিঙ্গেল-আইপি লক সুরক্ষিত)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = when {
                            state.telegramConfig.authState == "SESSION_EXPIRED" -> "⚠️ আগের সেশনটি বাতিল হয়েছে — নিচে শুধু ১ বার ওটিপি দিয়ে পুনরায় কানেক্ট করুন"
                            state.telegramConfig.isConnected -> "✅ কানেক্টেড: ${state.telegramConfig.accountDisplayName} (${state.telegramConfig.dialogs.size}টি চ্যানেল/গ্রুপ সিঙ্কড)"
                            else -> "আপনার ফোন নাম্বার দিয়ে ওটিপি পাঠান"
                        },
                        fontSize = 12.sp,
                        color = if (state.telegramConfig.isConnected) Color(0xFF10B981) else Color(0xFFF59E0B)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = tgPhone,
                        onValueChange = { tgPhone = it },
                        label = { Text("টেলিগ্রাম ফোন নাম্বার (+8801XXXXXXXXX)", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { onRequestTelegramOtp(tgApiId, tgApiHash, tgPhone, tgTwoFaPassword) },
                        enabled = !state.isProcessingTelegramAuth && tgPhone.length >= 8,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (state.isProcessingTelegramAuth && state.telegramConfig.authState == "SENDING_CODE") {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = "📲 ধাপ ১: টেলিগ্রামে OTP কোড পাঠান",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = tgOtpCode,
                            onValueChange = { tgOtpCode = it },
                            label = { Text("৫ সংখ্যার OTP কোড", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = tgTwoFaPassword,
                            onValueChange = { tgTwoFaPassword = it },
                            label = { Text("2FA পাসওয়ার্ড (থাকলে)", fontSize = 11.sp) },
                            singleLine = true,
                            visualTransformation = if (showTgSecrets) VisualTransformation.None else PasswordVisualTransformation(),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { onVerifyTelegramOtp(tgOtpCode, tgTwoFaPassword) },
                        enabled = !state.isProcessingTelegramAuth && tgOtpCode.trim().length >= 4,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "🔐 ধাপ ২: ওটিপি ভেরিফাই ও কানেক্ট করুন",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = tgSessionString,
                        onValueChange = { tgSessionString = it },
                        label = { Text("Telethon STRING_SESSION (অটো তৈরি হবে)", fontSize = 11.sp) },
                        visualTransformation = if (showTgSecrets) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showTgSecrets = !showTgSecrets }) {
                                Icon(
                                    imageVector = if (showTgSecrets) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            }
                        },
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            onSaveTelegram(
                                tgApiId,
                                tgApiHash,
                                tgPhone,
                                tgSessionString,
                                state.telegramConfig.botToken,
                                tgChatId,
                                state.telegramConfig.accountDisplayName
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("টেলিগ্রাম কনফিগারেশন সেভ করুন", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 2. GitHub Setup Card
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "২. GitHub Account ও ২৪/৭ ক্লাউড ওয়ার্কার",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = if (state.githubConfig.isVerified) {
                            "✅ কানেক্টেড: @${state.githubConfig.connectedUsername} (${state.githubConfig.owner}/${state.githubConfig.repo})"
                        } else {
                            "GitHub Personal Access Token দিয়ে কানেক্ট করুন"
                        },
                        fontSize = 12.sp,
                        color = if (state.githubConfig.isVerified) Color(0xFF10B981) else Color(0xFFF59E0B)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = ghToken,
                        onValueChange = { ghToken = it },
                        label = { Text("GitHub Token (ghp_...)", fontSize = 12.sp) },
                        singleLine = true,
                        visualTransformation = if (showGhToken) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showGhToken = !showGhToken }) {
                                Icon(
                                    imageVector = if (showGhToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = ghOwner,
                            onValueChange = { ghOwner = it },
                            label = { Text("Owner", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = ghRepo,
                            onValueChange = { ghRepo = it },
                            label = { Text("Repository", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (state.githubRepos.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            state.githubRepos.take(10).forEach { repo ->
                                FilterChip(
                                    selected = ghRepo.equals(repo.name, ignoreCase = true),
                                    onClick = {
                                        ghOwner = repo.owner
                                        ghRepo = repo.name
                                        onSelectRepo(repo)
                                    },
                                    label = { Text(repo.name, fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onConnectGitHub(ghToken, ghOwner, ghRepo, "main") },
                            enabled = !state.isVerifyingGitHub,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("GitHub সেভ ও সেটআপ", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        if (state.githubConfig.isVerified) {
                            OutlinedButton(
                                onClick = onDeployWorkflow,
                                enabled = !state.isDeployingWorkflow,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("স্ক্রিপ্ট আপডেট", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TasksAndRoutinesSection(
    state: AiAgentUiState,
    onRetryTask: (AiAgentTask) -> Unit,
    onCancelRun: (Long) -> Unit,
    onDeleteTask: (String) -> Unit,
    onClearCompletedTasks: () -> Unit,
    onToggleSchedule: (AiAgentSchedule, Boolean) -> Unit,
    onDeleteSchedule: (String) -> Unit,
    onRunScheduleNow: (AiAgentSchedule) -> Unit,
    onCreateLifetimeCourseSync: (String, String, String, String, Boolean) -> Unit,
    onOpenUrl: (String) -> Unit
) {
    var selectedRoutineProgId by remember(state.selectedProgramId) {
        mutableStateOf(state.selectedProgramId)
    }
    var selectedRoutineProgTitle by remember(state.selectedProgramTitle) {
        mutableStateOf(state.selectedProgramTitle)
    }
    var selectedTimeBn by remember { mutableStateOf("প্রতিদিন রাত ০১:০০ (1:00 AM)") }
    var selectedCron by remember { mutableStateOf("0 19 * * *") }

    val timeOptions = listOf(
        Triple("রাত ১:০০ টা", "প্রতিদিন রাত ০১:০০ (1:00 AM)", "0 19 * * *"),
        Triple("রাত ২:০০ টা", "প্রতিদিন রাত ০২:০০ (2:00 AM)", "0 20 * * *"),
        Triple("রাত ১২:০০ টা", "প্রতিদিন রাত ১২:০০ (12:00 AM)", "0 18 * * *"),
        Triple("সকাল ৮:০০ টা", "প্রতিদিন সকাল ০৮:০০ (8:00 AM)", "0 2 * * *")
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 0. Quick 1-Click Full-Course Subject-Topic-Wise Upload + 24/7 Lifetime Routine Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF10B981).copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "🚀 সম্পূর্ণ কোর্স আপলোড ও ২৪/৭ লাইফটাইম অটো-সিঙ্ক",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        color = Color(0xFF34D399)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "যেকোনো কোর্স সিলেক্ট করে সময় নির্ধারণ করুন। এটি ১ম থেকে শেষ কোয়ার্টার পর্যন্ত সব ক্লাস সাবজেক্ট টপিক ওয়াইজ সাজাবে এবং প্রতিদিন নির্ধারিত সময়ে নতুন ক্লাস অটো আপলোড করবে (সিলেবাস পরিবর্তন বা লগআউট করলেও সচল থাকবে)।",
                        fontSize = 11.sp,
                        color = Color(0xFFCBD5E1)
                    )

                    if (state.enrolledPrograms.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            state.enrolledPrograms.forEach { prog ->
                                val pTitle = prog.title_bn ?: prog.id
                                QuarterChip(
                                    title = pTitle,
                                    selected = selectedRoutineProgId == prog.id,
                                    onClick = {
                                        selectedRoutineProgId = prog.id
                                        selectedRoutineProgTitle = pTitle
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "প্রতিদিন চেক করার সময় নির্বাচন করুন:",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        timeOptions.forEach { (shortLabel, fullBn, cron) ->
                            QuarterChip(
                                title = "⏰ $shortLabel",
                                selected = selectedCron == cron,
                                onClick = {
                                    selectedTimeBn = fullBn
                                    selectedCron = cron
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                onCreateLifetimeCourseSync(
                                    selectedRoutineProgId,
                                    selectedRoutineProgTitle,
                                    selectedTimeBn,
                                    selectedCron,
                                    true
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "🚀 সব কোয়ার্টার আপলোড + রুটিন চালু",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Color.White
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                onCreateLifetimeCourseSync(
                                    selectedRoutineProgId,
                                    selectedRoutineProgTitle,
                                    selectedTimeBn,
                                    selectedCron,
                                    false
                                )
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "শুধু রুটিন সেভ",
                                fontSize = 11.sp,
                                color = Color(0xFF38BDF8)
                            )
                        }
                    }
                }
            }
        }

        // 1. 24/7 Schedules List (with ON/OFF toggle, Run Now, and Delete)
        if (state.schedules.isNotEmpty()) {
            item {
                Text(
                    text = "⏰ সক্রিয় ২৪/৭ অটো রুটিনসমূহ (${state.schedules.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            items(state.schedules, key = { it.id }) { sched ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = sched.title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(
                                    text = "⏰ ${sched.timeDisplayBn} • ${if (sched.isEnabled) "চালু আছে ✅" else "বন্ধ আছে ⏸️"}",
                                    fontSize = 11.sp,
                                    color = if (sched.isEnabled) Color(0xFF10B981) else Color(0xFF94A3B8)
                                )
                                if (sched.targetChannelTitle.isNotBlank()) {
                                    Text(
                                        text = "🎯 গ্রুপ/চ্যানেল: ${sched.targetChannelTitle} (সাবজেক্ট টপিক ওয়াইজ)",
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            Switch(checked = sched.isEnabled, onCheckedChange = { onToggleSchedule(sched, it) })
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onRunScheduleNow(sched) },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("▶️ এখনই সিঙ্ক করুন", fontSize = 11.sp)
                            }
                            if (sched.telegramInviteLink.isNotBlank()) {
                                OutlinedButton(
                                    onClick = { onOpenUrl(sched.telegramInviteLink) },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("গ্রুপ ওপেন", fontSize = 11.sp)
                                }
                            }
                            OutlinedButton(
                                onClick = { onDeleteSchedule(sched.id) },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "ডিলিট", tint = Color(0xFFF87171), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("রুটিন মুছুন", fontSize = 11.sp, color = Color(0xFFF87171))
                            }
                        }
                    }
                }
            }
        }

        if (state.workflowRuns.isNotEmpty()) {
            item {
                Text(
                    text = "GitHub Actions Cloud Runs",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            items(state.workflowRuns.take(5), key = { it.id }) { run ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "${run.name} (#${run.id})", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(
                                text = "স্ট্যাটাস: ${run.status.uppercase()} • ${run.conclusion ?: "চলমান"}",
                                fontSize = 11.sp
                            )
                        }
                        if (run.status == "in_progress" || run.status == "queued") {
                            IconButton(onClick = { onCancelRun(run.id) }) {
                                Icon(Icons.Default.StopCircle, contentDescription = "Cancel", tint = Color(0xFFEF4444))
                            }
                        }
                        if (run.htmlUrl.isNotBlank()) {
                            IconButton(onClick = { onOpenUrl(run.htmlUrl) }) {
                                Icon(Icons.Default.OpenInNew, contentDescription = "Open")
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ব্যাকগ্রাউন্ড টাস্কসমূহ (${state.tasks.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                if (state.tasks.any { it.status.uppercase() in setOf("COMPLETED", "FAILED", "CANCELLED") }) {
                    OutlinedButton(
                        onClick = onClearCompletedTasks,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFF87171), modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("সম্পন্নগুলো মুছুন", fontSize = 11.sp, color = Color(0xFFF87171))
                    }
                }
            }
        }

        items(state.tasks, key = { it.id }) { task ->
            Card(
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = task.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { onDeleteTask(task.id) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "টাস্ক মুছুন",
                                tint = Color(0xFFF87171),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Text(text = task.statusMessage, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (task.telegramInviteLink.isNotBlank()) {
                            Button(onClick = { onOpenUrl(task.telegramInviteLink) }, shape = RoundedCornerShape(8.dp)) {
                                Text("টেলিগ্রাম গ্রুপ/চ্যানেল", fontSize = 11.sp)
                            }
                        }
                        OutlinedButton(onClick = { onRetryTask(task) }, shape = RoundedCornerShape(8.dp)) {
                            Text("রিস্টার্ট", fontSize = 11.sp)
                        }
                        OutlinedButton(onClick = { onDeleteTask(task.id) }, shape = RoundedCornerShape(8.dp)) {
                            Text("ডিলিট", fontSize = 11.sp, color = Color(0xFFF87171))
                        }
                    }
                }
            }
        }
    }
}
