package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
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
import com.example.api.AcademicChapterItem
import com.example.api.PhaseItem
import com.example.api.StudentLessonItem
import com.example.course.CourseUiState
import com.example.course.CourseViewModel
import com.example.utils.AcademicLocalizationUtils
import com.example.utils.toBengaliDigits
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectChaptersScreen(
    subjectCode: String,
    subjectTitle: String,
    subjectColorHex: String?,
    viewModel: CourseViewModel,
    onBack: () -> Unit,
    onChapterClick: (chapterId: String, chapterName: String, chapterStatus: String, initialTab: Int) -> Unit,
    onPlayVideo: (videoUrl: String, title: String, subjectName: String, subjectColor: String, isLive: Boolean) -> Unit,
    onNavigateToPracticeQuiz: ((subjectCode: String, subjectTitle: String, subjectColor: String?) -> Unit)? = null,
    onNavigateToSmartNotes: ((subjectCode: String, subjectTitle: String, subjectColor: String?, phaseId: String?) -> Unit)? = null,
    onNavigateToAnimatedLessons: ((subjectCode: String, subjectTitle: String, subjectColor: String?, programId: String?, phaseId: String?) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var modelTestTabSelected by remember { mutableStateOf(0) } // 0: Model Test, 1: Classes

    val subjectColor = remember(subjectColorHex) {
        try {
            if (!subjectColorHex.isNullOrBlank()) {
                Color(android.graphics.Color.parseColor(subjectColorHex))
            } else {
                Color(0xFF0072EC)
            }
        } catch (_: Exception) {
            Color(0xFF0072EC)
        }
    }

    // Load chapters & subject specific model tests on start
    LaunchedEffect(subjectCode, uiState.programId, uiState.activePhaseId) {
        viewModel.loadChaptersForSubject(
            subjectCode = subjectCode,
            subjectTitle = subjectTitle,
            subjectColor = subjectColorHex,
            phaseId = uiState.activePhaseId.ifBlank { null }
        )
    }

    // Determine if current course phase or subject uses ModelTest / Admission architecture
    val isModelTestArchitecture = remember(uiState.chapters, uiState.subjectModelTests, uiState.subjectLiveClasses, uiState.selectedPhase) {
        uiState.selectedPhase?.type?.equals("ModelTest", ignoreCase = true) == true ||
                uiState.selectedPhase?.type?.equals("Admission", ignoreCase = true) == true ||
                (uiState.chapters.isEmpty() && !uiState.isChaptersLoading && (uiState.subjectModelTests.isNotEmpty() || uiState.subjectLiveClasses.isNotEmpty()))
    }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 3.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Header Bar
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

                        // Subject Badge Dot + Subject Name
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(subjectColor)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = subjectTitle.ifBlank { "বিষয়সূচি" },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Dynamic Phase / Quarter Tabs (Shown ONLY if more than 1 phase exists)
                    if (uiState.phases.size > 1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            uiState.phases.forEach { phase ->
                                val isSelected = phase.id == uiState.activePhaseId || 
                                        (uiState.activePhaseId.isBlank() && phase == uiState.selectedPhase)

                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (isSelected) subjectColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = 1.dp,
                                        color = if (isSelected) subjectColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                    ),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .clickable {
                                            viewModel.onSelectPhaseTab(phase)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (isSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.White)
                                            )
                                        }
                                        Text(
                                            text = phase.title ?: "",
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.testTag("subject_chapters_screen")
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isChaptersLoading && uiState.isSubjectModelTestsLoading -> {
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
                            text = "তথ্য লোড হচ্ছে...",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                uiState.chaptersErrorMessage != null && uiState.chapters.isEmpty() && uiState.subjectModelTests.isEmpty() && uiState.subjectLiveClasses.isEmpty() -> {
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
                                    imageVector = Icons.Default.MenuBook,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = uiState.chaptersErrorMessage ?: "তথ্য পাওয়া যায়নি",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                viewModel.loadChaptersForSubject(
                                    subjectCode = subjectCode,
                                    subjectTitle = subjectTitle,
                                    subjectColor = subjectColorHex,
                                    phaseId = uiState.activePhaseId
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

                // ==========================================
                // ARCHITECTURE 1: Model Test & Live Class (Admission Program Mode)
                // ==========================================
                isModelTestArchitecture -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Top Shortcuts: Animated Lessons, Practice Quiz, E-Book
                        item {
                            CourseFeatureShortcuts(
                                subjectColor = subjectColor,
                                selectedMode = 0,
                                onSelectMode = { mode ->
                                    if (mode == 2) {
                                        onNavigateToPracticeQuiz?.invoke(subjectCode, subjectTitle, subjectColorHex)
                                    } else if (mode == 3) {
                                        onNavigateToSmartNotes?.invoke(subjectCode, subjectTitle, subjectColorHex, uiState.activePhaseId)
                                    } else if (mode == 4) {
                                        onNavigateToAnimatedLessons?.invoke(subjectCode, subjectTitle, subjectColorHex, uiState.programId, uiState.activePhaseId)
                                    }
                                }
                            )
                        }

                        // Model Test vs Class Dual Tab Bar (Official Shikho Design)
                        item {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(4.dp)
                                ) {
                                    // Tab 1: Model Test
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (modelTestTabSelected == 0) Color(0xFF4F46E5) else Color.Transparent,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { modelTestTabSelected = 0 }
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(vertical = 10.dp)
                                        ) {
                                            Text(
                                                text = "মডেল টেস্ট",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (modelTestTabSelected == 0) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    // Tab 2: Class
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (modelTestTabSelected == 1) Color(0xFF4F46E5) else Color.Transparent,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { modelTestTabSelected = 1 }
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(vertical = 10.dp)
                                        ) {
                                            Text(
                                                text = "ক্লাস",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (modelTestTabSelected == 1) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Content List based on selected sub-tab
                        if (modelTestTabSelected == 0) {
                            // Model Tests List
                            if (uiState.subjectModelTests.isEmpty()) {
                                item {
                                    EmptyStateCard(message = "এই বিষয়ে কোনো মডেল টেস্ট পাওয়া যায়নি")
                                }
                            } else {
                                items(
                                    items = uiState.subjectModelTests,
                                    key = { it.id.ifBlank { it.content_id ?: it.title ?: "" } }
                                ) { item ->
                                    ModelTestItemCard(
                                        item = item,
                                        subjectColor = subjectColor,
                                        onClick = {
                                            viewModel.selectLesson(item)
                                        }
                                    )
                                }
                            }
                        } else {
                            // Classes List
                            if (uiState.subjectLiveClasses.isEmpty()) {
                                item {
                                    EmptyStateCard(message = "এই বিষয়ে কোনো ক্লাস পাওয়া যায়নি")
                                }
                            } else {
                                items(
                                    items = uiState.subjectLiveClasses,
                                    key = { it.id.ifBlank { it.content_id ?: it.title ?: "" } }
                                ) { item ->
                                    ClassItemCard(
                                        item = item,
                                        subjectColor = subjectColor,
                                        onPlayClick = {
                                            viewModel.selectLesson(item)
                                            val url = item.resolvedVideoUrl ?: ""
                                            val title = item.title ?: "ক্লাস"
                                            if (url.isNotBlank()) {
                                                onPlayVideo(url, title, subjectTitle, subjectColorHex ?: "#0072EC", item.isLive)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // ==========================================
                // ARCHITECTURE 2: Academic Chapters Hierarchy Mode
                // ==========================================
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Top Shortcuts: Practice Quiz, E-Book, Animated Lessons
                        item {
                            CourseFeatureShortcuts(
                                subjectColor = subjectColor,
                                selectedMode = 0,
                                onSelectMode = { mode ->
                                    if (mode == 2) {
                                        onNavigateToPracticeQuiz?.invoke(subjectCode, subjectTitle, subjectColorHex)
                                    } else if (mode == 3) {
                                        onNavigateToSmartNotes?.invoke(subjectCode, subjectTitle, subjectColorHex, uiState.activePhaseId)
                                    } else if (mode == 4) {
                                        onNavigateToAnimatedLessons?.invoke(subjectCode, subjectTitle, subjectColorHex, uiState.programId, uiState.activePhaseId)
                                    }
                                }
                            )
                        }

                        // Section Header
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp, bottom = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "অধ্যায় তালিকা (${toBengaliDigits(uiState.chapters.size)}টি)",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                if (uiState.activePhaseTitle.isNotBlank()) {
                                    Text(
                                        text = uiState.activePhaseTitle,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = subjectColor
                                    )
                                }
                            }
                        }

                        if (uiState.chapters.isEmpty()) {
                            item {
                                EmptyStateCard(message = "এই কোয়ার্টারে কোনো অধ্যায় অন্তর্ভুক্ত নেই")
                            }
                        } else {
                            items(
                                items = uiState.chapters,
                                key = { it.id.ifBlank { it.chapter_id ?: it.effectiveName } }
                            ) { chapter ->
                                ChapterCard(
                                    chapter = chapter,
                                    subjectColor = subjectColor,
                                    activeMode = 0,
                                    onClick = {
                                        val primaryId = chapter.id.ifBlank { chapter.chapter_id ?: "" }
                                        val altId = chapter.chapter_id?.takeIf { it != primaryId }
                                        val targetName = chapter.effectiveName
                                        val targetStatus = chapter.status ?: ""
                                        viewModel.selectChapter(primaryId, targetName, targetStatus)
                                        viewModel.loadLessonsForChapter(
                                            chapterId = primaryId,
                                            altChapterId = altId,
                                            chapterName = targetName,
                                            chapterStatus = targetStatus
                                        )
                                        onChapterClick(primaryId, targetName, targetStatus, 0)
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
fun EmptyStateCard(message: String) {
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
                imageVector = Icons.Default.Inbox,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(44.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = message,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun ModelTestItemCard(
    item: StudentLessonItem,
    subjectColor: Color,
    onClick: () -> Unit
) {
    val categoryText = item.model_test?.exam_category?.ifBlank { null }
        ?: item.model_test?.type?.ifBlank { null }
        ?: "এডমিশন টেস্ট"

    val activityState = item.user_activity_state?.uppercase() ?: ""
    val (statusLabel, statusColor, statusBg) = when {
        activityState == "MISSED" -> Triple("মিসড", Color(0xFFEF4444), Color(0xFFFEE2E2))
        activityState == "LIVE" -> Triple("লাইভ", Color(0xFF10B981), Color(0xFFD1FAE5))
        activityState == "UPCOMING" -> Triple("আসন্ন", Color(0xFF3B82F6), Color(0xFFDBEAFE))
        activityState == "COMPLETED" || activityState == "ATTENDED" -> Triple("সম্পন্ন", Color(0xFF059669), Color(0xFFD1FAE5))
        else -> Triple("মিসড", Color(0xFFEF4444), Color(0xFFFEE2E2))
    }

    val dateFormatted = remember(item.start_time) {
        formatIsoToBengaliDate(item.start_time)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFEEF2FF),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Assignment,
                        contentDescription = null,
                        tint = Color(0xFF4F46E5),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Category & Status Badges
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFFCE7F3)
                    ) {
                        Text(
                            text = categoryText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFDB2777),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = statusBg
                    ) {
                        Text(
                            text = statusLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Title
                Text(
                    text = item.title ?: "মডেল টেস্ট",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Date
                if (dateFormatted.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Event,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = dateFormatted,
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ClassItemCard(
    item: StudentLessonItem,
    subjectColor: Color,
    onPlayClick: () -> Unit
) {
    val classTypeLabel = item.live_class?.type?.ifBlank { null }
        ?: item.content_type?.ifBlank { null }
        ?: "ওরিয়েন্টেশন ক্লাস"

    val activityState = item.user_activity_state?.uppercase() ?: ""
    val (statusLabel, statusColor, statusBg) = when {
        activityState == "MISSED" -> Triple("মিসড", Color(0xFFEF4444), Color(0xFFFEE2E2))
        activityState == "LIVE" -> Triple("লাইভ", Color(0xFF10B981), Color(0xFFD1FAE5))
        activityState == "UPCOMING" -> Triple("আসন্ন", Color(0xFF3B82F6), Color(0xFFDBEAFE))
        activityState == "COMPLETED" || activityState == "ATTENDED" -> Triple("সম্পন্ন", Color(0xFF059669), Color(0xFFD1FAE5))
        else -> Triple("মিসড", Color(0xFFEF4444), Color(0xFFFEE2E2))
    }

    val dateFormatted = remember(item.start_time) {
        formatIsoToBengaliDate(item.start_time)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onPlayClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Class / Teacher Icon
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFEFF6FF),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.OndemandVideo,
                        contentDescription = null,
                        tint = Color(0xFF2563EB),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Class Type & Status Badges
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFF1F5F9)
                    ) {
                        Text(
                            text = classTypeLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF475569),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = statusBg
                    ) {
                        Text(
                            text = statusLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Title
                Text(
                    text = item.title ?: "ক্লাস",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Date
                if (dateFormatted.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Event,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = dateFormatted,
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }
        }
    }
}

private fun formatIsoToBengaliDate(isoString: String?): String {
    if (isoString.isNullOrBlank()) return ""
    return try {
        val ms = com.example.api.parseIsoToDhakaMillis(isoString) ?: return ""
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Dhaka")
        }
        val formatted = sdf.format(Date(ms))
        toBengaliDigits(formatted)
    } catch (_: Exception) {
        ""
    }
}

@Composable
fun CourseFeatureShortcuts(
    subjectColor: Color,
    selectedMode: Int = 0,
    onSelectMode: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ShortcutButton(
            title = AcademicLocalizationUtils.translateContentType("Exam"),
            icon = Icons.Default.FactCheck,
            color = Color(0xFF10B981),
            isSelected = selectedMode == 2,
            modifier = Modifier.weight(1f),
            onClick = { onSelectMode(2) }
        )
        ShortcutButton(
            title = "ই-বুক",
            icon = Icons.Default.MenuBook,
            color = Color(0xFFF59E0B),
            isSelected = selectedMode == 3,
            modifier = Modifier.weight(1f),
            onClick = { onSelectMode(3) }
        )
        ShortcutButton(
            title = "অ্যানিমেটেড লেসনস",
            icon = Icons.Default.SmartDisplay,
            color = Color(0xFF8B5CF6),
            isSelected = selectedMode == 4,
            modifier = Modifier.weight(1f),
            onClick = { onSelectMode(4) }
        )
    }
}

@Composable
fun ShortcutButton(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    isSelected: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) color.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) color else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        shadowElevation = if (isSelected) 2.dp else 1.dp,
        modifier = if (onClick != null) {
            modifier.clickable(onClick = onClick)
        } else {
            modifier
        }
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
                maxLines = 1
            )
        }
    }
}

@Composable
fun ChapterCard(
    chapter: AcademicChapterItem,
    subjectColor: Color,
    activeMode: Int = 0,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progressPct = chapter.displayProgress

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(
                width = if (activeMode > 0) 1.5.dp else 1.dp,
                color = when (activeMode) {
                    1 -> Color(0xFF8B5CF6).copy(alpha = 0.4f)
                    2 -> Color(0xFF10B981).copy(alpha = 0.4f)
                    3 -> Color(0xFFF59E0B).copy(alpha = 0.4f)
                    else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                },
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Circular Progress Badge
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(48.dp)
            ) {
                CircularProgressIndicator(
                    progress = { (progressPct / 100f).coerceIn(0f, 1f) },
                    strokeWidth = 3.5.dp,
                    color = if (progressPct >= 100) Color(0xFF10B981) else subjectColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxSize()
                )
                Text(
                    text = "${toBengaliDigits(progressPct)}%",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (progressPct >= 100) Color(0xFF10B981) else subjectColor
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Center: Chapter Info & Counters
            Column(modifier = Modifier.weight(1f)) {
                // Status Badge + Chapter Number
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    when {
                        chapter.isCompleted -> {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.14f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = "পড়ানো শেষ",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF059669)
                                    )
                                }
                            }
                        }
                        chapter.isInProgress -> {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = subjectColor.copy(alpha = 0.14f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(subjectColor)
                                    )
                                    Text(
                                        text = "পড়ানো হচ্ছে",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = subjectColor
                                    )
                                }
                            }
                        }
                        else -> {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            ) {
                                Text(
                                    text = "পড়ানো হবে",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    if (chapter.effectiveNo != null) {
                        Text(
                            text = "• ${AcademicLocalizationUtils.CHAPTER_PREFIX}${toBengaliDigits(chapter.effectiveNo.toString())}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Chapter Name
                Text(
                    text = chapter.effectiveName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Counters: Class & Exam count
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val classCount = chapter.class_counter ?: 0
                    val examCount = chapter.exam_counter ?: 0

                    if (classCount > 0 || examCount == 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayLesson,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "ক্লাস: ${toBengaliDigits(classCount)}টি",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (examCount > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Assignment,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "এক্সাম: ${toBengaliDigits(examCount)}টি",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Right Arrow
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
