with open('app/src/main/java/com/example/xdownloader/ui/screens/HomeScreen.kt', 'r') as f:
    content = f.read()

import re

new_tweet_card = """@Composable
fun TweetCard(tweet: TweetParser.ParsedTweet) {
    var showViewer by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    var showDownloadSheet by remember { mutableStateOf(false) }

    val isFavorite by db.favoriteDao().isFavorite(tweet.tweetId).collectAsState(initial = false)

    Card(modifier = Modifier.fillMaxWidth().padding(8.dp).clickable { if (tweet.media.isNotEmpty()) showViewer = true }) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${tweet.authorName} (@${tweet.authorScreenName})", style = MaterialTheme.typography.titleMedium)
                Row {
                    IconButton(onClick = {
                        scope.launch {
                            if (isFavorite) {
                                val fav = db.favoriteDao().getFavoriteById(tweet.tweetId)
                                if (fav != null) db.favoriteDao().deleteFavorite(fav)
                            } else {
                                db.favoriteDao().insertFavorite(com.example.xdownloader.database.FavoriteEntity(
                                    tweetId = tweet.tweetId,
                                    authorName = tweet.authorName,
                                    authorScreenName = tweet.authorScreenName,
                                    authorAvatar = tweet.authorAvatar,
                                    text = tweet.text,
                                    createdAt = tweet.createdAt,
                                    mediaUrls = tweet.media.joinToString(",") { it.url },
                                    mediaTypes = tweet.media.joinToString(",") { it.type.name },
                                    mediaThumbnails = tweet.media.joinToString(",") { it.thumbnailUrl }
                                ))
                                android.widget.Toast.makeText(context, "Added to favorites", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) {
                        Icon(if (isFavorite) androidx.compose.material.icons.Icons.Default.Favorite else androidx.compose.material.icons.Icons.Default.FavoriteBorder, contentDescription = "Favorite", tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = { showDownloadSheet = true }) {
                        Icon(androidx.compose.material.icons.Icons.Default.Download, contentDescription = "Download")
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(tweet.text, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text("包含 ${tweet.media.size} 個媒體", color = MaterialTheme.colorScheme.primary)
        }
    }
    if (showDownloadSheet) {
        MediaDownloadBottomSheet(mediaItems = tweet.media, onDismiss = { showDownloadSheet = false })
    }

    if (showViewer) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showViewer = false }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
            Box(modifier = Modifier.fillMaxSize()) {
                com.example.xdownloader.ui.components.MediaViewer(tweet.media)
                IconButton(onClick = { showViewer = false }, modifier = Modifier.align(Alignment.TopStart).padding(16.dp)) {
                    Icon(androidx.compose.material.icons.Icons.Default.Close, contentDescription = "Close", tint = androidx.compose.ui.graphics.Color.White)
                }
            }
        }
    }
}"""

content = re.sub(r"@Composable\nfun TweetCard\(tweet: TweetParser.ParsedTweet\) \{.*?\n\}\n(?!\n)", new_tweet_card + "\n", content, flags=re.DOTALL)
content = content.replace("import androidx.compose.material.icons.filled.Favorite", "import androidx.compose.material.icons.filled.Favorite\nimport androidx.compose.material.icons.filled.FavoriteBorder")

with open('app/src/main/java/com/example/xdownloader/ui/screens/HomeScreen.kt', 'w') as f:
    f.write(content)
