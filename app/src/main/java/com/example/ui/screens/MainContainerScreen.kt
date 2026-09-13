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
                            onOpenLessonDetail = onOpenLessonDetail
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
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(64.dp)
            .shadow(
                elevation = 20.dp,
                shape = RoundedCornerShape(32.dp),
                spotColor = Color.Black.copy(alpha = 0.45f)
            ),
        shape = RoundedCornerShape(32.dp),
        color = Color(0xFF07152B),
        border = BorderStroke(1.dp, Color(0xFF1E3A5F).copy(alpha = 0.7f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavigationItem.items.forEachIndexed { index, item ->
                val isSelected = selectedIndex == index

                val activePillBg = remember(index) {
                    when (index) {
                        0 -> Color(0xFF261908) // Warm amber container
                        1 -> Color(0xFF082744) // Deep navy-azure container
                        2 -> Color(0xFF0C243D) // Ice blue container
                        else -> Color(0xFF201338) // Violet container
                    }
                }

                val activeBorderColor = remember(index) {
                    when (index) {
                        0 -> Color(0xFFF59E0B) // Amber
                        1 -> Color(0xFF0284C7) // Azure
                        2 -> Color(0xFF38BDF8) // Sky
                        else -> Color(0xFFA855F7) // Purple
                    }
                }

                val activeIconColor = remember(index) {
                    when (index) {
                        0 -> Color(0xFFF59E0B)
                        1 -> Color(0xFF38BDF8)
                        2 -> Color(0xFF38BDF8)
                        else -> Color(0xFFC084FC)
                    }
                }

                AnimatedContent(
                    targetState = isSelected,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(200)) + scaleIn(initialScale = 0.92f))
                            .togetherWith(fadeOut(animationSpec = tween(150)))
                    },
                    label = "TabPillTransition"
                ) { selected ->
                    if (selected) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = activePillBg,
                            border = BorderStroke(1.5.dp, activeBorderColor),
                            modifier = Modifier
                                .height(46.dp)
                                .clickable { onTabSelected(index) }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = item.selectedIcon,
                                    contentDescription = item.title,
                                    tint = activeIconColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = item.title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onTabSelected(index) }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = item.unselectedIcon,
                                contentDescription = item.title,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.title,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                }
            }
        }
    }
}


