package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.api.EnrolledProgram
import com.example.api.OtherProgram
import com.example.course.CourseUiState
import com.example.course.CourseViewModel
import com.example.course.SubjectWithProgress
import com.example.ui.components.getProgramBadge
import com.example.utils.SubjectIconBadge
import com.example.utils.SubjectColorUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseSubjectsScreen(
    viewModel: CourseViewModel,
    onSubjectClick: (subjectCode: String, subjectTitle: String, subjectColor: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedProgram = uiState.selectedCourseProgram

    // Fetch enrolled programs on launch if all lists are empty
    LaunchedEffect(Unit) {
        if (uiState.enrolledPrograms.isEmpty() && uiState.freePrograms.isEmpty() && uiState.otherPrograms.isEmpty()) {
            viewModel.fetchEnrolledPrograms()
        }
    }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (selectedProgram != null) {
                            IconButton(
                                onClick = { viewModel.closeCourseDetails() },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back to my courses",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        } else {
                            IconButton(
                                onClick = { },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Text(
                            text = if (selectedProgram != null) (selectedProgram.title_bn ?: "কোর্স") else "কোর্স",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 240.dp)
                        )
                    }
                }
            }
        },
        containerColor = Color(0xFFF4F7FC), // Soft clean background matching the screenshot
        modifier = modifier.testTag("course_subjects_screen")
    ) { paddingValues ->
        val isRefreshing = uiState.isProgramsLoading || uiState.isSubjectsLoading
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                viewModel.fetchEnrolledPrograms()
                if (selectedProgram != null) {
                    viewModel.loadSubjects(forceRefresh = true)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AnimatedContent(
                targetState = selectedProgram,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "CourseScreenTransition"
            ) { activeProgram ->
                if (activeProgram == null) {
                    // ==========================================
                    // VIEW 1: All Courses List View ("আমার কোর্স", "ফ্রি কোর্স", "সকল কোর্স")
                    // ==========================================
                    MyCoursesListView(
                        uiState = uiState,
                        onOpenEnrolledCourse = { program ->
                            viewModel.openCourse(program)
                        },
                        onOpenOtherCourse = { otherProgram ->
                            viewModel.openCourse(otherProgram.toEnrolledProgram())
                        },
                        onRefresh = {
                            viewModel.fetchEnrolledPrograms()
                        }
                    )
                } else {
                    // ==========================================
                    // VIEW 2: Course Subjects View
                    // ==========================================
                    CourseSubjectsDetailView(
                        activeProgram = activeProgram,
                        uiState = uiState,
                        viewModel = viewModel,
                        onSubjectClick = onSubjectClick,
                        onSwitchCourse = { viewModel.closeCourseDetails() }
                    )
                }
            }
        }
    }
}

/**
 * Course List View showing "আমার কোর্স", "ফ্রি কোর্স", and "সকল কোর্স" matching the user's screenshots!
 */
@Composable
private fun MyCoursesListView(
    uiState: CourseUiState,
    onOpenEnrolledCourse: (EnrolledProgram) -> Unit,
    onOpenOtherCourse: (OtherProgram) -> Unit,
    onRefresh: () -> Unit
) {
    val enrolled = uiState.enrolledPrograms
    val free = uiState.freePrograms
    val other = uiState.otherPrograms
    val isLoading = uiState.isProgramsLoading

    if (isLoading && enrolled.isEmpty() && free.isEmpty() && other.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 40.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(36.dp)
                )
                Text(
                    text = "কোর্স লোড হচ্ছে...",
                    fontSize = 14.sp,
                    color = Color(0xFF64748B)
                )
            }
        }
    } else if (!isLoading && enrolled.isEmpty() && free.isEmpty() && other.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "কোনো কোর্স পাওয়া যায়নি",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B)
                )
                Button(
                    onClick = onRefresh,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("পুনরায় চেষ্টা করুন")
                }
            }
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // ==========================================
            // 1. "আমার কোর্স" (My Enrolled Courses)
            // ==========================================
            if (enrolled.isNotEmpty()) {
                item {
                    Text(
                        text = "আমার কোর্স",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B),
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                items(
                    items = enrolled,
                    key = { "enrolled_${it.id}" }
                ) { program ->
                    EnrolledCourseBannerCard(
                        program = program,
                        onClick = { onOpenEnrolledCourse(program) }
                    )
                }
            }

            // ==========================================
            // 2. "ফ্রি কোর্স" (Free Available Courses)
            // ==========================================
            if (free.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "ফ্রি কোর্স",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B),
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                items(
                    items = free,
                    key = { "free_${it.id}" }
                ) { program ->
                    FreeCourseBannerCard(
                        program = program,
                        onOpen = { onOpenOtherCourse(program) }
                    )
                }
            }

            // ==========================================
            // 3. "সকল কোর্স" (All Other Courses)
            // ==========================================
            if (other.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "সকল কোর্স",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B),
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                items(
                    items = other,
                    key = { "other_${it.id}" }
                ) { program ->
                    OtherCourseBannerCard(
                        program = program,
                        onOpen = { onOpenOtherCourse(program) }
                    )
                }
            }
        }
    }
}

