package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.api.StudentLessonItem
import com.example.utils.SubjectColorUtils
import java.text.SimpleDateFormat
import java.util.*

fun String.toBengaliDigits(): String {
    val en = arrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
    val bn = arrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
    var result = this
    for (i in en.indices) {
        result = result.replace(en[i], bn[i])
    }
    return result
}

data class RoutineDayItem(
    val dayIndex: Int,
    val shortDayName: String,
    val fullDayName: String,
    val dateNumBn: String,
    val fullDateBn: String,
    val dateKey: String,
    val isToday: Boolean,
    val lessons: List<StudentLessonItem>,
    val classCount: Int,
    val examCount: Int
)

data class DayColorScheme(
    val selectedGradient: List<Color>,
    val selectedDotColor: Color,
    val unselectedBg: Color,
    val unselectedText: Color,
    val accentBorder: Color
)

fun getDayColorScheme(dayIndex: Int): DayColorScheme {
    return when (dayIndex % 7) {
        0 -> DayColorScheme( // Saturday (শনি) - Royal Indigo
            selectedGradient = listOf(Color(0xFF4F46E5), Color(0xFF6366F1)),
            selectedDotColor = Color.White,
            unselectedBg = Color(0xFFEEF2FF),
            unselectedText = Color(0xFF4338CA),
            accentBorder = Color(0xFF818CF8)
        )
        1 -> DayColorScheme( // Sunday (রবি) - Electric Azure Blue
            selectedGradient = listOf(Color(0xFF0284C7), Color(0xFF38BDF8)),
            selectedDotColor = Color.White,
            unselectedBg = Color(0xFFE0F2FE),
            unselectedText = Color(0xFF0369A1),
            accentBorder = Color(0xFF38BDF8)
        )
        2 -> DayColorScheme( // Monday (সোম) - Emerald Green
            selectedGradient = listOf(Color(0xFF059669), Color(0xFF10B981)),
            selectedDotColor = Color.White,
            unselectedBg = Color(0xFFD1FAE5),
            unselectedText = Color(0xFF047857),
            accentBorder = Color(0xFF34D399)
        )
        3 -> DayColorScheme( // Tuesday (মঙ্গল) - Sunset Coral Amber
            selectedGradient = listOf(Color(0xFFEA580C), Color(0xFFF97316)),
            selectedDotColor = Color.White,
            unselectedBg = Color(0xFFFFEDD5),
            unselectedText = Color(0xFFC2410C),
            accentBorder = Color(0xFFFB923C)
        )
        4 -> DayColorScheme( // Wednesday (বুধ) - Neon Violet
            selectedGradient = listOf(Color(0xFF9333EA), Color(0xFFC084FC)),
            selectedDotColor = Color.White,
            unselectedBg = Color(0xFFF3E8FF),
            unselectedText = Color(0xFF7E22CE),
            accentBorder = Color(0xFFC084FC)
        )
        5 -> DayColorScheme( // Thursday (বৃহ) - Deep Crimson Rose
            selectedGradient = listOf(Color(0xFFE11D48), Color(0xFFFB7185)),
            selectedDotColor = Color.White,
            unselectedBg = Color(0xFFFFE4E6),
            unselectedText = Color(0xFFBE123C),
            accentBorder = Color(0xFFFB7185)
        )
        else -> DayColorScheme( // Friday (শুক্র) - Vibrant Mint Teal
            selectedGradient = listOf(Color(0xFF0D9488), Color(0xFF2DD4BF)),
            selectedDotColor = Color.White,
            unselectedBg = Color(0xFFCCFBF1),
            unselectedText = Color(0xFF0F766E),
            accentBorder = Color(0xFF2DD4BF)
        )
    }
}

