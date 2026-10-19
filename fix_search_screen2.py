with open('app/src/main/java/com/example/xdownloader/ui/screens/SearchAuthorScreen.kt', 'r') as f:
    content = f.read()

content = content.replace("""                        val userId = apiClient.getUserIdByScreenName(cleanQuery)
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
                            Toast.makeText(context, "已新增追蹤 (Followed)", Toast.LENGTH_SHORT).show()
                            query = ""
                        } else {
                            Toast.makeText(context, "找不到該作者 (Author not found)", Toast.LENGTH_SHORT).show()
                        }""", """                        val result = apiClient.getUserIdByScreenName(cleanQuery)
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
                            Toast.makeText(context, "已新增追蹤 (Followed)", Toast.LENGTH_SHORT).show()
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
                        }""")

content = content.replace("""                    scope.launch {
                        isSearching = true
                        val id = tweetUrl.split("/").lastOrNull()?.split("?")?.firstOrNull()
                        if (id != null) {
                            val json = apiClient.getTweet(id)
                            if (json != null) {
                                val result = json.getAsJsonObject("data").getAsJsonObject("tweetResult").getAsJsonObject("result")
                                val (parsed, _) = TweetParser.parseMediaTimeline(result)
                                tweetData = parsed.firstOrNull()
                            }
                        }
                        isSearching = false
                    }""", """                    scope.launch {
                        isSearching = true
                        val id = tweetUrl.split("/").lastOrNull()?.split("?")?.firstOrNull()
                        if (id != null) {
                            when (val result = apiClient.getTweet(id)) {
                                is ApiResult.Success -> {
                                    val json = result.data
                                    try {
                                        val resultObj = json.getAsJsonObject("data").getAsJsonObject("tweetResult").getAsJsonObject("result")
                                        val (parsed, _) = TweetParser.parseMediaTimeline(resultObj)
                                        tweetData = parsed.firstOrNull()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "解析貼文失敗", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                is ApiResult.Unauthorized -> Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                                is ApiResult.RateLimited -> Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                                is ApiResult.NetworkError -> Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                                is ApiResult.ParsingError -> Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                            }
                        }
                        isSearching = false
                    }""")

with open('app/src/main/java/com/example/xdownloader/ui/screens/SearchAuthorScreen.kt', 'w') as f:
    f.write(content)
