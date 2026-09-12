package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.api.EnrolledProgram
import com.example.home.HomeViewModel
import com.example.ui.components.CourseSwitcherBottomSheet
import com.example.ui.components.HomeHeader
import com.example.ui.components.RoutineCard
import com.example.ui.components.WeeklyRoutineSection
import com.example.api.StudentLessonItem

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToProfile: () -> Unit = {},
    onNavigateToChangeSyllabus: () -> Unit = {},
    onCourseSelected: (EnrolledProgram) -> Unit = {},
    onOpenCourse: () -> Unit = {},
    onOpenFullRoutine: () -> Unit = {},
    onOpenLessonDetail: ((lesson: StudentLessonItem) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

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
        topBar = {
            com.example.ui.components.HomeHeader(
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
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
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
                    WeeklyRoutineSection(
                        lessons = uiState.weeklyRoutine,
                        isLoading = uiState.isRoutineLoading,
                        onSeeAllClick = { onOpenFullRoutine() },
                        onOpenLessonDetail = { lesson -> onOpenLessonDetail?.invoke(lesson) }
                    )
                }
            }
        }
    }
}
