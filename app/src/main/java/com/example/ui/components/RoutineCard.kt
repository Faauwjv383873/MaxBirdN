package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.FilterList
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
import com.example.api.StudentLessonItem
import com.example.utils.ClassTypeUtils
import com.example.utils.SubjectColorUtils
import java.text.SimpleDateFormat
import java.util.*

// ==================== UNCHANGED UTILITIES ====================

fun String.toBengaliDigits(): String = com.example.utils.toBengaliDigits(this)

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
        0 -> DayColorScheme(listOf(Color(0xFF4F46E5), Color(0xFF6366F1)), Color.White, Color(0xFFEEF2FF), Color(0xFF4338CA), Color(0xFF818CF8))
        1 -> DayColorScheme(listOf(Color(0xFF0284C7), Color(0xFF38BDF8)), Color.White, Color(0xFFE0F2FE), Color(0xFF0369A1), Color(0xFF38BDF8))
        2 -> DayColorScheme(listOf(Color(0xFF059669), Color(0xFF10B981)), Color.White, Color(0xFFD1FAE5), Color(0xFF047857), Color(0xFF34D399))
        3 -> DayColorScheme(listOf(Color(0xFFEA580C), Color(0xFFF97316)), Color.White, Color(0xFFFFEDD5), Color(0xFFC2410C), Color(0xFFFB923C))
        4 -> DayColorScheme(listOf(Color(0xFF9333EA), Color(0xFFC084FC)), Color.White, Color(0xFFF3E8FF), Color(0xFF7E22CE), Color(0xFFC084FC))
        5 -> DayColorScheme(listOf(Color(0xFFE11D48), Color(0xFFFB7185)), Color.White, Color(0xFFFFE4E6), Color(0xFFBE123C), Color(0xFFFB7185))
        else -> DayColorScheme(listOf(Color(0xFF0D9488), Color(0xFF2DD4BF)), Color.White, Color(0xFFCCFBF1), Color(0xFF0F766E), Color(0xFF2DD4BF))
    }
}

// ==================== IMPROVED UI ====================

