package com.example

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.api.ShikhoApiService
import com.example.auth.AuthViewModel
import com.example.auth.AuthViewModelFactory
import com.example.auth.SessionManager
import com.example.course.CourseViewModel
import com.example.course.CourseViewModelFactory
import com.example.home.HomeViewModel
import com.example.home.HomeViewModelFactory
import com.example.profile.EditProfileViewModel
import com.example.profile.EditProfileViewModelFactory
import com.example.syllabus.ChangeSyllabusViewModel
import com.example.syllabus.ChangeSyllabusViewModelFactory
import com.example.ui.screens.*
import java.net.URLDecoder
import java.net.URLEncoder

object Routes {
    const val LOGIN = "login"
    const val PIN = "pin/{phone}"
    const val OTP = "otp/{phone}/{authType}"
    const val SET_PIN = "set_pin/{phone}"
    const val RESET_SUCCESS = "reset_success/{phone}"
    const val HOME = "home"
    const val PROFILE = "profile"
    const val EDIT_PROFILE = "edit_profile"
    const val CHANGE_SYLLABUS = "change_syllabus"
    const val SUBJECT_CHAPTERS = "subject_chapters/{subjectCode}?title={title}&color={color}"
    const val CHAPTER_LESSONS = "chapter_lessons/{chapterId}?name={name}&status={status}"
    const val LESSON_DETAIL_PLAYER = "lesson_detail_player"
    const val VIDEO_PLAYER = "video_player?url={url}&title={title}&subject={subject}&color={color}&isLive={isLive}"
    const val ROUTINE_FULL = "routine_full"
    const val COURSE_ENROLLMENT_DETAILS = "course_enrollment_details"
}

