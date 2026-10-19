package com.example.xdownloader.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.xdownloader.api.TweetParser
import com.example.xdownloader.api.XApiClient
import com.example.xdownloader.api.ApiResult
import com.example.xdownloader.database.AppDatabase
import com.example.xdownloader.database.FollowEntity
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchAuthorScreen() {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val apiClient = remember { XApiClient(context) }
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var tweetUrl by remember { mutableStateOf("") }
    var tweetData by remember { mutableStateOf<TweetParser.ParsedTweet?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("搜尋與工具 (Search & Tools)") }) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("輸入作者帳號 (Enter username)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (query.isBlank()) return@Button

                    scope.launch {
                        isSearching = true
                        val cleanQuery = query.trim().removePrefix("@")
                        val result = apiClient.getUserIdByScreenName(cleanQuery)
                        if (result is ApiResult.Success) {
                            val userId = result.data
                            db.followDao().insertFollow(
                                FollowEntity(
                                    authorId = userId,
                                    authorName = cleanQuery,
                                    authorScreenName = cleanQuery,
                                    authorAvatar = "",
                                    isAppOnly = true
                                )
                            )
                            Toast.makeText(context, "已追蹤 @$cleanQuery", Toast.LENGTH_SHORT).show()
                            query = ""
                        } else {
                            val errMsg = when (result) {
                                is ApiResult.Unauthorized -> result.message
                                is ApiResult.RateLimited -> result.message
                                is ApiResult.NetworkError -> result.message
                                is ApiResult.ParsingError -> result.message
                                else -> "找不到該作者 (Author not found)"
                            }
                            Toast.makeText(context, errMsg, Toast.LENGTH_SHORT).show()
                        }
                        isSearching = false
                    }
                },
                enabled = !isSearching,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSearching) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Text("追蹤 (Follow)")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = tweetUrl,
                onValueChange = { tweetUrl = it },
                label = { Text("輸入貼文網址 (Enter Tweet URL)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (tweetUrl.isBlank()) return@Button
                    val idMatch = Regex("status/(\\d+)").find(tweetUrl)
                    if (idMatch == null) {
                        Toast.makeText(context, "無效的網址", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val tweetId = idMatch.groupValues[1]

                    scope.launch {
                        when (val result = apiClient.getTweet(tweetId)) {
                            is ApiResult.Success -> {
                                val tweetJson = result.data
                                val resultObj = tweetJson.getAsJsonObject("data")
                                    ?.getAsJsonObject("tweetResult")
                                    ?.getAsJsonObject("result")
                                if (resultObj != null) {
                                    tweetData = TweetParser.parseSingleTweetResult(resultObj)
                                } else {
                                    Toast.makeText(context, "無法解析貼文", Toast.LENGTH_SHORT).show()
                                }
                            }
                            is ApiResult.Unauthorized -> Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                            is ApiResult.RateLimited -> Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                            is ApiResult.NetworkError -> Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                            is ApiResult.ParsingError -> Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("開啟貼文 (Open Tweet)")
            }

            if (tweetData != null) {
                Spacer(modifier = Modifier.height(16.dp))
                TweetCard(tweetData!!)
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text("已追蹤作者 (僅此 App 內)", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            val following by db.followDao().getAllFollowing().collectAsState(initial = emptyList())
            if (following.isEmpty()) {
                Text("目前沒有追蹤任何人", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(following, key = { it.authorId }) { author ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("@${author.authorScreenName}", style = MaterialTheme.typography.bodyLarge)
                                IconButton(onClick = {
                                    scope.launch {
                                        db.followDao().deleteFollow(author)
                                        Toast.makeText(context, "已取消追蹤 @${author.authorScreenName}", Toast.LENGTH_SHORT).show()
                                    }
                                }) {
                                    Icon(androidx.compose.material.icons.Icons.Default.Close, contentDescription = "Unfollow")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