@Composable
fun WeeklyRoutineSection(
    lessons: List<StudentLessonItem>,
    isLoading: Boolean,
    onSeeAllClick: () -> Unit = {},
    onCustomizeSubjectsClick: () -> Unit = {},
    selectedSubjectsCount: Int = 0,
    totalSubjectsCount: Int = 0,
    selectedSubjectNames: List<String> = emptyList(),
    onOpenLessonDetail: (StudentLessonItem) -> Unit = {}
) {
    val dhakaZone = TimeZone.getTimeZone("Asia/Dhaka")
    val todayCal = Calendar.getInstance(dhakaZone)
    val todayDateKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = dhakaZone }.format(todayCal.time)

    // Calculate the 7 days for the current week starting from Saturday
    val daysList = remember(lessons) {
        val satCal = todayCal.clone() as Calendar
        satCal.set(Calendar.HOUR_OF_DAY, 0)
        satCal.set(Calendar.MINUTE, 0)
        satCal.set(Calendar.SECOND, 0)
        satCal.set(Calendar.MILLISECOND, 0)
        
        while (satCal.get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY) {
            satCal.add(Calendar.DAY_OF_YEAR, -1)
        }

        (0..6).map { offset ->
            val dayCal = satCal.clone() as Calendar
            dayCal.add(Calendar.DAY_OF_YEAR, offset)

            val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = dhakaZone }.format(dayCal.time)
            val dateNum = SimpleDateFormat("dd", Locale.US).apply { timeZone = dhakaZone }.format(dayCal.time).toBengaliDigits()
            
            val dayOfWeek = dayCal.get(Calendar.DAY_OF_WEEK)
            val (shortName, fullName) = when (dayOfWeek) {
                Calendar.SATURDAY -> "শনি" to "শনিবার"
                Calendar.SUNDAY -> "রবি" to "রবিবার"
                Calendar.MONDAY -> "সোম" to "সোমবার"
                Calendar.TUESDAY -> "মঙ্গল" to "মঙ্গলবার"
                Calendar.WEDNESDAY -> "বুধ" to "বুধবার"
                Calendar.THURSDAY -> "বৃহ" to "বৃহস্পতিবার"
                Calendar.FRIDAY -> "শুক্র" to "শুক্রবার"
                else -> "শনি" to "শনিবার"
            }

            val fullDateBn = "$fullName, ${SimpleDateFormat("dd/MM/yyyy", Locale.US).apply { timeZone = dhakaZone }.format(dayCal.time).toBengaliDigits()}"
            
            val rawDayLessons = lessons.filter { lesson ->
                val startTime = lesson.start_time ?: lesson.live_class?.start_time
                if (startTime.isNullOrBlank()) false
                else {
                    val lessonCal = parseIsoToDhakaCalendar(startTime)
                    if (lessonCal != null) {
                        val lessonDateKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = dhakaZone }.format(lessonCal.time)
                        lessonDateKey == dateKey
                    } else false
                }
            }
            val dayLessons = sortRoutineLessons(rawDayLessons)

            val classCount = dayLessons.count { it.content_type == "LiveClass" || it.content_type == "RecordedClass" || it.content_type == "Lesson" || it.content_type == null }
            val examCount = dayLessons.count { it.content_type == "LiveExam" || it.content_type == "ModelTest" || it.content_type == "Quiz" }

            RoutineDayItem(
                dayIndex = offset,
                shortDayName = shortName,
                fullDayName = fullName,
                dateNumBn = dateNum,
                fullDateBn = fullDateBn,
                dateKey = dateKey,
                isToday = dateKey == todayDateKey,
                lessons = dayLessons,
                classCount = classCount,
                examCount = examCount
            )
        }
    }

    var selectedDayIndex by remember(daysList) {
        val todayIdx = daysList.indexOfFirst { it.isToday }
        mutableStateOf(if (todayIdx >= 0) todayIdx else 0)
    }

    val selectedDay = daysList.getOrNull(selectedDayIndex) ?: daysList.firstOrNull()

    Column(modifier = Modifier.fillMaxWidth()) {
        // 1. Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "রুটিন",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1E293B),
                modifier = Modifier.clickable { onSeeAllClick() }
            ) {
                Text(
                    text = "সব দেখুন",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF38BDF8),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. 7 Days Strip Header with Clean 3-Color Dark Theme
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            daysList.forEach { day ->
                val isSelected = day.dayIndex == selectedDayIndex

                val containerModifier = if (isSelected) {
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFF0284C7), Color(0xFF38BDF8))
                            )
                        )
                        .border(1.5.dp, Color(0xFF38BDF8), RoundedCornerShape(20.dp))
                } else {
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF1E293B))
                }

                Column(
                    modifier = containerModifier
                        .clickable { selectedDayIndex = day.dayIndex }
                        .padding(vertical = 10.dp, horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = day.shortDayName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color.White else Color(0xFF94A3B8)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = day.dateNumBn,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color.White else Color(0xFFF8FAFC)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    if (day.lessons.isEmpty()) {
                        Text(
                            text = "—",
                            fontSize = 11.sp,
                            color = if (isSelected) Color.White.copy(alpha = 0.7f) else Color(0xFF64748B)
                        )
                    } else {
                        if (isSelected) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(Color.White))
                                Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(Color.White))
                            }
                        } else {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (day.classCount > 0) {
                                    Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(Color(0xFF38BDF8)))
                                }
                                if (day.examCount > 0) {
                                    Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(Color(0xFFF59E0B)))
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3. Selected Day Summary Row
        if (selectedDay != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selectedDay.fullDateBn,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF94A3B8)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF38BDF8))
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "ক্লাস ${selectedDay.classCount.toString().toBengaliDigits()}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFCBD5E1)
                    )

                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "|",
                        fontSize = 12.sp,
                        color = Color(0xFF475569)
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF59E0B))
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "এক্সাম ${selectedDay.examCount.toString().toBengaliDigits()}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFCBD5E1)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4. Lessons Horizontal Card List or Compact Empty State Banner
            when {
                isLoading -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF1E293B)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF38BDF8)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "রুটিন লোড হচ্ছে...",
                                fontSize = 13.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                }

                selectedDay.lessons.isEmpty() -> {
                    // Ultra compact banner when empty so the box never stretches big
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF1E293B).copy(alpha = 0.7f),
                        border = BorderStroke(1.dp, Color(0xFF334155))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.EventBusy,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "আজকে কোন ক্লাস বা পরীক্ষা নেই ✨",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFCBD5E1),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                else -> {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(end = 16.dp)
                    ) {
                        items(selectedDay.lessons) { lesson ->
                            ShikhoRoutineCard(
                                lesson = lesson,
                                onClick = { onOpenLessonDetail(lesson) }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 5. Redesigned Bento Card: "সাবজেক্ট সাজাও" (Customize Subjects) displaying selected subjects serially
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .clickable { onCustomizeSubjectsClick() },
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF1E293B),
            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.35f)),
            shadowElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF0F172A).copy(alpha = 0.8f),
                                Color(0xFF1E293B)
                            )
                        )
                    )
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0284C7).copy(alpha = 0.18f))
                                .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "সাবজেক্ট সাজাও",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(Color(0xFF38BDF8)))
                                    Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(Color(0xFFF59E0B)))
                                    Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(Color(0xFF10B981)))
                                }
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = if (selectedSubjectsCount > 0 && totalSubjectsCount > 0)
                                    "${selectedSubjectsCount.toString().toBengaliDigits()}/${totalSubjectsCount.toString().toBengaliDigits()} টি বিষয় নির্বাচন করা আছে"
                                else if (selectedSubjectsCount > 0)
                                    "${selectedSubjectsCount.toString().toBengaliDigits()} টি বিষয় নির্বাচন করা আছে"
                                else
                                    "তোমার প্রয়োজনীয় সাবজেক্টগুলো বেছে নাও",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Action Pill Button
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0284C7),
                        shadowElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "ফিল্টার",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                // Serial Selected Subjects list chips
                if (selectedSubjectNames.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(selectedSubjectNames) { index, subName ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF0F172A),
                                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF38BDF8))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${(index + 1).toString().toBengaliDigits()}. $subName",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFFF1F5F9)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Routine Card styled dynamically according to Server Subject Color Scheme
 */
