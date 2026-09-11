package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.api.ProgramSubject
import com.example.api.VideoItem
import com.example.home.HomeUiState
import com.example.home.HomeViewModel
import com.example.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToProfile: () -> Unit,
    onNavigateToVideoPlayer: (url: String, title: String, subject: String?, color: String?, isLive: Boolean) -> Unit = { _, _, _, _, _ -> },
    onLogout: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    // Modals
    if (uiState.showCourseSwitcher) {
        CourseSwitcherBottomSheet(
            enrolledPrograms = uiState.enrolledPrograms,
            activeProgram = uiState.activeProgram,
            onSelectProgram = { program ->
                viewModel.switchActiveCourse(program)
            },
            onDismiss = {
                viewModel.setCourseSwitcherVisible(false)
            }
        )
    }

    if (uiState.showProfileDrawer) {
        ProfileDrawerSheet(
            profile = uiState.userProfile,
            onDismiss = {
                viewModel.setProfileDrawerVisible(false)
            },
            onLogout = onLogout
        )
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                // Tab 0: হোম
                NavigationBarItem(
                    selected = uiState.selectedTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("হোম", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    )
                )

                // Tab 1: এক্সপ্লোর
                NavigationBarItem(
                    selected = uiState.selectedTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    icon = { Icon(Icons.Default.Explore, contentDescription = "Explore") },
                    label = { Text("এক্সপ্লোর", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    )
                )

                // Tab 2: কোর্স
                NavigationBarItem(
                    selected = uiState.selectedTab == 2,
                    onClick = { viewModel.selectTab(2) },
                    icon = { Icon(Icons.Default.Book, contentDescription = "Courses") },
                    label = { Text("কোর্স", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    )
                )

                // Tab 3: শিখো AI
                NavigationBarItem(
                    selected = uiState.selectedTab == 3,
                    onClick = { viewModel.selectTab(3) },
                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "Shikho AI") },
                    label = { Text("শিখো AI", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF7C4DFF),
                        selectedTextColor = Color(0xFF7C4DFF),
                        indicatorColor = Color(0xFF7C4DFF).copy(alpha = 0.12f)
                    )
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (uiState.selectedTab) {
                0 -> HomeProductionDashboard(
                    uiState = uiState,
                    onOpenCourseSwitcher = { viewModel.setCourseSwitcherVisible(true) },
                    onOpenProfileDrawer = { viewModel.setProfileDrawerVisible(true) },
                    onSelectCalendarDate = { viewModel.selectCalendarDate(it) },
                    onSelectPhase = { viewModel.selectPhase(it) },
                    onRefresh = { viewModel.loadDashboardData(isRefresh = true) },
                    onOpenShikhoAi = { viewModel.selectTab(3) },
                    onOpenLesson = { lesson ->
                        val isLive = lesson.live_class?.is_on_going == true || lesson.user_activity_state == "LIVE"
                        onNavigateToVideoPlayer(
                            "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_4x3/bipbop_4x3_variant.m3u8",
                            lesson.title ?: "ক্লাস লেকচার",
                            lesson.subject_name,
                            lesson.color_code,
                            isLive
                        )
                    },
                    onOpenVideo = { video ->
                        onNavigateToVideoPlayer(
                            video.stream_url ?: "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_4x3/bipbop_4x3_variant.m3u8",
                            video.title ?: "ভিডিও লেকচার",
                            video.subject,
                            "#0072EC",
                            false
                        )
                    }
                )
                1 -> ExploreTabScreen(
                    uiState = uiState,
                    onOpenVideo = { video ->
                        onNavigateToVideoPlayer(
                            video.stream_url ?: "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_4x3/bipbop_4x3_variant.m3u8",
                            video.title ?: "ভিডিও লেকচার",
                            video.subject,
                            "#0072EC",
                            false
                        )
                    }
                )
                2 -> CoursesDetailTabScreen(uiState = uiState)
                3 -> ShikhoAiTabScreen()
            }

            // Bottom Sticky Trial Expiry Banner
            if (uiState.isTrialExpired && uiState.selectedTab == 0) {
                TrialExpiryBanner(
                    onEnrollClick = { viewModel.setCourseSwitcherVisible(true) },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

@Composable
fun HomeProductionDashboard(
    uiState: HomeUiState,
    onOpenCourseSwitcher: () -> Unit,
    onOpenProfileDrawer: () -> Unit,
    onSelectCalendarDate: (String) -> Unit,
    onSelectPhase: (com.example.api.PhaseItem) -> Unit,
    onRefresh: () -> Unit,
    onOpenShikhoAi: () -> Unit,
    onOpenLesson: (com.example.api.StudentLessonItem) -> Unit = {},
    onOpenVideo: (VideoItem) -> Unit = {}
) {
    if (uiState.isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    val profile = uiState.userProfile
    val activeProgram = uiState.activeProgram

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = if (uiState.isTrialExpired) 90.dp else 30.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        // 1. Top Bar: Brand & Course Switcher Dropdown (Left) + Crown Avatar (Right)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Brand Logo + Course Switcher Dropdown
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                            modifier = Modifier.clickable { onOpenCourseSwitcher() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.School,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = activeProgram?.title_bn ?: "কোর্স সিলেক্ট করো",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 180.dp)
                                )
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = "Switch Course",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Right: Circular User Avatar with Gold Crown Badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Box(
                            contentAlignment = Alignment.TopEnd,
                            modifier = Modifier.clickable { onOpenProfileDrawer() }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                    .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!profile?.avatar.isNullOrBlank()) {
                                    AsyncImage(
                                        model = profile?.avatar,
                                        contentDescription = "Avatar",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Text(
                                        text = (profile?.first_name?.take(1) ?: "ফ").uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 17.sp
                                    )
                                }
                            }

                            // Gold Crown
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .offset(x = 2.dp, y = (-2).dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFFB300)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("👑", fontSize = 9.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Greeting & Student Info Header
                val firstName = profile?.first_name ?: "শিক্ষার্থী"
                Text(
                    text = "হ্যালো, $firstName 👋",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(2.dp))

                val classDisplay = profile?.`class`?.display ?: "HSC 2027"
                val groupDisplay = profile?.study_group ?: "মানবিক"
                val schoolName = profile?.school?.name ?: "ঢাকা কলেজ"
                Text(
                    text = "$classDisplay - $groupDisplay • $schoolName",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // 2. Weekly Routine Calendar & Class Cards
        item {
            RoutineSection(
                calendarDays = uiState.calendarDays,
                selectedDateIso = uiState.selectedCalendarDateIso,
                lessons = uiState.filteredLessons,
                onSelectDate = onSelectCalendarDate,
                onViewAllRoutine = { /* Open full routine calendar */ },
                onOpenLesson = onOpenLesson
            )
        }

        // 3. Features Grid (2x2 Cards)
        item {
            FeaturesGrid(
                overallScorePercentage = uiState.overallScorePercentage,
                practiceLimits = uiState.practiceLimits,
                onOpenReportCard = { /* Open full analytics scorecard */ },
                onOpenPracticeQuiz = { /* Open practice quiz module */ },
                onOpenAnimatedLessons = { /* Open animated videos library */ },
                onOpenShikhoAi = onOpenShikhoAi
            )
        }

        // 4. Subjects Chips & Breakdown from Active Program
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "বিষয়ভিত্তিক প্রস্তুতি",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${activeProgram?.subjects?.size ?: 0}টি বিষয়",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(activeProgram?.subjects ?: emptyList()) { subject ->
                        SubjectPillCard(subject = subject)
                    }
                }
            }
        }

        // 5. Course Progress / Quarters Timeline
        item {
            CourseProgressTimeline(
                phases = uiState.phases,
                activePhase = uiState.activePhase,
                onSelectPhase = onSelectPhase,
                onEnrollPhase = { onOpenCourseSwitcher() }
            )
        }

        // 6. Popular Video Lectures Carousel
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "জনপ্রিয় ভিডিও লেকচার",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "সব ভিডিও >",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(uiState.popularVideos) { video ->
                        DashboardVideoCard(
                            video = video,
                            onClick = { onOpenVideo(video) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SubjectPillCard(subject: ProgramSubject) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val pillColor = remember(subject.color_code, primaryColor) {
        try {
            if (!subject.color_code.isNullOrBlank()) {
                Color(android.graphics.Color.parseColor(subject.color_code))
            } else {
                primaryColor
            }
        } catch (_: Exception) {
            primaryColor
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
        modifier = Modifier.width(140.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(pillColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.MenuBook,
                    contentDescription = null,
                    tint = pillColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = subject.display_bn ?: subject.code ?: "বিষয়",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "সিলেবাস ও নোট",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
        }
    }
}

@Composable
fun DashboardVideoCard(
    video: VideoItem,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .width(220.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(115.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            ) {
                if (!video.thumbnail.isNullOrBlank()) {
                    AsyncImage(
                        model = video.thumbnail,
                        contentDescription = video.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .align(Alignment.Center)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                if (!video.duration.isNullOrBlank()) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp),
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Black.copy(alpha = 0.75f)
                    ) {
                        Text(
                            text = video.duration,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(12.dp)) {
                if (!video.subject.isNullOrBlank()) {
                    Text(
                        text = video.subject,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }

                Text(
                    text = video.title ?: "ভিডিও লেকচার",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = video.teacher_name ?: "শিক্ষক",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    if (!video.view_count.isNullOrBlank()) {
                        Text(
                            text = video.view_count,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// Explore Screen
// -------------------------------------------------------------
@Composable
fun ExploreTabScreen(
    uiState: HomeUiState,
    onOpenVideo: (VideoItem) -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "এক্সপ্লোর লার্নিং 🚀",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "সকল বিষয়, অধ্যায়ভিত্তিক কুইজ এবং ভিডিও লেকচার",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
            )
        }

        items(uiState.popularVideos) { video ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenVideo(video) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    ) {
                        if (!video.thumbnail.isNullOrBlank()) {
                            AsyncImage(
                                model = video.thumbnail,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = video.subject ?: "বিষয়",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = video.title ?: "লেকচার",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${video.teacher_name ?: "শিক্ষক"} • ${video.duration ?: ""}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// Courses Screen
// -------------------------------------------------------------
@Composable
fun CoursesDetailTabScreen(uiState: HomeUiState) {
    val subjects = uiState.activeProgram?.subjects ?: emptyList()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "আমার বিষয় ও সিলেবাস 📚",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "${uiState.activeProgram?.title_bn ?: "এইচএসসি ২০২৭"} এর বিষয়ভিত্তিক পাঠ্যক্রম",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
            )
        }

        items(subjects) { subject ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = subject.display_bn ?: subject.code ?: "বিষয়",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "লাইভ ক্লাস, প্র্যাকটিস ও লেকচার নোটস",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }

                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Open",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// Shikho AI Tab Screen
// -------------------------------------------------------------
@Composable
fun ShikhoAiTabScreen() {
    var queryText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF7C4DFF), Color(0xFF536DFE))
                        )
                    )
                    .padding(20.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "SHIKHO AI ASSISTANT",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "পড়ালেখার যেকোনো ডাউট বা প্রশ্নের সমাধান নাও মুহূর্তেই!",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 13.sp
                    )
                }
            }

            Text(
                text = "সচরাচর জিজ্ঞাসিত প্রশ্নসমূহ:",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            val suggestedPrompts = listOf(
                "পৌরনীতি ১ম পত্রের ৩য় অধ্যায়ের মূল পয়েন্টগুলো বলো",
                "চাহিদা ও যোগানের স্থিতিস্থাপকতা কীভাবে নির্ণয় করে?",
                "রবীন্দ্রনাথ ঠাকুরের 'অপরিচিতা' গল্পের মূলভাব কী?",
                "এইচএসসি পরীক্ষার জন্য পড়ার রুটিন তৈরি করে দাও"
            )

            suggestedPrompts.forEach { prompt ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { queryText = prompt }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = prompt,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = Color(0xFF7C4DFF),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Input Box
        OutlinedTextField(
            value = queryText,
            onValueChange = { queryText = it },
            placeholder = { Text("তোমার প্রশ্ন বা ডাউট এখানে লেখো...") },
            trailingIcon = {
                IconButton(
                    onClick = { /* Process AI doubt query */ },
                    enabled = queryText.isNotBlank()
                ) {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = "Send",
                        tint = if (queryText.isNotBlank()) Color(0xFF7C4DFF) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }
            },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        )
    }
}
