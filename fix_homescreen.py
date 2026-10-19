with open('app/src/main/java/com/example/xdownloader/ui/screens/HomeScreen.kt', 'r') as f:
    content = f.read()

# Add remember for cursors and states
content = content.replace("""    var tweets by remember { mutableStateOf<List<TweetParser.ParsedTweet>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }""", """    var tweets by remember { mutableStateOf<List<TweetParser.ParsedTweet>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // authorId to nextCursor mapping
    val cursors = remember { mutableMapOf<String, String>() }
    // authorId to hasMore mapping
    val hasMore = remember { mutableMapOf<String, Boolean>() }""")

# Add PullToRefresh
content = content.replace("import androidx.compose.material3.*", "import androidx.compose.material3.*\nimport androidx.compose.material3.pulltorefresh.PullToRefreshContainer\nimport androidx.compose.material3.pulltorefresh.rememberPullToRefreshState")
content = content.replace("import androidx.compose.ui.input.nestedscroll.nestedScroll", "") # might need this, let's just add it via regex later

if "import androidx.compose.ui.input.nestedscroll.nestedScroll" not in content:
    content = content.replace("import androidx.compose.ui.Alignment", "import androidx.compose.ui.Alignment\nimport androidx.compose.ui.input.nestedscroll.nestedScroll")

# Update loadTweets function
new_load_tweets = """        suspend fun loadTweets(isRefresh: Boolean = false) {
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

        loadTweets(isRefresh = true)"""

import re
content = re.sub(r"        suspend fun loadTweets\(\) \{.*loadTweets\(\)", new_load_tweets, content, flags=re.DOTALL)

content = content.replace("""        Box(modifier = Modifier.padding(padding).fillMaxSize()) {""", """        val pullRefreshState = rememberPullToRefreshState()
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

        Box(modifier = Modifier.padding(padding).fillMaxSize().nestedScroll(pullRefreshState.nestedScrollConnection)) {""")

content = content.replace("""            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(tweets) { tweet ->
                        TweetCard(tweet)
                    }
                }
            }""", """            } else {
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
            }""")


with open('app/src/main/java/com/example/xdownloader/ui/screens/HomeScreen.kt', 'w') as f:
    f.write(content)