@Composable
fun ShikhoRoutineCard(
    lesson: StudentLessonItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val startTime = lesson.start_time ?: lesson.live_class?.start_time
    val endTime = lesson.end_time ?: lesson.live_class?.end_time
    
    val startCal = parseIsoToDhakaCalendar(startTime)
    val endCal = parseIsoToDhakaCalendar(endTime)

    val timeString = formatTimeRange(startCal, endCal)
    val durationString = calculateDurationText(startCal, endCal)
    val fullTimeText = if (durationString.isNotBlank()) "$timeString • $durationString" else timeString

    val isExam = lesson.isExam
    val isLive = lesson.isLive
    val isRecorded = lesson.isRecorded

    val nowMs = System.currentTimeMillis()
    val startMs = startCal?.timeInMillis ?: Long.MAX_VALUE
    val endMs = endCal?.timeInMillis ?: (if (startMs != Long.MAX_VALUE) startMs + 3600_000L else Long.MAX_VALUE)
    
    val isLiveNow = isLive || lesson.live_class?.is_on_going == true || lesson.user_activity_state.equals("LIVE", ignoreCase = true) || (startMs != Long.MAX_VALUE && nowMs in startMs..endMs && !isRecorded)

    val classTypeLabel = when {
        isLiveNow -> "🔴 লাইভ চলছে"
        isExam -> "✍️ পরীক্ষা (Exam)"
        isLive -> "🔴 লাইভ ক্লাস"
        isRecorded -> "🎥 রেকর্ড করা ক্লাস"
        else -> "👨‍🏫 লেকচার ক্লাস"
    }

    val subjectName = lesson.subject_name ?: "বিষয়"
    val titleText = lesson.title ?: lesson.live_class?.chapter_name ?: "অনলাইন ক্লাস"
    
    // Server Subject Dynamic Color
    val subjectColors = SubjectColorUtils.getColorScheme(subjectName)

    val titleFontSize = when {
        titleText.length > 50 -> 11.sp
        titleText.length > 30 -> 12.sp
        else -> 13.sp
    }
    val titleLineHeight = when {
        titleText.length > 50 -> 15.sp
        titleText.length > 30 -> 16.5.sp
        else -> 17.5.sp
    }

    Card(
        modifier = modifier
            .width(245.dp)
            .height(148.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isLiveNow) Color(0xFFFFF1F2) else subjectColors.backgroundColor.copy(alpha = 0.25f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isLiveNow) 4.dp else 2.dp),
        border = BorderStroke(
            width = if (isLiveNow) 1.5.dp else 1.dp,
            color = if (isLiveNow) Color(0xFFEF4444) else subjectColors.textColor.copy(alpha = 0.4f)
        )
    ) {
        Row(modifier = Modifier.fillMaxHeight().fillMaxWidth()) {
            // Left Subject Accent Line
            Box(
                modifier = Modifier
                    .width(4.5.dp)
                    .fillMaxHeight()
                    .background(if (isLiveNow) Color(0xFFEF4444) else subjectColors.textColor)
            )

            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Tags Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
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

                    // Server Subject Tag (Dynamic Server Colors)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = subjectColors.backgroundColor
                    ) {
                        Text(
                            text = subjectName,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = subjectColors.textColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (!isLiveNow) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.8f)
                        ) {
                            Text(
                                text = classTypeLabel,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF374151),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Lesson Title (responsive bounded weight)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = titleText,
                        fontSize = titleFontSize,
                        lineHeight = titleLineHeight,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Time & Duration
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = subjectColors.textColor,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = fullTimeText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = subjectColors.textColor
                    )
                }
            }
        }
    }
}

