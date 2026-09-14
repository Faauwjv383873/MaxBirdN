package com.example.ui.screens

import android.app.Activity
import android.app.DownloadManager
import android.app.PictureInPictureParams
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Rational
import android.view.ViewGroup
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.api.LessonAttachmentItem
import com.example.api.StudentLessonItem
import com.example.player.ShikhoPlayerManager
import com.example.player.VideoTrackQuality
import com.example.ui.components.*
import com.example.utils.*
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
    onJoinLiveClass: ((StudentLessonItem) -> Unit)? = null,
    onBack: () -> Unit
) {
    if (lesson?.isUpcoming == true) {
        UpcomingCountdownScreen(
            lesson = lesson,
            subjectName = subjectName,
            subjectColorHex = subjectColorHex,
            onBack = onBack
        )
        return
    }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Live Class Detection & Info
    val isLive = remember(lesson) {
        lesson?.isLive == true
    }
    val isLiveOngoing = remember(lesson) {
        lesson?.live_class?.is_on_going == true || lesson?.content_type?.contains("LIVE", ignoreCase = true) == true
    }
    val joinLink = lesson?.live_class?.join_link
    val hmsRoomId = lesson?.live_class?.hms_room_id
    val liveProvider = lesson?.live_class?.provider ?: "100ms Live"

    val effectiveMeetingUrl = remember(lesson?.live_class?.liveMeetingUrl, joinLink, hmsRoomId, lesson?.live_class?.hms_token) {
        val url = when {
            !joinLink.isNullOrBlank() && !lesson?.live_class?.hms_token.isNullOrBlank() && !joinLink.contains("token=") -> {
                val separator = if (joinLink.contains("?")) "&" else "?"
                "$joinLink${separator}token=${lesson.live_class.hms_token}"
            }
            !joinLink.isNullOrBlank() -> joinLink
            lesson?.live_class?.liveMeetingUrl != null -> lesson.live_class.liveMeetingUrl
            !hmsRoomId.isNullOrBlank() -> {
                val tokenParam = if (!lesson?.live_class?.hms_token.isNullOrBlank()) "?token=${lesson.live_class.hms_token}" else ""
                "https://live.shikho.com/meeting/${hmsRoomId.trim()}$tokenParam"
            }
            else -> ""
        }
        url ?: ""
    }

    // Live Join State: "JOIN_PORTAL" (Get Started Screen) -> "LIVE_ROOM" (Full 100ms Room/Chat/Stream)
    var isLiveJoined by remember(lesson?.id) { mutableStateOf(false) }

    // Auto trigger joinLiveClass if it's a live class and joinLink or hmsRoomId is missing
    LaunchedEffect(lesson?.id, isLive) {
        if (isLive && lesson != null && (lesson.live_class?.join_link.isNullOrBlank() || lesson.live_class?.hms_room_id.isNullOrBlank()) && onJoinLiveClass != null) {
            onJoinLiveClass.invoke(lesson)
        }
    }

    // If it is a live class and user hasn't tapped "Join Now", show the exact "Get Started" screen from Screenshot 1!
    if (isLive && !isLiveJoined) {
        LiveGetStartedScreen(
            lesson = lesson,
            effectiveMeetingUrl = effectiveMeetingUrl,
            onJoinClick = {
                if (lesson != null && (lesson.live_class?.join_link.isNullOrBlank() || lesson.live_class?.hms_room_id.isNullOrBlank()) && onJoinLiveClass != null) {
                    onJoinLiveClass.invoke(lesson)
                }
                isLiveJoined = true
            },
            onBack = onBack
        )
        return
    }

    var livePlayerMode by remember { mutableStateOf(if (isLive) "MEETING" else "STREAM") }

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

    val candidateStreams = remember(lesson, lesson?.live_class?.hms_room_id, lesson?.live_class?.recording_url, lesson?.live_class?.playback_url, isLive) {
        val raw = lesson?.candidateStreamUrls?.filter { it.isNotBlank() && it != "null" } ?: emptyList()
        if (isLive) {
            val liveHls = raw.filter { it.contains("100ms.live") }
            val other = raw.filterNot { it.contains("100ms.live") }
            (liveHls + other).distinct()
        } else {
            raw.distinct()
        }
    }
    var currentStreamIndex by remember(lesson?.id, lesson?.live_class?.hms_room_id) { mutableIntStateOf(0) }
    var activeStreamUrl by remember(candidateStreams, currentStreamIndex) {
        mutableStateOf(
            candidateStreams.getOrNull(currentStreamIndex)
                ?: candidateStreams.firstOrNull()
                ?: lesson?.resolvedVideoUrl
                ?: ""
        )
    }

    LaunchedEffect(candidateStreams) {
        if (candidateStreams.isNotEmpty()) {
            if (activeStreamUrl.isBlank() || !candidateStreams.contains(activeStreamUrl)) {
                currentStreamIndex = 0
                activeStreamUrl = candidateStreams.first()
            }
        }
    }

    // Diagnostics & Dialog States
    var playbackError by remember { mutableStateOf<String?>(null) }
    var playbackErrorDetails by remember { mutableStateOf<String?>(null) }

    var showCustomUrlDialog = false

    // Player States
    var isPlaying by remember { mutableStateOf(false) }
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
    var showQualityDialog by remember { mutableStateOf(false) }
    var availableQualities by remember { mutableStateOf<List<VideoTrackQuality>>(emptyList()) }
    var selectedQualityLabel by remember { mutableStateOf("অটো") }

    // Slide viewing state
    var viewingSlideItem by remember { mutableStateOf<LessonAttachmentItem?>(null) }
    var isRefreshingSlide by remember { mutableStateOf(false) }

    // Expandable Accordion State for Topics
    var isTopicsExpanded by remember { mutableStateOf(true) }



    // ExoPlayer Instance with Shikho CDN headers & DefaultTrackSelector for HLS quality selection
    val trackSelector = remember { DefaultTrackSelector(context) }
    val exoPlayer = remember {
        ShikhoPlayerManager.buildExoPlayer(context, trackSelector).apply {
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    // Initialize media source when activeStreamUrl or mode changes
    LaunchedEffect(activeStreamUrl, isLive, livePlayerMode) {
        playbackError = null
        playbackErrorDetails = null
        if (livePlayerMode == "MEETING" || livePlayerMode == "WEB_PLAYER") {
            // When in WebView mode, stop ExoPlayer to prevent background stream fetching
            try {
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
            } catch (_: Exception) {}
            isBuffering = false
            return@LaunchedEffect
        }
        if (activeStreamUrl.isNotBlank()) {
            val urlToPlay = activeStreamUrl

            // If activeStreamUrl is a master.m3u8, ExoPlayer's onTracksChanged will parse tracks dynamically.
            // If it's a direct stream_X URL, fallback to pre-populating availableQualities.
            if (urlToPlay.contains("/stream_")) {
                val baseUrl = urlToPlay
                val s0 = baseUrl.replace(Regex("/stream_\\d+/stream\\.m3u8"), "/stream_0/stream.m3u8")
                val s1 = baseUrl.replace(Regex("/stream_\\d+/stream\\.m3u8"), "/stream_1/stream.m3u8")
                val s2 = baseUrl.replace(Regex("/stream_\\d+/stream\\.m3u8"), "/stream_2/stream.m3u8")
                val s3 = baseUrl.replace(Regex("/stream_\\d+/stream\\.m3u8"), "/stream_3/stream.m3u8")

                availableQualities = listOf(
                    VideoTrackQuality("auto", "অটো (Auto)", 0, 0, null, 0, s0),
                    VideoTrackQuality("1080p", "1080p (উচ্চ মান)", 1080, 0, null, 0, s0),
                    VideoTrackQuality("720p", "720p (এইচডি)", 720, 0, null, 0, s1),
                    VideoTrackQuality("480p", "480p (মাঝারি)", 480, 0, null, 0, s2),
                    VideoTrackQuality("360p", "360p (সাধারণ)", 360, 0, null, 0, s3)
                )
            }

            isBuffering = true
            try {
                val mediaSource = ShikhoPlayerManager.createMediaSource(urlToPlay, isLive = isLive)
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

            override fun onTracksChanged(tracks: Tracks) {
                val qualities = mutableListOf<VideoTrackQuality>()
                qualities.add(VideoTrackQuality(id = "auto", label = "অটো (Auto)", height = 0, bitrate = 0))

                for (group in tracks.groups) {
                    if (group.type == C.TRACK_TYPE_VIDEO) {
                        for (i in 0 until group.length) {
                            val format = group.getTrackFormat(i)
                            val height = format.height
                            val bitrate = format.bitrate
                            if (height > 0) {
                                val label = "${height}p" + if (bitrate > 0) " (${bitrate / 1000} kbps)" else ""
                                qualities.add(
                                    VideoTrackQuality(
                                        id = "${height}_${bitrate}",
                                        label = label,
                                        height = height,
                                        bitrate = bitrate,
                                        trackGroup = group,
                                        trackIndex = i
                                    )
                                )
                            }
                        }
                    }
                }
                availableQualities = qualities.distinctBy { it.label }
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
                if (livePlayerMode == "MEETING") {
                    // Do not process or display ExoPlayer errors when user is in 100ms MEETING WebView
                    isBuffering = false
                    isPlaying = false
                    return
                }

                if (currentStreamIndex + 1 < candidateStreams.size) {
                    currentStreamIndex += 1
                    // Updating currentStreamIndex automatically changes activeStreamUrl, which triggers LaunchedEffect
                    return
                }

                isBuffering = false
                isPlaying = false
                val cause = error.cause
                val httpEx = cause as? HttpDataSource.InvalidResponseCodeException
                    ?: (cause?.cause as? HttpDataSource.InvalidResponseCodeException)
                val httpCode = httpEx?.responseCode

                val detail = when {
                    httpCode == 404 -> "ভিডিও ফাইলটি সার্ভারে পাওয়া যায়নি (HTTP 404)"
                    httpCode == 403 -> "CDN অ্যাক্সেস রিজেক্টেড (HTTP 403)"
                    httpCode != null -> "CDN নেটওয়ার্ক রেসপন্স ত্রুটি (HTTP $httpCode)"
                    cause is java.net.UnknownHostException -> "ইন্টারনেট সংযোগ নেই বা CDN সার্ভারে পৌঁছানো যাচ্ছে না"
                    cause is java.net.SocketTimeoutException -> "সার্ভার সংযোগ সময়োত্তীর্ণ (Connection Timeout)"
                    else -> error.localizedMessage ?: "অজানা প্লেব্যাক ত্রুটি"
                }
                playbackError = "প্লেব্যাক এরর"
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

    val mediaSession = remember(exoPlayer) {
        MediaSession.Builder(context, exoPlayer).build()
    }
    DisposableEffect(mediaSession) {
        onDispose {
            mediaSession.release()
        }
    }
    
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    val activity = context as? Activity
                    if (activity?.isInPictureInPictureMode != true) {
                        exoPlayer.pause()
                    }
                }
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

    val isPipMode = com.example.LocalPictureInPictureMode.current
    val configuration = LocalConfiguration.current

    // Handle device physical orientation rotation dynamically
    LaunchedEffect(configuration.orientation) {
        if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && !isFullscreen) {
            isFullscreen = true
            activity?.let { act ->
                val window = act.window
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT && isFullscreen) {
            isFullscreen = false
            activity?.let { act ->
                val window = act.window
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                activity?.enterPictureInPictureMode(params)
            } catch (e: Exception) {
                Toast.makeText(context, "PiP মোড চালু করা সম্ভব হয়নি", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "আপনার ডিভাইসে PiP মোড সমর্থিত নয়", Toast.LENGTH_SHORT).show()
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
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
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
                if (act.isFinishing) {
                    act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    val window = act.window
                    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                    insetsController.show(WindowInsetsCompat.Type.systemBars())
                }
            }
        }
    }

    // Picture In Picture View Mode
    if (isPipMode) {
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
                update = { pv ->
                    pv.player = exoPlayer
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        return
    }

    // UI Structure
    if (isFullscreen) {
        // FULLSCREEN VIDEO PLAYER / MEETING
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (isLive && livePlayerMode == "MEETING" && effectiveMeetingUrl.isNotBlank()) {
                LiveMeetingWebView(
                    meetingUrl = effectiveMeetingUrl,
                    onBackToStream = {
                        livePlayerMode = "STREAM"
                        exoPlayer.play()
                    },
                    onOpenExternal = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(effectiveMeetingUrl)).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
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
                    isLive = isLive,
                    hasMeeting = effectiveMeetingUrl.isNotBlank(),
                    onSwitchToMeeting = {
                        exoPlayer.pause()
                        livePlayerMode = "MEETING"
                    },
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
                    onQualityClick = { showQualityDialog = true },
                    selectedQualityLabel = selectedQualityLabel,
                    onPipClick = { enterPipMode() },
                    onBack = { toggleFullscreen() }
                )
            }
        }
    } else if (isLive && livePlayerMode == "MEETING" && effectiveMeetingUrl.isNotBlank()) {
        // FULL HEIGHT LIVE ROOM (Matching Screenshot 2)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0B0E15))
        ) {
            LiveMeetingWebView(
                meetingUrl = effectiveMeetingUrl,
                onBackToStream = {
                    livePlayerMode = "STREAM"
                    exoPlayer.play()
                },
                onOpenExternal = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(effectiveMeetingUrl)).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                },
                modifier = Modifier.fillMaxSize()
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
                    if (isLive && livePlayerMode == "MEETING" && effectiveMeetingUrl.isNotBlank()) {
                        LiveMeetingWebView(
                            meetingUrl = effectiveMeetingUrl,
                            onBackToStream = {
                                livePlayerMode = "STREAM"
                                exoPlayer.play()
                            },
                            onOpenExternal = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(effectiveMeetingUrl)).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (livePlayerMode == "WEB_PLAYER") {
                        val webStreamUrl = if (activeStreamUrl.isNotBlank()) activeStreamUrl else "https://sh-cdn-in3.100ms.live/beam1/6507e56768111f6fe4b574c7/6aa00903f688c4f8bf624313/20260913/1789304361923/stream_0/stream.m3u8"
                        HlsWebPlayerView(
                            streamUrl = webStreamUrl,
                            onBackToStream = { livePlayerMode = "STREAM" },
                            onOpenExternal = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(webStreamUrl)).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (activeStreamUrl.isNotBlank() || candidateStreams.isNotEmpty()) {
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
                            val slideUrlForError = lesson?.resolvedSlideUrl
                                ?: lesson?.live_class?.lectureSlideUrl
                            PlayerErrorOverlay(
                                playbackError = playbackError ?: "ক্লাস লোড ব্যর্থ হয়েছে",
                                playbackErrorDetails = playbackErrorDetails,
                                isLive = isLive,
                                slideUrl = slideUrlForError,
                                onRefreshLesson = onRefreshLesson,
                                onRetryPlayback = {
                                    val curr = activeStreamUrl
                                    activeStreamUrl = ""
                                    coroutineScope.launch {
                                        delay(150)
                                        activeStreamUrl = curr
                                    }
                                },
                                onLaunchWebPlayer = { livePlayerMode = "WEB_PLAYER" },
                                onViewSlide = { item -> viewingSlideItem = item },
                                onBack = onBack
                            )
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
                                isLive = isLive,
                                hasMeeting = effectiveMeetingUrl.isNotBlank(),
                                onSwitchToMeeting = {
                                    exoPlayer.pause()
                                    livePlayerMode = "MEETING"
                                },
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
                                onQualityClick = { showQualityDialog = true },
                                selectedQualityLabel = selectedQualityLabel,
                                onPipClick = { enterPipMode() },
                                onDownloadClick = { {} },
                                onBack = {
                                    exoPlayer.stop()
                                    onBack()
                                }
                            )
                        }
                    } else {
                        // Empty / No Direct Stream State Placeholder with Diagnostics
                        val slideUrlForEmpty = lesson?.resolvedSlideUrl
                            ?: lesson?.live_class?.lectureSlideUrl
                        LessonStreamPlaceholder(
                            isLive = isLive,
                            lesson = lesson,
                            slideUrl = slideUrlForEmpty,
                            onJoinLiveClass = onJoinLiveClass,
                            onRefreshLesson = onRefreshLesson,
                            onViewSlide = { item -> viewingSlideItem = item },
                            onBack = onBack
                        )
                    }
                }

                // Live Class Active Room Banner & Control Card (if live)
                if (isLive) {
                    LiveClassRoomBanner(
                        isLiveOngoing = isLiveOngoing,
                        liveProvider = liveProvider,
                        hmsRoomId = hmsRoomId,
                        livePlayerMode = livePlayerMode,
                        effectiveMeetingUrl = effectiveMeetingUrl,
                        onTogglePlayerMode = {
                            if (livePlayerMode == "MEETING") {
                                livePlayerMode = "STREAM"
                                exoPlayer.play()
                            } else {
                                exoPlayer.pause()
                                livePlayerMode = "MEETING"
                            }
                        },
                        onRefreshLesson = onRefreshLesson
                    )
                }

                // 2. Class Header & Info Section
                LessonDetailHeader(lesson = lesson)

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(18.dp))

                // 3. "ক্লাসের বিষয়বস্তু" (Expandable Accordion)
                LessonTopicsAccordion(
                    lesson = lesson,
                    subjectThemeColor = subjectThemeColor,
                    isExpanded = isTopicsExpanded,
                    onToggleExpand = { isTopicsExpanded = !isTopicsExpanded },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 4. "লেকচার স্লাইডস ও ডকুমেন্টস"
                LessonDocumentsSection(
                    lesson = lesson,
                    context = context,
                    coroutineScope = coroutineScope,
                    onRefreshLesson = onRefreshLesson,
                    onViewAttachment = { attachment ->
                        viewingSlideItem = attachment
                    }
                )

                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }

    // Playback Speed Selection Dialog
    if (showSpeedDialog) {
        PlaybackSpeedDialog(
            playbackSpeed = playbackSpeed,
            onSpeedChange = { newSpeed ->
                playbackSpeed = newSpeed
                exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
            },
            onDismiss = { showSpeedDialog = false }
        )
    }

    // Video Quality Selection Dialog
    if (showQualityDialog) {
        VideoQualityDialog(
            availableQualities = availableQualities,
            selectedQualityLabel = selectedQualityLabel,
            onSelectQuality = { quality ->
                selectedQualityLabel = quality.label
                val parametersBuilder = trackSelector.buildUponParameters()
                if (quality.id == "auto") {
                    parametersBuilder.clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                    trackSelector.setParameters(parametersBuilder)
                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                        .buildUpon()
                        .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                        .build()
                } else if (quality.trackGroup != null) {
                    val override = TrackSelectionOverride(
                        quality.trackGroup.mediaTrackGroup,
                        listOf(quality.trackIndex)
                    )
                    parametersBuilder.setOverrideForType(override)
                    trackSelector.setParameters(parametersBuilder)
                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                        .buildUpon()
                        .setOverrideForType(override)
                        .build()
                } else if (quality.targetStreamUrl != null && quality.targetStreamUrl != activeStreamUrl) {
                    activeStreamUrl = quality.targetStreamUrl
                }
            },
            onDismiss = { showQualityDialog = false }
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
}



