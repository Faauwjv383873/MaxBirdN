package com.example.ui.screens

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.Build
import android.util.Rational
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.database.DownloadedItemEntity
import com.example.download.AppFileDownloadManager
import com.example.player.ShikhoPlayerManager
import com.example.player.VideoTrackQuality
import com.example.ui.components.PlaybackSpeedDialog
import com.example.ui.components.VideoDownloadQualityDialog
import com.example.ui.components.VideoQualityDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    videoUrl: String,
    title: String,
    subjectName: String?,
    subjectColorHex: String? = null,
    isLive: Boolean = false,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Player state
    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var totalDuration by remember { mutableLongStateOf(0L) }
    var bufferedPosition by remember { mutableLongStateOf(0L) }
    var isBuffering by remember { mutableStateOf(true) }
    var isFullscreen by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }

    // Dialog state
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showDownloadQualityDialog by remember { mutableStateOf(false) }
    var availableQualities by remember { mutableStateOf<List<VideoTrackQuality>>(emptyList()) }
    var selectedQualityLabel by remember { mutableStateOf("অটো") }

    // Controls visibility state
    var areControlsVisible by remember { mutableStateOf(true) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableLongStateOf(0L) }

    // Download Manager & Offline Check
    val downloadManager = remember { AppFileDownloadManager.getInstance(context) }
    val downloadId = remember(videoUrl, title) {
        "vid_" + (videoUrl.hashCode().toString() + "_" + title.hashCode().toString()).replace("-", "n")
    }
    val downloadedItem by downloadManager.getDownloadedItemById(downloadId).collectAsState(initial = null)
    var showDeleteDownloadDialog by remember { mutableStateOf(false) }

    val sessionManager = remember { com.example.auth.SessionManager(context) }
    LaunchedEffect(videoUrl, title) {
        if (title.isNotBlank()) {
            sessionManager.markLessonCompleted(title)
            sessionManager.markLessonCompleted(downloadId)
        }
    }

    // Fallback URL if passed URL is empty
    val effectivePlaybackUrl = remember(videoUrl, downloadedItem) {
        val completedLocal = if (downloadedItem?.status == DownloadedItemEntity.STATUS_COMPLETED) {
            val file = File(downloadedItem!!.localFilePath)
            if (file.exists() && file.length() > 0) file.absolutePath else null
        } else null

        when {
            completedLocal != null -> completedLocal
            videoUrl.isNotBlank() && videoUrl != "null" -> videoUrl
            else -> ""
        }
    }

    val isPlayingOffline = remember(effectivePlaybackUrl) {
        effectivePlaybackUrl.startsWith("/") || effectivePlaybackUrl.startsWith("file://")
    }

    // Subject color
    val badgeColor = remember(subjectColorHex) {
        try {
            if (!subjectColorHex.isNullOrBlank()) {
                Color(android.graphics.Color.parseColor(subjectColorHex))
            } else {
                Color(0xFF0072EC)
            }
        } catch (_: Exception) {
            Color(0xFF0072EC)
        }
    }

    // ExoPlayer instance configured with Shikho CDN headers or Local Offline source
    val exoPlayer = remember(context, effectivePlaybackUrl) {
        ShikhoPlayerManager.buildExoPlayer(context).apply {
            val mediaSource = ShikhoPlayerManager.createMediaSource(
                url = effectivePlaybackUrl,
                isLive = isLive && !isPlayingOffline,
                context = context
            )
            setMediaSource(mediaSource)
            prepare()
            playWhenReady = true
        }
    }

    // Handle Fullscreen system bars & orientation
    DisposableEffect(isFullscreen, activity) {
        activity?.let { act ->
            val window = act.window
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)

            if (isFullscreen) {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }

        onDispose {
            activity?.let { act ->
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                val insetsController = WindowCompat.getInsetsController(act.window, act.window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Player listener & periodic time tracker
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    totalDuration = exoPlayer.duration.coerceAtLeast(0L)
                }
            }

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
                // Sort descending by resolution
                availableQualities = qualities.distinctBy { it.label }
            }
        }

        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Periodic time update loop
    LaunchedEffect(exoPlayer, isPlaying) {
        while (true) {
            if (exoPlayer.playbackState == Player.STATE_READY || exoPlayer.playbackState == Player.STATE_BUFFERING) {
                if (!isSeeking) {
                    currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
                }
                bufferedPosition = exoPlayer.bufferedPosition.coerceAtLeast(0L)
                totalDuration = exoPlayer.duration.coerceAtLeast(0L)
            }
            delay(500)
        }
    }

    // Auto-hide controls timer (4 seconds)
    LaunchedEffect(areControlsVisible, isPlaying) {
        if (areControlsVisible && isPlaying) {
            delay(4000)
            areControlsVisible = false
        }
    }

    // Handle App Lifecycle (Pause video on background)
    val mediaSession = remember(exoPlayer) {
        try {
            MediaSession.Builder(context, exoPlayer)
                .setId("session_vp_${System.currentTimeMillis()}_${java.util.UUID.randomUUID()}")
                .build()
        } catch (_: Exception) {
            null
        }
    }
    DisposableEffect(mediaSession) {
        onDispose {
            try {
                mediaSession?.release()
            } catch (_: Exception) {}
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    val act = context as? Activity
                    if (act?.isInPictureInPictureMode != true) {
                        exoPlayer.pause()
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (isPlaying) {
                        exoPlayer.play()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val coroutineScope = rememberCoroutineScope()
    var doubleTapSide by remember { mutableStateOf<String?>(null) } // "LEFT" or "RIGHT"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(totalDuration, isLive) {
                detectTapGestures(
                    onTap = {
                        areControlsVisible = !areControlsVisible
                    },
                    onDoubleTap = { offset ->
                        val screenWidth = size.width
                        if (!isLive) {
                            if (offset.x < screenWidth * 0.38f) {
                                // Double tapped LEFT: seek -10s
                                val target = (exoPlayer.currentPosition - 10000L).coerceAtLeast(0L)
                                exoPlayer.seekTo(target)
                                doubleTapSide = "LEFT"
                                coroutineScope.launch {
                                    delay(650)
                                    if (doubleTapSide == "LEFT") doubleTapSide = null
                                }
                            } else if (offset.x > screenWidth * 0.62f) {
                                // Double tapped RIGHT: seek +10s
                                val maxDur = if (totalDuration > 0) totalDuration else exoPlayer.duration.coerceAtLeast(0L)
                                val target = (exoPlayer.currentPosition + 10000L).let {
                                    if (maxDur > 0) it.coerceAtMost(maxDur) else it
                                }
                                exoPlayer.seekTo(target)
                                doubleTapSide = "RIGHT"
                                coroutineScope.launch {
                                    delay(650)
                                    if (doubleTapSide == "RIGHT") doubleTapSide = null
                                }
                            } else {
                                // Double tapped CENTER: Play / Pause toggle
                                if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                            }
                        } else {
                            if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                        }
                    }
                )
            }
    ) {
        // 1. ExoPlayer Surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    this.resizeMode = resizeMode
                }
            },
            update = { playerView ->
                playerView.resizeMode = resizeMode
            },
            modifier = Modifier.fillMaxSize()
        )

        // 1.1 Double-Tap Animated Seek Visual Indicators
        AnimatedVisibility(
            visible = doubleTapSide == "LEFT",
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
            visible = doubleTapSide == "RIGHT",
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

        // 2. Buffering Spinner
        if (isBuffering) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(52.dp)
                )
            }
        }

        // 3. Overlay Controls
        AnimatedVisibility(
            visible = areControlsVisible || isBuffering,
            enter = fadeIn(animationSpec = tween(220)),
            exit = fadeOut(animationSpec = tween(220))
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
                // TOP BAR: Back Button, Title, Badges, Action Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .then(if (isFullscreen) Modifier.statusBarsPadding() else Modifier)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: Back button + Title info
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.18f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .clickable {
                                    if (isFullscreen) {
                                        isFullscreen = false
                                    } else {
                                        onBack()
                                    }
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Text(
                                text = title.ifBlank { "ক্লাস ভিডিও লেকচার" },
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Right: Action Strip (PiP, Zoom, Quality, Offline Download, Speed)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        // Picture-in-Picture button
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White.copy(alpha = 0.16f),
                                border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.22f)),
                                modifier = Modifier.clickable {
                                    try {
                                        val params = PictureInPictureParams.Builder()
                                            .setAspectRatio(Rational(16, 9))
                                            .build()
                                        activity?.enterPictureInPictureMode(params)
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "PiP মোড এই ডিভাইসে সমর্থিত নয়", Toast.LENGTH_SHORT).show()
                                    }
                                }
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
                        }

                        // Aspect ratio resize toggle
                        val resizeModeLabel = when (resizeMode) {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT -> "ফিট"
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "জুম"
                            AspectRatioFrameLayout.RESIZE_MODE_FILL -> "ফুল"
                            else -> "ফিট"
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.16f),
                            border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.22f)),
                            modifier = Modifier.clickable {
                                resizeMode = when (resizeMode) {
                                    AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                }
                            }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AspectRatio,
                                    contentDescription = "Aspect Ratio",
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

                        // Quality selector
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.16f),
                            border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.22f)),
                            modifier = Modifier.clickable { showQualityDialog = true }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HighQuality,
                                    contentDescription = "Quality",
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

                        // Offline In-App Download Action
                        when (downloadedItem?.status) {
                            DownloadedItemEntity.STATUS_DOWNLOADING -> {
                                val item = downloadedItem!!
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF0284C7).copy(alpha = 0.4f),
                                    border = BorderStroke(0.8.dp, Color(0xFF38BDF8).copy(alpha = 0.6f)),
                                    modifier = Modifier.clickable {
                                        downloadManager.cancelDownload(downloadId)
                                        Toast.makeText(context, "ডাউনলোড বাতিল করা হয়েছে", Toast.LENGTH_SHORT).show()
                                    }
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
                                    modifier = Modifier.clickable { showDeleteDownloadDialog = true }
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
                                    modifier = Modifier.clickable { showDownloadQualityDialog = true }
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

                        // Playback Speed
                        if (!isLive) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White.copy(alpha = 0.16f),
                                border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.22f)),
                                modifier = Modifier.clickable { showSpeedDialog = true }
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

                // CENTER CONTROLS: -10s, Play/Pause, +10s
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Rewind 10 seconds
                    if (!isLive) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable {
                                    val newPos = (exoPlayer.currentPosition - 10000).coerceAtLeast(0)
                                    exoPlayer.seekTo(newPos)
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Replay10,
                                    contentDescription = "Rewind 10 seconds",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }

                    // Play / Pause Button
                    Surface(
                        shape = CircleShape,
                        color = Color.White,
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .clickable {
                                if (exoPlayer.isPlaying) {
                                    exoPlayer.pause()
                                } else {
                                    exoPlayer.play()
                                }
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color(0xFF0F172A),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    // Forward 10 seconds
                    if (!isLive) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable {
                                    val newPos = (exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration)
                                    exoPlayer.seekTo(newPos)
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Forward10,
                                    contentDescription = "Forward 10 seconds",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }

                // BOTTOM CONTROLS: Seekbar + Bengali time + Fullscreen Toggle
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .then(if (isFullscreen) Modifier.navigationBarsPadding() else Modifier)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    if (!isLive) {
                        val bufferedFraction = if (totalDuration > 0) {
                            (bufferedPosition.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
                        } else 0f

                        val currentFraction = if (totalDuration > 0) {
                            (currentPosition.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
                        } else 0f

                        val seekValue = if (isSeeking) {
                            (seekPosition.toFloat() / (if (totalDuration > 0) totalDuration else 1L).toFloat()).coerceIn(0f, 1f)
                        } else {
                            currentFraction
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(26.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Secondary Track for Buffering
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

                            // Interactive Slider
                            Slider(
                                value = seekValue,
                                onValueChange = { fraction ->
                                    isSeeking = true
                                    seekPosition = (fraction * totalDuration).toLong().coerceIn(0L, totalDuration)
                                },
                                onValueChangeFinished = {
                                    currentPosition = seekPosition
                                    exoPlayer.seekTo(seekPosition)
                                    coroutineScope.launch {
                                        delay(300)
                                        isSeeking = false
                                    }
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
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Duration Text
                            val curPos = if (isSeeking) seekPosition else currentPosition
                            val timeString = "${ShikhoPlayerManager.formatTime(curPos, true)} / ${ShikhoPlayerManager.formatTime(totalDuration, true)}"
                            Text(
                                text = timeString,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            // Fullscreen Toggle
                            Surface(
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.16f),
                                border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.22f)),
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .clickable { isFullscreen = !isFullscreen }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                        contentDescription = if (isFullscreen) "Exit Fullscreen" else "Enter Fullscreen",
                                        tint = Color.White,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        // LIVE BAR
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
                                    .clickable { isFullscreen = !isFullscreen }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                        contentDescription = if (isFullscreen) "Exit Fullscreen" else "Enter Fullscreen",
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

        // Speed Selector Dialog
        if (showSpeedDialog) {
            PlaybackSpeedDialog(
                playbackSpeed = playbackSpeed,
                onSpeedChange = { speed ->
                    playbackSpeed = speed
                    exoPlayer.playbackParameters = PlaybackParameters(speed)
                },
                onDismiss = { showSpeedDialog = false }
            )
        }

        // Quality Selector Dialog
        if (showQualityDialog) {
            VideoQualityDialog(
                availableQualities = availableQualities,
                selectedQualityLabel = selectedQualityLabel,
                onSelectQuality = { quality ->
                    selectedQualityLabel = quality.label
                    if (quality.id == "auto") {
                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                            .buildUpon()
                            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                            .build()
                    } else if (quality.trackGroup != null) {
                        val override = TrackSelectionOverride(
                            quality.trackGroup.mediaTrackGroup,
                            listOf(quality.trackIndex)
                        )
                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                            .buildUpon()
                            .setOverrideForType(override)
                            .build()
                    }
                },
                onDismiss = { showQualityDialog = false }
            )
        }

        // Download Quality Selection Dialog
        if (showDownloadQualityDialog) {
            VideoDownloadQualityDialog(
                videoUrl = effectivePlaybackUrl,
                title = title.ifBlank { "ক্লাস ভিডিও লেকচার" },
                downloadedItem = downloadedItem,
                onDismiss = { showDownloadQualityDialog = false },
                onConfirmDownload = { selectedQuality ->
                    showDownloadQualityDialog = false
                    downloadManager.downloadFile(
                        id = downloadId,
                        title = title.ifBlank { "ক্লাস ভিডিও লেকচার" },
                        subtitle = subjectName,
                        fileType = DownloadedItemEntity.FILE_TYPE_VIDEO,
                        remoteUrl = selectedQuality.targetM3u8Url
                    )
                    Toast.makeText(
                        context,
                        "ভিডিও ডাউনলোড শুরু হয়েছে (${selectedQuality.labelBangla})। 'ডাউনলোড' ট্যাবে দেখতে পাবেন।",
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        }

        // Downloaded Video Info & Deletion Dialog
        if (showDeleteDownloadDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDownloadDialog = false },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.DownloadDone,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(24.dp)
                        )
                        Text("অফলাইন ডাউনলোড", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                },
                text = {
                    Column {
                        Text(
                            text = "এই ভিডিওটি আপনার ডিভাইসের সুরক্ষিত অ্যাপ স্টোরেজে অফলাইনে সংরক্ষিত রয়েছে।",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (downloadedItem != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "সাইজ: ${downloadManager.formatFileSize(downloadedItem!!.totalBytes)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showDeleteDownloadDialog = false }) {
                        Text("ঠিক আছে", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            downloadManager.deleteDownloadedFile(downloadId)
                            showDeleteDownloadDialog = false
                            Toast.makeText(context, "ডাউনলোড করা ফাইল ডিলিট করা হয়েছে", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("ডিলিট করুন", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}

// Extension to find Activity from Context
private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
