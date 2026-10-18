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
import com.example.xdownloader.database.AppDatabase
import com.example.xdownloader.database.FollowEntity
import kotlinx.coroutines.launch

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
                        val userId = apiClient.getUserIdByScreenName(cleanQuery)
                        if (userId != null) {
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
                        } else {
                            Toast.makeText(context, "找不到該作者", Toast.LENGTH_SHORT).show()
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
                        val tweetJson = apiClient.getTweet(tweetId)
                        if (tweetJson != null) {
                            val result = tweetJson.getAsJsonObject("data")
                                ?.getAsJsonObject("tweetResult")
                                ?.getAsJsonObject("result")
                            if (result != null) {
                                tweetData = TweetParser.parseSingleTweetResult(result)
                            } else {
                                Toast.makeText(context, "無法解析貼文", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "無法取得貼文", Toast.LENGTH_SHORT).show()
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
        }
    }
}
