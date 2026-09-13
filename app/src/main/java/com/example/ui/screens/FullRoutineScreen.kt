package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.api.StudentLessonItem
import com.example.home.HomeViewModel
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

    // Start at weekOffset = 0 (Page 1000)
    val initialPage = 1000
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { 2000 })

    val currentWeekOffset = pagerState.currentPage - initialPage

    // Get 7 days (Sat-Fri) for the current pager week offset
    val weekDays = remember(currentWeekOffset) {
        val cal = Calendar.getInstance(dhakaZone)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        // Find Saturday of base week
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
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
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

    // Load routine for the selected month automatically when year or month changes
    val displayedYear = selectedDate.get(Calendar.YEAR)
    val displayedMonth = selectedDate.get(Calendar.MONTH)

    LaunchedEffect(displayedYear, displayedMonth, uiState.activeProgram?.id) {
        viewModel.fetchMonthlyRoutine(displayedYear, displayedMonth)
    }

    // Month and Year for Header (e.g. "সেপ্টেম্বর ২০২৬")
    val monthYearText = remember(selectedDate) {
        val monthStr = bengaliMonthNames[selectedDate.get(Calendar.MONTH)]
        val yearStr = selectedDate.get(Calendar.YEAR).toString().toBengaliDigits()
        "$monthStr $yearStr"
    }

    // Full Date String (e.g. "রবিবার, ১৩/০৯/২০২৬")
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

    // Filter lessons for selected day using the user's selected subjects (filteredWeeklyRoutine) and sort with LIVE priority & time
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "রুটিন",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "ফিরে যান",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    // Customize Subjects Button (সাবজেক্ট সাজাও)
                    IconButton(
                        onClick = { viewModel.openSubjectFilterDialog() },
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "সাবজেক্ট সাজাও",
                            tint = if (uiState.selectedSubjectCodes.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                        )
                    }

                    // Clickable Month & Year Pill
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { showMonthYearPicker = true }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = monthYearText,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = "মাস ও বছর পরিবর্তন করুন",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
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
            // Horizontal Week Pager (Swipe Left/Right to change weeks)
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth()
            ) { page ->
                val pageWeekOffset = page - initialPage
                val pDays = remember(pageWeekOffset) {
                    val cal = Calendar.getInstance(dhakaZone)
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)

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

                // 7-Day Horizontal Capsule Strip Header (Matching Shikho Design)
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

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(22.dp))
                                .background(
                                    if (isSelected) Color(0xFF3B82F6) else Color(0xFFF1F5F9)
                                )
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
                                color = if (isSelected) Color.White.copy(alpha = 0.9f) else Color(0xFF64748B)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = dayNum,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else Color(0xFF0F172A)
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            // Indicator Dots
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
                                                if (isSelected) Color.White.copy(alpha = 0.6f) else Color(0xFFCBD5E1)
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Selected Day Summary Row (e.g., "রবিবার, ১৩/০৯/২০২৬"  |  🔵 ক্লাস ৩  🟡 এক্সাম ০)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = fullDateText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF06B6D4))
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "ক্লাস ${classCount.toString().toBengaliDigits()}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF475569)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF59E0B))
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "এক্সাম ${examCount.toString().toBengaliDigits()}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF475569)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Vertical Timeline Routine List
            when {
                uiState.isRoutineLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }

                selectedDayLessons.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.EventBusy,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "এই দিনের জন্য কোনো ক্লাস বা পরীক্ষার রুটিন পাওয়া যায়নি",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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

    // Month and Year Selection Dialog
    if (showMonthYearPicker) {
        MonthYearSelectionDialog(
            initialYear = selectedDate.get(Calendar.YEAR),
            initialMonth = selectedDate.get(Calendar.MONTH),
            onDismiss = { showMonthYearPicker = false },
            onSelect = { year, month ->
                showMonthYearPicker = false

                // Calculate target date (1st of that month, or today if that month is current)
                val targetCal = Calendar.getInstance(dhakaZone).apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                // Calculate base Saturday of initial page
                val baseSat = Calendar.getInstance(dhakaZone).apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    while (get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY) {
                        add(Calendar.DAY_OF_YEAR, -1)
                    }
                }

                // Target Saturday
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

@Composable
private fun MonthYearSelectionDialog(
    initialYear: Int,
    initialMonth: Int,
    onDismiss: () -> Unit,
    onSelect: (year: Int, month: Int) -> Unit
) {
    var selectedYear by remember { mutableIntStateOf(initialYear) }
    var selectedMonth by remember { mutableIntStateOf(initialMonth) }

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
                Text(
                    text = "মাস ও বছর বাছাই করুন",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Year Selector Header with Previous/Next Arrows
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { selectedYear-- },
                        enabled = selectedYear > 2020
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "পূর্ববর্তী বছর",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Text(
                        text = selectedYear.toString().toBengaliDigits() + " সাল",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    IconButton(
                        onClick = { selectedYear++ },
                        enabled = selectedYear < 2035
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "পরবর্তী বছর",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 12 Months 3x4 Grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(bengaliMonthNames) { index, monthName ->
                        val isSelected = selectedMonth == index
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
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

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("বাতিল", fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = { onSelect(selectedYear, selectedMonth) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("বাছাই করুন", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun RoutineTimelineCard(
    lesson: StudentLessonItem,
    onClick: () -> Unit
) {
    val st = lesson.start_time ?: lesson.live_class?.start_time
    val et = lesson.end_time ?: lesson.live_class?.end_time

    val startCal = remember(st) { parseIsoToDhakaCalendar(st) }
    val endCal = remember(et) { parseIsoToDhakaCalendar(et) }

    // Format Start Time & End Time properly with correct time range and duration
    val formattedTime = remember(startCal, endCal) {
        val timeRange = formatTimeRange(startCal, endCal)
        val duration = calculateDurationText(startCal, endCal)

        if (duration.isNotBlank()) {
            "$timeRange • $duration"
        } else {
            timeRange
        }
    }

    val isExam = lesson.isExam
    val isLive = lesson.isLive
    val isRecorded = lesson.isRecorded

    val nowMs = System.currentTimeMillis()
    val startMs = startCal?.timeInMillis ?: Long.MAX_VALUE
    val endMs = endCal?.timeInMillis ?: (if (startMs != Long.MAX_VALUE) startMs + (90 * 60 * 1000L) else Long.MAX_VALUE)
    
    val isLiveNow = lesson.isLiveNow || (startMs != Long.MAX_VALUE && nowMs in (startMs - 5 * 60 * 1000L)..endMs && !isExam)

    val typeText = when {
        isLiveNow -> "🔴 লাইভ চলছে"
        isExam -> "✍️ পরীক্ষা (Exam)"
        isLive -> "🔴 লাইভ ক্লাস"
        isRecorded -> "🎥 রেকর্ড করা ক্লাস"
        lesson.live_class?.type == "EXTRA" -> "👨‍🏫 এক্সট্রা ক্লাস"
        else -> "👨‍🏫 লেকচার ক্লাস"
    }

    val subjectName = lesson.subject_name ?: "বিষয়"
    val titleText = lesson.title ?: lesson.live_class?.chapter_name ?: "অনলাইন ক্লাস"
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

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        // Timeline Left Bar
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
                        .clip(CircleShape)
                        .background(if (isLiveNow) Color(0xFFEF4444) else if (isExam) Color(0xFFD97706) else Color(0xFF0284C7))
                )
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Main Card
        Card(
            modifier = Modifier
                .weight(1f)
                .clickable { onClick() },
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isLiveNow) Color(0xFFFFF1F2) else Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isLiveNow) 3.5.dp else 1.5.dp),
            border = if (isLiveNow) BorderStroke(1.5.dp, Color(0xFFEF4444)) else BorderStroke(1.dp, Color(0xFFE5E7EB))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                // Top Tag Row: Subject Tag + Class Type Tag
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isLiveNow) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFEF4444)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                                Text(
                                    text = "লাইভ চলছে",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    // Subject Tag
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = subjectColors.backgroundColor
                    ) {
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
                        // Type Tag
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFF1F5F9)
                        ) {
                            Text(
                                text = typeText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF334155),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Lesson Title
                Text(
                    text = titleText,
                    fontSize = titleFontSize,
                    lineHeight = titleLineHeight,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Time & Duration
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
