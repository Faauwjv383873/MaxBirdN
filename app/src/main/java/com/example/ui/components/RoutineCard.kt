package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
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

@Composable
fun WeeklyRoutineSection(
    lessons: List<StudentLessonItem>,
    isLoading: Boolean,
    onSeeAllClick: () -> Unit = {},
    onCustomizeSubjectsClick: () -> Unit = {},
    selectedSubjectsCount: Int = 0,
    totalSubjectsCount: Int = 0,
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

        val shortDayNames = arrayOf("শনি", "রবি", "সোম", "মঙ্গল", "বুধ", "বৃহ", "শুক্র")
        val fullDayNames = arrayOf("শনিবার", "রবিবার", "সোমবার", "মঙ্গলবার", "বৃহস্পতিবার", "শুক্রবার", "শনিবার")

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
            
            // Filter lessons matching dateKey
            val dayLessons = lessons.filter { lesson ->
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

    // Default selected day: Today's index or index 0
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
                color = Color(0xFFEFF4FF),
                modifier = Modifier.clickable { onSeeAllClick() }
            ) {
                Text(
                    text = "সব দেখুন",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF3B82F6),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. 7 Days Strip Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            daysList.forEach { day ->
                val isSelected = day.dayIndex == selectedDayIndex

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) Color(0xFF3B82F6) else Color(0xFFF3F4F6))
                        .clickable { selectedDayIndex = day.dayIndex }
                        .padding(vertical = 10.dp, horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = day.shortDayName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isSelected) Color.White else Color(0xFF6B7280)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = day.dateNumBn,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color.White else Color(0xFF1F2937)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (day.lessons.isEmpty()) {
                        Text(
                            text = "—",
                            fontSize = 12.sp,
                            color = if (isSelected) Color.White.copy(alpha = 0.7f) else Color(0xFF9CA3AF)
                        )
                    } else {
                        if (isSelected) {
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Box(modifier = Modifier.size(3.5.dp).clip(CircleShape).background(Color.White))
                                    Box(modifier = Modifier.size(3.5.dp).clip(CircleShape).background(Color.White))
                                    Box(modifier = Modifier.size(3.5.dp).clip(CircleShape).background(Color.White))
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Box(modifier = Modifier.size(3.5.dp).clip(CircleShape).background(Color.White))
                                    Box(modifier = Modifier.size(3.5.dp).clip(CircleShape).background(Color.White))
                                    Box(modifier = Modifier.size(3.5.dp).clip(CircleShape).background(Color.White))
                                }
                            }
                        } else {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (day.classCount > 0) {
                                    Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(Color(0xFF06B6D4)))
                                }
                                if (day.examCount > 0) {
                                    Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(Color(0xFFF59E0B)))
                                }
                                if (day.classCount == 0 && day.examCount == 0) {
                                    Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(Color(0xFF06B6D4)))
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
                    color = Color(0xFF6B7280)
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
                        text = "ক্লাস ${selectedDay.classCount.toString().toBengaliDigits()}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF4B5563)
                    )

                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "|",
                        fontSize = 12.sp,
                        color = Color(0xFFD1D5DB)
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
                        color = Color(0xFF4B5563)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4. Lessons Horizontal Card List for Selected Day
            when {
                isLoading -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "রুটিন লোড হচ্ছে...",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                selectedDay.lessons.isEmpty() -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, Color(0xFFE5E7EB))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.EventBusy,
                                contentDescription = null,
                                tint = Color(0xFF9CA3AF),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "আজকে কোন ক্লাস বা পরীক্ষা নেই",
                                fontSize = 13.sp,
                                color = Color(0xFF6B7280),
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

        Spacer(modifier = Modifier.height(14.dp))

        // 5. "Customize Subjects" Button (সাবজেক্ট সাজাও)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable { onCustomizeSubjectsClick() },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(20.dp)
                                .padding(8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "সাবজেক্ট সাজাও",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (selectedSubjectsCount > 0 && totalSubjectsCount > 0)
                                "${selectedSubjectsCount.toString().toBengaliDigits()}/${totalSubjectsCount.toString().toBengaliDigits()} টি বিষয় নির্বাচন করা আছে"
                            else if (selectedSubjectsCount > 0)
                                "${selectedSubjectsCount.toString().toBengaliDigits()} টি বিষয় নির্বাচন করা আছে"
                            else
                                "তোমার প্রয়োজনীয় সাবজেক্টগুলো সিলেক্ট করো",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "পরিবর্তন",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

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

    val isExam = lesson.content_type == "LiveExam" || lesson.content_type == "ModelTest" || lesson.content_type == "Quiz"
    val classTypeLabel = when {
        isExam -> "✍️ লাইভ এক্সাম"
        lesson.content_type == "RecordedClass" -> "🎥 রেকর্ড করা ক্লাস"
        else -> "👨‍🏫 লেকচার ক্লাস"
    }

    val subjectName = lesson.subject_name ?: "বিষয়"
    val titleText = lesson.title ?: lesson.live_class?.chapter_name ?: "অনলাইন ক্লাস"
    val subjectColors = SubjectColorUtils.getColorScheme(subjectName)

    val titleFontSize = when {
        titleText.length > 50 -> 12.sp
        titleText.length > 30 -> 13.5.sp
        else -> 15.5.sp
    }
    val titleLineHeight = when {
        titleText.length > 50 -> 16.sp
        titleText.length > 30 -> 18.sp
        else -> 21.sp
    }

    Card(
        modifier = modifier
            .width(290.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, Color(0xFFE5E7EB))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Top Tags Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Subject Tag (Dynamic Colorful Background)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = subjectColors.backgroundColor
                ) {
                    Text(
                        text = subjectName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = subjectColors.textColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Class Type Tag
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF3F4F6)
                ) {
                    Text(
                        text = classTypeLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF374151),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Lesson Title (Auto-scaling font size with height constraint to prevent layout breaking)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = titleText,
                    fontSize = titleFontSize,
                    lineHeight = titleLineHeight,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Time & Duration String (Pinkish Red text)
            Text(
                text = fullTimeText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFDC2626)
            )
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

    // Check for dummy or invalid dates
    if (clean.startsWith("0000-00-00") || clean.startsWith("1970-01-01")) return null

    // 1. UTC ISO format ending with Z
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

        // Fallback for Z with take(19)
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

    // 2. ISO format with timezone offset (e.g. +06:00 or -05:00)
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

    // 3. Local Dhaka time string
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

    // 4. Date only string
    try {
        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = dhakaZone
        }
        val date = sdfDate.parse(clean.take(10))
        if (date != null) {
            return Calendar.getInstance(dhakaZone).apply { time = date }
        }
    } catch (_: Exception) {}

    // 5. Milliseconds timestamp
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
    
    // Check if endCal is valid and not absurdly far in the future
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
