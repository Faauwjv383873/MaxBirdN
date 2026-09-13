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
import com.example.utils.ClassTypeUtils
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
    var showDiagnosticDialog by remember { mutableStateOf(false) }

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

    // Download & Offline Dialog States
    var showDownloadResolutionDialog by remember { mutableStateOf(false) }
    var showOfflineDownloadsDialog by remember { mutableStateOf(false) }
    val downloadedVideosList = remember { mutableStateListOf<Pair<String, String>>() }

    if (showDownloadResolutionDialog) {
        VideoDownloadResolutionDialog(
            lessonTitle = lesson?.title ?: "ক্লাস ভিডিও",
            onDismiss = { showDownloadResolutionDialog = false },
            onStartDownload = { quality, estSize ->
                showDownloadResolutionDialog = false
                val videoTitle = lesson?.title ?: "লেকচার ভিডিও"
                if (!downloadedVideosList.any { it.first == videoTitle }) {
                    downloadedVideosList.add(Pair(videoTitle, quality))
                }
                Toast.makeText(
                    context,
                    "'$quality' রেজুলেশনে ভিডিও ডাউনলোড শুরু হয়েছে!\nঅ্যাপের 'আমার ডাউনলোডসমূহ (অফলাইন)' তে সংরক্ষিত থাকবে।",
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }

    if (showOfflineDownloadsDialog) {
        OfflineDownloadsDialog(
            downloadedList = downloadedVideosList,
            onDismiss = { showOfflineDownloadsDialog = false },
            onPlayOffline = { title ->
                showOfflineDownloadsDialog = false
                Toast.makeText(context, "'$title' অফলাইনে প্লেইং...", Toast.LENGTH_SHORT).show()
            }
        )
    }

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

                                        Button(
                                            onClick = { livePlayerMode = "WEB_PLAYER" },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("ব্রাউজার প্লেয়ার", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                                onDownloadClick = { showDownloadResolutionDialog = true },
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

                // Live Class Active Room Banner & Control Card (if live)
                if (isLive) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFFF1F2)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFFDA4AF)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier
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
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .padding(2.dp)
                                        )
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
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        if (livePlayerMode == "MEETING") {
                                            livePlayerMode = "STREAM"
                                            exoPlayer.play()
                                        } else {
                                            exoPlayer.pause()
                                            livePlayerMode = "MEETING"
                                        }
                                    },
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

                // 2. Class Header & Info Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // Teacher Hero Card (Matching Screenshot 2)
                    TeacherHeroCard(
                        lesson = lesson,
                        onDownloadClick = { showDownloadResolutionDialog = true }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Title
                    Text(
                        text = lesson?.title ?: "ক্লাস লেকচার",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        lineHeight = 28.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Date and Time in Bengali & Offline Downloads Button Row
                    val formattedTime = remember(lesson) {
                        val rawStart = lesson?.live_class?.start_time ?: lesson?.start_time
                        formatBanglaDateTime(rawStart)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
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

                        // Offline Downloads Button
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                            modifier = Modifier.clickable { showOfflineDownloadsDialog = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderZip,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "আমার ডাউনলোডসমূহ (${downloadedVideosList.size})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
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

                Spacer(modifier = Modifier.height(20.dp))

                // 4. Class Resources Section (Matching Screenshot 2)
                ClassResourcesSection(
                    lesson = lesson,
                    onOpenSlide = { attachment ->
                        viewingSlideItem = attachment
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 5. Animated Lessons Section (Matching Screenshot 2)
                AnimatedLessonsSection(
                    lesson = lesson,
                    onPlayAnimatedLesson = { title ->
                        Toast.makeText(context, "এনিমেটেড লেসন: $title প্লে হচ্ছে...", Toast.LENGTH_SHORT).show()
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 6. "লেকচার স্লাইডস ও ডকুমেন্টস"
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = "লেকচার স্লাইড ও ডকুমেন্টস",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

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
        val formattedSpeed = String.format(java.util.Locale.US, "%.2f", playbackSpeed)
        val presets = listOf(0.5f, 0.75f, 1.0f, 1.05f, 1.10f, 1.15f, 1.20f, 1.25f, 1.35f, 1.5f, 1.75f, 2.0f, 2.5f)
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = {
                Text("প্লেব্যাক স্পিড (Speed Control)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${formattedSpeed}x",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                val next = (playbackSpeed - 0.10f).coerceAtLeast(0.25f)
                                playbackSpeed = (Math.round(next * 100) / 100f)
                                exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
                            },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("-0.10", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                val next = (playbackSpeed - 0.05f).coerceAtLeast(0.25f)
                                playbackSpeed = (Math.round(next * 100) / 100f)
                                exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
                            },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("-0.05", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                val next = (playbackSpeed + 0.05f).coerceAtMost(3.00f)
                                playbackSpeed = (Math.round(next * 100) / 100f)
                                exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
                            },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("+0.05", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                val next = (playbackSpeed + 0.10f).coerceAtMost(3.00f)
                                playbackSpeed = (Math.round(next * 100) / 100f)
                                exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
                            },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("+0.10", fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Slider(
                        value = playbackSpeed,
                        onValueChange = { raw ->
                            val stepped = Math.round(raw / 0.05f) * 0.05f
                            playbackSpeed = (Math.round(stepped * 100) / 100f).coerceIn(0.25f, 3.00f)
                            exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
                        },
                        valueRange = 0.25f..3.00f,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "দ্রুত নির্বাচন (Presets):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(presets) { p ->
                            val isSel = Math.abs(playbackSpeed - p) < 0.02f
                            FilterChip(
                                selected = isSel,
                                onClick = {
                                    playbackSpeed = p
                                    exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
                                },
                                label = { Text("${p}x") }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSpeedDialog = false }) {
                    Text("সম্পূর্ণ")
                }
            }
        )
    }

    // Video Quality Selection Dialog
    if (showQualityDialog) {
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            title = {
                Text("ভিডিও রেজোলিউশন / কোয়ালিটি", fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    if (availableQualities.isEmpty()) {
                        Text(
                            "অটো রেজোলিউশন চলছে (বা সিলেক্টেড স্ট্রিমে একটাই কোয়ালিটি লভ্য)",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.padding(8.dp)
                        )
                    } else {
                        availableQualities.forEach { quality ->
                            val isSelected = selectedQualityLabel == quality.label
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
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
                                        showQualityDialog = false
                                    }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = quality.label,
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
                }
            },
            confirmButton = {
                TextButton(onClick = { showQualityDialog = false }) {
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
    isLive: Boolean = false,
    hasMeeting: Boolean = false,
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isLive) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFFE11D48),
                                        modifier = Modifier.padding(end = 6.dp)
                                    ) {
                                        Text(
                                            text = "🔴 LIVE",
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = title,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
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

                    // Interactive Quality, Speed, Download and PiP Buttons
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Picture in Picture (PiP) Button
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier.clickable { onPipClick() }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureInPictureAlt,
                                    contentDescription = "PiP",
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "PiP",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Quality Tag Button
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier.clickable { onQualityClick() }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HighQuality,
                                    contentDescription = "কোয়ালিটি",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = selectedQualityLabel,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Download Button
                        IconButton(onClick = onDownloadClick) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "ডাউনলোড",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Playback Speed Tag Button (only for recorded / if applicable)
                        if (!isLive) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White.copy(alpha = 0.2f),
                                modifier = Modifier.clickable { onSpeedClick() }
                            ) {
                                val displaySpeed = String.format(java.util.Locale.US, "%.2f", playbackSpeed).removeSuffix(".00")
                                Text(
                                    text = "${displaySpeed}x",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                // CENTER CONTROLS (Only Play/Pause Button)
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
                    }
                }

                // BOTTOM CONTROLS (Time & SeekBar, Fullscreen toggle)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    if (!isLive) {
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
                    } else {
                        // LIVE STATUS BAR
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFE11D48))
                                )
                                Text(
                                    text = "🔴 সরাসরি লাইভ সম্প্রচার চলছে (Live)",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

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

fun toBengaliDigits(input: Any?): String {
    if (input == null) return "০"
    val str = input.toString()
    val banglaDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
    val sb = StringBuilder()
    for (char in str) {
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

@Composable
fun InteractiveLiveMeetingView(
    meetingUrl: String,
    onToggleFullscreen: () -> Unit,
    onSwitchToPlayer: () -> Unit,
    onOpenExternal: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isLoadingMeeting by remember { mutableStateOf(true) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        allowFileAccess = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        databaseEnabled = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        userAgentString = "Mozilla/5.0 (Linux; Android 12; Pixel 6 Build/SD1A.210817.036) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.5735.196 Mobile Safari/537.36"
                    }
                    webChromeClient = object : android.webkit.WebChromeClient() {
                        override fun onPermissionRequest(request: android.webkit.PermissionRequest?) {
                            try {
                                request?.grant(request.resources)
                            } catch (_: Exception) {}
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoadingMeeting = false
                        }
                    }
                    loadUrl(meetingUrl)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay Action Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                    )
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFE11D48),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "লাইভ ক্লাস সম্প্রচার",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onOpenExternal,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInBrowser,
                        contentDescription = "ব্রাউজারে খুলুন",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onToggleFullscreen,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Fullscreen,
                        contentDescription = "ফুলস্ক্রিন",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        if (isLoadingMeeting) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFFE11D48), strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "লাইভ ক্লাসরুমে সংযোগ স্থাপন হচ্ছে...",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpcomingCountdownScreen(
    lesson: StudentLessonItem,
    subjectName: String,
    subjectColorHex: String?,
    onBack: () -> Unit
) {
    val startTimeStr = lesson.live_class?.start_time ?: lesson.start_time
    val endTimeStr = lesson.live_class?.end_time ?: lesson.end_time

    var timeDifferenceMs by remember(startTimeStr) {
        mutableLongStateOf(calculateTimeDifference(startTimeStr))
    }

    LaunchedEffect(startTimeStr) {
        while (true) {
            delay(1000L)
            timeDifferenceMs = calculateTimeDifference(startTimeStr)
        }
    }

    val secondsTotal = (timeDifferenceMs / 1000).coerceAtLeast(0L)
    val days = secondsTotal / (24 * 3600)
    val hours = (secondsTotal % (24 * 3600)) / 3600
    val minutes = (secondsTotal % 3600) / 60
    val seconds = secondsTotal % 60

    val isExam = lesson.isExam
    val headingText = if (isExam) "টেস্ট শুরু হতে সময় বাকি" else "ক্লাস শুরু হতে সময় বাকি"

    val formattedDate = remember(startTimeStr) {
        formatLessonDateDetailed(startTimeStr)
    }

    val formattedTimeRange = remember(startTimeStr, endTimeStr) {
        formatLessonTimeRange(startTimeStr, endTimeStr)
    }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp,
                modifier = Modifier.statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = ClassTypeUtils.formatLessonTitle(lesson.title ?: subjectName),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        containerColor = Color(0xFFF8FAFC)
    ) { paddingVals ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingVals)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Main Countdown Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = headingText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF334155)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // 4 Timer Boxes Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                    ) {
                        CountdownBox(value = String.format(Locale.getDefault(), "%02d", days), label = "দিন")
                        CountdownBox(value = String.format(Locale.getDefault(), "%02d", hours), label = "ঘণ্টা")
                        CountdownBox(value = String.format(Locale.getDefault(), "%02d", minutes), label = "মিনিট")
                        CountdownBox(value = String.format(Locale.getDefault(), "%02d", seconds), label = "সেকেন্ড")
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Lesson & Subject Info Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = subjectName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF2563EB)
                        )
                        val classTypeBadge = ClassTypeUtils.getClassTypeBadgeStyle(lesson)
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = classTypeBadge.backgroundColor
                        ) {
                            Text(
                                text = classTypeBadge.label,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = classTypeBadge.textColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = ClassTypeUtils.formatLessonTitle(lesson.title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = Color(0xFFF1F5F9))
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "তারিখ ও সময়:",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF334155)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = formattedDate,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF0F172A)
                    )
                    if (formattedTimeRange.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = formattedTimeRange,
                                fontSize = 13.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CountdownBox(value: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF0D9488), // Teal color matching screenshot
            modifier = Modifier.size(width = 64.dp, height = 64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = value,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF64748B)
        )
    }
}

fun calculateTimeDifference(startTimeStr: String?): Long {
    if (startTimeStr.isNullOrBlank()) return 0L
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        parser.timeZone = TimeZone.getTimeZone("UTC")
        val date = parser.parse(startTimeStr) ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(startTimeStr)
        if (date != null) {
            val diff = date.time - System.currentTimeMillis()
            if (diff > 0) diff else 0L
        } else 0L
    } catch (_: Exception) {
        0L
    }
}

