with open('app/src/main/java/com/example/xdownloader/ui/screens/SearchAuthorScreen.kt', 'r') as f:
    content = f.read()

content = content.replace("""                    scope.launch {
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
                    }""", """                    scope.launch {
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
                    }""")

with open('app/src/main/java/com/example/xdownloader/ui/screens/SearchAuthorScreen.kt', 'w') as f:
    f.write(content)
