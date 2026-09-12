package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
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
    onLogout: () -> Unit = {}
) {
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    val currentTab = NavigationItem.items[selectedIndex]

    Scaffold(
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 12.dp, spotColor = Color.Black.copy(alpha = 0.08f)),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                border = androidx.compose.foundation.BorderStroke(
                    width = 0.75.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )
            ) {
                NavigationBar(
                    modifier = Modifier.navigationBarsPadding(),
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp,
                    windowInsets = WindowInsets(0, 0, 0, 0)
                ) {
                    NavigationItem.items.forEachIndexed { index, item ->
                        val isSelected = selectedIndex == index
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                selectedIndex = index
                                if (index == 1) {
                                    courseViewModel.loadSubjects()
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.title,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = item.title,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            )
                        )
                    }
                }
            }
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
                            onOpenCourse = {
                                val activeProg = homeViewModel.uiState.value.activeProgram
                                if (activeProg != null) {
                                    courseViewModel.switchProgram(
                                        newProgramId = activeProg.id,
                                        newProgramTitle = activeProg.title_bn ?: "",
                                        batchId = activeProg.enrollment_details?.batch_id,
                                        classCode = activeProg.classes?.firstOrNull()
                                    )
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

