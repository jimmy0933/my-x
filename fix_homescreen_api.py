with open('app/src/main/java/com/example/xdownloader/ui/screens/HomeScreen.kt', 'r') as f:
    content = f.read()

content = content.replace("import com.example.xdownloader.api.XApiClient", "import com.example.xdownloader.api.XApiClient\nimport com.example.xdownloader.api.ApiResult")

content = content.replace("""        isLoading = true
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
        }""", """        suspend fun loadTweets() {
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

                    if (allTweets.isEmpty() && partialFailureMsg != null) {
                        errorMessage = partialFailureMsg
                    } else if (partialFailureMsg != null) {
                        android.widget.Toast.makeText(context, "部分載入失敗: $partialFailureMsg", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                errorMessage = "發生非預期錯誤: ${e.message}"
            } finally {
                isLoading = false
            }
        }
        loadTweets()""")

content = content.replace("""                Text(errorMessage!!, modifier = Modifier.align(Alignment.Center).padding(16.dp))""", """                Column(modifier = Modifier.align(Alignment.Center).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
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
                }""")

with open('app/src/main/java/com/example/xdownloader/ui/screens/HomeScreen.kt', 'w') as f:
    f.write(content)
