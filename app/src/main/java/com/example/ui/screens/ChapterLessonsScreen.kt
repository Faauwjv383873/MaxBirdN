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
import com.example.utils.toBengaliDigits
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.api.StudentLessonItem
import com.example.api.TopicFullItem
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import com.example.auth.SessionManager
import com.example.course.CourseUiState
import com.example.course.CourseViewModel
import com.example.utils.AcademicLocalizationUtils
import com.example.utils.ClassTypeUtils
import com.example.utils.EmptyQuestionsCard
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
    subjectCode: String = "",
    subjectTitle: String = "",
    subjectColorHex: String? = null,
    viewModel: CourseViewModel,
    onBack: () -> Unit,
    onPlayVideo: (videoUrl: String, title: String, subjectName: String, subjectColor: String, isLive: Boolean) -> Unit,
    onOpenLessonDetail: ((lesson: StudentLessonItem) -> Unit)? = null,
    onNavigateToAnimatedTopics: ((chapterId: String, chapterName: String, subjectCode: String) -> Unit)? = null,
    onNavigateToPracticeQuiz: ((subjectCode: String, subjectTitle: String, subjectColor: String?, chapterId: String, chapterName: String) -> Unit)? = null,
    onNavigateToExam: ((sessionId: String, lessonId: String, title: String, chapterName: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    var lessonCompletionCounter by remember { mutableIntStateOf(0) }
    val uiState by viewModel.uiState.collectAsState()

    val effectiveSubjectCode = subjectCode.ifBlank { uiState.selectedSubjectCode }
    val effectiveSubjectTitle = subjectTitle.ifBlank { uiState.selectedSubjectTitle }
    val effectiveSubjectColor = subjectColorHex?.ifBlank { null } ?: uiState.selectedSubjectColor

    val subjectColor = remember(effectiveSubjectColor) {
        try {
            if (effectiveSubjectColor.isNotBlank()) {
                Color(android.graphics.Color.parseColor(effectiveSubjectColor))
            } else {
                Color(0xFF0072EC)
            }
        } catch (_: Exception) {
            Color(0xFF0072EC)
        }
    }

    LaunchedEffect(chapterId) {
        val matching = uiState.chapters.firstOrNull { it.id == chapterId || it.chapter_id == chapterId }
        val altId = matching?.chapter_id?.takeIf { it != chapterId } ?: matching?.id?.takeIf { it != chapterId }
        viewModel.loadLessonsForChapter(
            chapterId = chapterId,
            altChapterId = altId,
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
                        val errorMsg = uiState.lessonsErrorMessage
                        val displayMsg = if (errorMsg.isNullOrBlank() || errorMsg.contains("আইডি") || errorMsg.contains("http") || errorMsg.contains("HTTP") || errorMsg.contains("এরর") || errorMsg.contains("Error")) {
                            "এই অধ্যায়ে কোনো ক্লাস বা লেকচার পাওয়া যায়নি"
                        } else {
                            errorMsg
                        }
                        Text(
                            text = displayMsg,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
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
                        // Top Shortcuts: Animated Lessons, Practice Quiz, E-Book
                        item {
                            ChapterFeatureShortcuts(
                                selectedTab = 0,
                                onTabSelected = { tab ->
                                    if (tab == 1) {
                                        // DISABLED: Animated lessons navigation disabled per user request
                                    } else if (tab == 2) {
                                        onNavigateToPracticeQuiz?.invoke(
                                            effectiveSubjectCode,
                                            effectiveSubjectTitle,
                                            effectiveSubjectColor,
                                            chapterId,
                                            chapterName
                                        )
                                    }
                                }
                            )
                        }

                        // Section Header
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

                        items(
                            items = uiState.lessons,
                            key = { it.id }
                        ) { lesson ->
                            val isCompleted = sessionManager.isLessonCompleted(lesson.id) ||
                                    lesson.user_activity_state.equals("COMPLETED", ignoreCase = true) ||
                                    lesson.user_activity_state.equals("ATTENDED", ignoreCase = true)
                            // Reference counter so recomposition occurs when marked completed
                            val currentCompletionCounter = lessonCompletionCounter

                            LessonCard(
                                lesson = lesson,
                                isCompleted = isCompleted,
                                subjectName = uiState.selectedSubjectTitle,
                                subjectColorHex = uiState.selectedSubjectColor,
                                subjectColor = subjectColor,
                                onClick = {
                                    sessionManager.markLessonCompleted(lesson.id)
                                    lessonCompletionCounter++
                                    viewModel.selectLesson(lesson)

                                    val isExamLesson = lesson.isExam ||
                                            lesson.content_type?.contains("EXAM", ignoreCase = true) == true ||
                                            lesson.class_type?.contains("EXAM", ignoreCase = true) == true

                                    if (isExamLesson && onNavigateToExam != null) {
                                        val sessionId = lesson.session_id?.takeIf { it.isNotBlank() }
                                            ?: lesson.live_class?.session_id?.takeIf { it.isNotBlank() }
                                            ?: lesson.content_id?.takeIf { it.isNotBlank() }
                                            ?: lesson.id
                                        val formattedTitle = ClassTypeUtils.formatLessonTitle(lesson.title)
                                        onNavigateToExam(sessionId, lesson.id, formattedTitle, chapterName)
                                    } else if (onOpenLessonDetail != null) {
                                        onOpenLessonDetail(lesson)
                                    } else {
                                        val videoUrl = lesson.resolvedVideoUrl
                                            ?: lesson.live_class?.resolvedVideoUrl
                                            ?: lesson.live_class?.recording_url
                                            ?: ""
                                        val title = ClassTypeUtils.formatLessonTitle(lesson.title)
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
                }
            }
        }
    }
}

@Composable
fun LessonCard(
    lesson: StudentLessonItem,
    isCompleted: Boolean = false,
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
    val isCompletedEffective = isCompleted || state == "COMPLETED" || state == "ATTENDED"

    val (statusText, statusBgColor, statusTextColor) = when {
        isCompletedEffective -> Triple("সম্পন্ন", Color(0xFF10B981).copy(alpha = 0.14f), Color(0xFF059669))
        isLive -> Triple("🔴 লাইভ চলছে", Color(0xFFEF4444).copy(alpha = 0.15f), Color(0xFFDC2626))
        isExam && isUpcoming -> Triple("আপকামিং", Color(0xFF3B82F6).copy(alpha = 0.12f), Color(0xFF2563EB))
        isExam -> Triple("পরীক্ষা", Color(0xFFF59E0B).copy(alpha = 0.14f), Color(0xFFD97706))
        isUpcoming -> Triple("আপকামিং", Color(0xFF3B82F6).copy(alpha = 0.12f), Color(0xFF2563EB))
        else -> Triple("রেকর্ড ক্লাস", Color(0xFF6366F1).copy(alpha = 0.12f), Color(0xFF4F46E5))
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
                color = if (isCompletedEffective) Color(0xFF10B981).copy(alpha = 0.25f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
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
                    isCompletedEffective -> Color(0xFF10B981).copy(alpha = 0.14f)
                    isExam -> Color(0xFFF59E0B).copy(alpha = 0.12f)
                    isLive -> Color(0xFFEF4444).copy(alpha = 0.12f)
                    else -> subjectColor.copy(alpha = 0.12f)
                },
                modifier = Modifier.size(50.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = when {
                            isCompletedEffective -> Icons.Default.CheckCircle
                            isExam -> Icons.Default.Assignment
                            isLive -> Icons.Default.LiveTv
                            else -> Icons.Default.PlayCircleFilled
                        },
                        contentDescription = "Icon",
                        tint = when {
                            isCompletedEffective -> Color(0xFF059669)
                            isExam -> Color(0xFFD97706)
                            isLive -> Color(0xFFEF4444)
                            else -> subjectColor
                        },
                        modifier = Modifier.size(26.dp)
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

                    // Class Type Badge in Bengali (ডাউট ক্লাস, লেকচার ক্লাস, ওরিয়েনটেশন ক্লাস, এক্সট্রা ক্লাস, সলভিং ক্লাস, কনসেপ্ট ক্লাস, অ্যানালাইসিস ক্লাস)
                    val classTypeBadge = remember(lesson) {
                        ClassTypeUtils.getClassTypeBadgeStyle(lesson)
                    }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = classTypeBadge.backgroundColor
                    ) {
                        Text(
                            text = classTypeBadge.label,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = classTypeBadge.textColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(5.dp))

                // Lesson Title (formatted with Bengali class type words and digits)
                val formattedTitle = remember(lesson.title) {
                    ClassTypeUtils.formatLessonTitle(lesson.title)
                }
                Text(
                    text = formattedTitle,
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
            title = AcademicLocalizationUtils.translateContentType("Video"),
            icon = Icons.Default.SlowMotionVideo,
            color = Color(0xFF8B5CF6),
            isSelected = selectedTab == 1,
            modifier = Modifier.weight(1f),
            onClick = { onTabSelected(1) }
        )
        ChapterShortcutButton(
            title = AcademicLocalizationUtils.translateContentType("Exam"),
            icon = Icons.Default.FactCheck,
            color = Color(0xFF10B981),
            isSelected = selectedTab == 2,
            modifier = Modifier.weight(1f),
            onClick = { onTabSelected(2) }
        )
        ChapterShortcutButton(
            title = AcademicLocalizationUtils.translateContentType("SmartNotes"),
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
