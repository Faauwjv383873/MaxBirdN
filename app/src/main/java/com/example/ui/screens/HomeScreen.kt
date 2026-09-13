package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.api.EnrolledProgram
import com.example.api.StudentLessonItem
import com.example.home.HomeViewModel
import com.example.ui.components.CourseProgressSection
import com.example.ui.components.CourseSwitcherBottomSheet
import com.example.ui.components.HomeHeader
import com.example.ui.components.RoutineCard
import com.example.ui.components.SubjectFilterDialog
import com.example.ui.components.WeeklyRoutineSection

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToProfile: () -> Unit = {},
    onNavigateToEditProfile: () -> Unit = onNavigateToProfile,
    onNavigateToChangeSyllabus: () -> Unit = {},
    onCourseSelected: (EnrolledProgram) -> Unit = {},
    onOpenCourse: (phaseId: String?) -> Unit = {},
    onOpenFullRoutine: () -> Unit = {},
    onOpenLessonDetail: ((lesson: StudentLessonItem) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    // Scroll-driven collapsible header state
    var isHeaderVisible by remember { mutableStateOf(true) }
    var previousScrollOffset by remember { mutableIntStateOf(0) }

    LaunchedEffect(scrollState.value) {
        val currentOffset = scrollState.value
        val delta = currentOffset - previousScrollOffset
        if (delta > 20 && currentOffset > 80) {
            isHeaderVisible = false // Scrolling DOWN -> hide header smoothly
        } else if (delta < -20 || currentOffset <= 40) {
            isHeaderVisible = true  // Scrolling UP or near top -> expand header
        }
        previousScrollOffset = currentOffset
    }

    // Course Switcher Bottom Sheet
    if (uiState.showCourseSwitcher) {
        CourseSwitcherBottomSheet(
            enrolledPrograms = uiState.enrolledPrograms,
            activeProgram = uiState.activeProgram,
            onSelectProgram = { program ->
                viewModel.switchActiveCourse(program)
                onCourseSelected(program)
            },
            onDismiss = {
                viewModel.setCourseSwitcherVisible(false)
            },
            onChangeSyllabusClick = onNavigateToChangeSyllabus
        )
    }

    // Subject Filter / Customizer Dialog
    if (uiState.showSubjectFilterDialog) {
        SubjectFilterDialog(
            courseTitle = uiState.activeProgram?.title_bn ?: "",
            subjects = uiState.courseSubjects,
            selectedSubjectCodes = uiState.selectedSubjectCodes,
            isLoading = uiState.isCourseSubjectsLoading,
            isSaving = uiState.isSavingSubjectFilter,
            onToggleSubject = { code -> viewModel.toggleSubjectSelection(code) },
            onSelectAll = { viewModel.selectAllSubjects() },
            onClearAll = { viewModel.clearAllSubjectSelection() },
            onSave = { viewModel.saveSubjectFilter() },
            onDismiss = { viewModel.dismissSubjectFilterDialog() }
        )
    }

    // Format Subtitle
    val subtitle = remember(uiState.userClass, uiState.userGroup, uiState.userSchool) {
        val classDisplay = when (uiState.userClass) {
            "C11", "Class 11" -> "একাদশ শ্রেণি"
            "C12", "Class 12" -> "দ্বাদশ শ্রেণি"
            "C10", "Class 10" -> "দশম শ্রেণি"
            "C9", "Class 9" -> "নবম শ্রেণি"
            else -> uiState.userClass.ifBlank { "একাদশ শ্রেণি" }
        }

        val groupDisplay = when (uiState.userGroup.lowercase()) {
            "humanities", "humanities_group" -> "মানবিক"
            "science", "science_group" -> "বিজ্ঞান"
            "business", "business_studies", "commerce" -> "ব্যবসায় শিক্ষা"
            else -> uiState.userGroup.ifBlank { "" }
        }

        listOf(classDisplay, groupDisplay, uiState.userSchool)
            .filter { it.isNotBlank() }
            .joinToString(" • ")
            .ifBlank { "এইচএসসি শিক্ষার্থী" }
    }

    val displayName = remember(uiState.userFirstName, uiState.userName) {
        when {
            uiState.userFirstName.isNotBlank() && uiState.userFirstName != "শিক্ষার্থী" -> uiState.userFirstName
            uiState.userName.isNotBlank() && uiState.userName != "শিক্ষার্থী" -> uiState.userName
            else -> "শিক্ষার্থী"
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HomeHeader(
                userName = displayName,
                subtitle = subtitle,
                avatarUrl = uiState.userAvatar ?: "",
                isPremium = uiState.isPremium,
                activeCourseTitle = uiState.activeProgram?.title_bn ?: "কোর্স নির্বাচন করো",
                onOpenCourseSwitcher = {
                    viewModel.setCourseSwitcherVisible(true)
                },
                onAvatarClick = {
                    onNavigateToProfile()
                }
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Account Incomplete Banner (Directing new / incomplete profile users)
                if (displayName == "শিক্ষার্থী" || uiState.userFirstName.isBlank()) {
                    AccountCompletionBanner(
                        onCompleteClick = onNavigateToEditProfile
                    )
                }

                when {
                    uiState.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "কোর্স লোড হচ্ছে...",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                uiState.errorMessage != null && uiState.enrolledPrograms.isEmpty() -> {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "কোর্স লোড করা যায়নি",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = uiState.errorMessage ?: "অনুগ্রহ করে আবার চেষ্টা করো",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = { viewModel.loadData(isRefresh = true) },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Retry",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("আবার চেষ্টা করো")
                            }
                        }
                    }
                }

                else -> {
                    val selectedSubjectNames = remember(uiState.courseSubjects, uiState.selectedSubjectCodes) {
                        uiState.courseSubjects
                            .filter { sub -> sub.code != null && uiState.selectedSubjectCodes.contains(sub.code) }
                            .mapNotNull { sub -> sub.display_bn.takeIf { !it.isNullOrBlank() } ?: sub.code }
                    }

                    WeeklyRoutineSection(
                        lessons = uiState.filteredWeeklyRoutine,
                        isLoading = uiState.isRoutineLoading,
                        selectedSubjectsCount = uiState.selectedSubjectCodes.size,
                        totalSubjectsCount = uiState.courseSubjects.size,
                        selectedSubjectNames = selectedSubjectNames,
                        onSeeAllClick = { onOpenFullRoutine() },
                        onCustomizeSubjectsClick = { viewModel.openSubjectFilterDialog() },
                        onOpenLessonDetail = { lesson -> onOpenLessonDetail?.invoke(lesson) }
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    CourseProgressSection(
                        phases = uiState.programPhases,
                        onPhaseClick = { phase ->
                            onOpenCourse(phase.id)
                        }
                    )
                }
            }
        }
    }
}
}

@Composable
fun AccountCompletionBanner(
    onCompleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
        border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "আপনার অ্যাকাউন্ট সম্পূর্ণ করুন 🎉",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "সঠিক সিলেবাস ও ক্লাসের নোটিফিকেশন পেতে আপনার পূর্ণাঙ্গ নাম ও কলেজের তথ্য যোগ করুন।",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onCompleteClick,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
            ) {
                Text(
                    text = "এখনই সম্পূর্ণ করুন",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

