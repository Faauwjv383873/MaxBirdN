package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.api.StudentLessonItem
import com.example.home.HomeViewModel
import com.example.utils.ClassTypeUtils
import com.example.ui.components.SubjectFilterDialog
import com.example.ui.components.calculateDurationText
import com.example.ui.components.formatTimeRange
import com.example.ui.components.parseIsoToDhakaCalendar
import com.example.ui.components.sortRoutineLessons
import com.example.ui.components.toBengaliDigits
import com.example.utils.SubjectColorUtils
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

private val bengaliDayNames = arrayOf("রবি", "সোম", "মঙ্গল", "বুধ", "বৃহ", "শুক্র", "শনি")
private val bengaliFullDayNames = arrayOf("রবিবার", "সোমবার", "মঙ্গলবার", "বুধবার", "বৃহস্পতিবার", "শুক্রবার", "শনিবার")
private val bengaliMonthNames = arrayOf(
    "জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন",
    "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullRoutineScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit,
    onOpenLessonDetail: ((lesson: StudentLessonItem) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val dhakaZone = remember { TimeZone.getTimeZone("Asia/Dhaka") }
    val coroutineScope = rememberCoroutineScope()

    // ===== LOGIC: pager হুবহু সেম =====
    val initialPage = 1000
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { 2000 })
    val currentWeekOffset = pagerState.currentPage - initialPage

    val weekDays = remember(currentWeekOffset) {
        val cal = Calendar.getInstance(dhakaZone)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY) {
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        cal.add(Calendar.WEEK_OF_YEAR, currentWeekOffset)

        val days = mutableListOf<Calendar>()
        for (i in 0..6) {
            val d = cal.clone() as Calendar
            d.add(Calendar.DAY_OF_YEAR, i)
            days.add(d)
        }
        days
    }

    val todayCal = remember {
        Calendar.getInstance(dhakaZone).apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
    }

    var selectedDayIndex by remember(currentWeekOffset) {
        val todayIdx = weekDays.indexOfFirst {
            it.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                    it.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)
        }
        mutableIntStateOf(if (todayIdx != -1) todayIdx else 0)
    }

    val selectedDate = weekDays.getOrNull(selectedDayIndex) ?: weekDays[0]

    val displayedYear = selectedDate.get(Calendar.YEAR)
    val displayedMonth = selectedDate.get(Calendar.MONTH)

    // ===== LOGIC: auto monthly fetch সেম =====
    LaunchedEffect(displayedYear, displayedMonth, uiState.activeProgram?.id) {
        viewModel.fetchMonthlyRoutine(displayedYear, displayedMonth)
    }

    val monthYearText = remember(selectedDate) {
        val monthStr = bengaliMonthNames[selectedDate.get(Calendar.MONTH)]
        val yearStr = selectedDate.get(Calendar.YEAR).toString().toBengaliDigits()
        "$monthStr $yearStr"
    }

    val fullDateText = remember(selectedDate) {
        val dayOfWeek = selectedDate.get(Calendar.DAY_OF_WEEK)
        val dayName = when (dayOfWeek) {
            Calendar.SATURDAY -> "শনিবার"
            Calendar.SUNDAY -> "রবিবার"
            Calendar.MONDAY -> "সোমবার"
            Calendar.TUESDAY -> "মঙ্গলবার"
            Calendar.WEDNESDAY -> "বুধবার"
            Calendar.THURSDAY -> "বৃহস্পতিবার"
            Calendar.FRIDAY -> "শুক্রবার"
            else -> ""
        }
        val dayNum = String.format(Locale.US, "%02d", selectedDate.get(Calendar.DAY_OF_MONTH)).toBengaliDigits()
        val monthNum = String.format(Locale.US, "%02d", selectedDate.get(Calendar.MONTH) + 1).toBengaliDigits()
        val yearNum = selectedDate.get(Calendar.YEAR).toString().toBengaliDigits()
        "$dayName, $dayNum/$monthNum/$yearNum"
    }

    // ===== LOGIC: day filter + sort সেম =====
    val selectedDayLessons = remember(uiState.filteredWeeklyRoutine, selectedDate) {
        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = dhakaZone }
        val targetDateStr = sdfDate.format(selectedDate.time)

        val raw = uiState.filteredWeeklyRoutine.filter { lesson ->
            val timeStr = lesson.start_time ?: lesson.live_class?.start_time
            if (timeStr.isNullOrBlank()) false
            else {
                val lessonCal = parseIsoToDhakaCalendar(timeStr)
                if (lessonCal != null) {
                    val lessonDateStr = sdfDate.format(lessonCal.time)
                    lessonDateStr == targetDateStr
                } else false
            }
        }
        sortRoutineLessons(raw)
    }

    val classCount = selectedDayLessons.count { it.live_class?.type != "EXAM" && it.content_type != "LiveExam" }
    val examCount = selectedDayLessons.count { it.live_class?.type == "EXAM" || it.content_type == "LiveExam" }

    var showMonthYearPicker by remember { mutableStateOf(false) }

    // Subject Filter Dialog — LOGIC সেম
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("রুটিন", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "ফিরে যান", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.openSubjectFilterDialog() },
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.Tune, "সাবজেক্ট সাজাও",
                            tint = if (uiState.selectedSubjectCodes.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                        )
                    }

                    // NEW: gradient month pill
                    Box(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
                                    )
                                )
                            )
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                RoundedCornerShape(14.dp)
                            )
                            .clickable { showMonthYearPicker = true }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(monthYearText, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.Default.DateRange, "মাস ও বছর পরিবর্তন করুন", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ===== Week Pager — LOGIC সেম =====
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { page ->
                val pageWeekOffset = page - initialPage
                val pDays = remember(pageWeekOffset) {
                    val cal = Calendar.getInstance(dhakaZone)
                    cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)

                    while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY) {
                        cal.add(Calendar.DAY_OF_YEAR, -1)
                    }
                    cal.add(Calendar.WEEK_OF_YEAR, pageWeekOffset)

                    val list = mutableListOf<Calendar>()
                    for (i in 0..6) {
                        val d = cal.clone() as Calendar
                        d.add(Calendar.DAY_OF_YEAR, i)
                        list.add(d)
                    }
                    list
                }

                // 7-Day Strip — NEW: animated pills
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    pDays.forEachIndexed { index, dateCal ->
                        val isSelected = (pageWeekOffset == currentWeekOffset) && (index == selectedDayIndex)
                        val dayNum = dateCal.get(Calendar.DAY_OF_MONTH).toString().toBengaliDigits()
                        val dayNameStr = when (dateCal.get(Calendar.DAY_OF_WEEK)) {
                            Calendar.SATURDAY -> "শনি"
                            Calendar.SUNDAY -> "রবি"
                            Calendar.MONDAY -> "সোম"
                            Calendar.TUESDAY -> "মঙ্গল"
                            Calendar.WEDNESDAY -> "বুধ"
                            Calendar.THURSDAY -> "বৃহ"
                            Calendar.FRIDAY -> "শুক্র"
                            else -> ""
                        }

                        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = dhakaZone }
                        val dStr = sdfDate.format(dateCal.time)
                        val dLessons = uiState.filteredWeeklyRoutine.filter { lesson ->
                            val st = lesson.start_time ?: lesson.live_class?.start_time
                            if (st.isNullOrBlank()) false
                            else {
                                val lCal = parseIsoToDhakaCalendar(st)
                                lCal != null && sdfDate.format(lCal.time) == dStr
                            }
                        }
                        val dClassCount = dLessons.count { it.live_class?.type != "EXAM" && it.content_type != "LiveExam" }
                        val dExamCount = dLessons.count { it.live_class?.type == "EXAM" || it.content_type == "LiveExam" }

                        // NEW: animated color + scale
                        val pillBg by animateColorAsState(
                            targetValue = if (isSelected) Color(0xFF3B82F6) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            animationSpec = tween(250), label = "pillBg$page$index"
                        )
                        val pillScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.06f else 1f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                            label = "pillScale$page$index"
                        )

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 2.dp)
                                .graphicsLayer { scaleX = pillScale; scaleY = pillScale }
                                .clip(RoundedCornerShape(22.dp))
                                .background(pillBg)
                                .clickable {
                                    if (pageWeekOffset == currentWeekOffset) {
                                        selectedDayIndex = index
                                    } else {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(page)
                                            selectedDayIndex = index
                                        }
                                    }
                                }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(
                                text = dayNameStr,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = dayNum,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            // Indicator dots — LOGIC সেম
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (dClassCount > 0 || dExamCount > 0) {
                                    val totalDots = (dClassCount + dExamCount).coerceAtMost(6)
                                    for (i in 0 until totalDots) {
                                        val isExam = i >= dClassCount
                                        Box(
                                            modifier = Modifier
                                                .size(5.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isSelected) Color.White
                                                    else if (isExam) Color(0xFFF59E0B)
                                                    else Color(0xFF06B6D4)
                                                )
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .width(8.dp)
                                            .height(2.dp)
                                            .background(
                                                if (isSelected) Color.White.copy(alpha = 0.6f)
                                                else MaterialTheme.colorScheme.outlineVariant
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Summary Row — LOGIC সেম
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(fullDateText, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF06B6D4)))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ক্লাস ${classCount.toString().toBengaliDigits()}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Spacer(modifier = Modifier.width(12.dp))

                    Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFF59E0B)))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("এক্সাম ${examCount.toString().toBengaliDigits()}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ===== List States =====
            when {
                uiState.isRoutineLoading -> {
                    // NEW: shimmer skeleton list
                    ShimmerRoutineList()
                }

                selectedDayLessons.isEmpty() -> {
                    // NEW: সুন্দর empty state
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.EventBusy, null,
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "এই দিনের জন্য কোনো ক্লাস বা পরীক্ষার রুটিন পাওয়া যায়নি",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                lineHeight = 19.sp,
                                modifier = Modifier.padding(horizontal = 32.dp)
                            )
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 20.dp)
                    ) {
                        items(selectedDayLessons) { lesson ->
                            RoutineTimelineCard(
                                lesson = lesson,
                                onClick = { onOpenLessonDetail?.invoke(lesson) }
                            )
                        }
                    }
                }
            }
        }
    }

    // ===== Month/Year Picker — LOGIC হুবহু সেম =====
    if (showMonthYearPicker) {
        MonthYearSelectionDialog(
            initialYear = selectedDate.get(Calendar.YEAR),
            initialMonth = selectedDate.get(Calendar.MONTH),
            onDismiss = { showMonthYearPicker = false },
            onSelect = { year, month ->
                showMonthYearPicker = false

                val targetCal = Calendar.getInstance(dhakaZone).apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                val baseSat = Calendar.getInstance(dhakaZone).apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    while (get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY) {
                        add(Calendar.DAY_OF_YEAR, -1)
                    }
                }

                val targetSat = targetCal.clone() as Calendar
                while (targetSat.get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY) {
                    targetSat.add(Calendar.DAY_OF_YEAR, -1)
                }

                val diffMillis = targetSat.timeInMillis - baseSat.timeInMillis
                val weekOffset = Math.round(diffMillis.toDouble() / (7.0 * 24 * 60 * 60 * 1000)).toInt()

                coroutineScope.launch {
                    val targetPage = (initialPage + weekOffset).coerceIn(0, 1999)
                    pagerState.scrollToPage(targetPage)
                    selectedDayIndex = 0
                }

                viewModel.fetchMonthlyRoutine(year, month)
            }
        )
    }
}

