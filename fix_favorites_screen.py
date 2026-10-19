with open('app/src/main/java/com/example/xdownloader/ui/screens/FavoritesScreen.kt', 'r') as f:
    content = f.read()

import re
new_fav_card = """@Composable
fun FavoriteCard(fav: FavoriteEntity) {
    var showViewer by remember { mutableStateOf(false) }
    var showDownloadSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)

    val urls = fav.mediaUrls.split(",").filter { it.isNotEmpty() }
    val types = fav.mediaTypes.split(",").filter { it.isNotEmpty() }

    val mediaItems = urls.indices.map {
        MediaItem(
            id = fav.tweetId + "_$it",
            url = urls[it],
            thumbnailUrl = "",
            type = if (types.getOrNull(it) == "VIDEO") MediaType.VIDEO else MediaType.IMAGE
        )
    }

    Card(modifier = Modifier.fillMaxWidth().padding(8.dp).clickable { if (mediaItems.isNotEmpty()) showViewer = true }) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${fav.authorName} (@${fav.authorScreenName})", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = {
                    scope.launch {
                        db.favoriteDao().deleteFavorite(fav)
                        android.widget.Toast.makeText(context, "Removed from favorites", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Icon(androidx.compose.material.icons.Icons.Default.Close, contentDescription = "Remove")
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(fav.text, style = MaterialTheme.typography.bodyMedium)
            if (fav.deleted) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("⚠️ 此貼文已被刪除或無法存取", color = MaterialTheme.colorScheme.error)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = { showDownloadSheet = true }) {
                    Icon(androidx.compose.material.icons.Icons.Default.Download, contentDescription = "Download")
                }
            }
        }
    }

    if (showDownloadSheet && mediaItems.isNotEmpty()) {
        MediaDownloadBottomSheet(mediaItems = mediaItems, onDismiss = { showDownloadSheet = false })
    }

    if (showViewer && mediaItems.isNotEmpty()) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showViewer = false }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
            Box(modifier = Modifier.fillMaxSize()) {
                com.example.xdownloader.ui.components.MediaViewer(mediaItems)
                IconButton(onClick = { showViewer = false }, modifier = Modifier.align(Alignment.TopStart).padding(16.dp)) {
                    Icon(androidx.compose.material.icons.Icons.Default.Close, contentDescription = "Close", tint = androidx.compose.ui.graphics.Color.White)
                }
            }
        }
    }
}"""
content = re.sub(r"@Composable\nfun FavoriteCard\(fav: FavoriteEntity\) \{.*?\n\}\n(?!\n)", new_fav_card + "\n", content, flags=re.DOTALL)
content = content.replace("import kotlinx.coroutines.flow.first", "import kotlinx.coroutines.flow.first\nimport kotlinx.coroutines.launch")

with open('app/src/main/java/com/example/xdownloader/ui/screens/FavoritesScreen.kt', 'w') as f:
    f.write(content)
