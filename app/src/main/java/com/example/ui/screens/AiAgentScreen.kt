package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.agent.*
import com.example.security.DeviceActivationRepository
import com.example.ui.theme.isAppInDarkTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAgentScreen(
    viewModel: AiAgentViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val isDark = isAppInDarkTheme()
    val uiState by viewModel.uiState.collectAsState()

    var canViewProfile by remember {
        mutableStateOf(DeviceActivationRepository.isProfileViewPermissionCached(context))
    }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var promptInput by rememberSaveable { mutableStateOf("") }
    var showCoursePickerSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val verified = DeviceActivationRepository.checkProfileViewPermission(context, forceRefresh = true)
        canViewProfile = verified
    }

    val pageBg = if (isDark) Color(0xFF060B16) else Color(0xFFF8FAFC)
    val cardBg = if (isDark) Color(0xFF111A2E) else Color.White
    val primaryText = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val secondaryText = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    // Strict Permission Gate: Only users with Profile View Permission can view or use the AI Agent
    if (!canViewProfile) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("অ্যাক্সেস সংরক্ষিত", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ফিরে যান")
                        }
                    }
                )
            },
            containerColor = pageBg
        ) { pad ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pad)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "শুধুমাত্র অনুমোদিত প্রোফাইল ভিউ পারমিশন ব্যবহারকারীদের জন্য",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = primaryText
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "এই AI Autonomous Agent অপশনটি শুধুমাত্র যাদের প্রোফাইল ভিউ পারমিশন (can_view_profiles) চালু আছে তাদের জন্য সংরক্ষিত।",
                            fontSize = 13.sp,
                            color = secondaryText
                        )
                    }
                }
            }
        }
        return
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF0F172A), Color(0xFF1E1B4B), Color(0xFF0F172A))
                        )
                    )
                    .statusBarsPadding()
            ) {
                // Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("ai_agent_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "ফিরে যান",
                            tint = Color.White
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF8B5CF6), Color(0xFF06B6D4))
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "MaxBird AI Agent",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color(0xFF10B981).copy(alpha = 0.22f),
                                border = BorderStroke(1.dp, Color(0xFF34D399).copy(alpha = 0.6f))
                            ) {
                                Text(
                                    text = "24/7 Cloud",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF6EE7B7),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "GitHub Actions • Telegram Userbot • Gemini 3.8",
                            fontSize = 11.sp,
                            color = Color(0xFFCBD5E1),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = { viewModel.refreshTasksAndSchedules() },
                        modifier = Modifier.testTag("ai_agent_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "রিফ্রেশ",
                            tint = Color(0xFF38BDF8)
                        )
                    }
                }

                // Connected Extensions Status Strip & Active Course Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Active Course Chip
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color(0xFF38BDF8).copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { showCoursePickerSheet = true }
                            .testTag("ai_agent_course_selector_chip")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.School,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = uiState.selectedProgramTitle.ifBlank { "কোর্স সিলেক্ট করুন" },
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1
                            )
                            Icon(
                                imageVector = Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    // GitHub Status Chip
                    ExtensionStatusPill(
                        label = if (uiState.githubConfig.isVerified) {
                            "GitHub: ${uiState.githubConfig.repo.ifBlank { uiState.githubConfig.connectedUsername }}"
                        } else {
                            "GitHub কানেক্ট করুন"
                        },
                        isConnected = uiState.githubConfig.isVerified,
                        onClick = { selectedTab = 3 }
                    )

                    // Telegram Status Chip
                    ExtensionStatusPill(
                        label = if (uiState.telegramConfig.isConnected) {
                            "Telegram: কানেক্টেড"
                        } else {
                            "Telegram কানেক্ট করুন"
                        },
                        isConnected = uiState.telegramConfig.isConnected,
                        onClick = { selectedTab = 3 }
                    )
                }

                // 4 Navigation Tabs
                val tabs = listOf(
                    "💬 AI চ্যাট" to Icons.Default.Chat,
                    "⚡ টাস্ক (${uiState.tasks.size})" to Icons.Default.Bolt,
                    "⏰ ২৪/৭ রুটিন (${uiState.schedules.count { it.isEnabled }})" to Icons.Default.Schedule,
                    "🔌 একাউন্টস" to Icons.Default.Extension
                )
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = Color.White,
                    edgePadding = 12.dp,
                    divider = {}
                ) {
                    tabs.forEachIndexed { index, (title, icon) ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            modifier = Modifier.testTag("ai_agent_tab_$index"),
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = title,
                                        fontSize = 12.5.sp,
                                        fontWeight = if (selectedTab == index) FontWeight.ExtraBold else FontWeight.Medium
                                    )
                                }
                            }
                        )
                    }
                }
            }
        },
        containerColor = pageBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Optional status banner
            AnimatedVisibility(visible = !uiState.statusBannerMessage.isNullOrBlank()) {
                uiState.statusBannerMessage?.let { msg ->
                    Surface(
                        color = Color(0xFF1E293B),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = msg,
                                fontSize = 12.sp,
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.clearBannerMessage() },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "বন্ধ করুন",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            when (selectedTab) {
                0 -> AiAgentChatTab(
                    uiState = uiState,
                    promptInput = promptInput,
                    onPromptChange = { promptInput = it },
                    onSendPrompt = {
                        val toSend = promptInput
                        promptInput = ""
                        viewModel.sendPromptToAgent(toSend)
                    },
                    onQuickPrompt = { quick ->
                        viewModel.sendPromptToAgent(quick)
                    },
                    onSubjectSelect = { code, title ->
                        viewModel.selectSubjectFilter(code, title)
                    },
                    onExecutePlan = { plan ->
                        viewModel.executeProposedPlan(plan)
                        selectedTab = 1
                    },
                    isDark = isDark
                )

                1 -> AiAgentTasksTab(
                    uiState = uiState,
                    onRetryOrResume = { viewModel.retryOrResumeTask(it) },
                    onCancelRun = { viewModel.cancelRunningWorkflow(it) },
                    onOpenUrl = { url ->
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }
                    },
                    isDark = isDark
                )

                2 -> AiAgentSchedulesTab(
                    uiState = uiState,
                    onToggleSchedule = { sched, enabled -> viewModel.toggleSchedule(sched, enabled) },
                    onDeleteSchedule = { id -> viewModel.deleteSchedule(id) },
                    onCreateQuickDailySchedule = {
                        val plan = ProposedAgentPlan(
                            actionType = "CREATE_SCHEDULE",
                            taskTitle = "${uiState.selectedProgramTitle} — দৈনিক অটো আপলোড",
                            programId = uiState.selectedProgramId,
                            programTitle = uiState.selectedProgramTitle,
                            subjectCode = uiState.selectedSubjectCode,
                            subjectTitle = uiState.selectedSubjectTitle,
                            targetChannelTitle = uiState.selectedProgramTitle,
                            targetChatId = uiState.telegramConfig.defaultChatId,
                            scheduleTimeBn = "প্রতিদিন সকাল ০৮:০০",
                            cronExpression = "0 2 * * *"
                        )
                        viewModel.executeProposedPlan(plan)
                    },
                    isDark = isDark
                )

                3 -> AiAgentExtensionsTab(
                    uiState = uiState,
                    onSaveGitHub = { token, owner, repo, branch ->
                        viewModel.connectAndVerifyGitHub(token, owner, repo, branch)
                    },
                    onSelectRepo = { repo ->
                        viewModel.selectGitHubRepository(repo)
                    },
                    onDeployGitHubWorkflow = {
                        viewModel.deployWorkflowToGitHubRepo()
                    },
                    onRequestTelegramOtp = { apiId, apiHash, phone ->
                        viewModel.requestTelegramOtpCode(apiId, apiHash, phone)
                    },
                    onVerifyTelegramOtp = { otpCode, twoFaPassword ->
                        viewModel.verifyTelegramOtpAndCreateSession(otpCode, twoFaPassword)
                    },
                    onSaveTelegram = { apiId, apiHash, phone, sessionStr, botToken, chatId, displayName ->
                        viewModel.saveTelegramAccountConfig(
                            apiId = apiId,
                            apiHash = apiHash,
                            phone = phone,
                            sessionString = sessionStr,
                            botToken = botToken,
                            defaultChatId = chatId,
                            accountDisplayName = displayName
                        )
                    },
                    isDark = isDark
                )
            }
        }
    }

    // Course Selector Bottom Sheet
    if (showCoursePickerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCoursePickerSheet = false },
            containerColor = cardBg
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "AI এজেন্টের জন্য কোর্স নির্বাচন করুন",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = primaryText
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (uiState.enrolledPrograms.isEmpty()) {
                    Text(
                        text = "বর্তমানে সক্রিয় কোর্স: ${uiState.selectedProgramTitle}",
                        fontSize = 14.sp,
                        color = secondaryText,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    uiState.enrolledPrograms.forEach { prog ->
                        val isSelected = prog.id == uiState.selectedProgramId
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) Color(0xFF8B5CF6).copy(alpha = 0.15f) else Color.Transparent,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) Color(0xFF8B5CF6) else secondaryText.copy(alpha = 0.25f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    viewModel.selectProgramForAgent(prog.id, prog.title_bn ?: "কোর্স")
                                    showCoursePickerSheet = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.School,
                                    contentDescription = null,
                                    tint = if (isSelected) Color(0xFF8B5CF6) else secondaryText
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = prog.title_bn ?: prog.id,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryText
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ExtensionStatusPill(
    label: String,
    isConnected: Boolean,
    onClick: () -> Unit
) {
    val color = if (isConnected) Color(0xFF10B981) else Color(0xFFF59E0B)
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.55f)),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun AiAgentChatTab(
    uiState: AiAgentUiState,
    promptInput: String,
    onPromptChange: (String) -> Unit,
    onSendPrompt: () -> Unit,
    onQuickPrompt: (String) -> Unit,
    onSubjectSelect: (String, String) -> Unit,
    onExecutePlan: (ProposedAgentPlan) -> Unit,
    isDark: Boolean
) {
    val listState = rememberLazyListState()
    val cardBg = if (isDark) Color(0xFF111A2E) else Color.White
    val primaryText = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val secondaryText = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Course Inspection Summary Bar
        uiState.activeInspection?.let { summary ->
            Surface(
                color = if (isDark) Color(0xFF0F172A) else Color(0xFFEFF6FF),
                border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.3f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📚 ${summary.programTitle}",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = primaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "🎬 ${summary.videoLessonsCount} ভিডিও • 📄 ${summary.slideAttachmentsCount} স্লাইড",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF8B5CF6)
                        )
                    }

                    if (summary.subjectBreakdown.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = uiState.selectedSubjectCode.isBlank(),
                                onClick = { onSubjectSelect("", "") },
                                label = { Text("সব বিষয় (${summary.totalLessonsCount})", fontSize = 11.sp) }
                            )
                            summary.subjectBreakdown.forEach { sub ->
                                FilterChip(
                                    selected = uiState.selectedSubjectCode == sub.subjectCode,
                                    onClick = { onSubjectSelect(sub.subjectCode, sub.subjectTitle) },
                                    label = {
                                        Text(
                                            "${sub.subjectTitle} (${sub.videosCount}V/${sub.slidesCount}S)",
                                            fontSize = 11.sp
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Chat Messages List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 10.dp)
        ) {
            items(uiState.messages, key = { it.id }) { msg ->
                val isUser = msg.role == "user"
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                ) {
                    Surface(
                        shape = RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 18.dp,
                            bottomStart = if (isUser) 18.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 18.dp
                        ),
                        color = when {
                            isUser -> Color(0xFF4F46E5)
                            msg.isError -> Color(0xFF7F1D1D)
                            else -> cardBg
                        },
                        border = if (!isUser) {
                            BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.35f))
                        } else null,
                        modifier = Modifier.widthIn(max = 340.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            if (msg.isStreaming) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = Color(0xFF38BDF8)
                                    )
                                    Text(
                                        text = msg.text,
                                        fontSize = 13.5.sp,
                                        color = primaryText
                                    )
                                }
                            } else {
                                Text(
                                    text = msg.text,
                                    fontSize = 13.8.sp,
                                    color = if (isUser || msg.isError) Color.White else primaryText,
                                    lineHeight = 20.sp
                                )
                            }

                            // Interactive Plan Execution Card inside AI response
                            msg.proposedPlan?.let { plan ->
                                Spacer(modifier = Modifier.height(12.dp))
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isDark) Color(0xFF090F1E) else Color(0xFFF1F5F9),
                                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AutoAwesomeMotion,
                                                contentDescription = null,
                                                tint = Color(0xFF10B981),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = "প্রস্তুতকৃত অ্যাকশন প্ল্যান",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color(0xFF10B981)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "• টাস্ক: ${plan.taskTitle}\n" +
                                                "• চ্যানেল/গ্রুপ: ${plan.targetChannelTitle}\n" +
                                                "• কন্টেন্ট: ${if (plan.includeVideos) "ভিডিও (${plan.videoQuality})" else ""} ${if (plan.includeSlides) "+ লেকচার স্লাইড (PDF)" else ""}\n" +
                                                "• আনুমানিক আইটেম: ${plan.estimatedVideos}টি ভিডিও ও ${plan.estimatedSlides}টি স্লাইড",
                                            fontSize = 12.sp,
                                            color = secondaryText,
                                            lineHeight = 18.sp
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Button(
                                            onClick = { onExecutePlan(plan) },
                                            enabled = !uiState.isDispatchingTask,
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("execute_agent_plan_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.RocketLaunch,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (plan.actionType == "CREATE_SCHEDULE") {
                                                    "২৪/৭ রুটিন চালু করুন"
                                                } else {
                                                    "ব্যাকগ্রাউন্ডে আপলোড শুরু করুন"
                                                },
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
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

        // Quick Action Prompt Suggestions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val suggestions = listOf(
                "📊 এই কোর্সের সব ভিডিও ও স্লাইড আপলোডের প্ল্যান দেখাও",
                "🚀 এই কোর্সের সব ভিডিও ও স্লাইড টেলিগ্রামে নতুন চ্যানেল খুলে আপলোড করো",
                "⏰ প্রতিদিন সকাল ৮টায় নতুন ক্লাস ও স্লাইড টেলিগ্রামে আপলোড করার রুটিন চালু করো",
                "✈️ এই কোর্সের নামে একটি নতুন টেলিগ্রাম চ্যানেল তৈরি করো"
            )
            suggestions.forEach { text ->
                AssistChip(
                    onClick = { onQuickPrompt(text) },
                    label = { Text(text, fontSize = 11.5.sp) }
                )
            }
        }

        // Chat Input Bar
        Surface(
            color = cardBg,
            tonalElevation = 4.dp,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = promptInput,
                    onValueChange = onPromptChange,
                    placeholder = {
                        Text(
                            "এজেন্টকে যেকোনো নির্দেশ দিন বা আলোচনা করুন...",
                            fontSize = 13.sp
                        )
                    },
                    maxLines = 3,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai_agent_prompt_input")
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilledIconButton(
                    onClick = onSendPrompt,
                    enabled = promptInput.isNotBlank() && uiState.brainState !is AgentBrainState.Thinking,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color(0xFF8B5CF6)
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("ai_agent_send_button")
                ) {
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

@Composable
private fun AiAgentTasksTab(
    uiState: AiAgentUiState,
    onRetryOrResume: (AiAgentTask) -> Unit,
    onCancelRun: (Long) -> Unit,
    onOpenUrl: (String) -> Unit,
    isDark: Boolean
) {
    val cardBg = if (isDark) Color(0xFF111A2E) else Color.White
    val primaryText = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val secondaryText = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 14.dp)
    ) {
        if (uiState.workflowRuns.isNotEmpty()) {
            item {
                Text(
                    text = "🐙 GitHub Actions লাইভ ক্লাউড রান",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = primaryText
                )
            }
            items(uiState.workflowRuns, key = { it.id }) { run ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isRunning = run.status in listOf("in_progress", "queued")
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isRunning) Color(0xFF38BDF8).copy(alpha = 0.2f)
                                    else Color(0xFF10B981).copy(alpha = 0.2f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isRunning) Icons.Default.Sync else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (isRunning) Color(0xFF38BDF8) else Color(0xFF10B981)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${run.name} (#${run.id})",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryText
                            )
                            Text(
                                text = "স্ট্যাটাস: ${run.status.uppercase()} ${run.conclusion?.let { "• $it" } ?: ""}",
                                fontSize = 11.5.sp,
                                color = secondaryText
                            )
                        }
                        if (isRunning) {
                            TextButton(onClick = { onCancelRun(run.id) }) {
                                Text("বাতিল", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                            }
                        }
                        if (run.htmlUrl.isNotBlank()) {
                            IconButton(onClick = { onOpenUrl(run.htmlUrl) }) {
                                Icon(
                                    Icons.Default.OpenInNew,
                                    contentDescription = "লগ দেখুন",
                                    tint = Color(0xFF38BDF8)
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "⚡ এজেন্ট ব্যাকগ্রাউন্ড টাস্কসমূহ",
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = primaryText
            )
        }

        if (uiState.tasks.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudQueue,
                            contentDescription = null,
                            tint = Color(0xFF8B5CF6),
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "এখনো কোনো ব্যাকগ্রাউন্ড টাস্ক দেওয়া হয়নি",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryText
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "AI চ্যাট ট্যাবে গিয়ে এজেন্টকে যেকোনো কোর্সের ভিডিও ও স্লাইড টেলিগ্রামে আপলোড করতে বলুন।",
                            fontSize = 12.5.sp,
                            color = secondaryText
                        )
                    }
                }
            }
        } else {
            items(uiState.tasks, key = { it.id }) { task ->
                val statusColor = when (task.status.uppercase()) {
                    "COMPLETED" -> Color(0xFF10B981)
                    "RUNNING", "RESUMING" -> Color(0xFF38BDF8)
                    "FAILED" -> Color(0xFFEF4444)
                    else -> Color(0xFFF59E0B)
                }
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = task.title,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = primaryText,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = statusColor.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = task.status,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = statusColor,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = task.statusMessage,
                            fontSize = 12.5.sp,
                            color = secondaryText
                        )

                        if (task.currentItemTitle.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "চলমান: ${task.currentItemTitle}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF38BDF8)
                            )
                        }

                        if (task.totalItems > 0) {
                            Spacer(modifier = Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = { task.progressFraction },
                                color = statusColor,
                                trackColor = statusColor.copy(alpha = 0.15f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(50))
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${task.completedItems} / ${task.totalItems} লেকচার সম্পন্ন (${(task.progressFraction * 100).toInt()}%)",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryText
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (task.telegramInviteLink.isNotBlank()) {
                                Button(
                                    onClick = { onOpenUrl(task.telegramInviteLink) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("টেলিগ্রাম চ্যানেল ওপেন করুন", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            OutlinedButton(
                                onClick = { onRetryOrResume(task) },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("রিস্টার্ট / রিজিউম", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiAgentSchedulesTab(
    uiState: AiAgentUiState,
    onToggleSchedule: (AiAgentSchedule, Boolean) -> Unit,
    onDeleteSchedule: (String) -> Unit,
    onCreateQuickDailySchedule: () -> Unit,
    isDark: Boolean
) {
    val cardBg = if (isDark) Color(0xFF111A2E) else Color.White
    val primaryText = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val secondaryText = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 14.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) Color(0xFF1E1B4B) else Color(0xFFEEF2FF)
                ),
                border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoMode,
                            contentDescription = null,
                            tint = Color(0xFF8B5CF6),
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "২৪/৭ অটোমেটিক রুটিন ওয়াচার",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = primaryText
                            )
                            Text(
                                text = "GitHub Actions প্রতি ঘণ্টায় চেক করবে এবং নির্ধারিত সময়ে নতুন ক্লাস ও স্লাইড টেলিগ্রামে আপলোড করবে।",
                                fontSize = 12.sp,
                                color = secondaryText
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onCreateQuickDailySchedule,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_daily_schedule_button")
                    ) {
                        Icon(Icons.Default.AddAlarm, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${uiState.selectedProgramTitle} — প্রতিদিন সকাল ৮টায় অটো-সিঙ্ক চালু করুন",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        items(uiState.schedules, key = { it.id }) { sched ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(
                    1.dp,
                    if (sched.isEnabled) Color(0xFF10B981).copy(alpha = 0.45f)
                    else secondaryText.copy(alpha = 0.25f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = sched.title,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = primaryText
                            )
                            Text(
                                text = "⏰ ${sched.timeDisplayBn} • চ্যানেল: ${sched.targetChannelTitle.ifBlank { "অটো চ্যানেল" }}",
                                fontSize = 12.sp,
                                color = Color(0xFF38BDF8)
                            )
                            Text(
                                text = "স্ট্যাটাস: ${sched.lastRunStatus}",
                                fontSize = 11.5.sp,
                                color = secondaryText
                            )
                        }
                        Switch(
                            checked = sched.isEnabled,
                            onCheckedChange = { onToggleSchedule(sched, it) }
                        )
                        IconButton(onClick = { onDeleteSchedule(sched.id) }) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "মুছে ফেলুন",
                                tint = Color(0xFFEF4444)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiAgentExtensionsTab(
    uiState: AiAgentUiState,
    onSaveGitHub: (String, String, String, String) -> Unit,
    onSelectRepo: (GitHubRepoSummary) -> Unit,
    onDeployGitHubWorkflow: () -> Unit,
    onRequestTelegramOtp: (String, String, String) -> Unit,
    onVerifyTelegramOtp: (String, String) -> Unit,
    onSaveTelegram: (String, String, String, String, String, String, String) -> Unit,
    isDark: Boolean
) {
    val context = LocalContext.current
    val cardBg = if (isDark) Color(0xFF111A2E) else Color.White
    val primaryText = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val secondaryText = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    var ghToken by remember(uiState.githubConfig.personalAccessToken) {
        mutableStateOf(uiState.githubConfig.personalAccessToken)
    }
    var ghOwner by remember(uiState.githubConfig.owner) {
        mutableStateOf(uiState.githubConfig.owner)
    }
    var ghRepo by remember(uiState.githubConfig.repo) {
        mutableStateOf(uiState.githubConfig.repo)
    }
    var ghBranch by remember(uiState.githubConfig.branch) {
        mutableStateOf(uiState.githubConfig.branch)
    }
    var showGhToken by remember { mutableStateOf(false) }

    var tgApiId by remember(uiState.telegramConfig.apiId) {
        mutableStateOf(uiState.telegramConfig.apiId.ifBlank { DEFAULT_TELEGRAM_API_ID })
    }
    var tgApiHash by remember(uiState.telegramConfig.apiHash) {
        mutableStateOf(uiState.telegramConfig.apiHash.ifBlank { DEFAULT_TELEGRAM_API_HASH })
    }
    var tgPhone by remember(uiState.telegramConfig.phone) {
        mutableStateOf(uiState.telegramConfig.phone)
    }
    var tgOtpCode by remember { mutableStateOf("") }
    var tgTwoFaPassword by remember { mutableStateOf("") }
    var tgSession by remember(uiState.telegramConfig.sessionString) {
        mutableStateOf(uiState.telegramConfig.sessionString)
    }
    var tgBotToken by remember(uiState.telegramConfig.botToken) {
        mutableStateOf(uiState.telegramConfig.botToken)
    }
    var tgDefaultChat by remember(uiState.telegramConfig.defaultChatId) {
        mutableStateOf(uiState.telegramConfig.defaultChatId)
    }
    var tgDisplayName by remember(uiState.telegramConfig.accountDisplayName) {
        mutableStateOf(uiState.telegramConfig.accountDisplayName)
    }
    var showTgSecrets by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Device Cloud Vault Banner
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isDark) Color(0xFF0F2942) else Color(0xFFE0F2FE)
            ),
            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.55f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ডিভাইস ক্লাউড ভল্ট সক্রিয় (অটো রিকভারি)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = primaryText
                    )
                }
                Text(
                    text = "আপনার GitHub ও Telegram (API_ID, API_HASH, STRING_SESSION) এই ডিভাইসের আন্ডারে ক্লাউডে সেভ থাকে। অ্যাপ আনইনস্টল বা ডিলিট করে আবার ইনস্টল করলেও এই ডিভাইসে সব অটোমেটিক ফিরে আসবে এবং GitHub Actions-এ ম্যানুয়ালি কোনো Secret বা Script যোগ করতে হবে না।",
                    fontSize = 12.sp,
                    color = secondaryText,
                    lineHeight = 17.sp
                )
            }
        }

        // 1. GitHub Full Access Extension Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = BorderStroke(
                1.dp,
                if (uiState.githubConfig.isVerified) Color(0xFF10B981) else Color(0xFF38BDF8).copy(alpha = 0.4f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "১. GitHub Account (24/7 অটো স্ক্রিপ্ট ও কন্ট্রোল)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = primaryText
                        )
                        Text(
                            text = if (uiState.githubConfig.isVerified) {
                                "✅ কানেক্টেড: @${uiState.githubConfig.connectedUsername} (${uiState.githubConfig.owner}/${uiState.githubConfig.repo})"
                            } else {
                                "GitHub Personal Access Token (repo + workflow পারমিশনসহ) দিন — অ্যাপ নিজেই আপনার রিপোজিটরিতে স্ক্রিপ্ট বসিয়ে নেবে"
                            },
                            fontSize = 12.sp,
                            color = if (uiState.githubConfig.isVerified) Color(0xFF10B981) else secondaryText
                        )
                    }
                }

                OutlinedTextField(
                    value = ghToken,
                    onValueChange = { ghToken = it },
                    label = { Text("GitHub Personal Access Token (ghp_...)") },
                    visualTransformation = if (showGhToken) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showGhToken = !showGhToken }) {
                            Icon(
                                if (showGhToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("github_token_input")
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = ghOwner,
                        onValueChange = { ghOwner = it },
                        label = { Text("GitHub Username/Owner") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = ghRepo,
                        onValueChange = { ghRepo = it },
                        label = { Text("Repository Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (uiState.githubRepos.isNotEmpty()) {
                    Text(
                        text = "আপনার রিপোজিটরি সিলেক্ট করুন (সিলেক্ট করলেই AI স্ক্রিপ্ট অটো-ইনস্টল হবে):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = secondaryText
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        uiState.githubRepos.take(12).forEach { repoItem ->
                            FilterChip(
                                selected = ghRepo == repoItem.name && ghOwner == repoItem.owner,
                                onClick = {
                                    ghOwner = repoItem.owner
                                    ghRepo = repoItem.name
                                    ghBranch = repoItem.defaultBranch
                                    onSelectRepo(repoItem)
                                },
                                label = { Text(repoItem.name, fontSize = 11.5.sp) }
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onSaveGitHub(ghToken, ghOwner, ghRepo, ghBranch) },
                        enabled = !uiState.isVerifyingGitHub,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_github_config_button")
                    ) {
                        if (uiState.isVerifyingGitHub) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("GitHub সেভ ও অটো-সেটআপ", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    }

                    if (uiState.githubConfig.isVerified && uiState.githubConfig.repo.isNotBlank()) {
                        OutlinedButton(
                            onClick = onDeployGitHubWorkflow,
                            enabled = !uiState.isDeployingWorkflow,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                            modifier = Modifier.testTag("deploy_github_workflow_button")
                        ) {
                            if (uiState.isDeployingWorkflow) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(15.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFF38BDF8)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = if (uiState.githubConfig.isWorkflowDeployed) "স্ক্রিপ্ট আপডেট" else "স্ক্রিপ্ট ইনস্টল",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8)
                            )
                        }
                    }
                }
            }
        }

        // 2. Telegram Personal Account Full Access + Auto STRING_SESSION Generator Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = BorderStroke(
                1.dp,
                if (uiState.telegramConfig.isConnected) Color(0xFF10B981) else Color(0xFF8B5CF6).copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = null,
                        tint = Color(0xFF229ED9),
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "২. Telegram Account ও অটো STRING_SESSION জেনারেটর",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = primaryText
                        )
                        Text(
                            text = if (uiState.telegramConfig.isConnected) {
                                "✅ কানেক্টেড: ${uiState.telegramConfig.accountDisplayName.ifBlank { "Telegram ফুল এক্সেস সক্রিয়" }} (এই ডিভাইসে স্থায়ীভাবে সেভড)"
                            } else {
                                "আপনার API_ID (38634154) ও API_HASH বসানো আছে — শুধু ফোন নাম্বার ও OTP দিয়ে অটোমেটিক STRING_SESSION তৈরি করুন"
                            },
                            fontSize = 12.sp,
                            color = if (uiState.telegramConfig.isConnected) Color(0xFF10B981) else secondaryText
                        )
                    }
                }

                // Pre-filled API_ID & API_HASH
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = tgApiId,
                        onValueChange = { tgApiId = it },
                        label = { Text("TELEGRAM_API_ID") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("telegram_api_id_input")
                    )
                    OutlinedTextField(
                        value = tgApiHash,
                        onValueChange = { tgApiHash = it },
                        label = { Text("TELEGRAM_API_HASH") },
                        visualTransformation = if (showTgSecrets) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showTgSecrets = !showTgSecrets }) {
                                Icon(
                                    if (showTgSecrets) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("telegram_api_hash_input")
                    )
                }

                // Automatic In-App STRING_SESSION Generator Box
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isDark) Color(0xFF172036) else Color(0xFFF0F9FF),
                    border = BorderStroke(1.dp, Color(0xFF229ED9).copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "⚡ অটোমেটিক STRING_SESSION জেনারেটর (অ্যাপের ভেতরেই তৈরি করুন)",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF38BDF8)
                        )
                        Text(
                            text = "ধাপ ১: আপনার টেলিগ্রাম ফোন নাম্বার (+8801...) লিখে 'OTP পাঠান' বাটনে চাপ দিন।\nধাপ ২: টেলিগ্রাম অ্যাপে যে ৫ সংখ্যার কোড যাবে সেটি বসিয়ে 'ভেরিফাই ও অটো সেভ' চাপুন।",
                            fontSize = 11.5.sp,
                            color = secondaryText,
                            lineHeight = 16.sp
                        )

                        OutlinedTextField(
                            value = tgPhone,
                            onValueChange = { tgPhone = it },
                            label = { Text("টেলিগ্রাম ফোন নাম্বার (যেমন: +88017XXXXXXXX)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("telegram_phone_input")
                        )

                        Button(
                            onClick = { onRequestTelegramOtp(tgApiId, tgApiHash, tgPhone) },
                            enabled = !uiState.isProcessingTelegramAuth,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("request_telegram_otp_button")
                        ) {
                            if (uiState.isProcessingTelegramAuth &&
                                uiState.telegramConfig.authState == "SENDING_CODE"
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text("📲 ধাপ ১: টেলিগ্রামে OTP কোড পাঠান", fontWeight = FontWeight.Bold)
                        }

                        // Show live status message from Supabase / Telethon worker
                        if (uiState.telegramConfig.authMessage.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF0F172A).copy(alpha = 0.65f),
                                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (uiState.isProcessingTelegramAuth) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = Color(0xFF38BDF8)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Text(
                                        text = uiState.telegramConfig.authMessage,
                                        fontSize = 12.sp,
                                        color = Color(0xFFE2E8F0)
                                    )
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = tgOtpCode,
                                onValueChange = { tgOtpCode = it },
                                label = { Text("টেলিগ্রাম OTP কোড") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("telegram_otp_input")
                            )
                            OutlinedTextField(
                                value = tgTwoFaPassword,
                                onValueChange = { tgTwoFaPassword = it },
                                label = { Text("2FA পাসওয়ার্ড (থাকলে)") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("telegram_2fa_input")
                            )
                        }

                        Button(
                            onClick = { onVerifyTelegramOtp(tgOtpCode, tgTwoFaPassword) },
                            enabled = !uiState.isProcessingTelegramAuth && tgOtpCode.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("verify_telegram_otp_button")
                        ) {
                            if (uiState.isProcessingTelegramAuth &&
                                uiState.telegramConfig.authState == "VERIFYING_OTP"
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = "🔐 ধাপ ২: ভেরিফাই ও অটো STRING_SESSION তৈরি করুন",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = tgSession,
                    onValueChange = { tgSession = it },
                    label = { Text("Telethon STRING_SESSION (অটোমেটিক তৈরি হবে অথবা ম্যানুয়ালি দিন)") },
                    visualTransformation = if (showTgSecrets) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showTgSecrets = !showTgSecrets }) {
                            Icon(
                                if (showTgSecrets) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("telegram_session_input")
                )

                OutlinedTextField(
                    value = tgDefaultChat,
                    onValueChange = { tgDefaultChat = it },
                    label = { Text("ডিফল্ট চ্যানেল/গ্রুপ Chat ID (ঐচ্ছিক, খালি রাখলে এজেন্ট নিজে চ্যানেল খুলবে)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        onSaveTelegram(tgApiId, tgApiHash, tgPhone, tgSession, tgBotToken, tgDefaultChat, tgDisplayName)
                        Toast.makeText(context, "টেলিগ্রাম কনফিগারেশন ডিভাইসের আন্ডারে ক্লাউডে সেভ হয়েছে!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF229ED9)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("save_telegram_config_button")
                ) {
                    Text("টেলিগ্রাম কনফিগারেশন ডিভাইস ভল্টে সেভ করুন", fontWeight = FontWeight.Bold)
                }
            }
        }

        // 3. Built-in Shikho & Cloud Vault Summary Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "৩. সংযুক্ত সিস্টেম ও ডিভাইস রিকভারি স্ট্যাটাস",
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = primaryText
                )
                Text(
                    text = "✅ Telegram API_ID (38634154) ও API_HASH — প্রি-লোডেড ও ক্লাউড-সিঙ্কড\n" +
                        "✅ অটোমেটিক STRING_SESSION জেনারেটর — সরাসরি অ্যাপ থেকে OTP ভেরিফিকেশন\n" +
                        "✅ ডিভাইস-ভিত্তিক রিকভারি — অ্যাপ ডিলিট করে পুনরায় ইনস্টল করলেও সব ফিরে আসবে\n" +
                        "✅ জিরো-সিক্রেট GitHub Actions — ম্যানুয়ালি কোনো GitHub Secret বা Script যোগ করতে হবে না",
                    fontSize = 12.5.sp,
                    color = secondaryText,
                    lineHeight = 20.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}
