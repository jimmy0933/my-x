with open('app/src/main/java/com/example/xdownloader/ui/components/MediaViewer.kt', 'r') as f:
    kt = f.read()

kt = kt.replace("@Composable\nfun VideoPlayer(url: String, isActive: Boolean)", "@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)\n@Composable\nfun VideoPlayer(url: String, isActive: Boolean)")

with open('app/src/main/java/com/example/xdownloader/ui/components/MediaViewer.kt', 'w') as f:
    f.write(kt)
