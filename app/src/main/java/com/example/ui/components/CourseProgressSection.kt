package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.api.PhaseItem
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

@Composable
fun CourseProgressSection(
    phases: List<PhaseItem>,
    onPhaseClick: (PhaseItem) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val displayPhases = if (phases.isNotEmpty()) phases else defaultCoursePhases

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        // Section Title matching design language
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "কোর্স প্রগ্রেস",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Timeline Items
        Column(
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            displayPhases.forEachIndexed { index, phase ->
                val isLast = index == displayPhases.size - 1

                val isCompleted = phase.status.equals("COMPLETED", ignoreCase = true) ||
                        ((phase.course_progress_percentage ?: 0.0) > 0.0 && phase.is_current != true && !phase.status.equals("UPCOMING", ignoreCase = true))
                val isActive = phase.is_current == true || phase.status.equals("ACTIVE", ignoreCase = true)
                val isUpcoming = phase.status.equals("UPCOMING", ignoreCase = true) || (!isCompleted && !isActive)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.Top
                ) {
                    // Left Timeline Node & Connecting Line
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(42.dp)
                            .fillMaxHeight()
                    ) {
                        // Node Icon Box
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(36.dp)
                        ) {
                            when {
                                isCompleted -> {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color(0xFF22C55E), // Solid Green
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Completed",
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                                isActive -> {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(34.dp)
                                            .border(
                                                width = 3.dp,
                                                color = Color(0xFF3B82F6), // Blue Outer Ring
                                                shape = CircleShape
                                            )
                                            .background(Color.White, CircleShape)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .background(Color(0xFF3B82F6), CircleShape) // Inner Blue Dot
                                        )
                                    }
                                }

                                else -> {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color(0xFFE2E8F0), // Light Grey
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Lock,
                                                contentDescription = "Locked",
                                                tint = Color(0xFF94A3B8),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Connecting Line Down (if not last item)
                        if (!isLast) {
                            if (isCompleted) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .weight(1f)
                                        .background(Color(0xFF22C55E))
                                )
                            } else {
                                Canvas(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .weight(1f)
                                ) {
                                    val pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                                    drawLine(
                                        color = Color(0xFFCBD5E1),
                                        start = Offset(size.width / 2, 0f),
                                        end = Offset(size.width / 2, size.height),
                                        strokeWidth = 3.dp.toPx(),
                                        pathEffect = pathEffect
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Right Content Card
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(bottom = if (isLast) 0.dp else 16.dp)
                    ) {
                        QuarterCard(
                            phase = phase,
                            isCompleted = isCompleted,
                            isActive = isActive,
                            isUpcoming = isUpcoming,
                            onClick = { onPhaseClick(phase) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuarterCard(
    phase: PhaseItem,
    isCompleted: Boolean,
    isActive: Boolean,
    isUpcoming: Boolean,
    onClick: () -> Unit
) {
    val cardBackground = if (isUpcoming) {
        Color(0xFFF1F5F9)
    } else {
        Color(0xFF8B96B8) // Premium slate-blue gradient card matching screenshot
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isUpcoming) 0.dp else 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Left edge dark blue vertical accent bar for active/completed cards
            if (!isUpcoming) {
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .height(88.dp)
                        .align(Alignment.CenterStart)
                        .background(
                            color = Color(0xFF1D4ED8),
                            shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)
                        )
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    // Title and Badge Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = phase.title ?: "কোয়ার্টার",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isUpcoming) Color(0xFF1E293B) else Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        // Pill Badge
                        val (badgeText, badgeBg, badgeTextColor) = when {
                            isCompleted -> Triple("পড়ানো শেষ", Color(0xFFDCFCE7), Color(0xFF15803D))
                            isActive -> Triple("চলছে", Color(0xFFDBEAFE), Color(0xFF1E40AF))
                            else -> Triple("ভর্তি হয়েছো", Color(0xFFDCFCE7), Color(0xFF15803D))
                        }

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = badgeBg
                        ) {
                            Text(
                                text = badgeText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = badgeTextColor,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Date Range Subtitle (visible on all cards)
                    val dateRangeText = formatQuarterDateRange(phase.start_date, phase.end_date)
                    if (dateRangeText.isNotBlank()) {
                        Text(
                            text = dateRangeText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isUpcoming) Color(0xFF64748B) else Color.White.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Progress %
                    if (!isUpcoming) {
                        val pct = (phase.course_progress_percentage ?: 0.0).roundToInt()
                        Text(
                            text = "${pct.toString().toBengaliDigits()}%",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // Arrow Button
                Surface(
                    shape = CircleShape,
                    color = Color.White,
                    shadowElevation = 1.dp,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Open",
                            tint = Color(0xFF334155),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

private val bengaliMonths = arrayOf(
    "জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন",
    "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর"
)

private fun parseDateSafe(dateStr: String?): java.util.Date? {
    if (dateStr.isNullOrBlank()) return null
    val formats = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSX",
        "yyyy-MM-dd'T'HH:mm:ssX",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd"
    )
    for (fmt in formats) {
        try {
            val parser = SimpleDateFormat(fmt, Locale.US)
            parser.timeZone = TimeZone.getTimeZone("UTC")
            val date = parser.parse(dateStr)
            if (date != null) return date
        } catch (_: Exception) {}
    }
    try {
        val ts = dateStr.toLongOrNull()
        if (ts != null) {
            val millis = if (ts < 10000000000L) ts * 1000 else ts
            return java.util.Date(millis)
        }
    } catch (_: Exception) {}
    return null
}

private fun formatQuarterDateRange(startDateStr: String?, endDateStr: String?): String {
    if (startDateStr.isNullOrBlank() && endDateStr.isNullOrBlank()) {
        return "অক্টোবর'২৫ - ডিসেম্বর'২৫"
    }
    val start = parseDateSafe(startDateStr)
    val end = parseDateSafe(endDateStr)

    if (start != null || end != null) {
        val dhakaZone = TimeZone.getTimeZone("Asia/Dhaka")
        val cal = java.util.Calendar.getInstance(dhakaZone)

        fun formatSingle(date: java.util.Date): String {
            cal.time = date
            val monthIdx = cal.get(java.util.Calendar.MONTH)
            val monthName = bengaliMonths.getOrElse(monthIdx) { "" }
            val year = cal.get(java.util.Calendar.YEAR) % 100
            val yearBn = String.format(Locale.US, "%02d", year).toBengaliDigits()
            return "$monthName'$yearBn"
        }

        val startBn = start?.let { formatSingle(it) } ?: ""
        val endBn = end?.let { formatSingle(it) } ?: ""

        return when {
            startBn.isNotBlank() && endBn.isNotBlank() -> "$startBn - $endBn"
            startBn.isNotBlank() -> "শুরু: $startBn"
            endBn.isNotBlank() -> "পর্যন্ত: $endBn"
            else -> "অক্টোবর'২৫ - ডিসেম্বর'২৫"
        }
    }

    return "অক্টোবর'২৫ - ডিসেম্বর'২৫"
}

val defaultCoursePhases = listOf(
    PhaseItem(
        id = "q1",
        academic_program_id = "",
        title = "কোয়ার্টার ১",
        status = "COMPLETED",
        is_current = false,
        has_enrolment = true,
        course_progress_percentage = 1.0,
        start_date = "2025-01-01T00:00:00Z",
        end_date = "2025-03-31T23:59:59Z"
    ),
    PhaseItem(
        id = "q2",
        academic_program_id = "",
        title = "কোয়ার্টার ২",
        status = "COMPLETED",
        is_current = false,
        has_enrolment = true,
        course_progress_percentage = 6.0,
        start_date = "2025-04-01T00:00:00Z",
        end_date = "2025-06-30T23:59:59Z"
    ),
    PhaseItem(
        id = "q3",
        academic_program_id = "",
        title = "কোয়ার্টার ৩",
        status = "COMPLETED",
        is_current = false,
        has_enrolment = true,
        course_progress_percentage = 5.0,
        start_date = "2025-07-01T00:00:00Z",
        end_date = "2025-09-30T23:59:59Z"
    ),
    PhaseItem(
        id = "q4",
        academic_program_id = "",
        title = "কোয়ার্টার ৪",
        status = "ACTIVE",
        is_current = true,
        has_enrolment = true,
        course_progress_percentage = 8.0,
        start_date = "2025-10-01T00:00:00Z",
        end_date = "2025-12-31T23:59:59Z"
    ),
    PhaseItem(
        id = "q5",
        academic_program_id = "",
        title = "কোয়ার্টার ৫",
        status = "UPCOMING",
        is_current = false,
        has_enrolment = true,
        course_progress_percentage = 0.0,
        start_date = "2026-01-01T00:00:00Z",
        end_date = "2026-03-31T23:59:59Z"
    )
)
