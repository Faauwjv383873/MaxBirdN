package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.database.DownloadedItemEntity
import com.example.player.ShikhoPlayerManager
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun PlayerControlsOverlay(
    title: String,
    subjectName: String,
    isPlaying: Boolean,
    isBuffering: Boolean,
    currentPosition: Long,
    bufferedPosition: Long,
    totalDuration: Long,
    areControlsVisible: Boolean,
    isFullscreen: Boolean,
    playbackSpeed: Float,
    isLive: Boolean = false,
    viewerCount: Int? = null,
    classType: com.example.player.PlayerClassType? = null,
    hasMeeting: Boolean = false,
    downloadedItem: DownloadedItemEntity? = null,
    onSwitchToMeeting: (() -> Unit)? = null,
    onTogglePlayPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekStarted: (Long) -> Unit,
    onSeekChanged: (Long) -> Unit,
    onSeekFinished: (Long) -> Unit,
    onToggleFullscreen: () -> Unit,
    onToggleControls: () -> Unit,
    onSpeedClick: () -> Unit,
    onQualityClick: () -> Unit = {},
    selectedQualityLabel: String = "অটো",
    onPipClick: () -> Unit = {},
    onDownloadClick: () -> Unit = {},
    resizeMode: Int = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT,
    onToggleResizeMode: () -> Unit = {},
    onBack: () -> Unit
) {
    val effectiveClassType = remember(classType, isLive) {
        classType ?: if (isLive) com.example.player.PlayerClassType.LIVE else com.example.player.PlayerClassType.RECORDED_LECTURE
    }

    // Double tap feedback state
    var doubleTapFeedbackSide by remember { mutableStateOf<String?>(null) }
    var doubleTapTriggerKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(doubleTapTriggerKey) {
        if (doubleTapFeedbackSide != null) {
            delay(650)
            doubleTapFeedbackSide = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        onToggleControls()
                    },
                    onDoubleTap = { offset ->
                        if (!isLive) {
                            val screenWidth = size.width
                            if (offset.x < screenWidth * 0.40f) {
                                // Left double tap -> Rewind 10s
                                onSeekBack()
                                doubleTapFeedbackSide = "LEFT"
                                doubleTapTriggerKey++
                            } else if (offset.x > screenWidth * 0.60f) {
                                // Right double tap -> Fast Forward 10s
                                onSeekForward()
                                doubleTapFeedbackSide = "RIGHT"
                                doubleTapTriggerKey++
                            } else {
                                // Center double tap -> Toggle play/pause
                                onTogglePlayPause()
                            }
                        }
                    }
                )
            }
    ) {
        // Double Tap Visual Ripple Feedback Overlays
        AnimatedVisibility(
            visible = doubleTapFeedbackSide == "LEFT",
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + scaleOut(targetScale = 0.85f),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 32.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.72f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay10,
                        contentDescription = "১০ সেকেন্ড পেছনে",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "-১০ সেকেন্ড",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = doubleTapFeedbackSide == "RIGHT",
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + scaleOut(targetScale = 0.85f),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 32.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.72f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Forward10,
                        contentDescription = "১০ সেকেন্ড সামনে",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "+১০ সেকেন্ড",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Overlay Controls
        AnimatedVisibility(
            visible = areControlsVisible || isBuffering,
            enter = fadeIn(animationSpec = tween(220)),
            exit = fadeOut(animationSpec = tween(220)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.82f),
                                Color.Black.copy(alpha = 0.15f),
                                Color.Black.copy(alpha = 0.88f)
                            )
                        )
                    )
            ) {
                // TOP BAR
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .then(if (isFullscreen) Modifier.statusBarsPadding() else Modifier)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: Back Button + Badges + Title
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1.2f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.18f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .clickable { onBack() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "ফিরে যান",
                                    tint = Color.White,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            when (effectiveClassType) {
                                com.example.player.PlayerClassType.LIVE -> {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFFE11D48),
                                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.3f))
                                    ) {
                                        Text(
                                            text = "🔴 LIVE",
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                    if (viewerCount != null && viewerCount > 0) {
                                        LiveViewerBadge(viewerCount = viewerCount)
                                    }
                                }
                                com.example.player.PlayerClassType.ANIMATED -> {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF8B5CF6),
                                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.3f))
                                    ) {
                                        Text(
                                            text = "🎬 অ্যানিমেটেড",
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                com.example.player.PlayerClassType.RECORDED_LECTURE -> {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF2563EB),
                                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.3f))
                                    ) {
                                        Text(
                                            text = "📖 লেকচার",
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            if (subjectName.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color.White.copy(alpha = 0.15f),
                                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.2f))
                                ) {
                                    Text(
                                        text = subjectName,
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Text(
                                text = title,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Right: Modern Glass Pills Action Strip
                    Row(
                        modifier = Modifier
                            .weight(0.8f)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        // Picture in Picture (PiP)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.16f),
                            border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.22f)),
                            modifier = Modifier.clickable { onPipClick() }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureInPictureAlt,
                                    contentDescription = "PiP",
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "PiP",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Aspect Ratio / Zoom Toggle Button
                        val resizeModeLabel = when (resizeMode) {
                            androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT -> "ফিট"
                            androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "জুম"
                            androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL -> "ফুল"
                            else -> "ফিট"
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.16f),
                            border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.22f)),
                            modifier = Modifier.clickable { onToggleResizeMode() }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AspectRatio,
                                    contentDescription = "ভিডিও সাইজ / জুম",
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = resizeModeLabel,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Quality Tag Button
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.16f),
                            border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.22f)),
                            modifier = Modifier.clickable { onQualityClick() }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HighQuality,
                                    contentDescription = "কোয়ালিটি",
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = selectedQualityLabel,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Download Button with Live Status Indicator
                        when (downloadedItem?.status) {
                            DownloadedItemEntity.STATUS_DOWNLOADING -> {
                                val item = downloadedItem
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF0284C7).copy(alpha = 0.4f),
                                    border = BorderStroke(0.8.dp, Color(0xFF38BDF8).copy(alpha = 0.6f)),
                                    modifier = Modifier.clickable { onDownloadClick() }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            progress = { item.progressFraction },
                                            color = Color(0xFF38BDF8),
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "${item.progressPercent}%",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                            DownloadedItemEntity.STATUS_COMPLETED -> {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF10B981).copy(alpha = 0.3f),
                                    border = BorderStroke(0.8.dp, Color(0xFF34D399).copy(alpha = 0.6f)),
                                    modifier = Modifier.clickable { onDownloadClick() }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DownloadDone,
                                            contentDescription = "অফলাইন ডাউনলোড সম্পন্ন",
                                            tint = Color(0xFF34D399),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "সেভড",
                                            color = Color(0xFF34D399),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            else -> {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.White.copy(alpha = 0.16f),
                                    border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.22f)),
                                    modifier = Modifier.clickable { onDownloadClick() }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Download,
                                            contentDescription = "ডাউনলোড",
                                            tint = Color.White,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "ডাউনলোড",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // Playback Speed Tag Button
                        if (!isLive) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White.copy(alpha = 0.16f),
                                border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.22f)),
                                modifier = Modifier.clickable { onSpeedClick() }
                            ) {
                                val displaySpeed = String.format(Locale.US, "%.2f", playbackSpeed).removeSuffix(".00")
                                Text(
                                    text = "${displaySpeed}x",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                // CENTER CONTROLS (-10s Rewind, Play/Pause Hero, +10s Forward)
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isBuffering) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.6f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                    } else {
                        // Rewind 10s Button
                        if (!isLive) {
                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .clickable { onSeekBack() }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Replay10,
                                        contentDescription = "১০ সেকেন্ড পেছনে যান",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }

                        // Play / Pause Hero Button
                        Surface(
                            shape = CircleShape,
                            color = Color.White,
                            shadowElevation = 8.dp,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .clickable { onTogglePlayPause() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "পজ" else "প্লে",
                                    tint = Color(0xFF0F172A),
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        // Forward 10s Button
                        if (!isLive) {
                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .clickable { onSeekForward() }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Forward10,
                                        contentDescription = "১০ সেকেন্ড সামনে যান",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // BOTTOM CONTROLS (Seekbar with buffered track + Bengali time + Fullscreen)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    if (!isLive) {
                        var isDraggingSlider by remember { mutableStateOf(false) }
                        var dragProgressFraction by remember { mutableFloatStateOf(0f) }

                        val displayPosition = if (isDraggingSlider) {
                            (dragProgressFraction * totalDuration).toLong().coerceIn(0L, totalDuration)
                        } else {
                            currentPosition
                        }

                        val sliderValue = if (isDraggingSlider) {
                            dragProgressFraction
                        } else if (totalDuration > 0) {
                            (currentPosition.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
                        } else 0f

                        val bufferedFraction = if (totalDuration > 0) {
                            (bufferedPosition.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
                        } else 0f

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(26.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Secondary Track for Buffering visualization
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color.White.copy(alpha = 0.2f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(bufferedFraction)
                                        .fillMaxHeight()
                                        .background(Color.White.copy(alpha = 0.45f))
                                )
                            }

                            // Interactive M3 Slider
                            Slider(
                                value = sliderValue,
                                onValueChange = { fraction ->
                                    isDraggingSlider = true
                                    dragProgressFraction = fraction
                                    val target = (fraction * totalDuration).toLong().coerceIn(0L, totalDuration)
                                    onSeekStarted(target)
                                    onSeekChanged(target)
                                },
                                onValueChangeFinished = {
                                    val target = (dragProgressFraction * totalDuration).toLong().coerceIn(0L, totalDuration)
                                    onSeekFinished(target)
                                    isDraggingSlider = false
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = Color(0xFFE11D48),
                                    inactiveTrackColor = Color.Transparent
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Time string in Bengali digits
                            val timeString = "${ShikhoPlayerManager.formatTime(displayPosition, true)} / ${ShikhoPlayerManager.formatTime(totalDuration, true)}"
                            Text(
                                text = timeString,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            Surface(
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.16f),
                                border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.22f)),
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .clickable { onToggleFullscreen() }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                        contentDescription = if (isFullscreen) "ছোট স্ক্রিন" else "ফুল স্ক্রিন",
                                        tint = Color.White,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        // LIVE STATUS BAR
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFE11D48))
                                )
                                Text(
                                    text = "🔴 সরাসরি লাইভ সম্প্রচার চলছে (Live)",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Surface(
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.16f),
                                border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.22f)),
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .clickable { onToggleFullscreen() }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                        contentDescription = if (isFullscreen) "ছোট স্ক্রিন" else "ফুল স্ক্রিন",
                                        tint = Color.White,
                                        modifier = Modifier.size(19.dp)
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
