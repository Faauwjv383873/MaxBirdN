package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.api.StudentLessonItem
import com.example.course.CourseUiState
import com.example.course.CourseViewModel
import java.text.SimpleDateFormat
import java.util.*

// Date Formatter helper
fun formatLessonDate(rawDate: String?): String {
    if (rawDate.isNullOrBlank()) return ""
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        parser.timeZone = TimeZone.getTimeZone("UTC")
        val date = parser.parse(rawDate)
        if (date != null) {
            val formatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
            formatter.timeZone = TimeZone.getTimeZone("Asia/Dhaka")
            val formatted = formatter.format(date)
            toBengaliDigits(formatted)
        } else {
            rawDate
        }
    } catch (_: Exception) {
        try {
            val simple = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(rawDate)
            if (simple != null) {
                val out = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(simple)
                toBengaliDigits(out)
            } else {
                rawDate
            }
        } catch (_: Exception) {
            rawDate
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterLessonsScreen(
    chapterId: String,
    chapterName: String,
    chapterStatus: String,
    viewModel: CourseViewModel,
    onBack: () -> Unit,
    onPlayVideo: (videoUrl: String, title: String, subjectName: String, subjectColor: String, isLive: Boolean) -> Unit,
    onOpenLessonDetail: ((lesson: StudentLessonItem) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    val subjectColor = remember(uiState.selectedSubjectColor) {
        try {
            if (uiState.selectedSubjectColor.isNotBlank()) {
                Color(android.graphics.Color.parseColor(uiState.selectedSubjectColor))
            } else {
                Color(0xFF0072EC)
            }
        } catch (_: Exception) {
            Color(0xFF0072EC)
        }
    }

    LaunchedEffect(chapterId) {
        viewModel.loadLessonsForChapter(
            chapterId = chapterId,
            chapterName = chapterName,
            chapterStatus = chapterStatus
        )
    }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = chapterName.ifBlank { "ক্লাস তালিকা" },
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (uiState.selectedSubjectTitle.isNotBlank()) {
                            Text(
                                text = uiState.selectedSubjectTitle,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = subjectColor
                            )
                        }
                    }

                    // Refresh Button
                    IconButton(
                        onClick = {
                            viewModel.loadLessonsForChapter(
                                chapterId = chapterId,
                                chapterName = chapterName,
                                chapterStatus = chapterStatus
                            )
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.testTag("chapter_lessons_screen")
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLessonsLoading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = subjectColor,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "ক্লাস ও লেকচার লোড হচ্ছে...",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                uiState.lessonsErrorMessage != null && uiState.lessons.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PlayLesson,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = uiState.lessonsErrorMessage ?: "কোনো ক্লাস পাওয়া যায়নি",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                viewModel.loadLessonsForChapter(
                                    chapterId = chapterId,
                                    chapterName = chapterName,
                                    chapterStatus = chapterStatus
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = subjectColor),
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
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Chapter Summary Header Card
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = subjectColor.copy(alpha = 0.08f),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    subjectColor.copy(alpha = 0.2f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = chapterName,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "মোট ক্লাস ও কন্টেন্ট: ${toBengaliDigits(uiState.lessons.size)}টি",
                                            fontSize = 12.sp,
                                            color = subjectColor,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }

                                    if (chapterStatus.isNotBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(20.dp),
                                            color = if (chapterStatus.equals("COMPLETED", ignoreCase = true)) {
                                                Color(0xFF10B981).copy(alpha = 0.15f)
                                            } else {
                                                subjectColor.copy(alpha = 0.15f)
                                            }
                                        ) {
                                            Text(
                                                text = if (chapterStatus.equals("COMPLETED", ignoreCase = true)) "পড়ানো শেষ" else "চলছে",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (chapterStatus.equals("COMPLETED", ignoreCase = true)) Color(0xFF10B981) else subjectColor,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (uiState.lessons.isEmpty()) {
                            item {
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SlowMotionVideo,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.size(44.dp)
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            text = "এই অধ্যায়ে কোনো ক্লাস বা ভিডিও পাওয়া যায়নি",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        } else {
                            items(
                                items = uiState.lessons,
                                key = { it.id }
                            ) { lesson ->
                                LessonCard(
                                    lesson = lesson,
                                    subjectName = uiState.selectedSubjectTitle,
                                    subjectColorHex = uiState.selectedSubjectColor,
                                    subjectColor = subjectColor,
                                    onClick = {
                                        viewModel.selectLesson(lesson)
                                        if (onOpenLessonDetail != null) {
                                            onOpenLessonDetail(lesson)
                                        } else {
                                            val videoUrl = lesson.resolvedVideoUrl
                                                ?: lesson.live_class?.resolvedVideoUrl
                                                ?: lesson.live_class?.recording_url
                                                ?: ""
                                            val title = lesson.title ?: "ক্লাস লেকচার"
                                            val isLive = lesson.live_class?.is_on_going == true ||
                                                    lesson.content_type?.contains("LIVE", ignoreCase = true) == true
                                            onPlayVideo(
                                                videoUrl,
                                                title,
                                                uiState.selectedSubjectTitle,
                                                uiState.selectedSubjectColor,
                                                isLive
                                            )
                                        }
                                    }
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
fun LessonCard(
    lesson: StudentLessonItem,
    subjectName: String,
    subjectColorHex: String,
    subjectColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLive = lesson.live_class?.is_on_going == true ||
            lesson.content_type?.contains("LIVE", ignoreCase = true) == true
    val recordingUrl = lesson.resolvedVideoUrl ?: lesson.live_class?.resolvedVideoUrl ?: lesson.live_class?.recording_url
    val hasPlayableVideo = !recordingUrl.isNullOrBlank() || isLive || !lesson.id.isNullOrBlank()

    val state = lesson.user_activity_state?.uppercase() ?: ""
    val (statusText, statusBgColor, statusTextColor) = when (state) {
        "COMPLETED", "ATTENDED" -> Triple("সম্পন্ন", Color(0xFF10B981).copy(alpha = 0.12f), Color(0xFF10B981))
        "MISSED" -> Triple("মিসড", Color(0xFFEF4444).copy(alpha = 0.12f), Color(0xFFEF4444))
        "UPCOMING" -> Triple("আসন্ন", Color(0xFF3B82F6).copy(alpha = 0.12f), Color(0xFF3B82F6))
        else -> Triple(
            if (isLive) "লাইভ" else "রেকর্ডেড",
            subjectColor.copy(alpha = 0.12f),
            subjectColor
        )
    }

    val startTimeFormatted = remember(lesson.live_class?.start_time ?: lesson.start_time) {
        formatLessonDate(lesson.live_class?.start_time ?: lesson.start_time)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Video Icon / Play Badge
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isLive) Color(0xFFEF4444).copy(alpha = 0.12f) else subjectColor.copy(alpha = 0.12f),
                modifier = Modifier.size(50.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isLive) Icons.Default.LiveTv else Icons.Default.PlayCircleFilled,
                        contentDescription = "Play",
                        tint = if (isLive) Color(0xFFEF4444) else subjectColor,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Center: Title, Date, Status Badges
            Column(modifier = Modifier.weight(1f)) {
                // Badges Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Status Badge (সম্পন্ন / মিসড / লাইভ)
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = statusBgColor
                    ) {
                        Text(
                            text = statusText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusTextColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (isLive) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFEF4444)
                        ) {
                            Text(
                                text = "🔴 LIVE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (!lesson.content_type.isNullOrBlank() && !isLive) {
                        Text(
                            text = if (lesson.content_type.contains("LiveExam", ignoreCase = true)) "এক্সাম" else "ভিডিও ক্লাস",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Lesson Title
                Text(
                    text = lesson.title ?: "ক্লাস লেকচার",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (startTimeFormatted.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = startTimeFormatted,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Play Icon Button
            Surface(
                shape = CircleShape,
                color = subjectColor,
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
