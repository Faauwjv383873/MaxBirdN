package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.api.LessonAttachmentItem
import com.example.api.StudentLessonItem
import com.example.utils.formatBanglaDateTime

/**
 * Diagnostic error overlay displayed on top of the player when a stream error occurs.
 */
@Composable
fun PlayerErrorOverlay(
    playbackError: String,
    playbackErrorDetails: String?,
    isLive: Boolean,
    slideUrl: String?,
    onRefreshLesson: (() -> Unit)?,
    onRetryPlayback: () -> Unit,
    onLaunchWebPlayer: () -> Unit,
    onViewSlide: (LessonAttachmentItem) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xE60F172A)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = null,
                tint = Color(0xFFFBBF24),
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = playbackError,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = playbackErrorDetails ?: "সার্ভার রেসপন্স চেক করুন",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isLive && onRefreshLesson != null) {
                    Button(
                        onClick = onRefreshLesson,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("লাইভ ক্লাস রিফ্রেশ", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedButton(
                    onClick = onRetryPlayback,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("পুনরায় চেষ্টা", fontSize = 12.sp)
                }

                Button(
                    onClick = onLaunchWebPlayer,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("ব্রাউজার প্লেয়ার", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (!slideUrl.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                TextButton(
                    onClick = {
                        onViewSlide(
                            LessonAttachmentItem(
                                title = "লেকচার স্লাইড ও নোটস",
                                url = slideUrl,
                                file_type = "pdf"
                            )
                        )
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF93C5FD))
                ) {
                    Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("লেকচার স্লাইড ও ক্লাস নোটস পড়ুন", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Top Back Button
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "ফিরে যান",
                tint = Color.White
            )
        }
    }
}

/**
 * Empty / Placeholder state when video stream is not directly available.
 */
@Composable
fun LessonStreamPlaceholder(
    isLive: Boolean,
    lesson: StudentLessonItem?,
    slideUrl: String?,
    onJoinLiveClass: ((StudentLessonItem) -> Unit)?,
    onRefreshLesson: (() -> Unit)?,
    onViewSlide: (LessonAttachmentItem) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Dns,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(44.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isLive) "লাইভ ক্লাসের সংযোগ গ্রহণ করা হচ্ছে..." else "এই ক্লাসের সরাসরি রেকর্ডিং লিংক পাওয়া যায়নি",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isLive) "লাইভ স্ট্রিমে যুক্ত হতে নিচের বাটনে ক্লিক করুন" else "কোর্সে সরাসরি ভর্তি না থাকলে বা ক্লাস অপ্রস্তুত থাকলে Shikho API লিংক পাঠায় না।",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isLive && onJoinLiveClass != null && lesson != null) {
                    Button(
                        onClick = { onJoinLiveClass.invoke(lesson) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.LiveTv, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("লাইভ ক্লাসে যুক্ত হন", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (onRefreshLesson != null) {
                    Button(
                        onClick = onRefreshLesson,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("সার্ভার রিফ্রেশ", fontSize = 12.sp)
                    }
                }
            }

            if (!slideUrl.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                TextButton(
                    onClick = {
                        onViewSlide(
                            LessonAttachmentItem(
                                title = "লেকচার স্লাইড ও নোটস",
                                url = slideUrl,
                                file_type = "pdf"
                            )
                        )
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF93C5FD))
                ) {
                    Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("লেকচার স্লাইড ও ক্লাস নোটস পড়ুন", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Top Back Button
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "ফিরে যান",
                tint = Color.White
            )
        }
    }
}

/**
 * Live Class Active Room Banner & Control Card.
 */
@Composable
fun LiveClassRoomBanner(
    isLiveOngoing: Boolean,
    liveProvider: String,
    hmsRoomId: String?,
    livePlayerMode: String,
    effectiveMeetingUrl: String,
    onTogglePlayerMode: () -> Unit,
    onRefreshLesson: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF1F2)),
        border = BorderStroke(1.5.dp, Color(0xFFFDA4AF)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFE11D48),
                    ) {
                        Box(modifier = Modifier.size(10.dp).padding(2.dp))
                    }
                    Text(
                        text = if (isLiveOngoing) "🔴 সরাসরি লাইভ ক্লাস সম্প্রচার চলছে" else "🔴 লাইভ রুম নির্ধারিত",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFF9F1239)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFBE123C).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = liveProvider.uppercase(),
                        color = Color(0xFF9F1239),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            if (!hmsRoomId.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Room ID: $hmsRoomId",
                    fontSize = 11.sp,
                    color = Color(0xFF881337),
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onTogglePlayerMode,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (livePlayerMode == "MEETING") Color(0xFF2563EB) else Color(0xFFE11D48)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        if (livePlayerMode == "MEETING") Icons.Default.LiveTv else Icons.Default.Groups,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (livePlayerMode == "MEETING") "HLS প্লেয়ারে যান" else "100ms লাইভ রুমে যান",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (onRefreshLesson != null) {
                    IconButton(
                        onClick = onRefreshLesson,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White)
                            .border(1.dp, Color(0xFFFDA4AF), RoundedCornerShape(10.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "রিফ্রেশ",
                            tint = Color(0xFF9F1239),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                if (effectiveMeetingUrl.isNotBlank()) {
                    IconButton(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(effectiveMeetingUrl)).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White)
                            .border(1.dp, Color(0xFFFDA4AF), RoundedCornerShape(10.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInBrowser,
                            contentDescription = "ব্রাউজার",
                            tint = Color(0xFF9F1239),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Class Header & Info Section with Bengali Date-Time formatting.
 */
@Composable
fun LessonDetailHeader(
    lesson: StudentLessonItem?,
    modifier: Modifier = Modifier
) {
    val teacher = lesson?.live_class?.teacher 
        ?: lesson?.live_class?.instructor

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Title
        Text(
            text = lesson?.title ?: "ক্লাস লেকচার",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            lineHeight = 28.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Date and Time in Bengali
        val rawStart = lesson?.live_class?.start_time ?: lesson?.start_time
        val formattedTime = formatBanglaDateTime(rawStart)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = formattedTime,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }

        // Teacher Profile Card (Issue #5)
        if (teacher != null) {
            Spacer(modifier = Modifier.height(16.dp))
            
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                thickness = 0.8.dp,
                modifier = Modifier.padding(bottom = 14.dp)
            )

            Text(
                text = "ক্লাস শিক্ষক (Teacher)",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Avatar Box
                    val avatarUrl = teacher.displayAvatar
                    val teacherName = teacher.displayName
                    val context = LocalContext.current
                    
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.secondary
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        val isValidUrl = !avatarUrl.isNullOrBlank() && (avatarUrl.startsWith("http://") || avatarUrl.startsWith("https://"))
                        if (isValidUrl) {
                            coil.compose.SubcomposeAsyncImage(
                                model = coil.request.ImageRequest.Builder(context)
                                    .data(avatarUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = teacherName,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                error = {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = teacherName.firstOrNull()?.toString()?.uppercase() ?: "T",
                                            color = Color.White,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = teacherName.firstOrNull()?.toString()?.uppercase() ?: "T",
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    // Text Details
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = teacherName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        val designation = teacher.designation ?: teacher.bio ?: "মেন্টর ও শিক্ষক"
                        Text(
                            text = designation,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        val degrees = teacher.university_degree
                        if (!degrees.isNullOrEmpty()) {
                            Text(
                                text = degrees.joinToString(", "),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
