package com.example

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
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
import com.example.course.AnimatedLessonsViewModel
import com.example.course.AnimatedLessonsViewModelFactory
import com.example.course.ChapterExamViewModel
import com.example.course.ChapterExamViewModelFactory
import com.example.home.HomeViewModel
import com.example.home.HomeViewModelFactory
import com.example.profile.EditProfileViewModel
import com.example.profile.EditProfileViewModelFactory
import com.example.syllabus.ChangeSyllabusViewModel
import com.example.syllabus.ChangeSyllabusViewModelFactory
import com.example.quiz.PracticeQuizViewModel
import com.example.quiz.PracticeQuizViewModelFactory
import com.example.database.AppDatabase
import com.example.database.SavedItemRepository
import com.example.reportcard.ReportCardViewModel
import com.example.reportcard.ReportCardViewModelFactory
import com.example.saved.SavedViewModel
import com.example.saved.SavedViewModelFactory
import com.example.smartnotes.SmartNotesViewModel
import com.example.smartnotes.SmartNotesViewModelFactory
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
    const val SAVED_ITEMS = "saved_items"
    const val DOWNLOADS = "downloads"
    const val REPORT_CARD = "report_card?programId={programId}&programTitle={programTitle}&phaseId={phaseId}"
    const val SMART_NOTES = "smart_notes/{subjectCode}?title={title}&color={color}&phaseId={phaseId}"
    const val CHAPTER_RESOURCES = "chapter_resources/{chapterId}?name={name}&subjectCode={subjectCode}&phaseId={phaseId}"
    const val SUBJECT_CHAPTERS = "subject_chapters/{subjectCode}?title={title}&color={color}"
    const val CHAPTER_LESSONS = "chapter_lessons/{chapterId}?name={name}&status={status}&initialTab={initialTab}&subjectCode={subjectCode}&subjectTitle={subjectTitle}&subjectColor={subjectColor}"
    const val LESSON_DETAIL_PLAYER = "lesson_detail_player"
    const val VIDEO_PLAYER = "video_player?url={url}&title={title}&subject={subject}&color={color}&isLive={isLive}"
    const val ROUTINE_FULL = "routine_full"
    const val COURSE_ENROLLMENT_DETAILS = "course_enrollment_details"
    const val ANIMATED_CHAPTERS = "animated_chapters/{subjectCode}?title={title}"
    const val ANIMATED_TOPICS = "animated_topics/{chapterId}?name={name}&subjectCode={subjectCode}"
    const val CHAPTER_EXAM = "chapter_exam/{sessionId}?lessonId={lessonId}&title={title}&chapter={chapter}"
    const val PRACTICE_QUIZ_CHAPTERS = "practice_quiz_chapters/{subjectCode}?title={title}&color={color}&chapterId={chapterId}&chapterName={chapterName}"
    const val PRACTICE_QUIZ_COUNT = "practice_quiz_count"
    const val PRACTICE_QUIZ_PLAYER = "practice_quiz_player/{sessionId}"
    const val PRACTICE_QUIZ_RESULT = "practice_quiz_result/{sessionId}"
    const val PRACTICE_QUIZ_FEEDBACK = "practice_quiz_feedback/{sessionId}"
}

// ============================================================
//  NEW: Premium Navigation Transition System
//  লজিক অপরিবর্তিত — শুধু স্ক্রিন বদলের অ্যানিমেশন যোগ হয়েছে
// ============================================================

private data class NavTransitions(
    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition
)

private object NavAnim {
    /** Auth flow: নিচ থেকে হালকা ভেসে ওঠা + fade */
    val auth = NavTransitions(
        enter = {
            slideInVertically(tween(380, easing = FastOutSlowInEasing)) { it / 7 } +
                    fadeIn(tween(340))
        },
        exit = { fadeOut(tween(220)) },
        popEnter = { fadeIn(tween(280)) },
        popExit = {
            slideOutVertically(tween(320, easing = FastOutSlowInEasing)) { it / 7 } +
                    fadeOut(tween(260))
        }
    )

