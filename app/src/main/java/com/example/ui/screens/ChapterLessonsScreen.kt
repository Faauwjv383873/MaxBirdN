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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.api.StudentLessonItem
import com.example.api.TopicFullItem
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
    initialTab: Int = 0,
    viewModel: CourseViewModel,
    onBack: () -> Unit,
    onPlayVideo: (videoUrl: String, title: String, subjectName: String, subjectColor: String, isLive: Boolean) -> Unit,
    onOpenLessonDetail: ((lesson: StudentLessonItem) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
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

    var selectedTab by remember(initialTab) { mutableStateOf(initialTab) }

    LaunchedEffect(chapterId) {
        val matching = uiState.chapters.firstOrNull { it.id == chapterId || it.chapter_id == chapterId }
        val altId = matching?.chapter_id?.takeIf { it != chapterId } ?: matching?.id?.takeIf { it != chapterId }
        viewModel.loadLessonsForChapter(
            chapterId = chapterId,
            altChapterId = altId,
            chapterName = chapterName,
            chapterStatus = chapterStatus
        )
        viewModel.loadAnimatedLessonsForChapter(chapterId)
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
                            viewModel.loadAnimatedLessonsForChapter(chapterId)
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

                uiState.lessons.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
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
                            text = uiState.lessonsErrorMessage ?: "এই অধ্যায়ে কোনো ক্লাস বা লেকচার পাওয়া যায়নি",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        if (!uiState.lessonsDiagnosticInfo.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "সার্ভার অনুসন্ধান তথ্য:",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = uiState.lessonsDiagnosticInfo ?: "",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                val matching = uiState.chapters.firstOrNull { it.id == chapterId || it.chapter_id == chapterId }
                                val altId = matching?.chapter_id?.takeIf { it != chapterId } ?: matching?.id?.takeIf { it != chapterId }
                                viewModel.loadLessonsForChapter(
                                    chapterId = chapterId,
                                    altChapterId = altId,
                                    chapterName = chapterName,
                                    chapterStatus = chapterStatus
                                )
                                viewModel.loadAnimatedLessonsForChapter(chapterId)
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

                        // Top Shortcuts: Animated Lessons, Practice Quiz, E-Book (Matching SubjectChaptersScreen)
                        item {
                            ChapterFeatureShortcuts(
                                selectedTab = selectedTab,
                                onTabSelected = { newTab ->
                                    selectedTab = if (selectedTab == newTab) 0 else newTab
                                    if (selectedTab == 1 && uiState.chapterAnimatedLessons.isEmpty()) {
                                        viewModel.loadAnimatedLessonsForChapter(chapterId)
                                    }
                                }
                            )
                        }

                        // Active Filter Banner if a shortcut is selected
                        if (selectedTab > 0) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = when (selectedTab) {
                                        1 -> Color(0xFF8B5CF6).copy(alpha = 0.12f)
                                        2 -> Color(0xFF10B981).copy(alpha = 0.12f)
                                        else -> Color(0xFFF59E0B).copy(alpha = 0.12f)
                                    },
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        when (selectedTab) {
                                            1 -> Color(0xFF8B5CF6).copy(alpha = 0.3f)
                                            2 -> Color(0xFF10B981).copy(alpha = 0.3f)
                                            else -> Color(0xFFF59E0B).copy(alpha = 0.3f)
                                        }
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = when (selectedTab) {
                                                    1 -> Icons.Default.SlowMotionVideo
                                                    2 -> Icons.Default.FactCheck
                                                    else -> Icons.Default.MenuBook
                                                },
                                                contentDescription = null,
                                                tint = when (selectedTab) {
                                                    1 -> Color(0xFF8B5CF6)
                                                    2 -> Color(0xFF10B981)
                                                    else -> Color(0xFFF59E0B)
                                                },
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = when (selectedTab) {
                                                    1 -> "অ্যানিমেটেড ক্লাসসমূহ (${toBengaliDigits(uiState.chapterAnimatedLessons.size)}টি)"
                                                    2 -> "প্র্যাকটিস কুইজ ও পরীক্ষা"
                                                    else -> "ই-বুক ও লেকচার নোটস"
                                                },
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = when (selectedTab) {
                                                    1 -> Color(0xFF8B5CF6)
                                                    2 -> Color(0xFF10B981)
                                                    else -> Color(0xFFD97706)
                                                }
                                            )
                                        }
                                        TextButton(
                                            onClick = { selectedTab = 0 },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text("সব ক্লাস ✕", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        if (selectedTab == 0) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp, bottom = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "ক্লাস ও পরীক্ষা তালিকা (${toBengaliDigits(uiState.lessons.size)}টি)",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
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
                                                imageVector = Icons.Default.PlayLesson,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                modifier = Modifier.size(44.dp)
                                            )
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Text(
                                                text = "এই অধ্যায়ে কোনো ক্লাস পাওয়া যায়নি",
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
                                                val isLive = lesson.isLiveNow ||
                                                        lesson.live_class?.is_on_going == true ||
                                                        lesson.isLive ||
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
                        } else if (selectedTab == 1) {
                            if (uiState.isChapterAnimationsLoading) {
                                item {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        CircularProgressIndicator(color = Color(0xFF8B5CF6), modifier = Modifier.size(28.dp))
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            "অ্যানিমেটেড ভিডিও লোড হচ্ছে...",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            } else if (uiState.chapterAnimatedLessons.isEmpty()) {
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
                                                text = "এই অধ্যায়ে কোনো অ্যানিমেটেড লেসন পাওয়া যায়নি",
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
                                    items = uiState.chapterAnimatedLessons,
                                    key = { it.id ?: "" }
                                ) { topic ->
                                    val video = topic.videos?.data?.firstOrNull { !it.playback_url.isNullOrBlank() }
                                    AnimatedLessonCard(
                                        topic = topic,
                                        subjectColor = Color(0xFF8B5CF6),
                                        onClick = {
                                            val videoUrl = video?.playback_url ?: ""
                                            val title = "${topic.no ?: ""} ${topic.name ?: "অ্যানিমেটেড লেসন"}".trim()
                                            onPlayVideo(
                                                videoUrl,
                                                title,
                                                uiState.selectedSubjectTitle,
                                                uiState.selectedSubjectColor,
                                                false
                                            )
                                        }
                                    )
                                }
                            }
                        } else if (selectedTab == 2) {
                            val examLessons = uiState.lessons.filter { it.isExam }
                            if (examLessons.isEmpty()) {
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
                                                imageVector = Icons.Default.FactCheck,
                                                contentDescription = null,
                                                tint = Color(0xFF10B981).copy(alpha = 0.6f),
                                                modifier = Modifier.size(44.dp)
                                            )
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Text(
                                                text = "এই অধ্যায়ে বর্তমানে কোনো প্র্যাকটিস কুইজ পাওয়া যায়নি",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            } else {
                                items(items = examLessons, key = { it.id }) { examLesson ->
                                    LessonCard(
                                        lesson = examLesson,
                                        subjectName = uiState.selectedSubjectTitle,
                                        subjectColorHex = uiState.selectedSubjectColor,
                                        subjectColor = Color(0xFF10B981),
                                        onClick = {
                                            viewModel.selectLesson(examLesson)
                                            if (onOpenLessonDetail != null) {
                                                onOpenLessonDetail(examLesson)
                                            }
                                        }
                                    )
                                }
                            }
                        } else {
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
                                            imageVector = Icons.Default.MenuBook,
                                            contentDescription = null,
                                            tint = Color(0xFFF59E0B).copy(alpha = 0.6f),
                                            modifier = Modifier.size(44.dp)
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            text = "এই অধ্যায়ের লেকচার শিট ও ই-বুক শীঘ্রই যুক্ত করা হবে",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
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
    val isExam = lesson.isExam
    val isUpcoming = lesson.isUpcoming
    val isLive = lesson.isLiveNow || lesson.isLive
    val isRecorded = lesson.isRecorded

    val state = lesson.user_activity_state?.uppercase() ?: ""
    val (statusText, statusBgColor, statusTextColor) = when {
        isLive -> Triple("🔴 লাইভ চলছে", Color(0xFFEF4444).copy(alpha = 0.15f), Color(0xFFEF4444))
        isExam && isUpcoming -> Triple("আপকামিং", Color(0xFF3B82F6).copy(alpha = 0.12f), Color(0xFF3B82F6))
        isExam -> Triple("পরীক্ষা", Color(0xFFF59E0B).copy(alpha = 0.12f), Color(0xFFD97706))
        isUpcoming -> Triple("আপকামিং", Color(0xFF3B82F6).copy(alpha = 0.12f), Color(0xFF3B82F6))
        state == "COMPLETED" || state == "ATTENDED" -> Triple("সম্পন্ন", Color(0xFF10B981).copy(alpha = 0.12f), Color(0xFF10B981))
        state == "MISSED" -> Triple("মিসড", Color(0xFFEF4444).copy(alpha = 0.12f), Color(0xFFEF4444))
        else -> Triple(
            when {
                isRecorded -> "রেকর্ডেড"
                else -> "ক্লাস"
            },
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
            // Left: Icon
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = when {
                    isExam -> Color(0xFFF59E0B).copy(alpha = 0.12f)
                    isLive -> Color(0xFFEF4444).copy(alpha = 0.12f)
                    else -> subjectColor.copy(alpha = 0.12f)
                },
                modifier = Modifier.size(50.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = when {
                            isExam -> Icons.Default.Assignment
                            isLive -> Icons.Default.LiveTv
                            else -> Icons.Default.PlayCircleFilled
                        },
                        contentDescription = "Icon",
                        tint = when {
                            isExam -> Color(0xFFD97706)
                            isLive -> Color(0xFFEF4444)
                            else -> subjectColor
                        },
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
                    // Status Badge
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

                    val typeLabel = when {
                        isExam -> "চ্যাপ্টার এক্সাম"
                        isUpcoming -> "লেকচার ক্লাস"
                        isLive -> "লাইভ ক্লাস"
                        isRecorded -> "রেকর্ড করা ক্লাস"
                        else -> "লেকচার ক্লাস"
                    }
                    Text(
                        text = typeLabel,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = when {
                            isExam -> Color(0xFFD97706)
                            isLive -> Color(0xFFEF4444)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        }
                    )
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

@Composable
fun AnimatedLessonCard(
    topic: com.example.api.TopicFullItem,
    subjectColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
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
            // Left: Play Circle Icon with Subject Color
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = subjectColor.copy(alpha = 0.12f),
                modifier = Modifier.size(50.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.SlowMotionVideo,
                        contentDescription = "Animated Lesson",
                        tint = subjectColor,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Center: Topic info
            Column(modifier = Modifier.weight(1f)) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF8B5CF6).copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "অ্যানিমেটেড লেসন",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF8B5CF6),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${topic.no ?: ""} ${topic.name ?: "অধ্যায় কন্টেন্ট"}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (!topic.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = topic.description,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Right: Play button with subject color
            IconButton(
                onClick = onClick,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = subjectColor.copy(alpha = 0.1f)
                ),
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play Animation",
                    tint = subjectColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun ChapterFeatureShortcuts(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ChapterShortcutButton(
            title = "অ্যানিমেটেড লেসন",
            icon = Icons.Default.SlowMotionVideo,
            color = Color(0xFF8B5CF6),
            isSelected = selectedTab == 1,
            modifier = Modifier.weight(1f),
            onClick = { onTabSelected(1) }
        )
        ChapterShortcutButton(
            title = "প্র্যাকটিস কুইজ",
            icon = Icons.Default.FactCheck,
            color = Color(0xFF10B981),
            isSelected = selectedTab == 2,
            modifier = Modifier.weight(1f),
            onClick = { onTabSelected(2) }
        )
        ChapterShortcutButton(
            title = "ই-বুক",
            icon = Icons.Default.MenuBook,
            color = Color(0xFFF59E0B),
            isSelected = selectedTab == 3,
            modifier = Modifier.weight(1f),
            onClick = { onTabSelected(3) }
        )
    }
}

@Composable
fun ChapterShortcutButton(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) color.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) color else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        shadowElevation = if (isSelected) 2.dp else 1.dp,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = if (isSelected) color.copy(alpha = 0.25f) else color.copy(alpha = 0.12f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = color,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isSelected) color else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
