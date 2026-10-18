package com.example.xdownloader.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import com.example.xdownloader.database.FavoriteEntity
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.xdownloader.api.TweetParser
import com.example.xdownloader.api.XApiClient
import com.example.xdownloader.database.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val apiClient = remember { XApiClient(context) }
    val scope = rememberCoroutineScope()

    var tweets by remember { mutableStateOf<List<TweetParser.ParsedTweet>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (!apiClient.isConfigured()) {
            errorMessage = "請先至設定填寫登入憑證 (Please configure credentials in settings)"
            return@LaunchedEffect
        }

        isLoading = true
        try {
            val following = db.followDao().getAllFollowing().first()
            if (following.isEmpty()) {
                errorMessage = "目前沒有追蹤任何人 (No follows yet)"
            } else {
                val allTweets = mutableListOf<TweetParser.ParsedTweet>()
                for (author in following) {
                    val mediaJson = apiClient.getUserMedia(author.authorId)
                    if (mediaJson != null) {
                        val (parsedTweets, _) = TweetParser.parseMediaTimeline(mediaJson)
                        allTweets.addAll(parsedTweets)
                    }
                }
                tweets = allTweets.distinctBy { it.tweetId }
                errorMessage = null
            }
        } catch (e: Exception) {
            errorMessage = "載入失敗: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("追蹤動態 (Home)") }) }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (errorMessage != null) {
                Text(errorMessage!!, modifier = Modifier.align(Alignment.Center).padding(16.dp))
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(tweets) { tweet ->
                        TweetCard(tweet)
                    }
                }
            }
        }
    }
}

@Composable
fun TweetCard(tweet: TweetParser.ParsedTweet) {
    var showViewer by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)

    Card(modifier = Modifier.fillMaxWidth().padding(8.dp).clickable { if (tweet.media.isNotEmpty()) showViewer = true }) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
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
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(tweet.text, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text("包含 ${tweet.media.size} 個媒體", color = MaterialTheme.colorScheme.primary)
        }
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
}