    /** Home: soft scale + fade — প্রিমিয়াম landing feel */
    val home = NavTransitions(
        enter = {
            scaleIn(tween(380, easing = FastOutSlowInEasing), initialScale = 0.93f) +
                    fadeIn(tween(340))
        },
        exit = { fadeOut(tween(220)) },
        popEnter = { fadeIn(tween(300)) },
        popExit = {
            scaleOut(tween(300, easing = FastOutSlowInEasing), targetScale = 0.95f) +
                    fadeOut(tween(280))
        }
    )

    /** Main flow: ডান থেকে slide + scale, back-এ বামে slide */
    val forward = NavTransitions(
        enter = {
            slideInHorizontally(tween(360, easing = FastOutSlowInEasing)) { it / 5 } +
                    fadeIn(tween(300)) +
                    scaleIn(tween(360, easing = FastOutSlowInEasing), initialScale = 0.97f)
        },
        exit = {
            slideOutHorizontally(tween(280)) { -it / 9 } +
                    fadeOut(tween(220))
        },
        popEnter = {
            slideInHorizontally(tween(300)) { -it / 9 } +
                    fadeIn(tween(260))
        },
        popExit = {
            slideOutHorizontally(tween(340, easing = FastOutSlowInEasing)) { it / 5 } +
                    fadeOut(tween(260))
        }
    )

    /** Immersive: Video/Exam/Player — পুরো নিচ থেকে ওঠে, back-এ নামে */
    val immersive = NavTransitions(
        enter = {
            slideInVertically(tween(400, easing = FastOutSlowInEasing)) { it } +
                    fadeIn(tween(300))
        },
        exit = { fadeOut(tween(200)) },
        popEnter = { fadeIn(tween(260)) },
        popExit = {
            slideOutVertically(tween(360, easing = FastOutSlowInEasing)) { it } +
                    fadeOut(tween(300))
        }
    )
}

/**
 * Reusable animated composable — সব রাউটে একই লজিক, ভিন্ন অ্যানিমেশন
 */