// ==================== NEW: Shimmer Loading ====================
@Composable
private fun ShimmerRoutineList() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shimmerAlpha by transition.animateFloat(
        initialValue = 0.3f, targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
        label = "shimmerAlpha"
    )
    val skeletonColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        repeat(4) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .padding(top = 12.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(
                        Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(skeletonColor.copy(alpha = shimmerAlpha))
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(64.dp, 18.dp).clip(RoundedCornerShape(8.dp)).background(skeletonColor.copy(alpha = shimmerAlpha)))
                        Box(Modifier.size(52.dp, 18.dp).clip(RoundedCornerShape(8.dp)).background(skeletonColor.copy(alpha = shimmerAlpha)))
                    }
                    Box(Modifier.fillMaxWidth(0.85f).height(15.dp).clip(RoundedCornerShape(5.dp)).background(skeletonColor.copy(alpha = shimmerAlpha)))
                    Box(Modifier.fillMaxWidth(0.45f).height(12.dp).clip(RoundedCornerShape(5.dp)).background(skeletonColor.copy(alpha = shimmerAlpha)))
                }
            }
        }
    }
}

// ==================== Month/Year Dialog — LOGIC সেম ====================
@Composable
private fun MonthYearSelectionDialog(
    initialYear: Int,
    initialMonth: Int,
    onDismiss: () -> Unit,
    onSelect: (year: Int, month: Int) -> Unit
) {
    var selectedYear by remember { mutableIntStateOf(initialYear) }
    var selectedMonth by remember { mutableIntStateOf(initialMonth) }
    val primary = MaterialTheme.colorScheme.primary

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("মাস ও বছর বাছাই করুন", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

                Spacer(modifier = Modifier.height(16.dp))

                // Year Selector — LOGIC bounds সেম (2020-2035)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { selectedYear-- }, enabled = selectedYear > 2020) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "পূর্ববর্তী বছর", tint = primary)
                    }

                    Text(
                        text = selectedYear.toString().toBengaliDigits() + " সাল",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = primary
                    )

                    IconButton(onClick = { selectedYear++ }, enabled = selectedYear < 2035) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "পরবর্তী বছর", tint = primary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 12 Months Grid — LOGIC সেম
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(bengaliMonthNames) { index, monthName ->
                        val isSelected = selectedMonth == index
                        val cellBg by animateColorAsState(
                            targetValue = if (isSelected) primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            animationSpec = tween(200), label = "monthBg$index"
                        )

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = cellBg,
                            border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { selectedMonth = index }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = monthName,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Actions — NEW: gradient confirm
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("বাতিল", fontWeight = FontWeight.SemiBold)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.horizontalGradient(listOf(primary, MaterialTheme.colorScheme.secondary))
                            )
                            .clickable { onSelect(selectedYear, selectedMonth) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("বাছাই করুন", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

// ==================== Timeline Card — LOGIC সেম ====================
@Composable
private fun RoutineTimelineCard(
    lesson: StudentLessonItem,
    onClick: () -> Unit
) {
    // ===== LOGIC (হুবহু সেম) =====
    val st = lesson.start_time ?: lesson.live_class?.start_time
    val et = lesson.end_time ?: lesson.live_class?.end_time

    val startCal = remember(st) { parseIsoToDhakaCalendar(st) }
    val endCal = remember(et) { parseIsoToDhakaCalendar(et) }

    val formattedTime = remember(startCal, endCal) {
        val timeRange = formatTimeRange(startCal, endCal)
        val duration = calculateDurationText(startCal, endCal)
        if (duration.isNotBlank()) "$timeRange • $duration" else timeRange
    }

    val isExam = lesson.isExam
    val isLive = lesson.isLive

    val nowMs = System.currentTimeMillis()
    val startMs = startCal?.timeInMillis ?: Long.MAX_VALUE
    val endMs = endCal?.timeInMillis ?: (if (startMs != Long.MAX_VALUE) startMs + (90 * 60 * 1000L) else Long.MAX_VALUE)

    val isLiveNow = lesson.isLiveNow || (startMs != Long.MAX_VALUE && nowMs in (startMs - 5 * 60 * 1000L)..endMs && !isExam)

    val classTypeBadge = ClassTypeUtils.getClassTypeBadgeStyle(lesson)
    val typeText = when {
        isLiveNow -> "🔴 লাইভ চলছে"
        isExam -> "✍️ পরীক্ষা (Exam)"
        else -> classTypeBadge.label
    }

    val subjectName = lesson.subject_name ?: "বিষয়"
    val titleText = ClassTypeUtils.formatLessonTitle(lesson.title ?: lesson.live_class?.chapter_name ?: "অনলাইন ক্লাস")
    val subjectColors = SubjectColorUtils.getColorScheme(subjectName)

    val titleFontSize = when {
        titleText.length > 50 -> 11.5.sp
        titleText.length > 30 -> 12.5.sp
        else -> 13.5.sp
    }
    val titleLineHeight = when {
        titleText.length > 50 -> 15.5.sp
        titleText.length > 30 -> 17.sp
        else -> 18.5.sp
    }

    // NEW: press scale + live pulse
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue = if (isPressed) 0.975f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "tlCardScale"
    )

    val liveTransition = rememberInfiniteTransition(label = "tlLivePulse")
    val liveDotScale by liveTransition.animateFloat(
        initialValue = 0.7f, targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(650, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "tlLiveDotScale"
    )

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        // Timeline dot — NEW: pulsing when live
        Box(
            modifier = Modifier
                .width(28.dp)
                .padding(top = 12.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (isLiveNow) Color(0xFFFEE2E2) else if (isExam) Color(0xFFFEF3C7) else Color(0xFFE0F2FE)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(if (isLiveNow) 10.dp else 8.dp)
                        .graphicsLayer {
                            if (isLiveNow) { scaleX = liveDotScale; scaleY = liveDotScale }
                        }
                        .clip(CircleShape)
                        .background(
                            when {
                                isLiveNow -> Color(0xFFEF4444)
                                isExam -> Color(0xFFD97706)
                                else -> Color(0xFF0284C7)
                            }
                        )
                )
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Main Card
        Card(
            modifier = Modifier
                .weight(1f)
                .graphicsLayer { scaleX = cardScale; scaleY = cardScale }
                .clickable(interactionSource = interaction, indication = null) { onClick() },
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isLiveNow) Color(0xFFFFF1F2) else MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isLiveNow) 3.5.dp else 1.5.dp),
            border = if (isLiveNow) BorderStroke(1.5.dp, Color(0xFFEF4444)) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                // Top Tag Row — LOGIC সেম
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isLiveNow) {
                        Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFEF4444)) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .graphicsLayer { scaleX = liveDotScale; scaleY = liveDotScale }
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                                Text("লাইভ চলছে", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }

                    Surface(shape = RoundedCornerShape(8.dp), color = subjectColors.backgroundColor) {
                        Text(
                            text = subjectName,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = subjectColors.textColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (!isLiveNow) {
                        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)) {
                            Text(
                                text = typeText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = titleText,
                    fontSize = titleFontSize,
                    lineHeight = titleLineHeight,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                if (formattedTime.isNotBlank()) {
                    Text(
                        text = formattedTime,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFDC2626)
                    )
                }
            }
        }
    }
}