@Composable
fun RoutineCard(
    lesson: StudentLessonItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ShikhoRoutineCard(
        lesson = lesson,
        onClick = onClick,
        modifier = modifier
    )
}

fun parseIsoToDhakaCalendar(isoString: String?): Calendar? {
    if (isoString.isNullOrBlank()) return null
    val clean = isoString.trim()
    val dhakaZone = TimeZone.getTimeZone("Asia/Dhaka")

    if (clean.startsWith("0000-00-00") || clean.startsWith("1970-01-01")) return null

    if (clean.endsWith("Z", ignoreCase = true)) {
        try {
            val formatStr = if (clean.contains(".")) "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'" else "yyyy-MM-dd'T'HH:mm:ss'Z'"
            val sdfUtc = SimpleDateFormat(formatStr, Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val date = sdfUtc.parse(clean.replace(" ", "T"))
            if (date != null) {
                return Calendar.getInstance(dhakaZone).apply { time = date }
            }
        } catch (_: Exception) {}

        try {
            val sdfUtc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val date = sdfUtc.parse(clean.replace(" ", "T").take(19))
            if (date != null) {
                return Calendar.getInstance(dhakaZone).apply { time = date }
            }
        } catch (_: Exception) {}
    }

    if (clean.contains("+") || (clean.contains("-") && clean.length > 10 && clean.lastIndexOf("-") > 10)) {
        try {
            val cleanT = clean.replace(" ", "T")
            val formatStr = if (cleanT.contains(".")) "yyyy-MM-dd'T'HH:mm:ss.SSSXXX" else "yyyy-MM-dd'T'HH:mm:ssXXX"
            val date = SimpleDateFormat(formatStr, Locale.US).parse(cleanT)
            if (date != null) {
                return Calendar.getInstance(dhakaZone).apply { time = date }
            }
        } catch (_: Exception) {}
    }

    try {
        val cleanT = clean.replace(" ", "T").take(19)
        val sdfLocal = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = dhakaZone
        }
        val date = sdfLocal.parse(cleanT)
        if (date != null) {
            return Calendar.getInstance(dhakaZone).apply { time = date }
        }
    } catch (_: Exception) {}

    try {
        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = dhakaZone
        }
        val date = sdfDate.parse(clean.take(10))
        if (date != null) {
            return Calendar.getInstance(dhakaZone).apply { time = date }
        }
    } catch (_: Exception) {}

    try {
        val ms = clean.toLongOrNull()
        if (ms != null && ms > 1000000000L) {
            return Calendar.getInstance(dhakaZone).apply {
                timeInMillis = if (ms < 100000000000L) ms * 1000L else ms
            }
        }
    } catch (_: Exception) {}

    return null
}