private fun NavGraphBuilder.animatedComposable(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    anim: NavTransitions = NavAnim.forward,
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit
) {
    composable(
        route = route,
        arguments = arguments,
        enterTransition = anim.enter,
        exitTransition = anim.exit,
        popEnterTransition = anim.popEnter,
        popExitTransition = anim.popExit,
        content = { entry -> content(entry) }
    )
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

    val animatedLessonsViewModel: AnimatedLessonsViewModel = viewModel(
        factory = AnimatedLessonsViewModelFactory(apiService, sessionManager)
    )

    val appDatabase = remember { AppDatabase.getDatabase(context) }
    val savedItemRepository = remember { SavedItemRepository(appDatabase.savedItemDao()) }

    val chapterExamViewModel: ChapterExamViewModel = viewModel(
        factory = ChapterExamViewModelFactory(apiService, sessionManager)
    )

    val practiceQuizViewModel: PracticeQuizViewModel = viewModel(
        factory = PracticeQuizViewModelFactory(apiService, sessionManager, savedItemRepository)
    )

    val savedViewModel: SavedViewModel = viewModel(
        factory = SavedViewModelFactory(savedItemRepository)
    )

    val smartNotesViewModel: SmartNotesViewModel = viewModel(
        factory = SmartNotesViewModelFactory(apiService, sessionManager)
    )

    val authState by authViewModel.authState.collectAsState()
    var showSessionExpiredDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        sessionManager.unauthorizedEvent.collect {
            val currentRoute = navController.currentBackStackEntry?.destination?.route
            if (currentRoute == null || !currentRoute.startsWith(Routes.LOGIN)) {
                showSessionExpiredDialog = true
            }
        }
    }

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
        animatedComposable(Routes.LOGIN, anim = NavAnim.auth) {
            LoginScreen(
                viewModel = authViewModel,
                authState = authState,
                sessionManager = sessionManager,
                onNavigateToPin = { phone ->
                    navController.navigate("pin/$phone")
                },
                onNavigateToOtp = { phone, authType ->
                    navController.navigate("otp/$phone/$authType")
                },
                onLoginSuccess = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        animatedComposable(Routes.PIN, anim = NavAnim.auth) { backStackEntry ->
            val phone = backStackEntry.arguments?.getString("phone") ?: ""
            PinScreen(
                phone = phone,
                viewModel = authViewModel,
                authState = authState,
                onLoginSuccess = {
                    homeViewModel.loadData()
                    courseViewModel.loadSubjects(forceRefresh = true)
                    sessionManager.setJustSignedUp(false)
                    sessionManager.setAccountComplete(true)
                    navController.navigate(Routes.HOME) {
                        popUpTo(0) { inclusive = true }
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

        animatedComposable(Routes.OTP, anim = NavAnim.auth) { backStackEntry ->
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

        animatedComposable(Routes.SET_PIN, anim = NavAnim.auth) { backStackEntry ->
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

        animatedComposable(Routes.RESET_SUCCESS, anim = NavAnim.auth) { backStackEntry ->
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

        animatedComposable(Routes.HOME, anim = NavAnim.home) {
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
                onNavigateToSavedItems = {
                    navController.navigate(Routes.SAVED_ITEMS)
                },
                onNavigateToDownloads = {
                    navController.navigate(Routes.DOWNLOADS)
                },
                onNavigateToReportCard = { programId, programTitle, phaseId ->
                    val encTitle = if (!programTitle.isNullOrBlank()) URLEncoder.encode(programTitle, "UTF-8") else ""
                    val pId = programId ?: ""
                    val phId = phaseId ?: ""
                    navController.navigate("report_card?programId=$pId&programTitle=$encTitle&phaseId=$phId")
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

        animatedComposable(Routes.ROUTINE_FULL, anim = NavAnim.forward) {
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

        animatedComposable(Routes.EDIT_PROFILE, anim = NavAnim.forward) {
            val editProfileViewModel: EditProfileViewModel = viewModel(
                factory = EditProfileViewModelFactory(apiService, sessionManager)
            )
            EditProfileScreen(
                viewModel = editProfileViewModel,
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(Routes.HOME) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            )
        }

        animatedComposable(Routes.CHANGE_SYLLABUS, anim = NavAnim.forward) {
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

        animatedComposable(Routes.COURSE_ENROLLMENT_DETAILS, anim = NavAnim.forward) {
            com.example.ui.screens.CourseEnrollmentDetailsScreen(
                apiService = apiService,
                sessionManager = sessionManager,
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        animatedComposable(
            route = Routes.SUBJECT_CHAPTERS,
            anim = NavAnim.forward,
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
                onNavigateToAnimatedChapters = { sCode, sTitle ->
                    val encodedTitle = URLEncoder.encode(sTitle, "UTF-8")
                    navController.navigate("animated_chapters/$sCode?title=$encodedTitle")
                },
                onNavigateToPracticeQuiz = { sCode, sTitle, sColor ->
                    val encodedTitle = URLEncoder.encode(sTitle, "UTF-8")
                    val encodedColor = URLEncoder.encode(sColor ?: "", "UTF-8")
                    navController.navigate("practice_quiz_chapters/$sCode?title=$encodedTitle&color=$encodedColor&chapterId=&chapterName=")
                },
                onNavigateToSmartNotes = { sCode, sTitle, sColor, phId ->
                    val encodedTitle = URLEncoder.encode(sTitle, "UTF-8")
                    val encodedColor = URLEncoder.encode(sColor ?: "", "UTF-8")
                    val ph = phId ?: ""
                    navController.navigate("smart_notes/$sCode?title=$encodedTitle&color=$encodedColor&phaseId=$ph")
                },
                onChapterClick = { chapterId, chapterName, chapterStatus, initialTab ->
                    val encodedName = URLEncoder.encode(chapterName, "UTF-8")
                    val encodedStatus = URLEncoder.encode(chapterStatus, "UTF-8")
                    val encodedSubCode = URLEncoder.encode(subjectCode, "UTF-8")
                    val encodedSubTitle = URLEncoder.encode(title, "UTF-8")
                    val encodedSubColor = URLEncoder.encode(color ?: "", "UTF-8")
                    navController.navigate("chapter_lessons/$chapterId?name=$encodedName&status=$encodedStatus&initialTab=$initialTab&subjectCode=$encodedSubCode&subjectTitle=$encodedSubTitle&subjectColor=$encodedSubColor")
                },
                onPlayVideo = { videoUrl, videoTitle, subjectName, subjectColor, isLive ->
                    val encodedUrl = URLEncoder.encode(videoUrl, "UTF-8")
                    val encodedTitle = URLEncoder.encode(videoTitle, "UTF-8")
                    val encodedSubject = URLEncoder.encode(subjectName, "UTF-8")
                    val encodedColor = URLEncoder.encode(subjectColor, "UTF-8")
                    navController.navigate("video_player?url=$encodedUrl&title=$encodedTitle&subject=$encodedSubject&color=$encodedColor&isLive=$isLive")
                }
            )
        }

        animatedComposable(
            route = Routes.CHAPTER_LESSONS,
            anim = NavAnim.forward,
            arguments = listOf(
                navArgument("chapterId") { type = NavType.StringType },
                navArgument("name") { type = NavType.StringType; defaultValue = "" },
                navArgument("status") { type = NavType.StringType; defaultValue = "" },
                navArgument("initialTab") { type = NavType.IntType; defaultValue = 0 },
                navArgument("subjectCode") { type = NavType.StringType; defaultValue = "" },
                navArgument("subjectTitle") { type = NavType.StringType; defaultValue = "" },
                navArgument("subjectColor") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val chapterId = backStackEntry.arguments?.getString("chapterId") ?: ""
            val name = backStackEntry.arguments?.getString("name")?.let { URLDecoder.decode(it, "UTF-8") } ?: ""
            val status = backStackEntry.arguments?.getString("status")?.let { URLDecoder.decode(it, "UTF-8") } ?: ""
            val initialTab = backStackEntry.arguments?.getInt("initialTab") ?: 0
            val subjectCode = backStackEntry.arguments?.getString("subjectCode")?.let { URLDecoder.decode(it, "UTF-8") } ?: ""
            val subjectTitle = backStackEntry.arguments?.getString("subjectTitle")?.let { URLDecoder.decode(it, "UTF-8") } ?: ""
            val subjectColor = backStackEntry.arguments?.getString("subjectColor")?.let { URLDecoder.decode(it, "UTF-8") }

            ChapterLessonsScreen(
                chapterId = chapterId,
                chapterName = name,
                chapterStatus = status,
                initialTab = initialTab,
                subjectCode = subjectCode,
                subjectTitle = subjectTitle,
                subjectColorHex = subjectColor,
                viewModel = courseViewModel,
                onBack = {
                    navController.popBackStack()
                },
                onNavigateToAnimatedTopics = { cId, cName, sCode ->
                    val encodedName = URLEncoder.encode(cName, "UTF-8")
                    val encodedCode = URLEncoder.encode(sCode, "UTF-8")
                    navController.navigate("animated_topics/$cId?name=$encodedName&subjectCode=$encodedCode")
                },
                onNavigateToPracticeQuiz = { sCode, sTitle, sColor, cId, cName ->
                    val encodedTitle = URLEncoder.encode(sTitle, "UTF-8")
                    val encodedColor = URLEncoder.encode(sColor ?: "", "UTF-8")
                    val encodedChapterId = URLEncoder.encode(cId, "UTF-8")
                    val encodedChapterName = URLEncoder.encode(cName, "UTF-8")
                    navController.navigate("practice_quiz_chapters/$sCode?title=$encodedTitle&color=$encodedColor&chapterId=$encodedChapterId&chapterName=$encodedChapterName")
                },
                onNavigateToExam = { sessionId, lessonId, title, chapter ->
                    val encodedTitle = URLEncoder.encode(title, "UTF-8")
                    val encodedChapter = URLEncoder.encode(chapter, "UTF-8")
                    navController.navigate("chapter_exam/$sessionId?lessonId=$lessonId&title=$encodedTitle&chapter=$encodedChapter")
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

        animatedComposable(
            route = Routes.ANIMATED_CHAPTERS,
            anim = NavAnim.forward,
            arguments = listOf(
                navArgument("subjectCode") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val subjectCode = backStackEntry.arguments?.getString("subjectCode") ?: ""
            val title = backStackEntry.arguments?.getString("title")?.let { URLDecoder.decode(it, "UTF-8") } ?: ""

            AnimatedChaptersScreen(
                subjectCode = subjectCode,
                subjectTitle = title,
                viewModel = animatedLessonsViewModel,
                onBack = { navController.popBackStack() },
                onChapterClick = { chapterId, chapterName, sCode ->
                    val encodedName = URLEncoder.encode(chapterName, "UTF-8")
                    val encodedCode = URLEncoder.encode(sCode, "UTF-8")
                    navController.navigate("animated_topics/$chapterId?name=$encodedName&subjectCode=$encodedCode")
                }
            )
        }

        animatedComposable(
            route = Routes.ANIMATED_TOPICS,
            anim = NavAnim.forward,
            arguments = listOf(
                navArgument("chapterId") { type = NavType.StringType },
                navArgument("name") { type = NavType.StringType; defaultValue = "" },
                navArgument("subjectCode") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val chapterId = backStackEntry.arguments?.getString("chapterId") ?: ""
            val name = backStackEntry.arguments?.getString("name")?.let { URLDecoder.decode(it, "UTF-8") } ?: ""
            val subjectCode = backStackEntry.arguments?.getString("subjectCode")?.let { URLDecoder.decode(it, "UTF-8") } ?: ""

            AnimatedTopicsScreen(
                chapterId = chapterId,
                chapterName = name,
                subjectCode = subjectCode,
                viewModel = animatedLessonsViewModel,
                onBack = { navController.popBackStack() },
                onPlayVideo = { videoUrl, title, subjectName ->
                    val encodedUrl = URLEncoder.encode(videoUrl, "UTF-8")
                    val encodedTitle = URLEncoder.encode(title, "UTF-8")
                    val encodedSubject = URLEncoder.encode(subjectName, "UTF-8")
                    navController.navigate("video_player?url=$encodedUrl&title=$encodedTitle&subject=$encodedSubject")
                }
            )
        }

        animatedComposable(
            route = Routes.CHAPTER_EXAM,
            anim = NavAnim.immersive,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.StringType },
                navArgument("lessonId") { type = NavType.StringType; defaultValue = "" },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
                navArgument("chapter") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
            val lessonId = backStackEntry.arguments?.getString("lessonId") ?: ""
            val title = backStackEntry.arguments?.getString("title")?.let { URLDecoder.decode(it, "UTF-8") } ?: ""
            val chapter = backStackEntry.arguments?.getString("chapter")?.let { URLDecoder.decode(it, "UTF-8") } ?: ""

            ChapterExamScreen(
                sessionId = sessionId,
                lessonId = lessonId,
                examTitle = title,
                chapterName = chapter,
                viewModel = chapterExamViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        animatedComposable(Routes.LESSON_DETAIL_PLAYER, anim = NavAnim.immersive) {
            val courseUiState by courseViewModel.uiState.collectAsState()
            LessonDetailPlayerScreen(
                lesson = courseUiState.selectedLesson,
                subjectName = courseUiState.selectedSubjectTitle,
                subjectColorHex = courseUiState.selectedSubjectColor,
                socketManager = courseViewModel.liveSocketManager,
                onRefreshLesson = {
                    courseViewModel.reloadSelectedLesson()
                },
                onJoinLiveClass = { lesson ->
                    courseViewModel.joinLiveClass(lesson)
                },
                onBack = {
                    courseViewModel.disconnectLiveSocket()
                    navController.popBackStack()
                }
            )
        }

        animatedComposable(
            route = Routes.VIDEO_PLAYER,
            anim = NavAnim.immersive,
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

        animatedComposable(Routes.PROFILE, anim = NavAnim.forward) {
            ProfileScreen(
                viewModel = authViewModel,
                authState = authState,
                sessionManager = sessionManager,
                onNavigateToEditProfile = {
                    navController.navigate(Routes.EDIT_PROFILE)
                },
                onNavigateToSavedItems = {
                    navController.navigate(Routes.SAVED_ITEMS)
                },
                onNavigateToDownloads = {
                    navController.navigate(Routes.DOWNLOADS)
                },
                onLogout = {
                    authViewModel.logout()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // ==========================================
        // Practice Quiz Navigation Flow
        // ==========================================

        animatedComposable(
            route = Routes.PRACTICE_QUIZ_CHAPTERS,
            anim = NavAnim.forward,
            arguments = listOf(
                navArgument("subjectCode") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
                navArgument("color") { type = NavType.StringType; defaultValue = "" },
                navArgument("chapterId") { type = NavType.StringType; defaultValue = "" },
                navArgument("chapterName") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val subjectCode = backStackEntry.arguments?.getString("subjectCode") ?: ""
            val title = backStackEntry.arguments?.getString("title")?.let { URLDecoder.decode(it, "UTF-8") } ?: ""
            val color = backStackEntry.arguments?.getString("color")?.let { URLDecoder.decode(it, "UTF-8") }
            val chapterId = backStackEntry.arguments?.getString("chapterId")?.let { URLDecoder.decode(it, "UTF-8") }?.ifBlank { null }
            val chapterName = backStackEntry.arguments?.getString("chapterName")?.let { URLDecoder.decode(it, "UTF-8") }?.ifBlank { null }

            PracticeQuizChapterSelectionScreen(
                subjectCode = subjectCode,
                subjectTitle = title,
                subjectColorHex = color,
                targetChapterId = chapterId,
                targetChapterName = chapterName,
                viewModel = practiceQuizViewModel,
                onBack = {
                    navController.popBackStack()
                },
                onProceedToCountSelection = {
                    navController.navigate(Routes.PRACTICE_QUIZ_COUNT)
                }
            )
        }

        animatedComposable(
            route = Routes.PRACTICE_QUIZ_COUNT,
            anim = NavAnim.forward
        ) {
            PracticeQuizCountSelectionScreen(
                viewModel = practiceQuizViewModel,
                onBack = {
                    navController.popBackStack()
                },
                onNavigateToPlayer = { sessionId ->
                    navController.navigate("practice_quiz_player/$sessionId")
                }
            )
        }

        animatedComposable(
            route = Routes.PRACTICE_QUIZ_PLAYER,
            anim = NavAnim.immersive,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""

            PracticeQuizPlayerScreen(
                sessionId = sessionId,
                viewModel = practiceQuizViewModel,
                onBack = {
                    navController.popBackStack()
                },
                onNavigateToResult = { sid ->
                    navController.navigate("practice_quiz_result/$sid") {
                        popUpTo(Routes.PRACTICE_QUIZ_COUNT) { inclusive = true }
                    }
                }
            )
        }

        animatedComposable(
            route = Routes.PRACTICE_QUIZ_RESULT,
            anim = NavAnim.forward,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""

            PracticeQuizResultScreen(
                sessionId = sessionId,
                sessionManager = sessionManager,
                viewModel = practiceQuizViewModel,
                onBackToChapters = {
                    navController.popBackStack(Routes.PRACTICE_QUIZ_CHAPTERS, inclusive = true)
                },
                onNavigateToFeedback = { sid ->
                    navController.navigate("practice_quiz_feedback/$sid")
                }
            )
        }

        animatedComposable(
            route = Routes.PRACTICE_QUIZ_FEEDBACK,
            anim = NavAnim.forward,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""

            PracticeQuizFeedbackScreen(
                sessionId = sessionId,
                viewModel = practiceQuizViewModel,
                onBack = {
                    navController.popBackStack()
                },
                onDone = {
                    navController.popBackStack(Routes.PRACTICE_QUIZ_CHAPTERS, inclusive = true)
                }
            )
        }

        animatedComposable(Routes.SAVED_ITEMS, anim = NavAnim.forward) {
            SavedItemsScreen(
                viewModel = savedViewModel,
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        animatedComposable(Routes.DOWNLOADS, anim = NavAnim.forward) {
            DownloadsScreen(
                onBack = {
                    navController.popBackStack()
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

        animatedComposable(
            route = Routes.SMART_NOTES,
            anim = NavAnim.forward,
            arguments = listOf(
                navArgument("subjectCode") { type = NavType.StringType },
                navArgument("title") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = ""
                },
                navArgument("color") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = ""
                },
                navArgument("phaseId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val subjectCode = backStackEntry.arguments?.getString("subjectCode") ?: ""
            val rawTitle = backStackEntry.arguments?.getString("title") ?: ""
            val title = try {
                URLDecoder.decode(rawTitle, "UTF-8")
            } catch (_: Exception) {
                rawTitle
            }
            val rawColor = backStackEntry.arguments?.getString("color") ?: ""
            val color = try {
                URLDecoder.decode(rawColor, "UTF-8")
            } catch (_: Exception) {
                rawColor
            }
            val phaseId = backStackEntry.arguments?.getString("phaseId") ?: ""

            SmartNotesScreen(
                subjectCode = subjectCode,
                subjectTitle = title,
                subjectColorHex = color,
                phaseId = phaseId,
                viewModel = smartNotesViewModel,
                onBack = {
                    navController.popBackStack()
                },
                onNavigateToChapterResources = { chapterId, chapterName, subCode, phId ->
                    val encodedName = URLEncoder.encode(chapterName, "UTF-8")
                    navController.navigate("chapter_resources/$chapterId?name=$encodedName&subjectCode=$subCode&phaseId=$phId")
                }
            )
        }

        animatedComposable(
            route = Routes.CHAPTER_RESOURCES,
            anim = NavAnim.forward,
            arguments = listOf(
                navArgument("chapterId") { type = NavType.StringType },
                navArgument("name") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = ""
                },
                navArgument("subjectCode") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = ""
                },
                navArgument("phaseId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val chapterId = backStackEntry.arguments?.getString("chapterId") ?: ""
            val rawName = backStackEntry.arguments?.getString("name") ?: ""
            val chapterName = try {
                URLDecoder.decode(rawName, "UTF-8")
            } catch (_: Exception) {
                rawName
            }
            val subjectCode = backStackEntry.arguments?.getString("subjectCode") ?: ""
            val phaseId = backStackEntry.arguments?.getString("phaseId") ?: ""

            ChapterResourcesScreen(
                chapterId = chapterId,
                chapterName = chapterName,
                subjectCode = subjectCode,
                phaseId = phaseId,
                viewModel = smartNotesViewModel,
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        animatedComposable(
            route = Routes.REPORT_CARD,
            anim = NavAnim.forward,
            arguments = listOf(
                navArgument("programId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = ""
                },
                navArgument("programTitle") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = ""
                },
                navArgument("phaseId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val programId = backStackEntry.arguments?.getString("programId")?.ifBlank { null }
            val rawTitle = backStackEntry.arguments?.getString("programTitle") ?: ""
            val programTitle = try {
                URLDecoder.decode(rawTitle, "UTF-8").ifBlank { null }
            } catch (_: Exception) {
                rawTitle.ifBlank { null }
            }
            val phaseId = backStackEntry.arguments?.getString("phaseId")?.ifBlank { null }

            val reportCardViewModel: ReportCardViewModel = viewModel(
                factory = ReportCardViewModelFactory(apiService, sessionManager)
            )

            androidx.compose.runtime.LaunchedEffect(programId, phaseId) {
                reportCardViewModel.initialize(
                    programId = programId,
                    programTitle = programTitle,
                    initialPhaseId = phaseId
                )
            }

            ReportCardScreen(
                viewModel = reportCardViewModel,
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }

    if (showSessionExpiredDialog) {
        com.example.ui.dialogs.SessionExpiredDialog(
            onReLogin = {
                showSessionExpiredDialog = false
                sessionManager.clearSession()
                sessionManager.resetUnauthorizedNotified()
                authViewModel.logout()
                navController.navigate(Routes.LOGIN) {
                    popUpTo(0) { inclusive = true }
                }
            }
        )
    }
}
