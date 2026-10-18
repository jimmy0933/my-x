with open('app/src/main/java/com/example/xdownloader/ui/components/MediaViewer.kt', 'r') as f:
    content = f.read()

import re

# Pass isActive state to VideoPlayer
content = content.replace("VideoPlayer(url = item.url)", "VideoPlayer(url = item.url, isActive = page == pagerState.currentPage)")

# Update VideoPlayer definition
new_video_player = """@Composable
fun VideoPlayer(url: String, isActive: Boolean) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(ExoMediaItem.fromUri(url))
            prepare()
        }
    }

    LaunchedEffect(url) {
        exoPlayer.setMediaItem(ExoMediaItem.fromUri(url))
        exoPlayer.prepare()
    }

    LaunchedEffect(isActive) {
        if (!isActive) {
            exoPlayer.pause()
        } else {
            exoPlayer.play()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE || event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                exoPlayer.pause()
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME && isActive) {
                exoPlayer.play()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = {
            PlayerView(context).apply {
                player = exoPlayer
                setShowNextButton(false)
                setShowPreviousButton(false)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}"""

content = re.sub(r"@Composable\nfun VideoPlayer\(url: String\) \{.*?\n\}\n(?!\n)", new_video_player + "\n", content, flags=re.DOTALL)
if "androidx.lifecycle.compose.LocalLifecycleOwner" not in content:
    content = content.replace("import androidx.media3.ui.PlayerView", "import androidx.media3.ui.PlayerView\nimport androidx.lifecycle.compose.LocalLifecycleOwner\nimport androidx.lifecycle.Lifecycle\nimport androidx.lifecycle.LifecycleEventObserver")

with open('app/src/main/java/com/example/xdownloader/ui/components/MediaViewer.kt', 'w') as f:
    f.write(content)