fun formatLessonDateDetailed(rawDate: String?): String {
    if (rawDate.isNullOrBlank()) return "শীঘ্রই আসছে"
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        parser.timeZone = TimeZone.getTimeZone("UTC")
        val date = parser.parse(rawDate)
        if (date != null) {
            val formatter = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("bn", "BD"))
            formatter.timeZone = TimeZone.getTimeZone("Asia/Dhaka")
            formatter.format(date)
        } else {
            rawDate
        }
    } catch (_: Exception) {
        rawDate
    }
}

fun formatLessonTimeRange(startTimeStr: String?, endTimeStr: String?): String {
    if (startTimeStr.isNullOrBlank()) return ""
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        parser.timeZone = TimeZone.getTimeZone("UTC")
        val start = parser.parse(startTimeStr)
        val end = if (!endTimeStr.isNullOrBlank()) parser.parse(endTimeStr) else null

        val timeFormatter = SimpleDateFormat("hh:mm a", Locale.getDefault())
        timeFormatter.timeZone = TimeZone.getTimeZone("Asia/Dhaka")
        val startFormatted = start?.let { timeFormatter.format(it) } ?: ""
        val endFormatted = end?.let { timeFormatter.format(it) } ?: ""

        if (startFormatted.isNotBlank() && endFormatted.isNotBlank()) {
            "$startFormatted - $endFormatted"
        } else if (startFormatted.isNotBlank()) {
            startFormatted
        } else {
            ""
        }
    } catch (_: Exception) {
        ""
    }
}

