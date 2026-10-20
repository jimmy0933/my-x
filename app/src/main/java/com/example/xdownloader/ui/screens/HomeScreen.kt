package com.example.xdownloader.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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

    val viewModel: com.example.xdownloader.ui.viewmodel.HomeScreenViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return com.example.xdownloader.ui.viewmodel.HomeScreenViewModel(db, apiClient) as T
            }
        }
    )

    val tweets by viewModel.tweets.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val paginationError by viewModel.paginationError.collectAsState()

    val pullRefreshState = rememberPullToRefreshState()
    val listState = rememberLazyListState()

    val shouldLoadMore = remember {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            val lastVisibleItemIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItemIndex >= totalItems - 3
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value && !isLoading && !isLoadingMore && paginationError == null) {
            viewModel.loadMore()
        }
    }

    LaunchedEffect(Unit) {
        if (tweets.isEmpty()) {
            viewModel.loadTimeline(isRefresh = true)
        }
    }

    if (pullRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            viewModel.loadTimeline(isRefresh = true)
            pullRefreshState.endRefresh()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("追蹤動態 (Home)") }) }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize().nestedScroll(pullRefreshState.nestedScrollConnection)) {
            if (isLoading && tweets.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (errorMessage != null && tweets.isEmpty()) {
                Column(modifier = Modifier.align(Alignment.Center).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(errorMessage!!)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { viewModel.loadTimeline(isRefresh = true) }) {
                        Text("重試 (Retry)")
                    }
                }
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(tweets, key = { it.tweetId }) { tweet ->
                        TweetCard(tweet)
                    }
                    if (viewModel.hasMore.values.any { it }) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                if (paginationError != null) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(paginationError!!, color = MaterialTheme.colorScheme.error)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Button(onClick = {
                                            viewModel.dismissPaginationError()
                                            viewModel.loadMore()
                                        }) {
                                            Text("重試載入更多 (Retry)")
                                        }
                                    }
                                } else if (isLoadingMore || (tweets.isEmpty() && viewModel.hasMore.values.any { it })) {
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
