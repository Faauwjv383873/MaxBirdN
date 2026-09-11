package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.api.PhaseItem
import com.example.home.HomeViewModel

@Composable
fun CourseProgressTimeline(
    phases: List<PhaseItem>,
    activePhase: PhaseItem?,
    onSelectPhase: (PhaseItem) -> Unit,
    onEnrollPhase: (PhaseItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "কোর্স প্রগ্রেস",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "${phases.size}টি কোয়ার্টার ধাপ",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Step timeline
        phases.forEachIndexed { index, phase ->
            val isLast = index == phases.size - 1
            val isSelected = activePhase?.id == phase.id
            val isEnrolled = phase.has_enrolment == true || phase.is_current == true || phase.status == "ACTIVE"
            val progress = phase.course_progress_percentage ?: 0.0

            val nodeColor = when {
                progress >= 100.0 -> Color(0xFF0F9D58)
                isEnrolled -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectPhase(phase) }
            ) {
                // Left Column: Step node + vertical connector line
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(28.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(
                                if (isEnrolled) nodeColor else Color.Transparent
                            )
                            .border(
                                2.dp,
                                nodeColor,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (progress >= 100.0) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        } else if (isEnrolled) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                            )
                        } else {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                tint = nodeColor,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }

                    if (!isLast) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(72.dp)
                                .background(
                                    if (progress >= 100.0) Color(0xFF0F9D58)
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Right Column: Content Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = if (isLast) 0.dp else 14.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = phase.title ?: "কোয়ার্টার ${index + 1}",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isEnrolled) Color(0xFF0F9D58).copy(alpha = 0.12f) else Color(0xFFE53935).copy(alpha = 0.1f)
                                ) {
                                    Text(
                                        text = if (isEnrolled) "চলমান" else "ভর্তি হওনি",
                                        color = if (isEnrolled) Color(0xFF0F9D58) else Color(0xFFE53935),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            if (isEnrolled) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth(0.9f)
                                ) {
                                    LinearProgressIndicator(
                                        progress = { (progress / 100f).toFloat() },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(5.dp)
                                            .clip(RoundedCornerShape(3.dp)),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                                    )
                                    Text(
                                        text = "${HomeViewModel.toBengaliNumerals(progress.toInt())}%",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            } else {
                                Text(
                                    text = "এই কোয়ার্টারের সকল লাইভ ক্লাস ও নোট পেতে",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                )
                            }
                        }

                        if (!isEnrolled) {
                            Button(
                                onClick = { onEnrollPhase(phase) },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "ভর্তি হও",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
