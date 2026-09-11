package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.api.StudentLessonItem
import com.example.home.CalendarDay
import com.example.home.HomeViewModel

@Composable
fun RoutineSection(
    calendarDays: List<CalendarDay>,
    selectedDateIso: String,
    lessons: List<StudentLessonItem>,
    onSelectDate: (String) -> Unit,
    onViewAllRoutine: () -> Unit,
    onOpenLesson: (StudentLessonItem) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        // 1. Header with Title & "সব দেখো"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "রুটিন",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "সাপ্তাহিক",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            TextButton(
                onClick = onViewAllRoutine,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "সব দেখো >",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 2. 7-Day Horizontal Date Selector (Saturday to Friday)
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(calendarDays) { day ->
                val isSelected = day.dateIso == selectedDateIso

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    border = if (isSelected) null else androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    ),
                    shadowElevation = if (isSelected) 3.dp else 0.dp,
                    modifier = Modifier
                        .width(48.dp)
                        .clickable { onSelectDate(day.dateIso) }
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = day.dayNameBn,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = day.dayNumberBn,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Event dots row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val dotCount = minOf(day.eventCount, 3)
                            if (dotCount == 0) {
                                Box(
                                    modifier = Modifier
                                        .size(4.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) Color.White.copy(alpha = 0.3f) else Color.Transparent)
                                )
                            } else {
                                repeat(dotCount) {
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) Color.White else MaterialTheme.colorScheme.primary)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3. Horizontal Carousel of Selected Date's Lessons / Exams
        if (lessons.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "এই তারিখে কোনো নির্ধারিত ক্লাস বা পরীক্ষা নেই",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        } else {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(lessons) { lesson ->
                    RoutineLessonCard(
                        lesson = lesson,
                        onOpen = { onOpenLesson(lesson) }
                    )
                }
            }
        }
    }
}

@Composable
fun RoutineLessonCard(
    lesson: StudentLessonItem,
    onOpen: () -> Unit = {}
) {
    val isLive = lesson.live_class?.is_on_going == true || lesson.user_activity_state == "LIVE"
    val isExam = lesson.content_type == "LiveExam" || lesson.model_test != null

    val primaryColor = MaterialTheme.colorScheme.primary
    val cardAccentColor = remember(lesson.color_code, primaryColor) {
        try {
            if (!lesson.color_code.isNullOrBlank()) {
                Color(android.graphics.Color.parseColor(lesson.color_code))
            } else {
                primaryColor
            }
        } catch (_: Exception) {
            primaryColor
        }
    }

    val statusText = when {
        isLive -> "🔴 লাইভ চলছে"
        lesson.user_activity_state == "COMPLETED" -> "সম্পন্ন"
        lesson.user_activity_state == "MISSED" -> "মিস হয়েছে"
        else -> "আসন্ন"
    }

    val statusBg = when {
        isLive -> Color(0xFFE53935).copy(alpha = 0.12f)
        lesson.user_activity_state == "COMPLETED" -> Color(0xFF0F9D58).copy(alpha = 0.12f)
        lesson.user_activity_state == "MISSED" -> Color(0xFFFF9800).copy(alpha = 0.12f)
        else -> cardAccentColor.copy(alpha = 0.12f)
    }

    val statusColor = when {
        isLive -> Color(0xFFE53935)
        lesson.user_activity_state == "COMPLETED" -> Color(0xFF0F9D58)
        lesson.user_activity_state == "MISSED" -> Color(0xFFFF9800)
        else -> cardAccentColor
    }

    Card(
        modifier = Modifier
            .width(285.dp)
            .clickable { onOpen() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isLive) Color(0xFFE53935).copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Subject Pill + Content Type & Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(cardAccentColor)
                    )
                    Text(
                        text = lesson.subject_name ?: "বিষয়",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = cardAccentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = statusBg
                ) {
                    Text(
                        text = statusText,
                        color = statusColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Class / Exam Title
            Text(
                text = lesson.title ?: "ক্লাস শিডিউল",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (!lesson.live_class?.chapter_name.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = lesson.live_class?.chapter_name ?: "",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Time and Duration with Bengali Numerals
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                val startTime = HomeViewModel.toBengaliNumerals(lesson.start_time ?: "০৬:০০ PM")
                val endTime = HomeViewModel.toBengaliNumerals(lesson.end_time ?: "০৭:৩০ PM")
                Text(
                    text = "$startTime - $endTime",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Button
            Button(
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isLive) Color(0xFFE53935) else cardAccentColor
                ),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                Icon(
                    if (isExam) Icons.Default.Quiz else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = when {
                        isLive -> "লাইভ ক্লাসে জয়েন করো"
                        isExam -> "পরীক্ষা শুরু করো"
                        lesson.user_activity_state == "COMPLETED" -> "রেকর্ডিং দেখো"
                        else -> "ক্লাস বিবরণী"
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
