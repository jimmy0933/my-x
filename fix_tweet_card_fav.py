with open('app/src/main/java/com/example/xdownloader/ui/screens/HomeScreen.kt', 'r') as f:
    content = f.read()

content = content.replace('var showViewer by remember { mutableStateOf(false) }', """var showViewer by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    """)

content = content.replace('Text("${tweet.authorName} (@${tweet.authorScreenName})", style = MaterialTheme.typography.titleMedium)', """Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${tweet.authorName} (@${tweet.authorScreenName})", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = {
                    scope.launch {
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
                }) {
                    Icon(androidx.compose.material.icons.Icons.Default.Favorite, contentDescription = "Favorite")
                }
            }""")

content = content.replace("import androidx.compose.material.icons.filled.Close", "import androidx.compose.material.icons.filled.Close\nimport androidx.compose.material.icons.filled.Favorite\nimport com.example.xdownloader.database.FavoriteEntity")

with open('app/src/main/java/com/example/xdownloader/ui/screens/HomeScreen.kt', 'w') as f:
    f.write(content)
