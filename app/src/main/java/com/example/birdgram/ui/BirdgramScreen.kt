package com.example.birdgram.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.birdgram.BirdgramChat
import com.example.birdgram.BirdgramChatType
import com.example.birdgram.BirdgramRepository
import com.example.birdgram.TelegramAuthStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BirdgramScreen(
    repository: BirdgramRepository,
    modifier: Modifier = Modifier
) {
    val chats by repository.chats.collectAsState()
    val messagesMap by repository.messages.collectAsState()
    val authStatus by repository.authStatus.collectAsState()

    var activeChatId by remember { mutableStateOf<String?>(null) }
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearchField by remember { mutableStateOf(false) }
    var showConnectSheet by remember { mutableStateOf(false) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }

    val activeChat = chats.find { it.id == activeChatId }

    // If viewing an active conversation
    if (activeChat != null) {
        val chatMessages = messagesMap[activeChat.id] ?: emptyList()
        BirdgramChatDetailScreen(
            chat = activeChat,
            messages = chatMessages,
            onBack = { activeChatId = null },
            onSendMessage = { text, imageUrl ->
                repository.sendMessage(activeChat.id, text, imageUrl)
            }
        )
        return
    }

    // Categories
    val categories = listOf("সব চ্যাট", "স্টাডি গ্রুপ", "ব্যক্তিগত চ্যাট")

    val filteredChats = remember(chats, selectedCategoryIndex, searchQuery) {
        chats.filter { chat ->
            val matchesCategory = when (selectedCategoryIndex) {
                1 -> chat.type == BirdgramChatType.STUDY_GROUP
                2 -> chat.type == BirdgramChatType.DIRECT
                else -> true
            }
            val matchesSearch = if (searchQuery.isBlank()) true else {
                chat.title.contains(searchQuery, ignoreCase = true) ||
                        chat.lastMessage.contains(searchQuery, ignoreCase = true) ||
                        (chat.subjectTag?.contains(searchQuery, ignoreCase = true) == true)
            }
            matchesCategory && matchesSearch
        }
    }

    Scaffold(
        topBar = {
            Surface(
                color = Color(0xFF0F172A),
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Telegram Styled Birdgram Logo
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(Color(0xFF0284C7), Color(0xFF38BDF8))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(19.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Birdgram",
                                        fontSize = 19.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF0284C7).copy(alpha = 0.25f),
                                        border = BorderStroke(0.6.dp, Color(0xFF38BDF8).copy(alpha = 0.5f))
                                    ) {
                                        Text(
                                            text = "Telegram API",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF38BDF8),
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "ফ্রি স্টাডি চ্যাট ও ডাউট সমাধান",
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { showSearchField = !showSearchField }) {
                                Icon(
                                    imageVector = if (showSearchField) Icons.Default.Close else Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = Color(0xFF94A3B8)
                                )
                            }

                            IconButton(onClick = { showCreateGroupDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.GroupAdd,
                                    contentDescription = "Create Group",
                                    tint = Color(0xFF38BDF8)
                                )
                            }
                        }
                    }

                    // Search input field
                    if (showSearchField) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("গ্রুপ বা সহপাঠী খুঁজুন...", fontSize = 13.sp, color = Color(0xFF64748B)) },
                            leadingIcon = { Icon(Icons.Default.Search, null, tint = Color(0xFF38BDF8)) },
                            trailingIcon = {
                                if (searchQuery.isNotBlank()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Clear, null, tint = Color(0xFF94A3B8))
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF0284C7),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedContainerColor = Color(0xFF1E293B),
                                unfocusedContainerColor = Color(0xFF1E293B),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateGroupDialog = true },
                containerColor = Color(0xFF0284C7),
                contentColor = Color.White,
                shape = RoundedCornerShape(18.dp),
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                modifier = Modifier
                    .padding(bottom = 76.dp)
                    .offset(y = (-6).dp)
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("নতুন স্টাডি গ্রুপ", fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = Color(0xFF070D1B),
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Telegram Status Banner
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { showConnectSheet = true },
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF0F172A),
                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Telegram Cloud Server Sync",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981))
                            )
                        }
                        Text(
                            text = "সব চ্যাট টেলিগ্রাম সার্ভারে সংরক্ষিত • জিরো ডাটাবেজ বিল",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Details",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Categories Row
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories.size) { index ->
                    val isSelected = selectedCategoryIndex == index
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategoryIndex = index },
                        label = {
                            Text(
                                text = categories[index],
                                fontSize = 12.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF0284C7),
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFF1E293B),
                            labelColor = Color(0xFF94A3B8)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = Color(0xFF334155),
                            selectedBorderColor = Color(0xFF38BDF8)
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Chat List
            if (filteredChats.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = null,
                            tint = Color(0xFF475569),
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "কোনো চ্যাট পাওয়া যায়নি",
                            fontSize = 14.sp,
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 120.dp, top = 6.dp)
                ) {
                    items(filteredChats, key = { it.id }) { chat ->
                        BirdgramChatCard(
                            chat = chat,
                            onClick = { activeChatId = chat.id }
                        )
                        HorizontalDivider(
                            color = Color(0xFF1E293B).copy(alpha = 0.6f),
                            thickness = 0.8.dp,
                            modifier = Modifier.padding(start = 74.dp, end = 16.dp)
                        )
                    }
                }
            }
        }
    }

    // Connect Bottom Sheet
    if (showConnectSheet) {
        BirdgramConnectBottomSheet(
            authStatus = authStatus,
            onDismiss = { showConnectSheet = false },
            onRequestCode = { phone ->
                repository.requestTelegramCode(phone) {}
            },
            onVerifyCode = { code ->
                repository.verifyTelegramCode(code) {
                    showConnectSheet = false
                }
            }
        )
    }

    // Create Group Dialog
    if (showCreateGroupDialog) {
        CreateStudyGroupDialog(
            onDismiss = { showCreateGroupDialog = false },
            onCreate = { title, subject, msg ->
                repository.createStudyGroup(title, subject, msg)
                showCreateGroupDialog = false
            }
        )
    }
}