@Composable
fun WeeklyRoutineSection(
    lessons: List<StudentLessonItem>,
    isLoading: Boolean,
    onSeeAllClick: () -> Unit = {},
    onCustomizeSubjectsClick: () -> Unit = {},
    selectedSubjectsCount: Int = 0,
    totalSubjectsCount: Int = 0,
    selectedSubjectNames: List<String> = emptyList(),
    onOpenLessonDetail: (StudentLessonItem) -> Unit = {},
    onNavigateToExam: ((sessionId: String, lessonId: String, title: String, chapter: String) -> Unit)? = null
) {
    val dhakaZone = TimeZone.getTimeZone("Asia/Dhaka")
    val todayCal = Calendar.getInstance(dhakaZone)
    val todayDateKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = dhakaZone }.format(todayCal.time)

    // ===== LOGIC: daysList calculation (হুবহু সেম) =====
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

            RoutineDayItem(offset, shortName, fullName, dateNum, fullDateBn, dateKey, dateKey == todayDateKey, dayLessons, classCount, examCount)
        }
    }

    // ===== LOGIC: selectedDayIndex (সেম) =====
    var selectedDayIndex by remember(daysList) {
        val todayIdx = daysList.indexOfFirst { it.isToday }
        mutableStateOf(if (todayIdx >= 0) todayIdx else 0)
    }

    val selectedDay = daysList.getOrNull(selectedDayIndex) ?: daysList.firstOrNull()

    Column(modifier = Modifier.fillMaxWidth()) {
        // ---------- 1. Header Row ----------
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // NEW: gradient icon badge
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF0284C7), Color(0xFF38BDF8)))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "রুটিন",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // NEW: gradient "সব দেখুন" pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0xFF0284C7), Color(0xFF38BDF8))))
                    .clickable { onSeeAllClick() }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(Icons.Default.CalendarToday, null, tint = Color.White, modifier = Modifier.size(13.dp))
                    Text("সব দেখুন", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color.White, modifier = Modifier.size(13.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ---------- 2. 7-Day Strip — NEW: প্রতিদিন আলাদা রঙ + spring bounce ----------
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            daysList.forEach { day ->
                val isSelected = day.dayIndex == selectedDayIndex
                val scheme = getDayColorScheme(day.dayIndex)

                val pillScale by animateFloatAsState(
                    targetValue = if (isSelected) 1.07f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "pillScale${day.dayIndex}"
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp)
                        .graphicsLayer { scaleX = pillScale; scaleY = pillScale }
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            if (isSelected) {
                                Brush.verticalGradient(scheme.selectedGradient)
                            } else {
                                Brush.verticalGradient(listOf(Color(0xFF0D1B2A), Color(0xFF13233C)))
                            }
                        )
                        .border(
                            width = when {
                                isSelected -> 1.5.dp
                                day.isToday -> 1.dp
                                else -> 0.dp
                            },
                            color = when {
                                isSelected -> scheme.accentBorder
                                day.isToday -> Color(0xFF38BDF8).copy(alpha = 0.45f)
                                else -> Color.Transparent
                            },
                            shape = RoundedCornerShape(22.dp)
                        )
                        .clickable { selectedDayIndex = day.dayIndex }
                        .padding(vertical = 12.dp, horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = day.shortDayName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color.White else Color(0xFF94A3B8)
                    )

                    Spacer(modifier = Modifier.height(5.dp))

                    Text(
                        text = day.dateNumBn,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isSelected) Color.White else Color(0xFFE2E8F0)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    if (isSelected) {
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(4.5.dp).clip(CircleShape).background(Color.White))
                            Box(modifier = Modifier.size(4.5.dp).clip(CircleShape).background(Color.White))
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(4.5.dp)
                                .clip(CircleShape)
                                .background(scheme.accentBorder)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ---------- 3. Summary Row (সেম) ----------
        if (selectedDay != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarToday, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(selectedDay.fullDateBn, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF0284C7)))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ক্লাস ${selectedDay.classCount.toString().toBengaliDigits()}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("|", fontSize = 12.sp, color = Color(0xFF94A3B8))
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFFF59E0B)))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("এক্সাম ${selectedDay.examCount.toString().toBengaliDigits()}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ---------- 4. Lessons / Empty / Loading ----------
            when {
                isLoading -> {
                    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Color(0xFF1E293B)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color(0xFF38BDF8))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("রুটিন লোড হচ্ছে...", fontSize = 13.sp, color = Color(0xFF94A3B8))
                        }
                    }
                }

                selectedDay.lessons.isEmpty() -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF1E293B).copy(alpha = 0.7f),
                        border = BorderStroke(1.dp, Color(0xFF334155))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.EventBusy, null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("আজকে কোন ক্লাস বা পরীক্ষা নেই ✨", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFFCBD5E1), textAlign = TextAlign.Center)
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
                                onClick = { onOpenLessonDetail(lesson) },
                                onNavigateToExam = onNavigateToExam
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ---------- 5. সাবজেক্ট সাজাও Bento Card (সেম + press scale) ----------
        val bentoInteraction = remember { MutableInteractionSource() }
        val isBentoPressed by bentoInteraction.collectIsPressedAsState()
        val bentoScale by animateFloatAsState(
            targetValue = if (isBentoPressed) 0.98f else 1f,
            animationSpec = spring(stiffness = Spring.StiffnessMedium),
            label = "bentoScale"
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { scaleX = bentoScale; scaleY = bentoScale }
                .clip(RoundedCornerShape(22.dp))
                .clickable(interactionSource = bentoInteraction, indication = null) { onCustomizeSubjectsClick() },
            shape = RoundedCornerShape(22.dp),
            color = Color(0xFF071731),
            border = BorderStroke(1.dp, Color(0xFF1E3A5F)),
            shadowElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(Color(0xFF071731), Color(0xFF0C2448))))
                    .padding(16.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0284C7).copy(alpha = 0.2f))
                                .border(1.2.dp, Color(0xFF38BDF8), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Tune, null, tint = Color(0xFF38BDF8), modifier = Modifier.size(22.dp))
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("সাবজেক্ট সাজাও", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Box(Modifier.size(5.dp).clip(CircleShape).background(Color(0xFF38BDF8)))
                                    Box(Modifier.size(5.dp).clip(CircleShape).background(Color(0xFFF59E0B)))
                                    Box(Modifier.size(5.dp).clip(CircleShape).background(Color(0xFF10B981)))
                                }
                            }

                            Spacer(modifier = Modifier.height(3.dp))

                            Text(
                                text = if (selectedSubjectsCount > 0 && totalSubjectsCount > 0)
                                    "${selectedSubjectsCount.toString().toBengaliDigits()}/${totalSubjectsCount.toString().toBengaliDigits()} টি বিষয় নির্বাচন করা আছে"
                                else if (selectedSubjectsCount > 0)
                                    "${selectedSubjectsCount.toString().toBengaliDigits()} টি বিষয় নির্বাচন করা আছে"
                                else
                                    "তোমার প্রয়োজনীয় সাবজেক্টগুলো বেছে নাও",
                                fontSize = 11.5.sp,
                                color = Color(0xFF94A3B8),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF0284C7), shadowElevation = 2.dp) {
                        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FilterList, null, tint = Color.White, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("ফিল্টার", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }

                if (selectedSubjectNames.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        itemsIndexed(selectedSubjectNames) { index, subName ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.3f))
                            ) {
                                Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF38BDF8)))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("${(index + 1).toString().toBengaliDigits()}. $subName", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFFF1F5F9))
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
 * Routine Card — LOGIC সেম, NEW: live pulsing dot + press scale + gradient accent
 */
