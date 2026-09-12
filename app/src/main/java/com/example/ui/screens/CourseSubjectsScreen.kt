package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.example.course.CourseViewModel
import com.example.course.SubjectWithProgress
import com.example.ui.components.getProgramBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseSubjectsScreen(
    viewModel: CourseViewModel,
    onSubjectClick: (subjectCode: String, subjectTitle: String, subjectColor: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedProgram = uiState.selectedCourseProgram

    // Fetch enrolled programs on launch if empty
    LaunchedEffect(Unit) {
        if (uiState.enrolledPrograms.isEmpty()) {
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
                        .padding(horizontal = 12.dp, vertical = 12.dp),
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

                    // Refresh Button
                    IconButton(
                        onClick = {
                            viewModel.fetchEnrolledPrograms()
                            if (selectedProgram != null) {
                                viewModel.loadSubjects(forceRefresh = true)
                            }
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        },
        containerColor = Color(0xFFF4F7FC), // Soft clean background matching the screenshot
        modifier = modifier.testTag("course_subjects_screen")
    ) { paddingValues ->
        Box(
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
                    // VIEW 1: "আমার কোর্স" (My Courses) List View
                    // ==========================================
                    MyCoursesListView(
                        enrolledPrograms = uiState.enrolledPrograms,
                        onOpenCourse = { program ->
                            viewModel.openCourse(program)
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
 * "আমার কোর্স" List View matching the exact layout in the user's screenshot
 */
@Composable
private fun MyCoursesListView(
    enrolledPrograms: List<EnrolledProgram>,
    onOpenCourse: (EnrolledProgram) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "আমার কোর্স",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1E293B)
        )

        Spacer(modifier = Modifier.height(14.dp))

        if (enrolledPrograms.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(36.dp)
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = enrolledPrograms,
                    key = { it.id }
                ) { program ->
                    CourseBannerCard(
                        program = program,
                        onClick = { onOpenCourse(program) }
                    )
                }
            }
        }
    }
}

/**
 * Single Course Banner Card design matching the uploaded screenshot!
 */
@Composable
private fun CourseBannerCard(
    program: EnrolledProgram,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val badge = getProgramBadge(program)
    val title = program.title_bn ?: "কোর্স"

    // Custom gradient background if image is not present
    val cardGradient = remember(program.id, title) {
        when {
            title.contains("Think", ignoreCase = true) -> Brush.linearGradient(
                listOf(Color(0xFF080D27), Color(0xFF11184A), Color(0xFF1A1F5E))
            )
            title.contains("মানবিক", ignoreCase = true) -> Brush.linearGradient(
                listOf(Color(0xFF500F1F), Color(0xFF8B1A2F), Color(0xFF3B0B14))
            )
            title.contains("বিজ্ঞান", ignoreCase = true) -> Brush.linearGradient(
                listOf(Color(0xFF0F3854), Color(0xFF0A2540), Color(0xFF001220))
            )
            else -> Brush.linearGradient(
                listOf(Color(0xFF1A237E), Color(0xFF0D47A1), Color(0xFF01579B))
            )
        }
    }

    val actionButtonText = if (badge.text == "ফ্রিতে শেখা শেষ") "বিস্তারিত দেখো" else "শেখা চালিয়ে যাও"

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
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
            // Optional background banner image if available from API
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
                // Dark overlay to ensure crisp contrast for text and badges
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.45f),
                                    Color.Black.copy(alpha = 0.75f)
                                )
                            )
                        )
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Top Badge with Shikho Logo
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.maxbird_logo),
                            contentDescription = "MaxBird",
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = badge.containerColor
                    ) {
                        Text(
                            text = badge.text,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = badge.textColor,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Academic logo graphic or title banner styling
                if (title.contains("Think", ignoreCase = true)) {
                    Text(
                        text = "Think AI",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF0072EC).copy(alpha = 0.9f),
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Text(
                            text = "ACADEMIC PROGRAM",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Course Title
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

                // Action Pill Button ("বিস্তারিত দেখো" or "শেখা চালিয়ে যাও")
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
                            text = actionButtonText,
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
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Active Course Card Bar
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val badge = getProgramBadge(activeProgram)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = badge.containerColor,
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Text(
                            text = badge.text,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = badge.textColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = activeProgram.title_bn ?: "কোর্স",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = onSwitchCourse,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text(
                        text = "অন্য কোর্স",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Subjects Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "কোর্সের বিষয়সমূহ",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B)
            )
            if (uiState.subjects.isNotEmpty()) {
                Text(
                    text = "${toBengaliDigits(uiState.subjects.size)} টি বিষয়",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

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
    val context = LocalContext.current

    val parsedColor = remember(subject.color_code) {
        try {
            if (!subject.color_code.isNullOrBlank()) {
                Color(android.graphics.Color.parseColor(subject.color_code))
            } else {
                Color(0xFF0072EC)
            }
        } catch (_: Exception) {
            Color(0xFF0072EC)
        }
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = parsedColor.copy(alpha = 0.12f),
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (!subject.icon.isNullOrBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(subject.icon)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = subject.display_bn,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(26.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AutoStories,
                                contentDescription = null,
                                tint = parsedColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(26.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

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

            Spacer(modifier = Modifier.height(10.dp))

            val progress = (item.progressPercentage / 100f).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { progress },
                color = parsedColor,
                trackColor = parsedColor.copy(alpha = 0.15f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "মোট অগ্রগতি",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
                Text(
                    text = "${toBengaliDigits(item.progressPercentage)}%",
                    fontSize = 11.sp,
                    color = parsedColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
