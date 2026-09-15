package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ai.AiUiState
import com.example.ai.AiViewModel
import com.example.api.AiChatMessage
import com.example.api.AiChatSession
import com.example.api.AiSubjectOption
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiDoubtSolvingScreen(
    viewModel: AiViewModel,
    onBack: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToWeb: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        viewModel.onImageSelected(uri)
    }

    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(320.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                AiChatHistoryDrawer(
                    sessions = uiState.chatSessions,
                    currentSessionId = uiState.currentSessionId,
                    onSelectSession = { session ->
                        viewModel.loadSession(session)
                        scope.launch { drawerState.close() }
                    },
                    onDeleteSession = { sessionId ->
                        viewModel.deleteSession(sessionId)
                    },
                    onStartNewChat = {
                        viewModel.startNewChat()
                        scope.launch { drawerState.close() }
                    },
                    onUpgradeClick = {
                        scope.launch { drawerState.close() }
                        onNavigateToProfile()
                    }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                AiTopAppBar(
                    onBack = onBack,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onStartNewChat = { viewModel.startNewChat() },
                    onOpenProfile = onNavigateToProfile,
                    onToggleWeb = onNavigateToWeb,
                    userAvatar = uiState.userAvatar,
                    isUnlimited = uiState.isUnlimited
                )
            },
            bottomBar = {
                AiInputBottomBar(
                    inputText = uiState.inputText,
                    attachedImageUri = uiState.attachedImageUri,
                    isGenerating = uiState.isGenerating,
                    onTextChanged = { viewModel.onInputTextChanged(it) },
                    onAttachImage = { imagePickerLauncher.launch("image/*") },
                    onClearImage = { viewModel.clearAttachedImage() },
                    onSend = { viewModel.sendQuestion(context = context) }
                )
            },
            modifier = modifier
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                val activeSubjects = if (uiState.subjectsList.isNotEmpty()) uiState.subjectsList else viewModel.availableSubjects

                // Subject Filter Bar + Today's Usage Counter
                AiSubjectAndUsageBar(
                    subjects = activeSubjects,
                    selectedSubjectCode = uiState.selectedSubjectCode,
                    onSubjectSelected = { viewModel.onSubjectSelected(it) },
                    usedCount = uiState.usedQuestionsCount,
                    totalLimit = uiState.totalDailyLimit,
                    isUnlimited = uiState.isUnlimited
                )

                // Main Chat Body or Empty State
                if (uiState.currentMessages.isEmpty()) {
                    AiEmptyState(
                        selectedSubject = activeSubjects.find { it.code.equals(uiState.selectedSubjectCode, ignoreCase = true) }
                            ?: activeSubjects.firstOrNull() ?: viewModel.availableSubjects.first(),
                        onSelectSamplePrompt = { prompt ->
                            viewModel.setSamplePrompt(prompt)
                        },
                        onQuickSendPrompt = { prompt ->
                            viewModel.sendQuestion(prompt, context = context)
                        }
                    )
                } else {
                    AiChatMessagesList(
                        messages = uiState.currentMessages,
                        isGenerating = uiState.isGenerating,
                        onCopyText = { text ->
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("AI Solution", text)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "সমাধানটি কপি করা হয়েছে", Toast.LENGTH_SHORT).show()
                        },
                        onFeedback = { msgId, type ->
                            viewModel.submitFeedback(msgId, type)
                        },
                        onFollowUpAction = { followUpText ->
                            viewModel.sendQuestion(followUpText)
                        }
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// Top App Bar
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiTopAppBar(
    onBack: () -> Unit,
    onOpenDrawer: () -> Unit,
    onStartNewChat: () -> Unit,
    onOpenProfile: () -> Unit,
    onToggleWeb: () -> Unit,
    userAvatar: String?,
    isUnlimited: Boolean
) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "MaxBird AI",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF8B5CF6).copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = if (isUnlimited) "PRO PASS" else "BETA",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF7C3AED),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "ডাউট সলভিং ও ইনস্ট্যান্ট সমাধান",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        actions = {
            // Web view toggle
            IconButton(onClick = onToggleWeb) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = "Web View",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // History Drawer
            IconButton(onClick = onOpenDrawer) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "Chat History",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            // New Chat
            IconButton(onClick = onStartNewChat) {
                Icon(
                    imageVector = Icons.Default.AddComment,
                    contentDescription = "New Chat",
                    tint = Color(0xFF7C3AED)
                )
            }

            // Profile / Subscription Avatar
            Box(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, Color(0xFF8B5CF6), CircleShape)
                    .clickable { onOpenProfile() },
                contentAlignment = Alignment.Center
            ) {
                if (!userAvatar.isNullOrBlank()) {
                    AsyncImage(
                        model = userAvatar,
                        contentDescription = "Profile",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Profile",
                        tint = Color(0xFF7C3AED),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

// -------------------------------------------------------------
// Subject & Usage Bar
// -------------------------------------------------------------
@Composable
private fun AiSubjectAndUsageBar(
    subjects: List<AiSubjectOption>,
    selectedSubjectCode: String,
    onSubjectSelected: (String) -> Unit,
    usedCount: Int,
    totalLimit: Int,
    isUnlimited: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 6.dp)
    ) {
        // Usage / Pass indicator
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "বিষয় সিলেক্ট করো:",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color(0xFF10B981), CircleShape)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "প্রশ্ন: আনলিমিটেড",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF059669)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Horizontal Subject Chips
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(subjects) { subject ->
                val isSelected = subject.code == selectedSubjectCode
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onSubjectSelected(subject.code) },
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) Color(0xFF7C3AED) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = if (isSelected) null else androidx.compose.foundation.BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = subject.titleBn,
                            fontSize = 12.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// Empty State with 3D Graphic and Sample Prompts
