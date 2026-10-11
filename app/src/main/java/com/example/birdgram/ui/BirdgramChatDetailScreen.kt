package com.example.birdgram.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.birdgram.BirdgramChat
import com.example.birdgram.BirdgramChatType
import com.example.birdgram.BirdgramMessage
import com.example.birdgram.MessageStatus
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BirdgramChatDetailScreen(
    chat: BirdgramChat,
    messages: List<BirdgramMessage>,
    onBack: () -> Unit,
    onSendMessage: (text: String, imageUrl: String?) -> Unit
) {
    BackHandler { onBack() }

    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var inputText by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var expandedImagePreviewUrl by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
        }
    }

    // Auto-scroll to bottom on new message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            Surface(
                color = Color(0xFF0F172A),
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }

                    // Avatar
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF0284C7), Color(0xFF38BDF8))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = chat.avatarInitial,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = chat.title,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (chat.isVerified) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Verified",
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                        Text(
                            text = if (chat.type == BirdgramChatType.STUDY_GROUP)
                                "${chat.memberCount} জন শিক্ষার্থী সক্রিয়"
                            else if (chat.isOnline) "অনলাইন" else "টেলিগ্রাম ক্লাউড",
                            fontSize = 12.sp,
                            color = if (chat.isOnline) Color(0xFF34D399) else Color(0xFF94A3B8)
                        )
                    }

                    IconButton(onClick = { /* Group info or options */ }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Menu",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                }
            }
        },
        containerColor = Color(0xFF070D1B)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Message List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Topic Welcome Banner
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF1E293B).copy(alpha = 0.7f),
                            border = BorderStroke(0.8.dp, Color(0xFF38BDF8).copy(alpha = 0.25f))
                        ) {
                            Text(
                                text = if (chat.type == BirdgramChatType.STUDY_GROUP)
                                    "🔒 Telegram MTProto ক্লাউড এনক্রিপ্ট স্টাডি গ্রুপ • সব মেসেজ ও ছবি নিরাপদ"
                                else
                                    "🔒 এন্ড-টু-এন্ড এনক্রিপ্ট টেলিগ্রাম ডিরেক্ট চ্যাট",
                                fontSize = 11.5.sp,
                                color = Color(0xFF94A3B8),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                items(messages, key = { it.id }) { msg ->
                    BirdgramMessageBubble(
                        message = msg,
                        isGroup = chat.type == BirdgramChatType.STUDY_GROUP,
                        onImageClick = { url -> expandedImagePreviewUrl = url }
                    )
                }
            }

            // Image Selected Preview Banner
            if (selectedImageUri != null) {
                Surface(
                    color = Color(0xFF1E293B),
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = selectedImageUri,
                            contentDescription = "Attachment preview",
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ছবি সংযুক্ত করা হয়েছে 📷",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "মেসেজ লিখুন বা সরাসরি পাঠান",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                        IconButton(onClick = { selectedImageUri = null }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove",
                                tint = Color(0xFFEF4444)
                            )
                        }
                    }
                }
            }

            // Bottom Input Bar
            Surface(
                color = Color(0xFF0F172A),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Attachment / Photo Picker button
                    IconButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = "Attach file or photo",
                            tint = Color(0xFF38BDF8)
                        )
                    }

                    // Text Input
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = {
                            Text(
                                text = if (selectedImageUri != null) "ছবির সাথে ক্যাপশন লিখুন..." else "মেসেজ বা প্রশ্নের বিবরণ লিখুন...",
                                fontSize = 13.5.sp,
                                color = Color(0xFF64748B)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp, max = 120.dp),
                        shape = RoundedCornerShape(22.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF0284C7),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedContainerColor = Color(0xFF1E293B),
                            unfocusedContainerColor = Color(0xFF1E293B),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    // Send Button
                    val canSend = inputText.isNotBlank() || selectedImageUri != null
                    IconButton(
                        onClick = {
                            if (canSend) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                val imgUrl = selectedImageUri?.toString()
                                onSendMessage(inputText, imgUrl)
                                inputText = ""
                                selectedImageUri = null
                            }
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (canSend) Brush.linearGradient(
                                    listOf(Color(0xFF0284C7), Color(0xFF38BDF8))
                                ) else Brush.linearGradient(
                                    listOf(Color(0xFF334155), Color(0xFF475569))
                                )
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }

    // Full screen image preview dialog
    if (expandedImagePreviewUrl != null) {
        AlertDialog(
            onDismissRequest = { expandedImagePreviewUrl = null },
            confirmButton = {
                TextButton(onClick = { expandedImagePreviewUrl = null }) {
                    Text("বন্ধ করুন", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = expandedImagePreviewUrl,
                        contentDescription = "Full preview",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
            },
            containerColor = Color(0xFF0F172A)
        )
    }
}

/**
 * Message Bubble Composable (Telegram style)
 */
@Composable
private fun BirdgramMessageBubble(
    message: BirdgramMessage,
    isGroup: Boolean,
    onImageClick: (url: String) -> Unit
) {
    val isMe = message.isFromMe

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isMe) 16.dp else 4.dp,
                bottomEnd = if (isMe) 4.dp else 16.dp
            ),
            color = if (isMe) Color(0xFF0284C7) else Color(0xFF1E293B),
            border = if (!isMe) BorderStroke(0.8.dp, Color(0xFF334155)) else null,
            modifier = Modifier.widthIn(max = 290.dp)
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = 12.dp,
                    vertical = 8.dp
                )
            ) {
                // Sender Name in Groups
                if (!isMe && isGroup) {
                    Text(
                        text = message.senderName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8),
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }

                // Image if present
                if (!message.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = message.imageUrl,
                        contentDescription = "Message Image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onImageClick(message.imageUrl) },
                        contentScale = ContentScale.Crop
                    )
                    if (message.text.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }

                // Text
                if (message.text.isNotBlank()) {
                    Text(
                        text = message.text,
                        fontSize = 14.sp,
                        color = Color.White,
                        lineHeight = 19.sp
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Time and Status Indicator
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = message.timestamp,
                        fontSize = 10.5.sp,
                        color = if (isMe) Color.White.copy(alpha = 0.75f) else Color(0xFF94A3B8)
                    )

                    if (isMe) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = when (message.status) {
                                MessageStatus.READ -> Icons.Default.DoneAll
                                MessageStatus.DELIVERED -> Icons.Default.DoneAll
                                MessageStatus.SENT -> Icons.Default.Check
                                MessageStatus.SENDING -> Icons.Default.AccessTime
                            },
                            contentDescription = null,
                            tint = if (message.status == MessageStatus.READ) Color(0xFF67E8F9) else Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }
    }
}