@Composable
fun ShikhoRoutineCard(
    lesson: StudentLessonItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToExam: ((sessionId: String, lessonId: String, title: String, chapter: String) -> Unit)? = null
) {
    // ===== LOGIC (হুবহু সেম) =====
    val startTime = lesson.start_time ?: lesson.live_class?.start_time
    val endTime = lesson.end_time ?: lesson.live_class?.end_time
    val startCal = parseIsoToDhakaCalendar(startTime)
    val endCal = parseIsoToDhakaCalendar(endTime)
    val timeString = formatTimeRange(startCal, endCal)
    val durationString = calculateDurationText(startCal, endCal)
    val fullTimeText = if (durationString.isNotBlank()) "$timeString • $durationString" else timeString

    val isExam = lesson.isExam
    val isLive = lesson.isLive

    val nowMs = System.currentTimeMillis()
    val startMs = startCal?.timeInMillis ?: Long.MAX_VALUE
    val endMs = endCal?.timeInMillis ?: (if (startMs != Long.MAX_VALUE) startMs + (90 * 60 * 1000L) else Long.MAX_VALUE)
    val isLiveNow = lesson.isLiveNow || (startMs != Long.MAX_VALUE && nowMs in (startMs - 5 * 60 * 1000L)..endMs && !isExam)
    val isExamNow = isExam && (startMs != Long.MAX_VALUE && nowMs in (startMs - 5 * 60 * 1000L)..endMs)

    val classTypeBadge = ClassTypeUtils.getClassTypeBadgeStyle(lesson)
    val classTypeLabel = when {
        isExamNow -> "✍️ পরীক্ষা চলছে"
        isExam -> "✍️ পরীক্ষা (Exam)"
        else -> classTypeBadge.label
    }

    val subjectName = lesson.subject_name ?: "বিষয়"
    val titleText = ClassTypeUtils.formatLessonTitle(lesson.title ?: lesson.live_class?.chapter_name ?: "অনলাইন ক্লাস")
    val subjectColors = SubjectColorUtils.getColorScheme(subjectName)

    val handleCardClick = {
        if (isExam && onNavigateToExam != null) {
            val sessionId = lesson.session_id?.takeIf { it.isNotBlank() }
                ?: lesson.live_class?.session_id?.takeIf { it.isNotBlank() }
                ?: lesson.content_id?.takeIf { it.isNotBlank() }
                ?: lesson.id
            val formattedTitle = ClassTypeUtils.formatLessonTitle(lesson.title ?: "পরীক্ষা")
            val chapterName = lesson.subject_name ?: ""
            onNavigateToExam(sessionId, lesson.id, formattedTitle, chapterName)
        } else {
            onClick()
        }
    }

    // ===== NEW: press scale + live pulse =====
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "routineCardScale"
    )

    val liveTransition = rememberInfiniteTransition(label = "livePulse")
    val liveDotScale by liveTransition.animateFloat(
        initialValue = 0.7f, targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(650, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "liveDotScale"
    )

    Card(
        modifier = modifier
            .width(260.dp)
            .height(154.dp)
            .graphicsLayer { scaleX = cardScale; scaleY = cardScale }
            .clickable(interactionSource = interaction, indication = null, onClick = handleCardClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isLiveNow -> Color(0xFFFFF1F2)
                isExamNow -> Color(0xFFFFFBEB)
                isExam -> Color(0xFFFEF3C7).copy(alpha = 0.35f)
                else -> subjectColors.backgroundColor.copy(alpha = 0.35f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isLiveNow || isExamNow) 5.dp else 2.dp),
        border = BorderStroke(
            width = if (isLiveNow || isExamNow) 1.5.dp else 1.2.dp,
            color = when {
                isLiveNow -> Color(0xFFEF4444)
                isExamNow -> Color(0xFFF59E0B)
                isExam -> Color(0xFFFCD34D)
                else -> subjectColors.backgroundColor
            }
        )
    ) {
        Row(modifier = Modifier.fillMaxHeight().fillMaxWidth()) {
            // NEW: gradient accent bar
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(
                        when {
                            isLiveNow -> Brush.verticalGradient(listOf(Color(0xFFEF4444), Color(0xFFFB7185)))
                            isExamNow -> Brush.verticalGradient(listOf(Color(0xFFF59E0B), Color(0xFFFBBF24)))
                            isExam -> Brush.verticalGradient(listOf(Color(0xFFD97706), Color(0xFFF59E0B)))
                            else -> Brush.verticalGradient(listOf(subjectColors.textColor, subjectColors.textColor.copy(alpha = 0.55f)))
                        }
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top tags
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Surface(shape = RoundedCornerShape(12.dp), color = subjectColors.backgroundColor) {
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

                    // Live / Exam / Class type badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = when {
                            isLiveNow -> Color(0xFFFEE2E2)
                            isExamNow || isExam -> Color(0xFFFEF3C7)
                            isLive -> Color(0xFFFEE2E2)
                            else -> classTypeBadge.backgroundColor
                        }
                    ) {
                        when {
                            isLiveNow -> {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .graphicsLayer { scaleX = liveDotScale; scaleY = liveDotScale }
                                            .clip(CircleShape)
                                            .background(Color(0xFFEF4444))
                                    )
                                    Text("🔴 লাইভ চলছে", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                                }
                            }
                            isExamNow -> {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .graphicsLayer { scaleX = liveDotScale; scaleY = liveDotScale }
                                            .clip(CircleShape)
                                            .background(Color(0xFFD97706))
                                    )
                                    Text("✍️ পরীক্ষা চলছে", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD97706))
                                }
                            }
                            isExam -> {
                                Text(
                                    text = "✍️ পরীক্ষা (Exam)",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFD97706),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            isLive -> {
                                Text(
                                    text = "🔴 লাইভ ক্লাস",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFDC2626),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            else -> {
                                Text(
                                    text = classTypeBadge.label,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = classTypeBadge.textColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Title
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = titleText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Time
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccessTime, null, tint = subjectColors.textColor, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(fullTimeText, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = subjectColors.textColor)
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
    ShikhoRoutineCard(lesson = lesson, onClick = onClick, modifier = modifier)
}

// ==================== ROUTINE UTILITIES DELEGATION ====================

fun parseIsoToDhakaCalendar(isoString: String?): Calendar? =
    com.example.utils.RoutineDateUtils.parseIsoToDhakaCalendar(isoString)

fun formatTimeRange(startCal: Calendar?, endCal: Calendar?): String =
    com.example.utils.RoutineDateUtils.formatTimeRange(startCal, endCal)

fun calculateDurationText(startCal: Calendar?, endCal: Calendar?): String =
    com.example.utils.RoutineDateUtils.calculateDurationText(startCal, endCal)

fun sortRoutineLessons(lessons: List<StudentLessonItem>): List<StudentLessonItem> =
    com.example.utils.RoutineDateUtils.sortRoutineLessons(lessons)

