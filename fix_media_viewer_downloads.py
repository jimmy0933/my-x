with open('app/src/main/java/com/example/xdownloader/ui/components/MediaViewer.kt', 'r') as f:
    mv_content = f.read()

if "import com.example.xdownloader.ui.components.MediaDownloadBottomSheet" not in mv_content:
    mv_content = mv_content.replace(
        "import com.example.xdownloader.data.MediaType",
        "import com.example.xdownloader.data.MediaType\nimport androidx.compose.material.icons.Icons\nimport androidx.compose.material.icons.filled.Download"
    )

if "var showDownloadSheet by remember { mutableStateOf(false) }" not in mv_content:
    mv_content = mv_content.replace(
        "val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { mediaItems.size })",
        "val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { mediaItems.size })\n    var showDownloadSheet by remember { mutableStateOf(false) }"
    )

if "showDownloadSheet = true" not in mv_content:
    mv_content = mv_content.replace(
        """        if (mediaItems.size > 1) {""",
        """        IconButton(
            onClick = { showDownloadSheet = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.5f), shape = MaterialTheme.shapes.small)
        ) {
            Icon(Icons.Default.Download, contentDescription = "Download", tint = Color.White)
        }

        if (showDownloadSheet) {
            MediaDownloadBottomSheet(mediaItems = listOf(mediaItems[pagerState.currentPage]), onDismiss = { showDownloadSheet = false })
        }

        if (mediaItems.size > 1) {"""
    )

with open('app/src/main/java/com/example/xdownloader/ui/components/MediaViewer.kt', 'w') as f:
    f.write(mv_content)