fun formatTimeRange(startCal: Calendar?, endCal: Calendar?): String {
    if (startCal == null) return ""
    val sdf = SimpleDateFormat("hh:mm a", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("Asia/Dhaka")
    }
    val startStr = sdf.format(startCal.time).toBengaliDigits()
    if (endCal == null || endCal.timeInMillis <= startCal.timeInMillis) return startStr
    
    val diffHours = (endCal.timeInMillis - startCal.timeInMillis) / (1000 * 60 * 60)
    if (diffHours <= 0 || diffHours > 24) return startStr

    val endStr = sdf.format(endCal.time).toBengaliDigits()
    return "$startStr - $endStr"
}

fun calculateDurationText(startCal: Calendar?, endCal: Calendar?): String {
    if (startCal == null || endCal == null) return ""
    val diffMs = endCal.timeInMillis - startCal.timeInMillis
    if (diffMs <= 0 || diffMs > 24 * 60 * 60 * 1000L) return ""
    val diffMins = (diffMs / (1000 * 60)).toInt()
    val hours = diffMins / 60
    val mins = diffMins % 60
    return when {
        hours > 0 && mins > 0 -> "${hours.toString().toBengaliDigits()} ঘণ্টা ${mins.toString().toBengaliDigits()} মিনিট"
        hours > 0 -> "${hours.toString().toBengaliDigits()} ঘণ্টা"
        mins > 0 -> "${mins.toString().toBengaliDigits()} মিনিট"
        else -> ""
    }
}

fun sortRoutineLessons(lessons: List<StudentLessonItem>): List<StudentLessonItem> {
    val nowMs = System.currentTimeMillis()

    fun getStartMs(lesson: StudentLessonItem): Long {
        val startTimeStr = lesson.start_time ?: lesson.live_class?.start_time ?: return Long.MAX_VALUE
        return parseIsoToDhakaCalendar(startTimeStr)?.timeInMillis ?: Long.MAX_VALUE
    }

    fun getEndMs(lesson: StudentLessonItem): Long {
        val endTimeStr = lesson.end_time ?: lesson.live_class?.end_time
        val startMs = getStartMs(lesson)
        if (!endTimeStr.isNullOrBlank()) {
            val endCal = parseIsoToDhakaCalendar(endTimeStr)
            if (endCal != null) return endCal.timeInMillis
        }
        return if (startMs != Long.MAX_VALUE) startMs + 3600_000L else Long.MAX_VALUE
    }

    fun isLiveNow(lesson: StudentLessonItem): Boolean {
        if (lesson.isLive || lesson.live_class?.is_on_going == true || lesson.user_activity_state.equals("LIVE", ignoreCase = true)) {
            return true
        }
        val startMs = getStartMs(lesson)
        val endMs = getEndMs(lesson)
        return (startMs != Long.MAX_VALUE && nowMs in startMs..endMs && !lesson.isRecorded)
    }

    fun isPassed(lesson: StudentLessonItem): Boolean {
        if (isLiveNow(lesson)) return false
        if (lesson.user_activity_state.equals("COMPLETED", true) ||
            lesson.user_activity_state.equals("ATTENDED", true) ||
            lesson.user_activity_state.equals("MISSED", true)) {
            return true
        }
        val endMs = getEndMs(lesson)
        return endMs < nowMs
    }

    return lessons.sortedWith { a, b ->
        val aLive = isLiveNow(a)
        val bLive = isLiveNow(b)

        when {
            aLive && !bLive -> -1
            !aLive && bLive -> 1
            else -> {
                val aPassed = isPassed(a)
                val bPassed = isPassed(b)
                when {
                    !aPassed && bPassed -> -1
                    aPassed && !bPassed -> 1
                    else -> getStartMs(a).compareTo(getStartMs(b))
                }
            }
        }
    }
}