private fun getCourseGradient(title: String): Brush {
    return when {
        title.contains("Think", ignoreCase = true) -> Brush.linearGradient(
            listOf(Color(0xFF080D27), Color(0xFF11184A), Color(0xFF1A1F5E))
        )
        title.contains("মানবিক", ignoreCase = true) -> Brush.linearGradient(
            listOf(Color(0xFF500F1F), Color(0xFF8B1A2F), Color(0xFF3B0B14))
        )
        title.contains("বিজ্ঞান", ignoreCase = true) -> Brush.linearGradient(
            listOf(Color(0xFF0F3854), Color(0xFF0A2540), Color(0xFF001220))
        )
        title.contains("Next Champ", ignoreCase = true) -> Brush.linearGradient(
            listOf(Color(0xFF0284C7), Color(0xFF0369A1), Color(0xFF0C4A6E))
        )
        else -> Brush.linearGradient(
            listOf(Color(0xFF065F46), Color(0xFF047857), Color(0xFF064E3B))
        )
    }
}

@Composable
private fun EnrolledCourseBannerCard(
    program: EnrolledProgram,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val title = program.title_bn?.trim() ?: "কোর্স"
    val cardGradient = remember(program.id, title) { getCourseGradient(title) }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardGradient)
                .padding(18.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                // Top Tag: "ভর্তি হয়েছো"
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFFDCFCE7),
                    modifier = Modifier.padding(bottom = 10.dp)
                ) {
                    Text(
                        text = "ভর্তি হয়েছো",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF15803D),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }

                // Centered Logo / Banner Graphic Box (Matches Screenshot 2)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (!program.banner_url.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.Black.copy(alpha = 0.25f),
                            modifier = Modifier
                                .width(155.dp)
                                .height(92.dp)
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(program.banner_url)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = title,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(4.dp)
                            )
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                            modifier = Modifier
                                .width(155.dp)
                                .height(92.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (title.contains("Think", true)) "THINK AI" else "HSC '27",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Course Title
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Full Width Outline Pill Button: "শেখা চালিয়ে যাও"
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.25.dp, Color.White.copy(alpha = 0.85f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .clickable(onClick = onClick)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Text(
                            text = "শেখা চালিয়ে যাও",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FreeCourseBannerCard(
    program: OtherProgram,
    onOpen: () -> Unit
) {
    val context = LocalContext.current
    val title = program.title_bn ?: "ফ্রি কোর্স"
    val cardGradient = remember(program.id, title) { getCourseGradient(title) }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onOpen)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardGradient)
                .padding(18.dp)
        ) {
            if (!program.banner_url.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(program.banner_url)
                        .crossfade(true)
                        .build(),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(20.dp))
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.40f),
                                    Color.Black.copy(alpha = 0.82f)
                                )
                            )
                        )
                )
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                // Top Tag: "সম্পূর্ণ ফ্রি!"
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF16A34A),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Text(
                        text = "সম্পূর্ণ ফ্রি!",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Title
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons Row: "বিস্তারিত দেখো" | "সম্পূর্ণ ফ্রি'তে শুরু করো"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = Color.Transparent,
                        border = BorderStroke(1.25.dp, Color.White.copy(alpha = 0.85f)),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(28.dp))
                            .clickable(onClick = onOpen)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 11.dp)
                        ) {
                            Text(
                                text = "বিস্তারিত দেখো",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = Color(0xFF16A34A),
                        modifier = Modifier
                            .weight(1.3f)
                            .clip(RoundedCornerShape(28.dp))
                            .clickable(onClick = onOpen)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 11.dp)
                        ) {
                            Text(
                                text = "সম্পূর্ণ ফ্রি'তে শুরু করো",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OtherCourseBannerCard(
    program: OtherProgram,
    onOpen: () -> Unit
) {
    val context = LocalContext.current
    val title = program.title_bn ?: "কোর্স"
    val cardGradient = remember(program.id, title) { getCourseGradient(title) }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onOpen)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardGradient)
                .padding(18.dp)
        ) {
            if (!program.banner_url.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(program.banner_url)
                        .crossfade(true)
                        .build(),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(20.dp))
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.40f),
                                    Color.Black.copy(alpha = 0.82f)
                                )
                            )
                        )
                )
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                val classLabel = program.classes?.firstOrNull()
                if (!classLabel.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White.copy(alpha = 0.2f),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Text(
                            text = classLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(24.dp))
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.25.dp, Color.White.copy(alpha = 0.85f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .clickable(onClick = onOpen)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Text(
                            text = "বিস্তারিত দেখো",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

/**
 * Course Subjects Grid View shown when a user opens a course
 */
@Composable
private fun CourseSubjectsDetailView(
    activeProgram: EnrolledProgram,
    uiState: com.example.course.CourseUiState,
    viewModel: CourseViewModel,
    onSubjectClick: (subjectCode: String, subjectTitle: String, subjectColor: String) -> Unit,
    onSwitchCourse: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Clean, Compact Header (No redundant bulky cards)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "কোর্সের বিষয়সমূহ",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (uiState.subjects.isNotEmpty()) {
                    Text(
                        text = "মোট ${toBengaliDigits(uiState.subjects.size)} টি বিষয় অন্তর্ভুক্ত",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedButton(
                onClick = onSwitchCourse,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text(
                    text = "অন্য কোর্স",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Main Grid or Loading / Error State
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when {
                uiState.isSubjectsLoading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "বিষয়সমূহ লোড হচ্ছে...",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                uiState.subjectsErrorMessage != null && uiState.subjects.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = uiState.subjectsErrorMessage ?: "বিষয় পাওয়া যায়নি",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = { viewModel.loadSubjects(forceRefresh = true) },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("পুনরায় চেষ্টা করুন")
                        }
                    }
                }

                uiState.subjects.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Inbox,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "এই কোর্সে কোনো বিষয় পাওয়া যায়নি",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = uiState.subjects,
                            key = { it.subject.code ?: it.subject.display_bn ?: "" }
                        ) { subjectWithProgress ->
                            SubjectGridCard(
                                item = subjectWithProgress,
                                onClick = {
                                    val code = subjectWithProgress.subject.code ?: ""
                                    val title = subjectWithProgress.subject.display_bn ?: ""
                                    val color = subjectWithProgress.subject.color_code ?: "#0072EC"
                                    viewModel.selectSubject(code, title, color)
                                    onSubjectClick(code, title, color)
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
fun SubjectGridCard(
    item: SubjectWithProgress,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val subject = item.subject

    val parsedColor = remember(subject.color_code, subject.display_bn) {
        try {
            if (!subject.color_code.isNullOrBlank()) {
                Color(android.graphics.Color.parseColor(subject.color_code))
            } else {
                SubjectColorUtils.getColorScheme(subject.display_bn).textColor
            }
        } catch (_: Exception) {
            SubjectColorUtils.getColorScheme(subject.display_bn).textColor
        }
    }

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
                color = parsedColor.copy(alpha = 0.25f),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(parsedColor.copy(alpha = 0.04f))
                .padding(14.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SubjectIconBadge(
                        iconUrl = subject.icon,
                        subjectName = subject.display_bn,
                        subjectCode = subject.code,
                        color = parsedColor,
                        size = 44.dp,
                        iconSize = 24.dp
                    )

                    Surface(
                        shape = CircleShape,
                        color = parsedColor.copy(alpha = 0.12f),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = parsedColor,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = subject.display_bn ?: subject.code ?: "বিষয়",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 19.sp,
                    modifier = Modifier.heightIn(min = 38.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Chapter count pill / Progress
                if (item.totalChapters > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = parsedColor.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "${toBengaliDigits(item.completedChapters)}/${toBengaliDigits(item.totalChapters)} অধ্যায়",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = parsedColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Text(
                            text = "${toBengaliDigits(item.progressPercentage)}%",
                            fontSize = 11.sp,
                            color = parsedColor,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    val progress = (item.progressPercentage / 100f).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progress },
                        color = parsedColor,
                        trackColor = parsedColor.copy(alpha = 0.15f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = parsedColor.copy(alpha = 0.10f)
                    ) {
                        Text(
                            text = "অধ্যায়সমূহ দেখুন",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = parsedColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
