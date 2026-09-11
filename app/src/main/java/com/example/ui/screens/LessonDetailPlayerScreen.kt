package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.api.StudentLessonItem
import com.example.player.ShikhoPlayerManager
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@OptIn(UnstableApi::class)
@Composable
fun LessonDetailPlayerScreen(
    lesson: StudentLessonItem?,
    subjectName: String,
    subjectColorHex: String?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Parse Subject Color
    val subjectThemeColor = remember(subjectColorHex) {
        if (!subjectColorHex.isNullOrBlank()) {
            try {
                Color(android.graphics.Color.parseColor(subjectColorHex))
            } catch (e: Exception) {
                Color(0xFF2563EB)
            }
        } else {
            Color(0xFF2563EB)
        }
    }

    // Video URL Resolution (recording_url from live_class)
    val videoUrl = remember(lesson) {
        val url = lesson?.live_class?.recording_url ?: ""
        if (url.isNotBlank() && url != "null") url else ""
    }

    // Player States
    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var totalDuration by remember { mutableLongStateOf(0L) }
    var bufferedPosition by remember { mutableLongStateOf(0L) }
    var isBuffering by remember { mutableStateOf(true) }
    var isFullscreen by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var areControlsVisible by remember { mutableStateOf(true) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableLongStateOf(0L) }
    var showSpeedDialog by remember { mutableStateOf(false) }

    // Expandable Accordion State for Topics
    var isTopicsExpanded by remember { mutableStateOf(true) }

    // ExoPlayer Instance with Shikho CDN headers
    val exoPlayer = remember {
        ShikhoPlayerManager.buildExoPlayer(context).apply {
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    // Initialize media source when videoUrl changes
    LaunchedEffect(videoUrl) {
        if (videoUrl.isNotBlank()) {
            val mediaSource = ShikhoPlayerManager.createMediaSource(videoUrl)
            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        } else {
            isBuffering = false
        }
    }

    // Player Event Listener
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> isBuffering = true
                    Player.STATE_READY -> {
                        isBuffering = false
                        totalDuration = exoPlayer.duration.coerceAtLeast(0L)
                    }
                    Player.STATE_ENDED -> {
                        isPlaying = false
                        isBuffering = false
                    }
                    Player.STATE_IDLE -> {
                        isBuffering = false
                    }
                }
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Periodic Progress Tracking Loop
    LaunchedEffect(isPlaying, isSeeking) {
        while (isPlaying && !isSeeking) {
            currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
            bufferedPosition = exoPlayer.bufferedPosition.coerceAtLeast(0L)
            totalDuration = exoPlayer.duration.coerceAtLeast(0L)
            delay(500)
        }
    }

    // Controls Auto-Hide Timer
    LaunchedEffect(areControlsVisible, isPlaying) {
        if (areControlsVisible && isPlaying && !isSeeking) {
            delay(4000)
            areControlsVisible = false
        }
    }

    // Lifecycle Observer (Pause on background, Resume on foreground)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                Lifecycle.Event.ON_RESUME -> {
                    if (isPlaying) exoPlayer.play()
                }
                Lifecycle.Event.ON_STOP -> exoPlayer.pause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Fullscreen Screen Orientation & Immersive Sticky System Bars
    fun toggleFullscreen() {
        val newFullscreen = !isFullscreen
        isFullscreen = newFullscreen
        activity?.let { act ->
            val window = act.window
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (newFullscreen) {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Back Handler to exit fullscreen first if active
    BackHandler {
        if (isFullscreen) {
            toggleFullscreen()
        } else {
            exoPlayer.stop()
            onBack()
        }
    }

    // Clean up screen orientation on leaving
    DisposableEffect(Unit) {
        onDispose {
            activity?.let { act ->
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                val window = act.window
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // UI Structure
    if (isFullscreen) {
        // FULLSCREEN VIDEO PLAYER
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Fullscreen Player Controls Overlay
            PlayerControlsOverlay(
                title = lesson?.title ?: "রেকর্ডকৃত ক্লাস",
                subjectName = subjectName,
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                currentPosition = if (isSeeking) seekPosition else currentPosition,
                bufferedPosition = bufferedPosition,
                totalDuration = totalDuration,
                areControlsVisible = areControlsVisible,
                isFullscreen = true,
                playbackSpeed = playbackSpeed,
                onTogglePlayPause = {
                    if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                },
                onSeekBack = {
                    val target = (exoPlayer.currentPosition - 10000L).coerceAtLeast(0L)
                    exoPlayer.seekTo(target)
                },
                onSeekForward = {
                    val target = (exoPlayer.currentPosition + 10000L).coerceAtMost(totalDuration)
                    exoPlayer.seekTo(target)
                },
                onSeekStarted = {
                    isSeeking = true
                    seekPosition = it
                },
                onSeekChanged = {
                    seekPosition = it
                },
                onSeekFinished = {
                    isSeeking = false
                    exoPlayer.seekTo(it)
                },
                onToggleFullscreen = { toggleFullscreen() },
                onToggleControls = { areControlsVisible = !areControlsVisible },
                onSpeedClick = { showSpeedDialog = true },
                onBack = { toggleFullscreen() }
            )
        }
    } else {
        // PORTRAIT DETAIL SCREEN
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
            ) {
                // 1. Top Video Player (16:9 Aspect Ratio)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black)
                ) {
                    if (videoUrl.isNotBlank()) {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    player = exoPlayer
                                    useController = false
                                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    layoutParams = FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Player Controls Overlay
                        PlayerControlsOverlay(
                            title = lesson?.title ?: "ক্লাস",
                            subjectName = subjectName,
                            isPlaying = isPlaying,
                            isBuffering = isBuffering,
                            currentPosition = if (isSeeking) seekPosition else currentPosition,
                            bufferedPosition = bufferedPosition,
                            totalDuration = totalDuration,
                            areControlsVisible = areControlsVisible,
                            isFullscreen = false,
                            playbackSpeed = playbackSpeed,
                            onTogglePlayPause = {
                                if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                            },
                            onSeekBack = {
                                val target = (exoPlayer.currentPosition - 10000L).coerceAtLeast(0L)
                                exoPlayer.seekTo(target)
                            },
                            onSeekForward = {
                                val target = (exoPlayer.currentPosition + 10000L).coerceAtMost(totalDuration)
                                exoPlayer.seekTo(target)
                            },
                            onSeekStarted = {
                                isSeeking = true
                                seekPosition = it
                            },
                            onSeekChanged = {
                                seekPosition = it
                            },
                            onSeekFinished = {
                                isSeeking = false
                                exoPlayer.seekTo(it)
                            },
                            onToggleFullscreen = { toggleFullscreen() },
                            onToggleControls = { areControlsVisible = !areControlsVisible },
                            onSpeedClick = { showSpeedDialog = true },
                            onBack = {
                                exoPlayer.stop()
                                onBack()
                            }
                        )
                    } else {
                        // Empty / No Video State Placeholder
                        Box(
                            modifier = Modifier
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
                                    imageVector = Icons.Default.PlayDisabled,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "এই ক্লাসের রেকর্ডিং শীঘ্রই যুক্ত হবে",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
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
                }

                // 2. Class Header & Info Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 18.dp)
                ) {
                    // Subject Tag & Teacher Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Subject Tag
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = subjectThemeColor.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                subjectThemeColor.copy(alpha = 0.3f)
                            )
                        ) {
                            Text(
                                text = subjectName.ifBlank { "সাধারণ বিষয়" },
                                color = subjectThemeColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }

                        // Teacher Info
                        val teacherName = lesson?.live_class?.teacherName
                        val teacherAvatar = lesson?.live_class?.teacherAvatar

                        if (!teacherName.isNullOrBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (!teacherAvatar.isNullOrBlank()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(teacherAvatar)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = teacherName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(subjectThemeColor.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = null,
                                            tint = subjectThemeColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = teacherName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Title
                    Text(
                        text = lesson?.title ?: "ক্লাস লেকচার",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        lineHeight = 28.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Date and Time in Bengali
                    val formattedTime = remember(lesson) {
                        val rawStart = lesson?.live_class?.start_time ?: lesson?.start_time
                        formatBanglaDateTime(rawStart)
                    }

                    Row(
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
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(18.dp))

                // 3. "ক্লাসের বিষয়বস্তু" (Expandable Accordion)
                val topics = lesson?.live_class?.topics ?: emptyList()
                val topicTitles = topics.map { it.displayTitle }.filter { it.isNotBlank() }

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            RoundedCornerShape(16.dp)
                        )
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Header (Clickable for Expand / Collapse)
                        val rotationState by animateFloatAsState(
                            targetValue = if (isTopicsExpanded) 180f else 0f,
                            label = "accordion_rotation"
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isTopicsExpanded = !isTopicsExpanded }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(subjectThemeColor.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MenuBook,
                                        contentDescription = null,
                                        tint = subjectThemeColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Text(
                                    text = "ক্লাসের বিষয়বস্তু",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isTopicsExpanded) "সংকোচন করুন" else "প্রসারিত করুন",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.rotate(rotationState)
                            )
                        }

                        // Expandable Content
                        AnimatedVisibility(
                            visible = isTopicsExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 20.dp, end = 20.dp, bottom = 16.dp)
                            ) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )

                                if (topicTitles.isNotEmpty()) {
                                    topicTitles.forEachIndexed { index, topic ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.Top,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .padding(top = 7.dp)
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(subjectThemeColor)
                                            )
                                            Text(
                                                text = topic,
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                                                lineHeight = 20.sp
                                            )
                                        }
                                    }
                                } else {
                                    // Fallback when topics list from API is generic
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .padding(top = 7.dp)
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(subjectThemeColor)
                                        )
                                        Text(
                                            text = lesson?.title ?: "এই ক্লাসের সকল মূল আলোচ্য বিষয় অন্তর্ভুক্ত রয়েছে",
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                                            lineHeight = 20.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 4. "ক্লাস রিসোর্সেস" ➔ "লেকচার স্লাইড" কার্ড
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = "ক্লাস রিসোর্সেস",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    val slideUrl = lesson?.live_class?.lectureSlideUrl

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                RoundedCornerShape(16.dp)
                            )
                            .clickable {
                                if (!slideUrl.isNullOrBlank()) {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(slideUrl)).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(
                                            context,
                                            "স্লাইড ওপেন করার জন্য উপযুক্ত অ্যাপ পাওয়া যায়নি",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                } else {
                                    Toast.makeText(
                                        context,
                                        "এই ক্লাসের লেকচার স্লাইড শীঘ্রই যুক্ত করা হবে",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFFE11D48).copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CoPresent,
                                        contentDescription = "লেকচার স্লাইড",
                                        tint = Color(0xFFE11D48),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Column {
                                    Text(
                                        text = "লেকচার স্লাইড",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (!slideUrl.isNullOrBlank()) "পিডিএফ স্লাইড ভিউ বা ডাউনলোড করুন" else "স্লাইড পিডিএফ ফরম্যাট",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                contentDescription = "ওপেন করুন",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }

    // Playback Speed Selection Dialog
    if (showSpeedDialog) {
        val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = {
                Text("প্লেব্যাক স্পিড", fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    speeds.forEach { speed ->
                        val isSelected = playbackSpeed == speed
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    playbackSpeed = speed
                                    exoPlayer.playbackParameters = PlaybackParameters(speed)
                                    showSpeedDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${speed}x" + if (speed == 1.0f) " (স্বাভাবিক)" else "",
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSpeedDialog = false }) {
                    Text("বন্ধ করুন")
                }
            }
        )
    }
}

// -----------------------------------------------------------------------------------------
// CUSTOM MODERN PLAYER CONTROLS OVERLAY
// -----------------------------------------------------------------------------------------
@Composable
private fun PlayerControlsOverlay(
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
    onTogglePlayPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekStarted: (Long) -> Unit,
    onSeekChanged: (Long) -> Unit,
    onSeekFinished: (Long) -> Unit,
    onToggleFullscreen: () -> Unit,
    onToggleControls: () -> Unit,
    onSpeedClick: () -> Unit,
    onBack: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                onToggleControls()
            }
    ) {
        AnimatedVisibility(
            visible = areControlsVisible || isBuffering,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.75f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            ) {
                // TOP BAR
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.4f))
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "ফিরে যান",
                                tint = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Text(
                                text = title,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (subjectName.isNotBlank()) {
                                Text(
                                    text = subjectName,
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // Speed and Settings Button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Playback Speed Tag Button
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier.clickable { onSpeedClick() }
                        ) {
                            Text(
                                text = "${playbackSpeed}x",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // CENTER CONTROLS (Play/Pause, Rewind, Fast Forward, Spinner)
                Box(
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    if (isBuffering) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(48.dp)
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(32.dp)
                        ) {
                            // Seek -10s
                            IconButton(
                                onClick = onSeekBack,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.45f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Replay10,
                                    contentDescription = "১০ সেকেন্ড পেছনে",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            // Play / Pause Button
                            IconButton(
                                onClick = onTogglePlayPause,
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "পজ" else "প্লে",
                                    tint = Color.Black,
                                    modifier = Modifier.size(34.dp)
                                )
                            }

                            // Seek +10s
                            IconButton(
                                onClick = onSeekForward,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.45f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Forward10,
                                    contentDescription = "১০ সেকেন্ড সামনে",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }

                // BOTTOM CONTROLS (Time & SeekBar, Fullscreen toggle)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    // Slider / Progress Bar
                    val sliderValue = if (totalDuration > 0) {
                        (currentPosition.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
                    } else 0f

                    Slider(
                        value = sliderValue,
                        onValueChange = { fraction ->
                            val target = (fraction * totalDuration).toLong()
                            onSeekStarted(target)
                            onSeekChanged(target)
                        },
                        onValueChangeFinished = {
                            val target = (sliderValue * totalDuration).toLong()
                            onSeekFinished(target)
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color(0xFFE11D48),
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(20.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Time string in Bengali digits
                        val timeString = "${ShikhoPlayerManager.formatTime(currentPosition, true)} / ${ShikhoPlayerManager.formatTime(totalDuration, true)}"
                        Text(
                            text = timeString,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )

                        IconButton(
                            onClick = onToggleFullscreen,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = if (isFullscreen) "ছোট স্ক্রিন" else "ফুল স্ক্রিন",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// HELPER FUNCTIONS
// -----------------------------------------------------------------------------------------
private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

private fun formatBanglaDateTime(isoDateStr: String?): String {
    if (isoDateStr.isNullOrBlank()) return "১ সেপ্টেম্বর ২০২৬ • ১৭:০০ pm"
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val date = parser.parse(isoDateStr) ?: return isoDateStr

        val dayFormat = SimpleDateFormat("d", Locale.getDefault())
        val monthFormat = SimpleDateFormat("MMMM", Locale("bn"))
        val yearFormat = SimpleDateFormat("yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

        val day = toBengaliDigits(dayFormat.format(date))
        val month = monthFormat.format(date)
        val year = toBengaliDigits(yearFormat.format(date))
        val time = toBengaliDigits(timeFormat.format(date).lowercase())

        "$day $month $year • $time"
    } catch (e: Exception) {
        isoDateStr
    }
}

private fun toBengaliDigits(input: String): String {
    val banglaDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
    val sb = StringBuilder()
    for (char in input) {
        if (char in '0'..'9') {
            sb.append(banglaDigits[char - '0'])
        } else {
            sb.append(char)
        }
    }
    return sb.toString()
}
