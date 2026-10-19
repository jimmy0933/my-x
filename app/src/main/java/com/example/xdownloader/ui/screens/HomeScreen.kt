package com.example.xdownloader.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
    var paginationError by remember { mutableStateOf<String?>(null) }

    // authorId to nextCursor mapping
    val cursors = remember { mutableMapOf<String, String>() }
    // authorId to hasMore mapping
    val hasMore = remember { mutableMapOf<String, Boolean>() }

    var currentFetchGeneration by remember { mutableStateOf(0) }

    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    val pullRefreshState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    val shouldLoadMore = remember {
        androidx.compose.runtime.derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            val lastVisibleItemIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItemIndex >= totalItems - 2 && hasMore.values.any { it } && !isLoadingMore && !isLoading && paginationError == null
        }
    }

    suspend fun fetchTimelineFn(isRefresh: Boolean, isLoadMore: Boolean) {
        if (!apiClient.isConfigured()) {
            errorMessage = "請先至設定填寫登入憑證 (Please configure credentials in settings)"
        } else {
            val myGeneration = ++currentFetchGeneration

            val initialCursors = mutableMapOf<String, String>()
            if (isRefresh) {
                // Do not clear the existing ones immediately so we can revert if failed
            } else {
                initialCursors.putAll(cursors)
            }

            if (!isLoadMore) {
                isLoading = !pullRefreshState.isRefreshing
            } else {
                isLoadingMore = true
            }

            try {
                val following = db.followDao().getAllFollowing().first()
                if (following.isEmpty()) {
                    errorMessage = "目前沒有追蹤任何人 (No follows yet)"
                } else {
                    val allTweets = if (isRefresh) mutableListOf() else tweets.toMutableList()
                    var partialFailureMsg: String? = null
                    var anySuccess = false

                    val newCursors = mutableMapOf<String, String>()
                    val newHasMore = mutableMapOf<String, Boolean>()

                    for (author in following) {
                        if (!isRefresh && hasMore[author.authorId] == false) continue

                        val cursor = if (isRefresh) null else cursors[author.authorId]
                        when (val result = apiClient.getUserMedia(author.authorId, cursor)) {
                            is ApiResult.Success -> {
                                anySuccess = true
                                val (parsedTweets, nextCursor) = TweetParser.parseMediaTimeline(result.data)

                                allTweets.addAll(parsedTweets)

                                if (nextCursor != null && nextCursor.isNotEmpty() && nextCursor != cursor) {
                                    newCursors[author.authorId] = nextCursor
                                    newHasMore[author.authorId] = true
                                } else {
                                    newHasMore[author.authorId] = false
                                }
                            }
                            is ApiResult.Unauthorized -> {
                                partialFailureMsg = result.message
                                if (isRefresh) allTweets.addAll(tweets.filter { it.authorScreenName == author.authorName || it.authorScreenName == author.authorScreenName })
                            }
                            is ApiResult.RateLimited -> {
                                partialFailureMsg = result.message
                                if (isRefresh) allTweets.addAll(tweets.filter { it.authorScreenName == author.authorName || it.authorScreenName == author.authorScreenName })
                            }
                            is ApiResult.NetworkError -> {
                                partialFailureMsg = result.message
                                if (isRefresh) allTweets.addAll(tweets.filter { it.authorScreenName == author.authorName || it.authorScreenName == author.authorScreenName })
                            }
                            is ApiResult.ParsingError -> {
                                partialFailureMsg = result.message
                                if (isRefresh) allTweets.addAll(tweets.filter { it.authorScreenName == author.authorName || it.authorScreenName == author.authorScreenName })
                            }
                        }
                    }

                    if (myGeneration == currentFetchGeneration) {
                        if (isRefresh) {
                            cursors.clear()
                            hasMore.clear()
                        }
                        cursors.putAll(newCursors)
                        for ((k, v) in newHasMore) hasMore[k] = v

                        val distinctTweets = allTweets.distinctBy { it.tweetId }.sortedByDescending { it.createdAt }

                        val parsedTweetsAdded = distinctTweets.size - (if (isRefresh) 0 else tweets.size)

                        if (anySuccess || (isRefresh && !anySuccess && distinctTweets.isNotEmpty())) {
                            tweets = distinctTweets
                        }

                        if (distinctTweets.isEmpty() && partialFailureMsg != null && !anySuccess) {
                            if (isLoadMore) paginationError = partialFailureMsg else errorMessage = partialFailureMsg
                        } else if (partialFailureMsg == null) {
                            errorMessage = null
                            paginationError = null
                        } else if (partialFailureMsg != null) {
                            if (isLoadMore) paginationError = partialFailureMsg else errorMessage = null
                            android.widget.Toast.makeText(context, "部分載入失敗: \$partialFailureMsg", android.widget.Toast.LENGTH_LONG).show()
                        }

                        if (isLoadMore && parsedTweetsAdded == 0 && hasMore.values.any { it } && partialFailureMsg == null) {
                            if (distinctTweets.isEmpty()) {
                                 scope.launch { fetchTimelineFn(false, true) }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                if (myGeneration == currentFetchGeneration) {
                    if (tweets.isEmpty()) errorMessage = "發生非預期錯誤: \${e.message}"
                    else if (isLoadMore) paginationError = "發生非預期錯誤: \${e.message}"
                }
            } finally {
                if (myGeneration == currentFetchGeneration) {
                    isLoading = false
                    isLoadingMore = false
                    pullRefreshState.endRefresh()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        fetchTimelineFn(true, false)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("追蹤動態 (Home)") }) }
    ) { padding ->
        if (pullRefreshState.isRefreshing) {
            LaunchedEffect(true) {
                fetchTimelineFn(true, false)
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
                            isLoading = true
                            fetchTimelineFn(tweets.isEmpty(), false) // resume if we have tweets, else refresh
                        }
                    }) {
                        Text("重試 (Retry)")
                    }
                }
            } else {
                LaunchedEffect(shouldLoadMore.value) {
                    if (shouldLoadMore.value) {
                        fetchTimelineFn(false, true)
                    }
                }

                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(tweets, key = { it.tweetId }) { tweet ->
                        TweetCard(tweet)
                    }
                    if (hasMore.values.any { it }) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                if (paginationError != null) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(paginationError!!, color = MaterialTheme.colorScheme.error)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Button(onClick = {
                                            paginationError = null
                                            scope.launch { fetchTimelineFn(false, true) }
                                        }) {
                                            Text("重試載入更多 (Retry)")
                                        }
                                    }
                                } else if (isLoadingMore || (tweets.isEmpty() && hasMore.values.any { it })) {
                                    CircularProgressIndicator()
                                }
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
