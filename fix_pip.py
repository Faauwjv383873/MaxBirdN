import re

def fix_file(filepath):
    with open(filepath, 'r') as f:
        content = f.read()

    # Import MediaSession if not present
    if 'import androidx.media3.session.MediaSession' not in content:
        content = content.replace('import androidx.media3.exoplayer.ExoPlayer', 'import androidx.media3.exoplayer.ExoPlayer\nimport androidx.media3.session.MediaSession\nimport android.app.Activity')

    # Replace ON_PAUSE logic
    if 'Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()' in content:
        content = content.replace(
            'Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()',
            'Lifecycle.Event.ON_PAUSE -> {\n                    val activity = context as? Activity\n                    if (activity?.isInPictureInPictureMode != true) {\n                        exoPlayer.pause()\n                    }\n                }'
        )
    elif 'Lifecycle.Event.ON_PAUSE -> {\n                    exoPlayer.pause()\n                }' in content:
        content = content.replace(
            'Lifecycle.Event.ON_PAUSE -> {\n                    exoPlayer.pause()\n                }',
            'Lifecycle.Event.ON_PAUSE -> {\n                    val activity = context as? Activity\n                    if (activity?.isInPictureInPictureMode != true) {\n                        exoPlayer.pause()\n                    }\n                }'
        )

    # Insert MediaSession block right after DisposableEffect(lifecycleOwner)
    if 'val mediaSession = remember(' not in content:
        # We need to find a place to put mediaSession
        # How about right before DisposableEffect(lifecycleOwner)?
        media_session_code = """
    val mediaSession = remember(exoPlayer) {
        MediaSession.Builder(context, exoPlayer).build()
    }
    DisposableEffect(mediaSession) {
        onDispose {
            mediaSession.release()
        }
    }
    """
        content = content.replace('    DisposableEffect(lifecycleOwner) {', media_session_code + '\n    DisposableEffect(lifecycleOwner) {')

    with open(filepath, 'w') as f:
        f.write(content)

fix_file('app/src/main/java/com/example/ui/screens/LessonDetailPlayerScreen.kt')
fix_file('app/src/main/java/com/example/ui/screens/VideoPlayerScreen.kt')
