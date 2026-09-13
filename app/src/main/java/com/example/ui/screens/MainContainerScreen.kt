package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.auth.SessionManager
import com.example.course.CourseViewModel
import com.example.home.HomeViewModel
import com.example.ui.navigation.NavigationItem

@Composable
fun MainContainerScreen(
    homeViewModel: HomeViewModel,
    courseViewModel: CourseViewModel,
    sessionManager: SessionManager,
    onNavigateToSubjectChapters: (subjectCode: String, subjectTitle: String, subjectColor: String) -> Unit = { _, _, _ -> },
    onNavigateToEditProfile: () -> Unit = {},
    onNavigateToChangeSyllabus: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToCourseEnrollment: () -> Unit = {},
    onNavigateToFullRoutine: () -> Unit = {},
    onOpenLessonDetail: (com.example.api.StudentLessonItem) -> Unit = {},
    onLogout: () -> Unit = {}
) {
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            ElementalFloatingBottomBar(
                selectedIndex = selectedIndex,
                onTabSelected = { index ->
                    selectedIndex = index
                    if (index == 1) {
                        courseViewModel.loadSubjects()
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = selectedIndex,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
                },
                label = "TabContentTransition"
            ) { tabIndex ->
                when (tabIndex) {
                    0 -> {
                        HomeScreen(
                            viewModel = homeViewModel,
                            onNavigateToProfile = { selectedIndex = 3 },
                            onNavigateToChangeSyllabus = onNavigateToChangeSyllabus,
                            onCourseSelected = { program ->
                                homeViewModel.switchActiveCourse(program)
                                courseViewModel.openCourse(program)
                                selectedIndex = 1
                            },
                            onOpenCourse = { phaseId ->
                                val activeProg = homeViewModel.uiState.value.activeProgram
                                if (activeProg != null) {
                                    courseViewModel.switchProgram(
                                        newProgramId = activeProg.id,
                                        newProgramTitle = activeProg.title_bn ?: "",
                                        batchId = activeProg.enrollment_details?.batch_id,
                                        classCode = activeProg.classes?.firstOrNull(),
                                        targetPhaseId = phaseId
                                    )
                                } else {
                                    courseViewModel.loadSubjects(targetPhaseId = phaseId)
                                }
                                selectedIndex = 1
                            },
                            onOpenFullRoutine = onNavigateToFullRoutine,
                            onOpenLessonDetail = onOpenLessonDetail,
                            onOpenAnimatedLessons = {
                                val activeProg = homeViewModel.uiState.value.activeProgram
                                if (activeProg != null) {
                                    courseViewModel.openCourse(activeProg)
                                } else {
                                    courseViewModel.loadSubjects()
                                }
                                selectedIndex = 1
                            }
                        )
                    }
                    1 -> {
                        CourseSubjectsScreen(
                            viewModel = courseViewModel,
                            onSubjectClick = onNavigateToSubjectChapters
                        )
                    }
                    3 -> {
                        SettingsScreen(
                            sessionManager = sessionManager,
                            onNavigateToEditProfile = onNavigateToEditProfile,
                            onNavigateToChangeSyllabus = onNavigateToChangeSyllabus,
                            onNavigateToProfile = onNavigateToProfile,
                            onNavigateToCourseEnrollment = onNavigateToCourseEnrollment,
                            onLogout = onLogout
                        )
                    }
                    else -> {
                        ComingSoonScreen(tabItem = NavigationItem.items[tabIndex])
                    }
                }
            }
        }
    }
}

/**
 * Compact Floating Dock Navigation Bar with Water, Fire, Ice & Light Animations
 */
@Composable
fun ElementalFloatingBottomBar(
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .height(58.dp)
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(26.dp),
                spotColor = Color.Black.copy(alpha = 0.25f)
            ),
        shape = RoundedCornerShape(26.dp),
        color = Color(0xFF0F172A),
        border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavigationItem.items.forEachIndexed { index, item ->
                val isSelected = selectedIndex == index

                val scale by animateFloatAsState(
                    targetValue = if (isSelected) 1.12f else 1.0f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "TabScale"
                )

                // Elemental Themes: 0: Fire 🔥, 1: Water 🌊, 2: Ice ❄️, 3: Light/Plasma ⚡
                val elementalGradients = remember(index) {
                    when (index) {
                        0 -> listOf(Color(0xFFEF4444), Color(0xFFF97316), Color(0xFFF59E0B)) // Fire / Flame Amber 🔥
                        1 -> listOf(Color(0xFF0284C7), Color(0xFF06B6D4), Color(0xFF38BDF8)) // Water / Aqua 🌊
                        2 -> listOf(Color(0xFF0284C7), Color(0xFF6366F1), Color(0xFFC084FC)) // Ice / Crystal Frost ❄️
                        else -> listOf(Color(0xFF7C3AED), Color(0xFFC084FC), Color(0xFFE879F9)) // Plasma Light ⚡
                    }
                }

                val activeColor = remember(index) {
                    when (index) {
                        0 -> Color(0xFFF97316)
                        1 -> Color(0xFF38BDF8)
                        2 -> Color(0xFF818CF8)
                        else -> Color(0xFFC084FC)
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(20.dp))
                        .then(
                            if (isSelected) {
                                Modifier
                                    .background(
                                        Brush.linearGradient(
                                            colors = elementalGradients.map { it.copy(alpha = 0.18f) }
                                        )
                                    )
                                    .border(
                                        width = 1.dp,
                                        brush = Brush.linearGradient(elementalGradients),
                                        shape = RoundedCornerShape(20.dp)
                                    )
                            } else Modifier
                        )
                        .clickable { onTabSelected(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.scale(scale)
                    ) {
                        Icon(
                            imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = item.title,
                            tint = if (isSelected) activeColor else Color(0xFF94A3B8),
                            modifier = Modifier.size(20.dp)
                        )

                        if (isSelected) {
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = item.title,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}


