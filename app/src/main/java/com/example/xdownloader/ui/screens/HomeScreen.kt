package com.example.xdownloader.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import com.example.xdownloader.database.FavoriteEntity
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.xdownloader.api.TweetParser
import com.example.xdownloader.api.XApiClient
import com.example.xdownloader.api.ApiResult
import com.example.xdownloader.database.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.example.xdownloader.ui.components.MediaDownloadBottomSheet
import androidx.compose.material.icons.filled.Download

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val apiClient = remember { XApiClient(context) }
    val scope = rememberCoroutineScope()

    var tweets by remember { mutableStateOf<List<TweetParser.ParsedTweet>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // authorId to nextCursor mapping
    val cursors = remember { mutableMapOf<String, String>() }
    // authorId to hasMore mapping
    val hasMore = remember { mutableMapOf<String, Boolean>() }

    LaunchedEffect(Unit) {
        if (!apiClient.isConfigured()) {
            errorMessage = "請先至設定填寫登入憑證 (Please configure credentials in settings)"
            return@LaunchedEffect
        }

        suspend fun loadTweets(isRefresh: Boolean = false) {
            if (isRefresh) {
                cursors.clear()
                hasMore.clear()
            }
            isLoading = !isRefresh
            errorMessage = null
            try {
                val following = db.followDao().getAllFollowing().first()
                if (following.isEmpty()) {
                    errorMessage = "目前沒有追蹤任何人 (No follows yet)"
                } else {
                    val allTweets = if (isRefresh) mutableListOf<TweetParser.ParsedTweet>() else tweets.toMutableList()
                    var partialFailureMsg: String? = null

                    for (author in following) {
                        if (hasMore[author.authorId] == false) continue // Reached end for this author

                        val cursor = cursors[author.authorId]
                        when (val result = apiClient.getUserMedia(author.authorId, cursor)) {
                            is ApiResult.Success -> {
                                val (parsedTweets, nextCursor) = TweetParser.parseMediaTimeline(result.data)
                                allTweets.addAll(parsedTweets)
                                if (nextCursor != null) {
                                    cursors[author.authorId] = nextCursor
                                    hasMore[author.authorId] = true
                                } else {
                                    hasMore[author.authorId] = false
                                }
                            }
                            is ApiResult.Unauthorized -> partialFailureMsg = result.message
                            is ApiResult.RateLimited -> partialFailureMsg = result.message
                            is ApiResult.NetworkError -> partialFailureMsg = result.message
                            is ApiResult.ParsingError -> partialFailureMsg = result.message
                        }
                    }

                    tweets = allTweets.distinctBy { it.tweetId }.sortedByDescending { it.createdAt }

                    if (allTweets.isEmpty() && partialFailureMsg != null) {
                        errorMessage = partialFailureMsg
                    } else if (partialFailureMsg != null && isRefresh) {
                        android.widget.Toast.makeText(context, "部分載入失敗: $partialFailureMsg", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                if (tweets.isEmpty()) errorMessage = "發生非預期錯誤: ${e.message}"
            } finally {
                isLoading = false
            }
        }

        loadTweets(isRefresh = true)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("追蹤動態 (Home)") }) }
    ) { padding ->
        val pullRefreshState = rememberPullToRefreshState()
        if (pullRefreshState.isRefreshing) {
            LaunchedEffect(true) {
                isLoading = true
                cursors.clear()
                hasMore.clear()
                try {
                    val following = db.followDao().getAllFollowing().first()
                    if (following.isEmpty()) {
                        errorMessage = "目前沒有追蹤任何人 (No follows yet)"
                    } else {
                        val allTweets = mutableListOf<TweetParser.ParsedTweet>()
                        var partialFailureMsg: String? = null

                        for (author in following) {
                            when (val result = apiClient.getUserMedia(author.authorId)) {
                                is ApiResult.Success -> {
                                    val (parsedTweets, nextCursor) = TweetParser.parseMediaTimeline(result.data)
                                    allTweets.addAll(parsedTweets)
                                    if (nextCursor != null) {
                                        cursors[author.authorId] = nextCursor
                                        hasMore[author.authorId] = true
                                    } else {
                                        hasMore[author.authorId] = false
                                    }
                                }
                                is ApiResult.Unauthorized -> partialFailureMsg = result.message
                                is ApiResult.RateLimited -> partialFailureMsg = result.message
                                is ApiResult.NetworkError -> partialFailureMsg = result.message
                                is ApiResult.ParsingError -> partialFailureMsg = result.message
                            }
                        }

                        tweets = allTweets.distinctBy { it.tweetId }.sortedByDescending { it.createdAt }
                        if (allTweets.isEmpty() && partialFailureMsg != null) errorMessage = partialFailureMsg
                    }
                } catch(e:Exception){} finally { pullRefreshState.endRefresh(); isLoading = false }
            }
        }

        Box(modifier = Modifier.padding(padding).fillMaxSize().nestedScroll(pullRefreshState.nestedScrollConnection)) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (errorMessage != null) {
                Column(modifier = Modifier.align(Alignment.Center).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(errorMessage!!)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = {
                        scope.launch {
                            // Quick refresh
                            isLoading = true
                            errorMessage = null
                            try {
                                val following = db.followDao().getAllFollowing().first()
                                if (following.isEmpty()) {
                                    errorMessage = "目前沒有追蹤任何人 (No follows yet)"
                                } else {
                                    val allTweets = mutableListOf<TweetParser.ParsedTweet>()
                                    var partialFailureMsg: String? = null

                                    for (author in following) {
                                        when (val result = apiClient.getUserMedia(author.authorId)) {
                                            is ApiResult.Success -> {
                                                val (parsedTweets, _) = TweetParser.parseMediaTimeline(result.data)
                                                allTweets.addAll(parsedTweets)
                                            }
                                            is ApiResult.Unauthorized -> partialFailureMsg = result.message
                                            is ApiResult.RateLimited -> partialFailureMsg = result.message
                                            is ApiResult.NetworkError -> partialFailureMsg = result.message
                                            is ApiResult.ParsingError -> partialFailureMsg = result.message
                                        }
                                    }
                                    tweets = allTweets.distinctBy { it.tweetId }
                                    if (allTweets.isEmpty() && partialFailureMsg != null) errorMessage = partialFailureMsg
                                }
                            } catch(e:Exception){} finally { isLoading = false }
                        }
                    }) {
                        Text("重試 (Retry)")
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(tweets, key = { it.tweetId }) { tweet ->
                        TweetCard(tweet)
                    }
                    if (tweets.isNotEmpty() && hasMore.values.any { it }) {
                        item {
                            LaunchedEffect(Unit) {
                                if (!isLoadingMore) {
                                    isLoadingMore = true
                                    try {
                                        val following = db.followDao().getAllFollowing().first()
                                        val allTweets = tweets.toMutableList()
                                        for (author in following) {
                                            if (hasMore[author.authorId] == false) continue
                                            val cursor = cursors[author.authorId]
                                            when (val result = apiClient.getUserMedia(author.authorId, cursor)) {
                                                is ApiResult.Success -> {
                                                    val (parsedTweets, nextCursor) = TweetParser.parseMediaTimeline(result.data)
                                                    allTweets.addAll(parsedTweets)
                                                    if (nextCursor != null) {
                                                        cursors[author.authorId] = nextCursor
                                                        hasMore[author.authorId] = true
                                                    } else {
                                                        hasMore[author.authorId] = false
                                                    }
                                                }
                                                else -> {}
                                            }
                                        }
                                        tweets = allTweets.distinctBy { it.tweetId }.sortedByDescending { it.createdAt }
                                    } catch(e:Exception){} finally { isLoadingMore = false }
                                }
                            }
                            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                if (isLoadingMore) CircularProgressIndicator()
                            }
                        }
                    } else if (tweets.isNotEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                Text("沒有更多貼文了", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                PullToRefreshContainer(
                    modifier = Modifier.align(Alignment.TopCenter),
                    state = pullRefreshState
                )
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
}
