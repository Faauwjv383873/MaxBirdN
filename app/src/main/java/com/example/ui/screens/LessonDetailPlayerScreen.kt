package com.example.ui.screens

import android.app.Activity
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Environment
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.api.LessonAttachmentItem
import com.example.api.StudentLessonItem
import com.example.player.ShikhoPlayerManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*

@OptIn(UnstableApi::class)
@Composable
fun LessonDetailPlayerScreen(
    lesson: StudentLessonItem?,
    subjectName: String,
    subjectColorHex: String?,
    onRefreshLesson: (() -> Unit)? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
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

    val candidateStreams = remember(lesson) {
        lesson?.candidateStreamUrls ?: emptyList()
    }
    var currentStreamIndex by remember(lesson) { mutableIntStateOf(0) }
    var activeStreamUrl by remember(candidateStreams, currentStreamIndex) {
        mutableStateOf(candidateStreams.getOrNull(currentStreamIndex) ?: lesson?.resolvedVideoUrl ?: "")
    }

    // Diagnostics & Dialog States
    var playbackError by remember { mutableStateOf<String?>(null) }
    var playbackErrorDetails by remember { mutableStateOf<String?>(null) }
    var showDiagnosticDialog by remember { mutableStateOf(false) }

    var showCustomUrlDialog = false

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

    // Slide viewing state
    var viewingSlideItem by remember { mutableStateOf<LessonAttachmentItem?>(null) }
    var isRefreshingSlide by remember { mutableStateOf(false) }

    // Expandable Accordion State for Topics
    var isTopicsExpanded by remember { mutableStateOf(true) }

    // ExoPlayer Instance with Shikho CDN headers
    val exoPlayer = remember {
        ShikhoPlayerManager.buildExoPlayer(context).apply {
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    // Initialize media source when activeStreamUrl changes
    LaunchedEffect(activeStreamUrl) {
        playbackError = null
        playbackErrorDetails = null
        if (activeStreamUrl.isNotBlank()) {
            isBuffering = true
            try {
                val mediaSource = ShikhoPlayerManager.createMediaSource(activeStreamUrl)
                exoPlayer.setMediaSource(mediaSource)
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
            } catch (e: Exception) {
                isBuffering = false
                playbackError = "প্লেয়ার প্রস্তুত করতে ব্যর্থ"
                playbackErrorDetails = e.localizedMessage ?: "অজানা ত্রুটি"
            }
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
                        playbackError = null
                        playbackErrorDetails = null
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

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                if (currentStreamIndex + 1 < candidateStreams.size) {
                    currentStreamIndex += 1
                    activeStreamUrl = candidateStreams[currentStreamIndex]
                    return
                }

                isBuffering = false
                isPlaying = false
                val cause = error.cause
                val httpEx = cause as? HttpDataSource.InvalidResponseCodeException
                    ?: (cause?.cause as? HttpDataSource.InvalidResponseCodeException)
                val httpCode = httpEx?.responseCode

                val detail = when {
                    httpCode == 404 -> "এই কোর্সটিতে সরাসরি ভিডিও অ্যাক্সেস নেই (ভর্তি প্রয়োজন বা রেকর্ডিং নেই)"
                    httpCode == 403 -> "CDN সার্ভারে অ্যাক্সেস অনুমোদিত নয় (HTTP 403 Forbidden)"
                    httpCode != null -> "CDN নেটওয়ার্ক রেসপন্স ত্রুটি (HTTP $httpCode)"
                    cause is java.net.UnknownHostException -> "ইন্টারনেট সংযোগ নেই বা CDN সার্ভারে পৌঁছানো যাচ্ছে না"
                    cause is java.net.SocketTimeoutException -> "সার্ভার সংযোগ সময়োত্তীর্ণ (Connection Timeout)"
                    else -> error.localizedMessage ?: "অজানা প্লেব্যাক ত্রুটি"
                }
                playbackError = "ভিডিও লোড হয়নি"
                playbackErrorDetails = detail
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
                    if (activeStreamUrl.isNotBlank()) {
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

                        // If playbackError is present, show a sleek diagnostic overlay on top of the player!
                        if (playbackError != null) {
                            Box(
                                modifier = Modifier
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
                                        text = playbackError ?: "ক্লাস লোড ব্যর্থ হয়েছে",
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

                                        OutlinedButton(
                                            onClick = {
                                                val curr = activeStreamUrl
                                                activeStreamUrl = ""
                                                coroutineScope.launch {
                                                    delay(150)
                                                    activeStreamUrl = curr
                                                }
                                            },
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = Color.White
                                            ),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("পুনরায় চেষ্টা", fontSize = 12.sp)
                                        }

                                        IconButton(
                                            onClick = { showDiagnosticDialog = true },
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(Color.White.copy(alpha = 0.15f))
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Info,
                                                contentDescription = "কারণ ও বিবরণ",
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }


                                    }

                                    val slideUrlForError = lesson?.resolvedSlideUrl
                                        ?: lesson?.live_class?.lectureSlideUrl
                                    if (!slideUrlForError.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        TextButton(
                                            onClick = {
                                                viewingSlideItem = LessonAttachmentItem(
                                                    title = "লেকচার স্লাইড ও নোটস",
                                                    url = slideUrlForError,
                                                    file_type = "pdf"
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
                        } else {
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
                        }
                    } else {
                        // Empty / No Direct Stream State Placeholder with Diagnostics
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
                                    imageVector = Icons.Default.Dns,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "এই ক্লাসের সরাসরি রেকর্ডিং লিংক পাওয়া যায়নি",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "কোর্সে সরাসরি ভর্তি না থাকলে বা ক্লাস অপ্রস্তুত থাকলে Shikho API লিংক পাঠায় না।",
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

                                    OutlinedButton(
                                        onClick = { showDiagnosticDialog = true },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("কারণ ও ডায়াগনস্টিক", fontSize = 12.sp)
                                    }

                                    OutlinedButton(
                                        onClick = { showCustomUrlDialog = true },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("কাস্টম লিংক", fontSize = 12.sp)
                                    }
                                }

                                val slideUrlForEmpty = lesson?.resolvedSlideUrl
                                    ?: lesson?.live_class?.lectureSlideUrl
                                if (!slideUrlForEmpty.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    TextButton(
                                        onClick = {
                                            viewingSlideItem = LessonAttachmentItem(
                                                title = "লেকচার স্লাইড ও নোটস",
                                                url = slideUrlForEmpty,
                                                file_type = "pdf"
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

                    // Stream Status Banner & Diagnostic Controls
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (playbackError != null) Color(0xFFFEF2F2) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (playbackError != null) Color(0xFFFECACA) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                playbackError != null -> Color(0xFFEF4444)
                                                isBuffering -> Color(0xFFF59E0B)
                                                isPlaying -> Color(0xFF10B981)
                                                else -> Color(0xFF6B7280)
                                            }
                                        )
                                )
                                Column {
                                    Text(
                                        text = when {
                                            playbackError != null -> "ভিডিও লোড হয়নি (${playbackErrorDetails ?: "ত্রুটি"})"
                                            isBuffering -> "ভিডিও বাফারিং হচ্ছে..."
                                            isPlaying -> "ক্লাস চলছে (CDN #${currentStreamIndex + 1})"
                                            else -> "স্ট্রিমিং প্রস্তুত (সার্ভার #${currentStreamIndex + 1})"
                                        },
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (playbackError != null) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (candidateStreams.isNotEmpty()) "মোট CDN অপশন: ${candidateStreams.size}টি" else "সরাসরি লিঙ্ক অনুপস্থিত",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {

                                IconButton(
                                    onClick = { showDiagnosticDialog = true },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "কেন লোড হচ্ছে না",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
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

                // 4. "ক্লাস রিসোর্সেস" ➔ "লেকচার স্লাইড" সেকশন
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ক্লাস রিসোর্সেস ও স্লাইড",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        if (onRefreshLesson != null) {
                            TextButton(
                                onClick = {
                                    if (!isRefreshingSlide) {
                                        isRefreshingSlide = true
                                        onRefreshLesson.invoke()
                                        Toast.makeText(context, "স্লাইড আপডেট চেক করা হচ্ছে...", Toast.LENGTH_SHORT).show()
                                        coroutineScope.launch {
                                            delay(1500)
                                            isRefreshingSlide = false
                                        }
                                    }
                                },
                                enabled = !isRefreshingSlide
                            ) {
                                if (isRefreshingSlide) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "রিফ্রেশ",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text("রিফ্রেশ", fontSize = 13.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val allAttachments = lesson?.allAttachments ?: emptyList()
                    val fallbackSlideUrl = lesson?.resolvedSlideUrl ?: lesson?.live_class?.lectureSlideUrl

                    if (allAttachments.isNotEmpty()) {
                        allAttachments.forEach { attachment ->
                            val downloadUrl = attachment.downloadUrl
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                        RoundedCornerShape(16.dp)
                                    )
                                    .clickable {
                                        if (!downloadUrl.isNullOrBlank()) {
                                            viewingSlideItem = attachment
                                        } else {
                                            Toast.makeText(context, "এই ফাইলের লিঙ্ক উপলব্ধ নেই", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color(0xFFE11D48).copy(alpha = 0.12f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PictureAsPdf,
                                                contentDescription = "পিডিএফ স্লাইড",
                                                tint = Color(0xFFE11D48),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }

                                        Column {
                                            Text(
                                                text = attachment.displayTitle,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "ইন-অ্যাপ দেখুন • ডাউনলোড (PDF)",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (!downloadUrl.isNullOrBlank()) {
                                            IconButton(
                                                onClick = {
                                                    downloadFile(context, downloadUrl, attachment.displayTitle)
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Download,
                                                    contentDescription = "ডাউনলোড",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }

                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                            contentDescription = "দেখুন",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else if (!fallbackSlideUrl.isNullOrBlank()) {
                        val singleAttachment = LessonAttachmentItem(
                            title = "লেকচার স্লাইড (PDF)",
                            url = fallbackSlideUrl,
                            file_type = "pdf"
                        )
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
                                    viewingSlideItem = singleAttachment
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.weight(1f)
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
                                            text = "ইন-অ্যাপ স্লাইড ভিউ বা ডাউনলোড করুন",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            downloadFile(context, fallbackSlideUrl, "লেকচার স্লাইড")
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Download,
                                            contentDescription = "ডাউনলোড",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
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
                    } else {
                        // Fallback state when server hasn't provided a slide yet
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MenuBook,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "এই ক্লাসের লেকচার স্লাইড প্রক্রিয়াধীন",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "লাইভ ক্লাসের পর সাধারণত শিক্ষক স্লাইড আপলোড করেন। নতুন স্লাইড এসেছে কিনা চেক করতে রিফ্রেশ করুন।",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    lineHeight = 16.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                if (onRefreshLesson != null) {
                                    FilledTonalButton(
                                        onClick = {
                                            if (!isRefreshingSlide) {
                                                isRefreshingSlide = true
                                                onRefreshLesson.invoke()
                                                Toast.makeText(context, "স্লাইড আপডেট চেক করা হচ্ছে...", Toast.LENGTH_SHORT).show()
                                                coroutineScope.launch {
                                                    delay(1500)
                                                    isRefreshingSlide = false
                                                }
                                            }
                                        },
                                        enabled = !isRefreshingSlide
                                    ) {
                                        if (isRefreshingSlide) {
                                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                            Spacer(modifier = Modifier.width(6.dp))
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                        }
                                        Text("এখনই চেক করুন")
                                    }
                                }
                            }
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

    // In-App Slide Viewer Dialog
    if (viewingSlideItem != null) {
        val slide = viewingSlideItem!!
        val url = slide.downloadUrl ?: ""
        SlideViewerDialog(
            slideUrl = url,
            title = slide.displayTitle,
            onDismiss = { viewingSlideItem = null }
        )
    }

    // Diagnostic Explanation Dialog
    if (showDiagnosticDialog) {
        Dialog(
            onDismissRequest = { showDiagnosticDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .padding(vertical = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "ক্লাস লোড তথ্য ও ডায়াগনস্টিক",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(
                            onClick = { showDiagnosticDialog = false },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "বন্ধ করুন")
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Explanation Box
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "📌 কেন কিছু ক্লাসের ভিডিও সরাসরি লোড হয় না?",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "১. Shikho এর সার্ভার নীতি অনুযায়ী, আপনি যদি নির্দিষ্ট কোর্স বা ব্যাচে সরাসরি ভর্তি না থাকেন (যেমন: ফ্রি ট্রায়াল শেষ কোর্স বা ফ্রিতে শেখা শেষ ব্যাচ), তাহলে তাদের মূল GraphQL API ক্লাসের সরাসরি recording_url প্রদান করে না (ফাঁকা বা null পাঠায়)।\n\n" +
                                        "২. অ্যাপটি আপনাকে ক্লাস দেখানোর জন্য Shikho এর নিজস্ব ক্লাউড CDN সার্ভার (shikho-stream2.tenbytecdn.com) থেকে ক্লাস আইডি ও কনটেন্ট আইডি ব্যবহার করে স্বয়ংক্রিয়ভাবে স্ট্রিম লোড করার সর্বোচ্চ চেষ্টা করে।\n\n" +
                                        "৩. Shikho সার্ভারে যদি কোনো ক্লাসের ভিডিও প্রসেসিং সম্পন্ন না হয়ে থাকে বা সার্ভার থেকে ফাইল সরানো হয়, তখন CDN সার্ভার HTTP 404 (ফাইল পাওয়া যায়নি) দেয়।",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "সার্ভার ও স্ট্রিম মেটাডাটা:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    // Key Values
                    val metaItems: List<Pair<String, String>> = listOf(
                        "লেসন আইডি" to (lesson?.id ?: "নেই"),
                        "লাইভ ক্লাস আইডি" to (lesson?.live_class?.id ?: "নেই"),
                        "কনটেন্ট আইডি" to (lesson?.content_id ?: "নেই"),
                        "সরাসরি স্ট্রিম" to activeStreamUrl.ifBlank { "কোনো স্ট্রিম লিংক পাওয়া যায়নি" },
                        "প্লেব্যাক স্ট্যাটাস" to when {
                            playbackError != null -> "ব্যর্থ (${playbackErrorDetails ?: "এরর"})"
                            isBuffering -> "বাফারিং হচ্ছে"
                            isPlaying -> "সফলভাবে চলছে"
                            else -> "প্রস্তুত"
                        }
                    )

                    metaItems.forEach { (label, value) ->
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = value,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        OutlinedButton(
                            onClick = {
                                copyToClipboard(context, "Lesson: ${lesson?.id}\nClass: ${lesson?.live_class?.id}\nContent: ${lesson?.content_id}\nURL: $activeStreamUrl\nError: $playbackErrorDetails")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ডায়াগনস্টিক তথ্য কপি", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
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

@Composable
fun SlideViewerDialog(
    slideUrl: String,
    title: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var hasError by remember { mutableStateOf(false) }

    val encodedUrl = remember(slideUrl) {
        try {
            URLEncoder.encode(slideUrl, "UTF-8")
        } catch (_: Exception) {
            slideUrl
        }
    }
    val viewerUrl = remember(encodedUrl) {
        "https://docs.google.com/gview?embedded=true&url=$encodedUrl"
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8FAFC))
        ) {
            // Header Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "বন্ধ করুন"
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text(
                                text = title,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "পিডিএফ লেকচার স্লাইড ভিউয়ার",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                downloadFile(context, slideUrl, title)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "ডাউনলোড করুন",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = {
                                openInExternalApp(context, slideUrl)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = "ব্রাউজারে ওপেন করুন"
                            )
                        }
                        IconButton(
                            onClick = {
                                copyToClipboard(context, slideUrl)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "লিংক কপি করুন"
                            )
                        }
                    }
                }
            }

            // Webview Content Area
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .background(Color(0xFFF8FAFC)),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                loadWithOverviewMode = true
                                useWideViewPort = true
                                builtInZoomControls = true
                                displayZoomControls = false
                                setSupportZoom(true)
                                cacheMode = WebSettings.LOAD_DEFAULT
                            }
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    isLoading = true
                                    hasError = false
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    isLoading = false
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    errorCode: Int,
                                    description: String?,
                                    failingUrl: String?
                                ) {
                                    super.onReceivedError(view, errorCode, description, failingUrl)
                                    isLoading = false
                                    hasError = true
                                }
                            }
                            loadUrl(viewerUrl)
                            webViewRef = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (isLoading) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.5.dp
                            )
                            Text(
                                text = "স্লাইড লোড হচ্ছে...",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                if (hasError) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "স্লাইড প্রিভিউ লোড হতে সমস্যা হয়েছে",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "সরাসরি ব্রাউজারে দেখতে বা ডাউনলোড করতে নিচের বাটনে চাপুন।",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        openInExternalApp(context, slideUrl)
                                    }
                                ) {
                                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("ব্রাউজারে খুলুন")
                                }
                                OutlinedButton(
                                    onClick = {
                                        downloadFile(context, slideUrl, title)
                                    }
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("ডাউনলোড")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun downloadFile(context: Context, url: String, title: String) {
    try {
        val uri = Uri.parse(url)
        val sanitizedTitle = title.replace("[^a-zA-Z0-9_\\-\\u0980-\\u09FF]".toRegex(), "_")
        val fileName = if (sanitizedTitle.endsWith(".pdf", ignoreCase = true)) sanitizedTitle else "$sanitizedTitle.pdf"

        val request = DownloadManager.Request(uri).apply {
            setTitle(title)
            setDescription("লেকচার স্লাইড ডাউনলোড হচ্ছে...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            setMimeType("application/pdf")
            addRequestHeader("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 12; V2029 Build/SP1A.210812.003)")
            addRequestHeader("referer", "https://shikho.com/")
        }
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.enqueue(request)
        Toast.makeText(context, "ডাউনলোড শুরু হয়েছে", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        // Fallback to opening the URL directly
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "ডাউনলোড করা যায়নি: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}

private fun openInExternalApp(context: Context, url: String) {
    try {
        val uri = Uri.parse(url)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(Intent.createChooser(intent, "স্লাইড ওপেন করুন"))
    } catch (_: Exception) {
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(browserIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "ব্রাউজার বা পিডিএফ রিডার পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
        }
    }
}

private fun copyToClipboard(context: Context, url: String) {
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Lecture Slide URL", url)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "স্লাইড লিংক কপি করা হয়েছে", Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {}
}