/**
 * Single Chat Item Row
 */
@Composable
private fun BirdgramChatCard(
    chat: BirdgramChat,
    onClick: () -> Unit
) {
    Surface(
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            if (chat.type == BirdgramChatType.STUDY_GROUP)
                                listOf(Color(0xFF0F766E), Color(0xFF0EA5E9))
                            else
                                listOf(Color(0xFF1E3A8A), Color(0xFF0284C7))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = chat.avatarInitial,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                if (chat.isOnline) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981))
                            .border(2.dp, Color(0xFF070D1B), CircleShape)
                            .align(Alignment.BottomEnd)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = chat.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (chat.isVerified) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    Text(
                        text = chat.lastMessageTime,
                        fontSize = 11.sp,
                        color = if (chat.unreadCount > 0) Color(0xFF38BDF8) else Color(0xFF64748B)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = chat.lastMessage,
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (chat.unreadCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF0284C7),
                            modifier = Modifier.size(20.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = chat.unreadCount.toString(),
                                    fontSize = 10.5.sp,
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

/**
 * Dialog to create a new Study Group
 */
@Composable
private fun CreateStudyGroupDialog(
    onDismiss: () -> Unit,
    onCreate: (title: String, subject: String, initialMsg: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("পদার্থবিজ্ঞান") }
    var initialMsg by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        title = {
            Text("নতুন স্টাডি গ্রুপ তৈরি করুন", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("গ্রুপের নাম") },
                    placeholder = { Text("যেমন: HSC '27 Math Discussion") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF475569),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("বিষয় বা টপিক") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF475569),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                OutlinedTextField(
                    value = initialMsg,
                    onValueChange = { initialMsg = it },
                    label = { Text("প্রথম প্রশ্ন বা শুভেচ্ছা মেসেজ") },
                    placeholder = { Text("সবাইকে স্বাগতম! এখানে পড়াশোনা নিয়ে আলোচনা হবে।") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF475569),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (title.isNotBlank()) onCreate(title, subject, initialMsg) },
                enabled = title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
            ) {
                Text("তৈরি করুন", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("বাতিল", color = Color(0xFF94A3B8))
            }
        }
    )
}