// -------------------------------------------------------------
@Composable
private fun AiEmptyState(
    selectedSubject: AiSubjectOption,
    onSelectSamplePrompt: (String) -> Unit,
    onQuickSendPrompt: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(10.dp))

            // 3D Chat Graphic Banner
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                Color(0xFF8B5CF6).copy(alpha = 0.25f),
                                Color(0xFF6D28D9).copy(alpha = 0.05f),
                                Color.Transparent
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9), Color(0xFF4C1D95))
                            )
                        )
                        .shadow(8.dp, RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "প্রশ্ন করে ইনস্ট্যান্ট উত্তর দেখো",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "তোমার প্রশ্নটি যত স্পষ্ট ও সুনির্দিষ্ট করে লিখবে অথবা ছবিটি যত স্পষ্ট হবে, MaxBird AI তত নির্ভুলভাবে প্রশ্নের উত্তর দিতে পারবে।",
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Popular Sample Questions Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${selectedSubject.titleBn} সম্পর্কিত জনপ্রিয় প্রশ্নসমূহ:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        // Sample Prompt Cards
        items(selectedSubject.samplePrompts) { prompt ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onQuickSendPrompt(prompt) },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    0.9.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                ),
                tonalElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF8B5CF6).copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color(0xFF7C3AED),
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Text(
                            text = prompt,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = Color(0xFF7C3AED),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// Interactive Chat Messages List
// -------------------------------------------------------------
@Composable
private fun AiChatMessagesList(
    messages: List<AiChatMessage>,
    isGenerating: Boolean,
    onCopyText: (String) -> Unit,
    onFeedback: (String, String) -> Unit,
    onFollowUpAction: (String) -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, isGenerating) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(messages) { message ->
            if (message.isUser) {
                UserMessageBubble(message = message)
            } else {
                AiResponseBubble(
                    message = message,
                    onCopy = { onCopyText(message.text) },
                    onFeedback = { type -> onFeedback(message.id, type) },
                    onFollowUp = onFollowUpAction
                )
            }
        }

        if (isGenerating) {
            item {
                AiThinkingIndicator()
            }
        }
    }
}

// -------------------------------------------------------------
// User Message Bubble
// -------------------------------------------------------------
@Composable
private fun UserMessageBubble(message: AiChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            // Attached Image if available
            if (message.imageUri != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .padding(bottom = 6.dp)
                        .size(180.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    AsyncImage(
                        model = message.imageUri,
                        contentDescription = "Attached Question Image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
                color = Color(0xFF6D28D9),
                shadowElevation = 2.dp
            ) {
                Text(
                    text = message.text,
                    fontSize = 14.sp,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }
    }
}

// -------------------------------------------------------------
// AI Response Bubble with Formulas & Step-by-Step Breakdown
// -------------------------------------------------------------
@Composable
private fun AiResponseBubble(
    message: AiChatMessage,
    onCopy: () -> Unit,
    onFeedback: (String) -> Unit,
    onFollowUp: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9)))),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Surface(
                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.25f)),
                shadowElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Header Tag
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "MaxBird AI সলভার",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF7C3AED)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "ভেরিফাইড সমাধান",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF059669),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Main Text Explanation
                    Text(
                        text = message.text,
                        fontSize = 13.5.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Formatted LaTeX / Mathematical Formulas Box
                    if (message.formulas.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFF5F3FF),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDDD6FE)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Functions,
                                        contentDescription = null,
                                        tint = Color(0xFF7C3AED),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "গাণিতিক সূত্রাবলী (Formulas):",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF6D28D9)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                message.formulas.forEach { formula ->
                                    Text(
                                        text = formula,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color(0xFF4C1D95),
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Steps Breakdown
                    if (message.steps.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        message.steps.forEach { step ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = step,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // Final Answer Highlight Box
                    if (!message.finalAnswer.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFECFDF5),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFA7F3D0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF059669),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "ফলাফল: ${message.finalAnswer}",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF065F46)
                                )
                            }
                        }
                    }

                    // Tips Box
                    if (!message.tips.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = message.tips,
                            fontSize = 11.5.sp,
                            color = Color(0xFFD97706),
                            lineHeight = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(4.dp))

                    // Action buttons: Copy, Like, Dislike
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    imageVector = Icons.Outlined.ContentCopy,
                                    contentDescription = "Copy",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            IconButton(
                                onClick = { onFeedback("like") },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (message.feedback == "like") Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                                    contentDescription = "Like",
                                    tint = if (message.feedback == "like") Color(0xFF059669) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            IconButton(
                                onClick = { onFeedback("dislike") },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (message.feedback == "dislike") Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                                    contentDescription = "Dislike",
                                    tint = if (message.feedback == "dislike") Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Text(
                            text = "MaxBird AI Model",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // Follow-up Actions
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FollowUpChip(
                    text = "🔍 আরো এক্সপ্লেইন করো",
                    onClick = { onFollowUp("এই প্রশ্নটির প্রতিটি ধাপ আরও বিশদভাবে সহজ ভাষায় ব্যাখ্যা করো") }
                )
                FollowUpChip(
                    text = "📝 এক্সাম্পলসহ দেখাও",
                    onClick = { onFollowUp("এই সূত্রের ওপর বাস্তব একটি সংখ্যামান ভিত্তিক উদাহরণ সমাধান করে দেখাও") }
                )
            }
        }
    }
}

