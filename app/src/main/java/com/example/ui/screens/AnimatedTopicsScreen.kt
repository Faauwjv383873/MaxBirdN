package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.api.TopicFullItem
import com.example.course.AnimatedLessonsViewModel
import com.example.utils.toBengaliDigits

@Composable
fun AnimatedTopicsScreen(
    chapterId: String,
    chapterName: String,
    subjectCode: String,
    viewModel: AnimatedLessonsViewModel,
    onBack: () -> Unit,
    onPlayVideo: (videoUrl: String, title: String, subjectName: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.topicsUiState.collectAsState()

    LaunchedEffect(chapterId, subjectCode) {
        if (uiState.chapterId != chapterId || uiState.topics.isEmpty()) {
            viewModel.loadAnimatedTopics(chapterId, chapterName, subjectCode)
        }
    }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFEBF3FF),
                                Color(0xFFF8FAFC),
                                Color.White
                            )
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("animated_topics_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF0F172A),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (chapterName.isNotBlank()) chapterName else uiState.chapterName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val countBn = toBengaliDigits(uiState.topics.size)
                        Text(
                            text = "$countBn টি টপিক",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color(0xFF64748B)
                        )
                    }

                    AnimatedLessonBrandLogoHeader()
                }
            }
        },
        containerColor = Color(0xFFF8FAFC),
        modifier = modifier.testTag("animated_topics_screen")
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF3B82F6),
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "এনিমেটেড টপিকসমূহ লোড হচ্ছে...",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                uiState.topics.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = uiState.errorMessage ?: "এই অধ্যায়ে কোনো এনিমেটেড টপিক পাওয়া যায়নি",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF0F172A)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.loadAnimatedTopics(chapterId, chapterName, subjectCode) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("পুনরায় চেষ্টা করুন")
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(
                            items = uiState.topics,
                            key = { index, topic -> topic.id ?: "topic_$index" }
                        ) { index, topic ->
                            AnimatedTopicItemRow(
                                topic = topic,
                                index = index,
                                onClick = {
                                    val videoUrl = topic.videos?.data?.firstOrNull { !it.playback_url.isNullOrBlank() }?.playback_url
                                        ?: topic.videos?.data?.firstOrNull()?.playback_url
                                        ?: ""
                                    val title = topic.name ?: "এনিমেটেড ক্লাস"
                                    onPlayVideo(videoUrl, title, chapterName)
                                }
                            )
                            if (index < uiState.topics.size - 1) {
                                HorizontalDivider(
                                    color = Color(0xFFE2E8F0).copy(alpha = 0.6f),
                                    thickness = 1.dp,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AnimatedTopicItemRow(
    topic: TopicFullItem,
    index: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.White,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Video Thumbnail Container
            val videoData = topic.videos?.data?.firstOrNull()
            val thumbnailUrl = videoData?.video_thumbnail_url?.firstOrNull { it.isNotBlank() } ?: ""

            Box(
                modifier = Modifier
                    .width(125.dp)
                    .height(72.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF2E1065),
                                Color(0xFF4C1D95),
                                Color(0xFF3B0764)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnailUrl.isNotBlank()) {
                    AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = topic.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Stylish Shikho artwork fallback
                    Text(
                        text = "শিক্ষা",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White.copy(alpha = 0.25f)
                    )
                }

                // Semi-transparent play circle
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = Color(0xFF4C1D95),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Right: Text Details
            Column(modifier = Modifier.weight(1f)) {
                val topicNoText = topic.no?.takeIf { it.isNotBlank() } ?: "0.${index + 1}"
                Text(
                    text = toBengaliDigits(topicNoText),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF64748B)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = topic.name ?: "ক্লাস ${(index + 1)}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
