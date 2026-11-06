package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.agent.AiAgentRepository
import com.example.agent.AiAgentTask
import com.example.agent.TelegramDialogDestination
import com.example.agent.TelegramForumTopic
import com.example.api.ShikhoApiService
import com.example.api.StudentLessonItem
import com.example.auth.SessionManager
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramSendBottomSheet(
    title: String,
    subtitle: String,
    lessonsToSend: List<StudentLessonItem>,
    programId: String,
    programTitle: String,
    phaseId: String,
    subjectCode: String,
    subjectTitle: String,
    chapterId: String,
    chapterTitle: String,
    initialIncludeVideos: Boolean = true,
    initialIncludeSlides: Boolean = true,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sessionManager = remember { SessionManager(context.applicationContext) }
    val apiService = remember { ShikhoApiService.create(sessionManager) }
    val repository = remember { AiAgentRepository(context.applicationContext, apiService, sessionManager) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var includeVideos by remember { mutableStateOf(initialIncludeVideos) }
    var includeSlides by remember { mutableStateOf(initialIncludeSlides) }
    var selectedQuality by remember { mutableStateOf("720p") }

    // Destination mode: 0 = আমার চ্যানেল / গ্রুপে পাঠান, 1 = নতুন চ্যানেল / গ্রুপ তৈরি করে পাঠান
    var destinationMode by remember { mutableIntStateOf(0) }

    var dialogs by remember { mutableStateOf<List<TelegramDialogDestination>>(emptyList()) }
    var isLoadingDialogs by remember { mutableStateOf(false) }
    var dialogFilterQuery by remember { mutableStateOf("") }
    var dialogTypeFilter by remember { mutableStateOf("ALL") } // ALL, CHANNEL, GROUP, FORUM

    var selectedDialog by remember { mutableStateOf<TelegramDialogDestination?>(null) }
    var selectedTopic by remember { mutableStateOf<TelegramForumTopic?>(null) }
    var manualTopicIdInput by remember { mutableStateOf("") }
    var manualChatIdInput by remember { mutableStateOf("") }

    var newChannelTitle by remember {
        mutableStateOf(
            when {
                chapterTitle.isNotBlank() && subjectTitle.isNotBlank() -> "$subjectTitle • $chapterTitle"
                chapterTitle.isNotBlank() -> chapterTitle
                subjectTitle.isNotBlank() -> subjectTitle
                programTitle.isNotBlank() -> programTitle
                else -> title
            }
        )
    }
    var createNewSupergroup by remember { mutableStateOf(false) }

    var isDispatching by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isSuccessState by remember { mutableStateOf(false) }

    val tgConfig = remember { repository.getTelegramConfig() }
    val ghConfig = remember { repository.getGitHubConfig() }

    LaunchedEffect(Unit) {
        val cached = repository.getTelegramConfig().dialogs
        dialogs = cached
        if (cached.isNotEmpty() && selectedDialog == null) {
            selectedDialog = cached.firstOrNull()
        }
        isLoadingDialogs = true
        val loaded = repository.fetchTelegramDialogs(triggerRemoteRefresh = cached.isEmpty())
        dialogs = loaded
        if (selectedDialog == null && loaded.isNotEmpty()) {
            selectedDialog = loaded.firstOrNull()
        }
        isLoadingDialogs = false
    }

    val filteredDialogs = remember(dialogs, dialogFilterQuery, dialogTypeFilter) {
        dialogs.filter { d ->
            val matchesQuery = dialogFilterQuery.isBlank() ||
                d.title.contains(dialogFilterQuery, ignoreCase = true) ||
                d.username.contains(dialogFilterQuery, ignoreCase = true) ||
                d.id.contains(dialogFilterQuery, ignoreCase = true)
            val matchesType = when (dialogTypeFilter) {
                "CHANNEL" -> d.isChannel
                "GROUP" -> d.isMegagroup && !d.isForum
                "FORUM" -> d.isForum || d.topics.isNotEmpty()
                else -> true
            }
            matchesQuery && matchesType
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!isDispatching) onDismiss() },
        sheetState = sheetState,
        containerColor = Color(0xFF0E1628),
        contentColor = Color.White,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.35f)) }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF0088CC), Color(0xFF00B4DB))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send to Telegram",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "টেলিগ্রামে পাঠান (GitHub 24/7 Cloud)",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = title,
                                fontSize = 13.sp,
                                color = Color(0xFF38BDF8),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (subtitle.isNotBlank()) {
                                Text(
                                    text = subtitle,
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    IconButton(onClick = { if (!isDispatching) onDismiss() }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // Connection warning if GitHub or Telegram not connected
            val isGhConnected = ghConfig.isVerified || ghConfig.personalAccessToken.isNotBlank()
            if (!isGhConnected || !tgConfig.isConnected) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF3B1D2E)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "⚠️ একাউন্ট কানেকশন প্রয়োজন",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFFFDA4AF)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = buildString {
                                    if (!isGhConnected) append("• GitHub একাউন্ট কানেক্ট করা নেই (MaxBird AI Agent > একাউন্টস ট্যাবে গিয়ে সেটআপ করুন)।\n")
                                    if (!tgConfig.isConnected) append("• Telegram একাউন্ট কানেক্ট করা নেই (MaxBird AI Agent > একাউন্টস ট্যাবে গিয়ে OTP দিয়ে কানেক্ট করুন)।")
                                }.trim(),
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }

            // 1. What to Send (Video / Lecture Slide / Quality)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF17223B)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "১. কী কী পাঠাতে চান?",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF38BDF8)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FilterChip(
                                selected = includeVideos,
                                onClick = {
                                    if (includeVideos && !includeSlides) {
                                        includeSlides = true
                                    }
                                    includeVideos = !includeVideos
                                },
                                label = {
                                    Text(
                                        text = "🎬 ক্লাস ভিডিও",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (includeVideos) Icons.Default.CheckCircle else Icons.Default.Videocam,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF0284C7),
                                    selectedLabelColor = Color.White,
                                    selectedLeadingIconColor = Color.White,
                                    containerColor = Color(0xFF1E293B),
                                    labelColor = Color.White.copy(alpha = 0.8f)
                                ),
                                modifier = Modifier.weight(1f)
                            )

                            FilterChip(
                                selected = includeSlides,
                                onClick = {
                                    if (includeSlides && !includeVideos) {
                                        includeVideos = true
                                    }
                                    includeSlides = !includeSlides
                                },
                                label = {
                                    Text(
                                        text = "📄 লেকচার স্লাইড (PDF)",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (includeSlides) Icons.Default.CheckCircle else Icons.Default.PictureAsPdf,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF0D9488),
                                    selectedLabelColor = Color.White,
                                    selectedLeadingIconColor = Color.White,
                                    containerColor = Color(0xFF1E293B),
                                    labelColor = Color.White.copy(alpha = 0.8f)
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        AnimatedVisibility(visible = includeVideos) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "ভিডিও রেজোলিউশন / কোয়ালিটি:",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.75f)
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    listOf("1080p", "720p", "480p", "360p").forEach { q ->
                                        val isSel = selectedQuality == q
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isSel) Color(0xFF2563EB) else Color(0xFF0F172A),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { selectedQuality = q }
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.padding(vertical = 8.dp)
                                            ) {
                                                Text(
                                                    text = q,
                                                    fontSize = 12.sp,
                                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSel) Color.White else Color.White.copy(alpha = 0.7f)
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

            // 2. Destination Mode Selector (Existing Channel/Group/Topic vs Create New)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF17223B)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "২. কোথায় পাঠাতে চান? (চ্যানেল / গ্রুপ / টপিক)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8)
                            )

                            if (destinationMode == 0) {
                                TextButton(
                                    onClick = {
                                        if (!isLoadingDialogs) {
                                            scope.launch {
                                                isLoadingDialogs = true
                                                statusMessage = "🔄 টেলিগ্রাম থেকে আপনার সকল চ্যানেল, গ্রুপ ও ফোরাম টপিক সিঙ্ক করা হচ্ছে..."
                                                val refreshed = repository.fetchTelegramDialogs(triggerRemoteRefresh = true)
                                                dialogs = refreshed
                                                if (selectedDialog == null && refreshed.isNotEmpty()) {
                                                    selectedDialog = refreshed.firstOrNull()
                                                }
                                                isLoadingDialogs = false
                                                statusMessage = if (refreshed.isNotEmpty()) {
                                                    "✅ মোট ${refreshed.size}টি চ্যানেল ও গ্রুপ লোড হয়েছে!"
                                                } else {
                                                    "ℹ️ GitHub রানে চ্যানেল লিস্ট সিঙ্ক হচ্ছে, কয়েক সেকেন্ড পর আবার রিফ্রেশ দিন।"
                                                }
                                            }
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    if (isLoadingDialogs) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp,
                                            color = Color(0xFF38BDF8)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Refresh Channels",
                                            tint = Color(0xFF38BDF8),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Text(
                                        text = "লিস্ট রিফ্রেশ",
                                        fontSize = 12.sp,
                                        color = Color(0xFF38BDF8)
                                    )
                                }
                            }
                        }

                        // Mode Tabs
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (destinationMode == 0) Color(0xFF0284C7) else Color(0xFF0F172A),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { destinationMode = 0 }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Forum,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "আমার চ্যানেল / গ্রুপ (${dialogs.size})",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (destinationMode == 1) Color(0xFF7C3AED) else Color(0xFF0F172A),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { destinationMode = 1 }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AddCircleOutline,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "নতুন চ্যানেল / গ্রুপ খুলুন",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        if (destinationMode == 0) {
                            // Filter chips (All, Channels, Groups, Forum Groups with Topics)
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val filterOptions = listOf(
                                    "ALL" to "সব (${dialogs.size})",
                                    "CHANNEL" to "📢 চ্যানেল (${dialogs.count { it.isChannel }})",
                                    "GROUP" to "👥 গ্রুপ (${dialogs.count { it.isMegagroup && !it.isForum }})",
                                    "FORUM" to "📌 টপিক গ্রুপ (${dialogs.count { it.isForum || it.topics.isNotEmpty() }})"
                                )
                                items(filterOptions) { (key, label) ->
                                    val selected = dialogTypeFilter == key
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = if (selected) Color(0xFF38BDF8).copy(alpha = 0.22f) else Color(0xFF0F172A),
                                        modifier = Modifier
                                            .border(
                                                width = 1.dp,
                                                color = if (selected) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.12f),
                                                shape = RoundedCornerShape(20.dp)
                                            )
                                            .clickable { dialogTypeFilter = key }
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 11.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (selected) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.75f),
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }

                            // Search Box
                            OutlinedTextField(
                                value = dialogFilterQuery,
                                onValueChange = { dialogFilterQuery = it },
                                placeholder = {
                                    Text(
                                        "চ্যানেল বা গ্রুপের নাম লিখে খুঁজুন...",
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.45f)
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFF38BDF8),
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                    focusedContainerColor = Color(0xFF0F172A),
                                    unfocusedContainerColor = Color(0xFF0F172A)
                                ),
                                shape = RoundedCornerShape(10.dp)
                            )

                            if (isLoadingDialogs && dialogs.isEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 14.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = Color(0xFF38BDF8)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "টেলিগ্রাম থেকে আপনার চ্যানেল ও গ্রুপ লিস্ট লোড হচ্ছে...",
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                }
                            } else if (filteredDialogs.isEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF0F172A))
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = if (dialogs.isEmpty()) {
                                            "আপনার টেলিগ্রাম চ্যানেল ও গ্রুপের তালিকা এখনো সিঙ্ক হয়নি। উপরে 'লিস্ট রিফ্রেশ' বাটনে ট্যাপ করুন অথবা নিচে সরাসরি চ্যানেল/গ্রুপের Chat ID বা @username দিন।"
                                        } else {
                                            "এই ফিল্টারে কোনো চ্যানেল বা গ্রুপ পাওয়া যায়নি।"
                                        },
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.75f)
                                    )
                                }
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    filteredDialogs.take(25).forEach { dialog ->
                                        val isSelected = selectedDialog?.id == dialog.id && manualChatIdInput.isBlank()
                                        val badgeText = when {
                                            dialog.isForum || dialog.topics.isNotEmpty() -> "📌 ফোরাম গ্রুপ (${dialog.topics.size} টপিক)"
                                            dialog.isMegagroup -> "👥 গ্রুপ"
                                            else -> "📢 চ্যানেল"
                                        }

                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(
                                                    if (isSelected) Color(0xFF0284C7).copy(alpha = 0.22f)
                                                    else Color(0xFF0F172A)
                                                )
                                                .border(
                                                    width = if (isSelected) 1.5.dp else 1.dp,
                                                    color = if (isSelected) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.1f),
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                                .clickable {
                                                    selectedDialog = dialog
                                                    manualChatIdInput = ""
                                                    selectedTopic = dialog.topics.firstOrNull()
                                                }
                                                .padding(12.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    RadioButton(
                                                        selected = isSelected,
                                                        onClick = {
                                                            selectedDialog = dialog
                                                            manualChatIdInput = ""
                                                            selectedTopic = dialog.topics.firstOrNull()
                                                        },
                                                        colors = RadioButtonDefaults.colors(
                                                            selectedColor = Color(0xFF38BDF8),
                                                            unselectedColor = Color.White.copy(alpha = 0.5f)
                                                        )
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Column {
                                                        Text(
                                                            text = dialog.title,
                                                            fontSize = 14.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color.White,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Text(
                                                            text = buildString {
                                                                append(badgeText)
                                                                if (dialog.username.isNotBlank()) {
                                                                    append(" • @${dialog.username}")
                                                                }
                                                                append(" • ID: ${dialog.id}")
                                                            },
                                                            fontSize = 11.sp,
                                                            color = Color.White.copy(alpha = 0.65f)
                                                        )
                                                    }
                                                }
                                            }

                                            // Show Forum Topics if this group is selected and has topics (or is a Forum)
                                            if (isSelected && (dialog.isForum || dialog.topics.isNotEmpty() || dialog.isMegagroup)) {
                                                Spacer(modifier = Modifier.height(10.dp))
                                                HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                                                Spacer(modifier = Modifier.height(8.dp))

                                                Text(
                                                    text = if (dialog.topics.isNotEmpty()) {
                                                        "📌 এই গ্রুপের কোন টপিকে (Topic) পাঠাতে চান সিলেক্ট করুন:"
                                                    } else {
                                                        "📌 নির্দিষ্ট টপিকে পাঠাতে চাইলে Topic ID দিন (সাধারণ গ্রুপ হলে খালি রাখুন):"
                                                    },
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color(0xFF7DD3FC)
                                                )

                                                if (dialog.topics.isNotEmpty()) {
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    LazyRow(
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        item {
                                                            val generalSelected = selectedTopic == null && manualTopicIdInput.isBlank()
                                                            FilterChip(
                                                                selected = generalSelected,
                                                                onClick = {
                                                                    selectedTopic = null
                                                                    manualTopicIdInput = ""
                                                                },
                                                                label = {
                                                                    Text(
                                                                        "💬 General / মূল চ্যাট",
                                                                        fontSize = 12.sp
                                                                    )
                                                                },
                                                                colors = FilterChipDefaults.filterChipColors(
                                                                    selectedContainerColor = Color(0xFF0284C7),
                                                                    selectedLabelColor = Color.White,
                                                                    containerColor = Color(0xFF1E293B),
                                                                    labelColor = Color.White.copy(alpha = 0.8f)
                                                                )
                                                            )
                                                        }
                                                        items(dialog.topics) { topic ->
                                                            val topicSelected = selectedTopic?.id == topic.id && manualTopicIdInput.isBlank()
                                                            FilterChip(
                                                                selected = topicSelected,
                                                                onClick = {
                                                                    selectedTopic = topic
                                                                    manualTopicIdInput = ""
                                                                },
                                                                label = {
                                                                    Text(
                                                                        "📌 ${topic.title} (#${topic.id})",
                                                                        fontSize = 12.sp,
                                                                        fontWeight = if (topicSelected) FontWeight.Bold else FontWeight.Normal
                                                                    )
                                                                },
                                                                colors = FilterChipDefaults.filterChipColors(
                                                                    selectedContainerColor = Color(0xFF0D9488),
                                                                    selectedLabelColor = Color.White,
                                                                    containerColor = Color(0xFF1E293B),
                                                                    labelColor = Color.White.copy(alpha = 0.85f)
                                                                )
                                                            )
                                                        }
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(6.dp))
                                                OutlinedTextField(
                                                    value = manualTopicIdInput,
                                                    onValueChange = { manualTopicIdInput = it.filter { ch -> ch.isDigit() } },
                                                    label = {
                                                        Text(
                                                            "কাস্টম Topic ID (ঐচ্ছিক, যেমন: 12)",
                                                            fontSize = 11.sp
                                                        )
                                                    },
                                                    singleLine = true,
                                                    modifier = Modifier.fillMaxWidth(),
                                                    colors = OutlinedTextFieldDefaults.colors(
                                                        focusedTextColor = Color.White,
                                                        unfocusedTextColor = Color.White,
                                                        focusedBorderColor = Color(0xFF38BDF8),
                                                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                                                    ),
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Manual Chat ID / @username fallback input
                            OutlinedTextField(
                                value = manualChatIdInput,
                                onValueChange = { manualChatIdInput = it },
                                label = {
                                    Text(
                                        "অথবা সরাসরি চ্যানেল/গ্রুপের Chat ID বা @username দিন (যেমন: -100123456789)",
                                        fontSize = 11.sp
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFF38BDF8),
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                    focusedContainerColor = Color(0xFF0F172A),
                                    unfocusedContainerColor = Color(0xFF0F172A)
                                ),
                                shape = RoundedCornerShape(10.dp)
                            )
                        } else {
                            // Mode 1: Create a new Channel or Group automatically
                            OutlinedTextField(
                                value = newChannelTitle,
                                onValueChange = { newChannelTitle = it },
                                label = { Text("নতুন টেলিগ্রাম চ্যানেল বা গ্রুপের নাম") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFF38BDF8),
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                                    focusedContainerColor = Color(0xFF0F172A),
                                    unfocusedContainerColor = Color(0xFF0F172A)
                                ),
                                shape = RoundedCornerShape(10.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                FilterChip(
                                    selected = !createNewSupergroup,
                                    onClick = { createNewSupergroup = false },
                                    label = { Text("📢 নতুন ব্রডকাস্ট চ্যানেল খুলুন", fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF7C3AED),
                                        selectedLabelColor = Color.White,
                                        containerColor = Color(0xFF0F172A),
                                        labelColor = Color.White.copy(alpha = 0.75f)
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChip(
                                    selected = createNewSupergroup,
                                    onClick = { createNewSupergroup = true },
                                    label = { Text("👥 নতুন সুপারগ্রুপ খুলুন", fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF7C3AED),
                                        selectedLabelColor = Color.White,
                                        containerColor = Color(0xFF0F172A),
                                        labelColor = Color.White.copy(alpha = 0.75f)
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // Status / Feedback Banner
            if (!statusMessage.isNullOrBlank()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSuccessState) Color(0xFF064E3B) else Color(0xFF1E293B)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = statusMessage.orEmpty(),
                            fontSize = 13.sp,
                            color = Color.White,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            // Submit / Dispatch Button
            item {
                Button(
                    onClick = {
                        if (isDispatching) return@Button
                        if (lessonsToSend.isEmpty()) {
                            Toast.makeText(context, "পাঠানোর মতো কোনো ক্লাস পাওয়া যায়নি!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        val targetChatId = if (destinationMode == 0) {
                            manualChatIdInput.trim().ifBlank { selectedDialog?.id.orEmpty() }
                        } else {
                            ""
                        }

                        if (destinationMode == 0 && targetChatId.isBlank()) {
                            statusMessage = "⚠️ অনুগ্রহ করে তালিকা থেকে একটি চ্যানেল/গ্রুপ সিলেক্ট করুন অথবা Chat ID দিন, কিংবা 'নতুন চ্যানেল খুলুন' অপশনটি বেছে নিন।"
                            return@Button
                        }

                        val resolvedTopicId = if (destinationMode == 0) {
                            manualTopicIdInput.trim().toIntOrNull() ?: (selectedTopic?.id ?: 0)
                        } else {
                            0
                        }
                        val resolvedTopicTitle = if (destinationMode == 0) {
                            if (manualTopicIdInput.isNotBlank()) "Topic #$manualTopicIdInput"
                            else selectedTopic?.title.orEmpty()
                        } else {
                            ""
                        }

                        val targetChannelName = if (destinationMode == 0) {
                            selectedDialog?.title?.ifBlank { targetChatId } ?: targetChatId
                        } else {
                            newChannelTitle.trim().ifBlank { title }
                        }

                        scope.launch {
                            isDispatching = true
                            isSuccessState = false
                            statusMessage = "⏳ ক্লাসের আসল ভিডিও স্ট্রিম ও লেকচার স্লাইড (PDF) লিংক প্রস্তুত করা হচ্ছে (${lessonsToSend.size}টি ক্লাস)..."

                            val (resolvedCount, bundledJson) = repository.buildBundledLessonsFromItems(lessonsToSend)

                            statusMessage = "🚀 GitHub Actions 24/7 ক্লাউড এজেন্টে আপলোড টাস্ক পাঠানো হচ্ছে..."

                            val task = AiAgentTask(
                                id = "task_${System.currentTimeMillis()}",
                                title = "$title (${lessonsToSend.size}টি ক্লাস)",
                                taskType = "COURSE_TELEGRAM_UPLOAD",
                                status = "QUEUED",
                                programId = programId,
                                programTitle = programTitle,
                                phaseId = phaseId,
                                subjectCode = subjectCode,
                                subjectTitle = subjectTitle,
                                chapterId = chapterId,
                                chapterTitle = chapterTitle,
                                videoQuality = selectedQuality,
                                includeVideos = includeVideos,
                                includeSlides = includeSlides,
                                createNewChannel = destinationMode == 1,
                                useForumTopics = destinationMode == 1 && createNewSupergroup,
                                targetChannelTitle = targetChannelName,
                                targetChatId = targetChatId,
                                targetTopicId = resolvedTopicId,
                                targetTopicTitle = resolvedTopicTitle,
                                totalItems = resolvedCount.coerceAtLeast(lessonsToSend.size),
                                completedItems = 0,
                                statusMessage = "GitHub Actions-এ ডিসপ্যাচ করা হচ্ছে..."
                            )

                            val result = repository.dispatchTaskToGitHubActions(
                                task = task,
                                bundledLessonsJson = bundledJson
                            )

                            result.onSuccess { msg ->
                                isDispatching = false
                                isSuccessState = true
                                val topicInfo = if (resolvedTopicId > 0) " (টপিক: ${resolvedTopicTitle.ifBlank { "#$resolvedTopicId" }})" else ""
                                statusMessage = "✅ সফলভাবে চালু হয়েছে! '$targetChannelName'$topicInfo-এ ${lessonsToSend.size}টি ক্লাস ব্যাকগ্রাউন্ডে আপলোড হচ্ছে। আপনি অ্যাপ বন্ধ করলেও GitHub Actions নিজে থেকে ডাউনলোড ও টেলিগ্রামে আপলোড সম্পন্ন করবে!\n$msg"
                                Toast.makeText(
                                    context,
                                    "🚀 টেলিগ্রামে আপলোড শুরু হয়েছে!",
                                    Toast.LENGTH_LONG
                                ).show()
                            }.onFailure { err ->
                                isDispatching = false
                                isSuccessState = false
                                statusMessage = "❌ ত্রুটি: ${err.message}"
                            }
                        }
                    },
                    enabled = !isDispatching,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0088CC),
                        contentColor = Color.White
                    )
                ) {
                    if (isDispatching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "পাঠানো হচ্ছে...",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        val actionLabel = buildString {
                            append("🚀 টেলিগ্রামে পাঠান (")
                            append("${lessonsToSend.size}টি ক্লাস")
                            if (includeVideos && includeSlides) append(" • ভিডিও + স্লাইড")
                            else if (includeVideos) append(" • শুধু ভিডিও")
                            else if (includeSlides) append(" • শুধু স্লাইড")
                            append(")")
                        }
                        Text(
                            text = actionLabel,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
