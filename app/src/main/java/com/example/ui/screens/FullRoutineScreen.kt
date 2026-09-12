package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EventBusy
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.api.StudentLessonItem
import com.example.home.HomeViewModel
import java.text.SimpleDateFormat
import java.util.*

// Helper extension to convert English numbers to Bengali digits
private fun String.toBengaliDigits(): String {
    val englishDigits = arrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
    val bengaliDigits = arrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
    var result = this
    for (i in englishDigits.indices) {
        result = result.replace(englishDigits[i], bengaliDigits[i])
    }
    return result
}

private val bengaliDayNames = arrayOf("রবি", "সোম", "মঙ্গল", "বুধ", "বৃহ", "শুক্রবার", "শনি")
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

        // Find Saturday of current week
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

    // Default selected day index (0 to 6), default to today if in current week, else Saturday (0)
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

    // Month and Year for Header (e.g. "সেপ্টেম্বর ২০২৬")
    val monthYearText = remember(selectedDate) {
        val monthStr = bengaliMonthNames[selectedDate.get(Calendar.MONTH)]
        val yearStr = selectedDate.get(Calendar.YEAR).toString().toBengaliDigits()
        "$monthStr $yearStr"
    }

    // Full Date String (e.g. "শনিবার, ০৫/০৯/২০২৬")
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
        val dayNum = String.format("%02d", selectedDate.get(Calendar.DAY_OF_MONTH)).toBengaliDigits()
        val monthNum = String.format("%02d", selectedDate.get(Calendar.MONTH) + 1).toBengaliDigits()
        val yearNum = selectedDate.get(Calendar.YEAR).toString().toBengaliDigits()
        "$dayName, $dayNum/$monthNum/$yearNum"
    }

    // Filter lessons for selected day
    val selectedDayLessons = remember(uiState.weeklyRoutine, selectedDate) {
        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = dhakaZone }
        val targetDateStr = sdfDate.format(selectedDate.time)

        uiState.weeklyRoutine.filter { lesson ->
            val timeStr = lesson.start_time ?: lesson.live_class?.start_time
            if (timeStr.isNullOrBlank()) false
            else {
                val lessonCal = com.example.ui.components.parseIsoToDhakaCalendar(timeStr)
                if (lessonCal != null) {
                    val lessonDateStr = sdfDate.format(lessonCal.time)
                    lessonDateStr == targetDateStr
                } else false
            }
        }.sortedBy { lesson ->
            lesson.start_time ?: lesson.live_class?.start_time ?: ""
        }
    }

    val classCount = selectedDayLessons.count { it.live_class?.type != "EXAM" && it.content_type != "LiveExam" }
    val examCount = selectedDayLessons.count { it.live_class?.type == "EXAM" || it.content_type == "LiveExam" }

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
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
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
                                contentDescription = null,
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

                // 7-Day Horizontal Capsule Strip Header (Matching Screenshot 3)
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
                        val dLessons = uiState.weeklyRoutine.filter { lesson ->
                            val st = lesson.start_time ?: lesson.live_class?.start_time
                            if (st.isNullOrBlank()) false
                            else {
                                val lCal = com.example.ui.components.parseIsoToDhakaCalendar(st)
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

            // Selected Day Summary Row (e.g., "শনিবার, ০৫/০৯/২০২৬"  |  🟢 ক্লাস ২  🟡 এক্সাম ০)
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

            // Vertical Timeline Routine List (Matching Screenshot 3)
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
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
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
}

@Composable
private fun RoutineTimelineCard(
    lesson: StudentLessonItem,
    onClick: () -> Unit
) {
    val dhakaZone = TimeZone.getTimeZone("Asia/Dhaka")

    // Format Start Time & End Time
    val formattedTime = remember(lesson.start_time, lesson.end_time, lesson.live_class) {
        val st = lesson.start_time ?: lesson.live_class?.start_time ?: ""
        val et = lesson.end_time ?: lesson.live_class?.end_time ?: ""

        if (st.length >= 16) {
            try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).apply { timeZone = dhakaZone }
                val outputFormat = SimpleDateFormat("hh.mm a", Locale.US).apply { timeZone = dhakaZone }

                val startDate = inputFormat.parse(st.take(16))
                val endDate = if (et.length >= 16) inputFormat.parse(et.take(16)) else null

                val startStr = if (startDate != null) outputFormat.format(startDate) else ""
                val endStr = if (endDate != null) outputFormat.format(endDate) else ""

                val durationText = if (startDate != null && endDate != null) {
                    val diffMs = endDate.time - startDate.time
                    val minutesTotal = (diffMs / (1000 * 60)).toInt()
                    val hours = minutesTotal / 60
                    val mins = minutesTotal % 60

                    val hStr = if (hours > 0) "${hours.toString().toBengaliDigits()} ঘণ্টা " else ""
                    val mStr = if (mins > 0) "${mins.toString().toBengaliDigits()} মিনিট" else ""
                    " • $hStr$mStr".trimEnd()
                } else ""

                if (endStr.isNotBlank()) "$startStr - $endStr$durationText".toBengaliDigits()
                else "$startStr$durationText".toBengaliDigits()
            } catch (e: Exception) {
                "07.00 PM - 08.48 PM • ১ ঘণ্টা ৪৮ মিনিট"
            }
        } else {
            "07.00 PM - 08.48 PM • ১ ঘণ্টা ৪৮ মিনিট"
        }
    }

    val isExam = lesson.live_class?.type == "EXAM" || lesson.content_type == "LiveExam"
    val typeText = when {
        isExam -> "✍️ লাইভ এক্সাম"
        lesson.live_class?.type == "EXTRA" -> "👨‍🏫 এক্সট্রা ক্লাস"
        else -> "👨‍🏫 লেকচার ক্লাস"
    }

    val subjectName = lesson.subject_name ?: "পৌরনীতি ও সুশাসন ২য় পত্র"
    val titleText = lesson.title ?: lesson.live_class?.chapter_name ?: "পর্ব-১: স্থানীয় শাসন"

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        // Timeline Left Bar (Line + Circular Cross/Dot Indicator matching Screenshot 3)
        Box(
            modifier = Modifier
                .width(36.dp)
                .padding(top = 18.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            // Circle Dot with Cross Icon (matching Screenshot 3 red cross on faint pink background)
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFEE2E2)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Main Card (Matching Screenshot 3 design)
        Surface(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(20.dp))
                .clickable { onClick() }
                .shadow(
                    elevation = 2.dp,
                    shape = RoundedCornerShape(20.dp),
                    spotColor = Color(0xFF0284C7).copy(alpha = 0.08f)
                ),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFFF0F9FF),
            border = BorderStroke(
                width = 1.dp,
                color = Color(0xFFBAE6FD)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Top Tag Row: Subject Tag + Class Type Tag
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Subject Tag (Teal/Cyan pill)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0EA5E9)
                    ) {
                        Text(
                            text = subjectName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Type Tag (Light Grey pill)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF1F5F9)
                    ) {
                        Text(
                            text = typeText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF334155),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Lesson Title
                Text(
                    text = titleText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Time & Duration (Pinkish Red Text `#DC2626` / `#DB2777`)
                Text(
                    text = formattedTime,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFDC2626)
                )
            }
        }
    }
}