@Composable
fun LiveMeetingWebView(
    meetingUrl: String,
    onBackToStream: () -> Unit,
    onOpenExternal: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
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
                        databaseEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        allowFileAccess = true
                        allowContentAccess = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        userAgentString = "Mozilla/5.0 (Linux; Android 12; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 Shikho/6.0.7"
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onPermissionRequest(request: PermissionRequest?) {
                            request?.grant(request.resources)
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isLoading = false
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            if (request?.isForMainFrame == true) {
                                isLoading = false
                                hasError = true
                            }
                        }
                    }
                    loadUrl(meetingUrl)
                    webViewInstance = this
                }
            },
            update = { wv ->
                if (wv.url != meetingUrl && meetingUrl.isNotBlank()) {
                    hasError = false
                    isLoading = true
                    wv.loadUrl(meetingUrl)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (hasError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0F172A)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(20.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFEF4444).copy(alpha = 0.15f),
                        modifier = Modifier.size(54.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.LiveTv,
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "ওয়েব লাইভ রুম সংযোগ ব্যর্থ হয়েছে",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "সরাসরি HLS ভিডিও স্ট্রিম প্লেয়ারে ক্লাস উপভোগ করুন",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onBackToStream,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("HLS প্লেয়ারে যান", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = onOpenExternal,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF475569)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ব্রাউজার", fontSize = 12.sp)
                        }
                    }
                }
            }
        } else if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xE60F172A)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(16.dp)
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFFE11D48),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "100ms লাইভ রুমে সংযোগ করা হচ্ছে...",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Floating top control bar for web meeting
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.75f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE11D48).copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEF4444))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "🔴 100ms লাইভ রুম",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.75f),
                    modifier = Modifier.size(34.dp)
                ) {
                    IconButton(onClick = { 
                        hasError = false
                        isLoading = true
                        webViewInstance?.reload() 
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "রিফ্রেশ", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.75f),
                    modifier = Modifier.size(34.dp)
                ) {
                    IconButton(onClick = onOpenExternal) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = "ব্রাউজার", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF2563EB).copy(alpha = 0.9f),
                    modifier = Modifier.clickable { onBackToStream() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.LiveTv, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("HLS প্লেয়ার", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * HTML5 Web Player embedded inside WebView for directly streaming HLS .m3u8 URLs
 */
@Composable
fun HlsWebPlayerView(
    streamUrl: String,
    onBackToStream: () -> Unit,
    onOpenExternal: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isLoading by remember { mutableStateOf(true) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    val htmlContent = remember(streamUrl) {
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <style>
                body, html { margin: 0; padding: 0; width: 100%; height: 100%; background: #000; overflow: hidden; display: flex; align-items: center; justify-content: center; }
                video { width: 100%; height: 100%; object-fit: contain; }
            </style>
            <script src="https://cdn.jsdelivr.net/npm/hls.js@latest"></script>
        </head>
        <body>
            <video id="video" controls autoplay playsinline webkit-playsinline></video>
            <script>
                var video = document.getElementById('video');
                var videoSrc = "$streamUrl";
                if (Hls.isSupported()) {
                    var hls = new Hls({ enableWorker: true, lowLatencyMode: true });
                    hls.loadSource(videoSrc);
                    hls.attachMedia(video);
                    hls.on(Hls.Events.MANIFEST_PARSED, function() { video.play(); });
                } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
                    video.src = videoSrc;
                    video.addEventListener('loadedmetadata', function() { video.play(); });
                }
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    Box(modifier = modifier.background(Color.Black)) {
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
                        mediaPlaybackRequiresUserGesture = false
                        allowFileAccess = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    }
                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                        }
                    }
                    loadDataWithBaseURL("https://sh-cdn-in3.100ms.live", htmlContent, "text/html", "UTF-8", null)
                    webViewInstance = this
                }
            },
            update = { wv ->
                wv.loadDataWithBaseURL("https://sh-cdn-in3.100ms.live", htmlContent, "text/html", "UTF-8", null)
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF2563EB), modifier = Modifier.size(32.dp))
            }
        }

        // Top control buttons bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.75f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2563EB).copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF3B82F6))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "🌐 ব্রাউজার প্লেয়ার",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.75f),
                    modifier = Modifier.size(34.dp)
                ) {
                    IconButton(onClick = { 
                        isLoading = true
                        webViewInstance?.reload() 
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "রিফ্রেশ", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.75f),
                    modifier = Modifier.size(34.dp)
                ) {
                    IconButton(onClick = onOpenExternal) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = "ব্রাউজার", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF2563EB).copy(alpha = 0.9f),
                    modifier = Modifier.clickable { onBackToStream() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.LiveTv, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ExoPlayer", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * 100ms Prebuilt Live Class "Get Started" Screen (Exact replica of Screenshot 1)
 */
@Composable
fun LiveGetStartedScreen(
    lesson: StudentLessonItem?,
    effectiveMeetingUrl: String,
    onJoinClick: () -> Unit,
    onBack: () -> Unit
) {
    var userName by remember { mutableStateOf("Fahim Miya_7191") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080B11))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Top App Bar with circular back button and report icon
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF191B23),
                    modifier = Modifier.size(44.dp)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF191B23),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Assignment,
                            contentDescription = "Report/Feedback",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Center Content: "Get Started", subtitle, and "350 others in session" badge
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Get Started",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Enter your name before joining",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF9EABB8),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Session participants badge
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF191B23),
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = "350 others in session",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                }
            }

            // Bottom Section: Wifi network indicator + Name Input Box + "Join Now" Button
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                // Wifi indicator chip on left
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF191B23),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = "Network Status",
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Name text field box
                    OutlinedTextField(
                        value = userName,
                        onValueChange = { userName = it },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF11131B),
                            unfocusedContainerColor = Color(0xFF11131B),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF2572ED),
                            unfocusedBorderColor = Color(0xFF272932)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        modifier = Modifier.weight(1.3f)
                    )

                    // "Join Now" Button matching Screenshot 1
                    Button(
                        onClick = onJoinClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2572ED)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                    ) {
                        Text(
                            text = "Join Now",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// NEW CUSTOM COMPOSABLES (MATCHING DESIGN)
// ==========================================

@Composable
fun TeacherHeroCard(
    lesson: StudentLessonItem?,
    onDownloadClick: () -> Unit
) {
    val teacher = lesson?.live_class?.teacher
    val teacherName = teacher?.displayName ?: "মো: আশরাফুল ইসলাম"
    val universityName = teacher?.marketing_points?.firstOrNull() ?: "পদার্থবিজ্ঞান, ঢাকা বিশ্ববিদ্যালয়"
    val experienceText = teacher?.marketing_points?.getOrNull(1) ?: "৬ বছর শিক্ষকতার অভিজ্ঞতা"
    val studentsTaughtText = teacher?.marketing_points?.getOrNull(2) ?: "৫ লক্ষ+ শিক্ষার্থী পড়িয়েছেন"
    val avatarUrl = teacher?.displayAvatar

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Teacher Avatar
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .border(2.5.dp, MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!avatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = avatarUrl,
                                contentDescription = teacherName,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    // Teacher Info
                    Column {
                        Text(
                            text = teacherName,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        // University Tag
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.School,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = universityName,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Highlights
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = null,
                                    tint = Color(0xFF16A34A),
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = experienceText,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Download Action Icon
                IconButton(
                    onClick = onDownloadClick,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "ভিডিও ডাউনলোড",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Groups,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = studentsTaughtText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun ClassResourcesSection(
    lesson: StudentLessonItem?,
    onOpenSlide: (LessonAttachmentItem) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "ক্লাস রিসোর্সেস",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Main Card: Lecture Slide
        val slideUrl = lesson?.resolvedSlideUrl ?: lesson?.live_class?.lectureSlideUrl
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (!slideUrl.isNullOrBlank()) {
                        onOpenSlide(
                            LessonAttachmentItem(
                                title = "লেকচার স্লাইড (PDF)",
                                url = slideUrl,
                                file_type = "pdf"
                            )
                        )
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
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFE11D48).copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = "লেকচার স্লাইড",
                            tint = Color(0xFFE11D48),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "লেকচার স্লাইড",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "পিডিএফ নোটস ও স্লাইড দেখুন",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Side by side cards: Chapter Resource & Subject Resource
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Chapter Resource Card
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier
                    .weight(1f)
                    .clickable { }
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF0284C7).copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = Color(0xFF0284C7),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = "চ্যাপ্টার রিসোর্স",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Subject Resource Card
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier
                    .weight(1f)
                    .clickable { }
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF16A34A).copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = null,
                            tint = Color(0xFF16A34A),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = "সাবজেক্ট রিসোর্স",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun AnimatedLessonsSection(
    lesson: StudentLessonItem?,
    onPlayAnimatedLesson: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "এনিমেটেড লেসন",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Animated Lesson Cards List
        val animatedTitles = listOf(
            "ওয়েব পেজের ধারণা ও প্রকারভেদ",
            "এইচটিএমএল (HTML) ট্যাগ ও ফরম্যাটিং",
            "সিএসএস (CSS) লেআউট ও কালার প্রপার্টি"
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            animatedTitles.take(2).forEach { title ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1B4B)),
                    modifier = Modifier
                        .weight(1f)
                        .height(115.dp)
                        .clickable { onPlayAnimatedLesson(title) }
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Background decorative gradient shape
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFF312E81),
                                            Color(0xFF1E1B4B)
                                        )
                                    )
                                )
                        )

                        // Center Play Icon Button
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "প্লে",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Bottom Title Text
                        Surface(
                            color = Color.Black.copy(alpha = 0.6f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                        ) {
                            Text(
                                text = title,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// VIDEO DOWNLOAD RESOLUTION & OFFLINE DIALOGS
// ==========================================

@Composable
fun VideoDownloadResolutionDialog(
    lessonTitle: String,
    onDismiss: () -> Unit,
    onStartDownload: (quality: String, estSize: String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "ডাউনলোড রেজুলেশন সিলেক্ট করুন",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = lessonTitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val resolutions = listOf(
                    Triple("1080p Full HD", "~650 MB", "হাই ডেফিনিশন"),
                    Triple("720p HD", "~380 MB", "অনুমোদিত (সুপারিশকৃত)"),
                    Triple("480p Standard", "~180 MB", "স্ট্যান্ডার্ড"),
                    Triple("360p Data Saver", "~95 MB", "কম ডাটা খরচ")
                )

                resolutions.forEach { (resLabel, sizeText, desc) ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (resLabel.contains("720p"))
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (resLabel.contains("720p")) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onStartDownload(resLabel, sizeText) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = resLabel,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = desc,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = sizeText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Security Note
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f))
                        .padding(8.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "নিরাপত্তা নীতি অনুযায়ী ডাউনলোডকৃত ভিডিও শুধুমাত্র এই অ্যাপের 'ইন-অ্যাপ অফলাইন ডাউনলোডসমূহ' থেকে দেখতে পারবেন। গ্যালারিতে পাওয়া যাবে না।",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        lineHeight = 14.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("বাতিল করুন")
            }
        }
    )
}

@Composable
fun OfflineDownloadsDialog(
    downloadedList: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onPlayOffline: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FolderZip,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "ইন-অ্যাপ অফলাইন ডাউনলোডসমূহ",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (downloadedList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "কোন ডাউনলোডকৃত ভিডিও নেই।\nভিডিও রেজুলেশন সিলেক্ট করে ডাউনলোড করুন।",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    downloadedList.forEach { (title, quality) ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPlayOffline(title) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = title,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "কোয়ালিটি: $quality • অফলাইন ফাইল",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                IconButton(
                                    onClick = { onPlayOffline(title) },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "প্লে",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("বন্ধ করুন")
            }
        }
    )
}