@Composable
private fun FollowUpChip(text: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF3E8FF),
        border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0xFFD8B4FE))
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF6B21A8),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

// -------------------------------------------------------------
// AI Thinking Indicator
// -------------------------------------------------------------
@Composable
private fun AiThinkingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9)))),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.2f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = Color(0xFF7C3AED)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "MaxBird AI উত্তর প্রস্তুত করছে...",
                    fontSize = 12.5.sp,
                    color = Color(0xFF7C3AED),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// -------------------------------------------------------------
// Bottom Input Box with Image Attachment & Counter
// -------------------------------------------------------------
@Composable
private fun AiInputBottomBar(
    inputText: String,
    attachedImageUri: Uri?,
    isGenerating: Boolean,
    onTextChanged: (String) -> Unit,
    onAttachImage: () -> Unit,
    onClearImage: () -> Unit,
    onSend: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Thumbnail of attached image if exists
        if (attachedImageUri != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFF8B5CF6), RoundedCornerShape(8.dp))
                ) {
                    AsyncImage(
                        model = attachedImageUri,
                        contentDescription = "Attached image preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    IconButton(
                        onClick = onClearImage,
                        modifier = Modifier
                            .size(20.dp)
                            .align(Alignment.TopEnd)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ছবি সিলেক্ট করা হয়েছে",
                    fontSize = 11.5.sp,
                    color = Color(0xFF7C3AED),
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Input Card Container
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFF8B5CF6).copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Attach image button
                IconButton(onClick = onAttachImage) {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = "Attach photo",
                        tint = Color(0xFF7C3AED),
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Text Input Field
                TextField(
                    value = inputText,
                    onValueChange = onTextChanged,
                    placeholder = {
                        Text(
                            text = "তোমার প্রশ্নটি লিখো...",
                            fontSize = 13.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier.weight(1f),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    maxLines = 4
                )

                // Send Button
                val canSend = (inputText.isNotBlank() || attachedImageUri != null) && !isGenerating

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (canSend) Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9)))
                            else Brush.linearGradient(listOf(Color.Gray.copy(alpha = 0.4f), Color.Gray.copy(alpha = 0.4f)))
                        )
                        .clickable(enabled = canSend) { onSend() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Bottom Character counter and disclaimer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "MaxBird AI ভুল করতে পারে। তথ্য যাচাই করে নাও।",
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
            Text(
                text = "${inputText.length}/500",
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

// -------------------------------------------------------------
// History Side Drawer
// -------------------------------------------------------------
@Composable
private fun AiChatHistoryDrawer(
    sessions: List<AiChatSession>,
    currentSessionId: String?,
    onSelectSession: (AiChatSession) -> Unit,
    onDeleteSession: (String) -> Unit,
    onStartNewChat: () -> Unit,
    onUpgradeClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Drawer Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.ListAlt,
                    contentDescription = null,
                    tint = Color(0xFF7C3AED),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "চ্যাট হিস্ট্রি",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            IconButton(onClick = onStartNewChat) {
                Icon(
                    imageVector = Icons.Default.AddComment,
                    contentDescription = "New Chat",
                    tint = Color(0xFF7C3AED)
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

        // History List
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (sessions.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "কোনো পূর্ববর্তী চ্যাট নেই",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(sessions) { session ->
                    val isSelected = session.id == currentSessionId
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelectSession(session) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) Color(0xFF8B5CF6).copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChatBubbleOutline,
                                    contentDescription = null,
                                    tint = if (isSelected) Color(0xFF7C3AED) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = session.title,
                                    fontSize = 12.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color(0xFF7C3AED) else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = { onDeleteSession(session.id) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom Upgrade Banner
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable { onUpgradeClick() },
            shape = RoundedCornerShape(14.dp),
            color = Color.Transparent
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF6D28D9), Color(0xFF4338CA))
                        )
                    )
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "লার্নিং পাস সাবস্ক্রিপশন",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "আনলিমিটেড এআই সমাধান ও ইমেজ সাপোর্ট",
                            fontSize = 10.5.sp,
                            color = Color(0xFFDDD6FE)
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFFBBF24),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