@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val context = LocalContext.current
    
    val sessionManager = remember { SessionManager(context) }
    val apiService = remember { ShikhoApiService.create(sessionManager) }
    
    val authViewModel: AuthViewModel = viewModel(
        factory = AuthViewModelFactory(apiService, sessionManager)
    )
    
    val homeViewModel: HomeViewModel = viewModel(
        factory = HomeViewModelFactory(apiService, sessionManager)
    )

    val courseViewModel: CourseViewModel = viewModel(
        factory = CourseViewModelFactory(apiService, sessionManager)
    )
    
    val authState by authViewModel.authState.collectAsState()
    
    val startDestination = if (sessionManager.getAccessToken() != null) {
        Routes.HOME
    } else {
        Routes.LOGIN
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Routes.LOGIN) {
            LoginScreen(
                viewModel = authViewModel,
                authState = authState,
                onNavigateToPin = { phone ->
                    navController.navigate("pin/$phone")
                },
                onNavigateToOtp = { phone, authType ->
                    navController.navigate("otp/$phone/$authType")
                }
            )
        }
        
        composable(Routes.PIN) { backStackEntry ->
            val phone = backStackEntry.arguments?.getString("phone") ?: ""
            PinScreen(
                phone = phone,
                viewModel = authViewModel,
                authState = authState,
                onLoginSuccess = {
                    homeViewModel.loadData()
                    courseViewModel.loadSubjects(forceRefresh = true)
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onForgotPasswordNavigate = { authType ->
                    navController.navigate("otp/$phone/$authType")
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(Routes.OTP) { backStackEntry ->
            val phone = backStackEntry.arguments?.getString("phone") ?: ""
            val authType = backStackEntry.arguments?.getString("authType") ?: "signup"
            OtpScreen(
                phone = phone,
                authType = authType,
                viewModel = authViewModel,
                authState = authState,
                onNavigateToSetPin = { p ->
                    navController.navigate("set_pin/$p") {
                        popUpTo(Routes.LOGIN)
                    }
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.SET_PIN) { backStackEntry ->
            val phone = backStackEntry.arguments?.getString("phone") ?: ""
            SetPinScreen(
                phone = phone,
                viewModel = authViewModel,
                authState = authState,
                onPinSetSuccess = { p ->
                    navController.navigate("reset_success/$p") {
                        popUpTo(Routes.LOGIN)
                    }
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.RESET_SUCCESS) { backStackEntry ->
            val phone = backStackEntry.arguments?.getString("phone") ?: ""
            ResetSuccessScreen(
                phone = phone,
                onLoginClick = { p ->
                    navController.navigate("pin/$p") {
                        popUpTo(Routes.LOGIN)
                    }
                }
            )
        }

        composable(Routes.HOME) {
            MainContainerScreen(
                homeViewModel = homeViewModel,
                courseViewModel = courseViewModel,
                sessionManager = sessionManager,
                onNavigateToSubjectChapters = { subjectCode, subjectTitle, subjectColor ->
                    val encodedTitle = URLEncoder.encode(subjectTitle, "UTF-8")
                    val encodedColor = URLEncoder.encode(subjectColor, "UTF-8")
                    navController.navigate("subject_chapters/$subjectCode?title=$encodedTitle&color=$encodedColor")
                },
                onNavigateToEditProfile = {
                    navController.navigate(Routes.EDIT_PROFILE)
                },
                onNavigateToChangeSyllabus = {
                    navController.navigate(Routes.CHANGE_SYLLABUS)
                },
                onNavigateToProfile = {
                    navController.navigate(Routes.PROFILE)
                },
                onNavigateToCourseEnrollment = {
                    navController.navigate(Routes.COURSE_ENROLLMENT_DETAILS)
                },
                onNavigateToFullRoutine = {
                    navController.navigate(Routes.ROUTINE_FULL)
                },
                onOpenLessonDetail = { lesson ->
                    courseViewModel.selectLesson(lesson)
                    navController.navigate(Routes.LESSON_DETAIL_PLAYER)
                },
                onLogout = {
                    authViewModel.logout()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.ROUTINE_FULL) {
            FullRoutineScreen(
                viewModel = homeViewModel,
                onBack = {
                    navController.popBackStack()
                },
                onOpenLessonDetail = { lesson ->
                    courseViewModel.selectLesson(lesson)
                    navController.navigate(Routes.LESSON_DETAIL_PLAYER)
                }
            )
        }

        composable(Routes.EDIT_PROFILE) {
            val editProfileViewModel: EditProfileViewModel = viewModel(
                factory = EditProfileViewModelFactory(apiService, sessionManager)
            )
            EditProfileScreen(
                viewModel = editProfileViewModel,
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.CHANGE_SYLLABUS) {
            val changeSyllabusViewModel: ChangeSyllabusViewModel = viewModel(
                factory = ChangeSyllabusViewModelFactory(apiService, sessionManager)
            )
            ChangeSyllabusScreen(
                viewModel = changeSyllabusViewModel,
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.COURSE_ENROLLMENT_DETAILS) {
            com.example.ui.screens.CourseEnrollmentDetailsScreen(
                apiService = apiService,
                sessionManager = sessionManager,
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Routes.SUBJECT_CHAPTERS,
            arguments = listOf(
                navArgument("subjectCode") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
                navArgument("color") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val subjectCode = backStackEntry.arguments?.getString("subjectCode") ?: ""
            val title = backStackEntry.arguments?.getString("title")?.let { URLDecoder.decode(it, "UTF-8") } ?: ""
            val color = backStackEntry.arguments?.getString("color")?.let { URLDecoder.decode(it, "UTF-8") }

            SubjectChaptersScreen(
                subjectCode = subjectCode,
                subjectTitle = title,
                subjectColorHex = color,
                viewModel = courseViewModel,
                onBack = {
                    navController.popBackStack()
                },
                onChapterClick = { chapterId, chapterName, chapterStatus ->
                    val encodedName = URLEncoder.encode(chapterName, "UTF-8")
                    val encodedStatus = URLEncoder.encode(chapterStatus, "UTF-8")
                    navController.navigate("chapter_lessons/$chapterId?name=$encodedName&status=$encodedStatus")
                }
            )
        }

        composable(
            route = Routes.CHAPTER_LESSONS,
            arguments = listOf(
                navArgument("chapterId") { type = NavType.StringType },
                navArgument("name") { type = NavType.StringType; defaultValue = "" },
                navArgument("status") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val chapterId = backStackEntry.arguments?.getString("chapterId") ?: ""
            val name = backStackEntry.arguments?.getString("name")?.let { URLDecoder.decode(it, "UTF-8") } ?: ""
            val status = backStackEntry.arguments?.getString("status")?.let { URLDecoder.decode(it, "UTF-8") } ?: ""

            ChapterLessonsScreen(
                chapterId = chapterId,
                chapterName = name,
                chapterStatus = status,
                viewModel = courseViewModel,
                onBack = {
                    navController.popBackStack()
                },
                onOpenLessonDetail = { _ ->
                    navController.navigate(Routes.LESSON_DETAIL_PLAYER)
                },
                onPlayVideo = { videoUrl, title, subjectName, subjectColor, isLive ->
                    val encodedUrl = URLEncoder.encode(videoUrl, "UTF-8")
                    val encodedTitle = URLEncoder.encode(title, "UTF-8")
                    val encodedSubject = URLEncoder.encode(subjectName, "UTF-8")
                    val encodedColor = URLEncoder.encode(subjectColor, "UTF-8")
                    navController.navigate("video_player?url=$encodedUrl&title=$encodedTitle&subject=$encodedSubject&color=$encodedColor&isLive=$isLive")
                }
            )
        }

        composable(Routes.LESSON_DETAIL_PLAYER) {
            val courseUiState by courseViewModel.uiState.collectAsState()
            LessonDetailPlayerScreen(
                lesson = courseUiState.selectedLesson,
                subjectName = courseUiState.selectedSubjectTitle,
                subjectColorHex = courseUiState.selectedSubjectColor,
                onRefreshLesson = {
                    courseViewModel.reloadSelectedLesson()
                },
                onJoinLiveClass = { lesson ->
                    courseViewModel.joinLiveClass(lesson)
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Routes.VIDEO_PLAYER,
            arguments = listOf(
                navArgument("url") { type = NavType.StringType; defaultValue = "" },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
                navArgument("subject") { type = NavType.StringType; defaultValue = "" },
                navArgument("color") { type = NavType.StringType; defaultValue = "" },
                navArgument("isLive") { type = NavType.BoolType; defaultValue = false }
            )
        ) { backStackEntry ->
            val url = backStackEntry.arguments?.getString("url")?.let { URLDecoder.decode(it, "UTF-8") } ?: ""
            val title = backStackEntry.arguments?.getString("title")?.let { URLDecoder.decode(it, "UTF-8") } ?: ""
            val subject = backStackEntry.arguments?.getString("subject")?.let { URLDecoder.decode(it, "UTF-8") }
            val color = backStackEntry.arguments?.getString("color")?.let { URLDecoder.decode(it, "UTF-8") }
            val isLive = backStackEntry.arguments?.getBoolean("isLive") ?: false

            VideoPlayerScreen(
                videoUrl = url,
                title = title,
                subjectName = subject,
                subjectColorHex = color,
                isLive = isLive,
                onBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(Routes.PROFILE) {
            ProfileScreen(
                viewModel = authViewModel,
                authState = authState,
                onNavigateToEditProfile = {
                    navController.navigate(Routes.EDIT_PROFILE)
                },
                onLogout = {
                    authViewModel.logout()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
