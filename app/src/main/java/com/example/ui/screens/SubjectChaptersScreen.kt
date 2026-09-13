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
import com.example.api.TopicFullItem
import com.example.course.CourseUiState
import com.example.course.CourseViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectChaptersScreen(
    subjectCode: String,
    subjectTitle: String,
    subjectColorHex: String?,
    viewModel: CourseViewModel,
    onBack: () -> Unit,
    onChapterClick: (chapterId: String, chapterName: String, chapterStatus: String) -> Unit,
    onPlayVideo: (videoUrl: String, title: String, subjectName: String, subjectColor: String, isLive: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAnimatedLessonsSheet by remember { mutableStateOf(false) }

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

    // Load chapters on start
    LaunchedEffect(subjectCode) {
        viewModel.loadChaptersForSubject(
            subjectCode = subjectCode,
            subjectTitle = subjectTitle,
            subjectColor = subjectColorHex
        )
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
                            text = subjectTitle.ifBlank { "বিষয় অধ্যায়সমূহ" },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        // Refresh button
                        IconButton(
                            onClick = {
                                viewModel.loadChaptersForSubject(
                                    subjectCode = subjectCode,
                                    subjectTitle = subjectTitle,
                                    subjectColor = subjectColorHex,
                                    phaseId = uiState.activePhaseId
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
                uiState.isChaptersLoading -> {
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
                            text = "অধ্যায়সমূহ লোড হচ্ছে...",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                uiState.chaptersErrorMessage != null && uiState.chapters.isEmpty() -> {
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
                            text = uiState.chaptersErrorMessage ?: "অধ্যায় পাওয়া যায়নি",
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

                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Top Shortcuts: Animated Lessons, Practice Quiz, E-Book
                        item {
                            CourseFeatureShortcuts(
                                subjectColor = subjectColor,
                                onAnimatedLessonsClick = {
                                    showAnimatedLessonsSheet = true
                                    viewModel.loadAnimatedLessonsForSubject()
                                }
                            )
                        }

                        // Section Header
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp, bottom = 4.dp),
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
                                            text = "এই কোয়ার্টারে কোনো অধ্যায় অন্তর্ভুক্ত নেই",
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
                                items = uiState.chapters,
                                key = { it.id.ifBlank { it.chapter_id ?: it.effectiveName } }
                            ) { chapter ->
                                ChapterCard(
                                    chapter = chapter,
                                    subjectColor = subjectColor,
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
                                        onChapterClick(primaryId, targetName, targetStatus)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAnimatedLessonsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAnimatedLessonsSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "অ্যানিমেটেড লেসনসমূহ",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (subjectTitle.isNotBlank()) {
                            Text(
                                text = subjectTitle,
                                fontSize = 12.sp,
                                color = subjectColor,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    IconButton(
                        onClick = { showAnimatedLessonsSheet = false }
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                when {
                    uiState.isSubjectAnimationsLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = subjectColor)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "কোর্সের অ্যানিমেটেড লেসন লোড হচ্ছে...",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    uiState.subjectAnimatedLessons.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SlowMotionVideo,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "দুঃখিত, এই কোর্সের কোনো অ্যানিমেটেড লেসন পাওয়া যায়নি।",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "অন্যান্য চ্যাপ্টার বা কোর্সগুলোতে অ্যানিমেটেড লেসন রয়েছে কিনা তা দেখতে পারেন।",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    else -> {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            items(
                                items = uiState.subjectAnimatedLessons,
                                key = { it.id ?: "" }
                            ) { topic ->
                                val video = topic.videos?.data?.firstOrNull { !it.playback_url.isNullOrBlank() }
                                AnimatedLessonCardInSheet(
                                    topic = topic,
                                    subjectColor = subjectColor,
                                    onClick = {
                                        showAnimatedLessonsSheet = false
                                        val videoUrl = video?.playback_url ?: ""
                                        val title = "${topic.no ?: ""} ${topic.name ?: "অ্যানিমেটেড লেসন"}".trim()
                                        onPlayVideo(
                                            videoUrl,
                                            title,
                                            subjectTitle,
                                            subjectColorHex ?: "#0072EC",
                                            false
                                        )
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
fun CourseFeatureShortcuts(
    subjectColor: Color,
    onAnimatedLessonsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ShortcutButton(
            title = "অ্যানিমেটেড লেসন",
            icon = Icons.Default.SlowMotionVideo,
            color = Color(0xFF8B5CF6),
            modifier = Modifier.weight(1f),
            onClick = onAnimatedLessonsClick
        )
        ShortcutButton(
            title = "প্র্যাকটিস কুইজ",
            icon = Icons.Default.FactCheck,
            color = Color(0xFF10B981),
            modifier = Modifier.weight(1f)
        )
        ShortcutButton(
            title = "ই-বুক",
            icon = Icons.Default.MenuBook,
            color = Color(0xFFF59E0B),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun ShortcutButton(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        shadowElevation = 1.dp,
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
                color = color.copy(alpha = 0.12f),
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
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
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
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
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
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.12f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Text(
                                        text = "পড়ানো শেষ",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF10B981)
                                    )
                                }
                            }
                        }
                        chapter.isInProgress -> {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = subjectColor.copy(alpha = 0.12f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(subjectColor)
                                    )
                                    Text(
                                        text = "পড়ানো হচ্ছে",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = subjectColor
                                    )
                                }
                            }
                        }
                        else -> {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            ) {
                                Text(
                                    text = "আসন্ন",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    if (chapter.effectiveNo != null) {
                        Text(
                            text = "• অধ্যায় ${toBengaliDigits(chapter.effectiveNo.toString())}",
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

@Composable
fun AnimatedLessonCardInSheet(
    topic: com.example.api.TopicFullItem,
    subjectColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF8B5CF6).copy(alpha = 0.12f),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.SlowMotionVideo,
                        contentDescription = "Animated Lesson",
                        tint = Color(0xFF8B5CF6),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                if (!topic.header?.chapter_name.isNullOrBlank()) {
                    Text(
                        text = topic.header?.chapter_name ?: "",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = subjectColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }

                Text(
                    text = "${topic.no ?: ""} ${topic.name ?: "অধ্যায় কন্টেন্ট"}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
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

            IconButton(
                onClick = onClick,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color(0xFF8B5CF6).copy(alpha = 0.1f)
                ),
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play Animation",
                    tint = Color(0xFF8B5CF6),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
