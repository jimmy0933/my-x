with open('app/src/main/java/com/example/xdownloader/ui/screens/SearchAuthorScreen.kt', 'r') as f:
    content = f.read()

content = content.replace("import com.example.xdownloader.api.XApiClient", "import com.example.xdownloader.api.XApiClient\nimport com.example.xdownloader.api.ApiResult")

content = content.replace("""                    val id = apiClient.getUserIdByScreenName(username)
                    if (id != null) {
                        authorId = id
                        val mediaJson = apiClient.getUserMedia(id)""", """                    val result = apiClient.getUserIdByScreenName(username)
                    if (result is ApiResult.Success) {
                        val id = result.data
                        authorId = id
                        val mediaResult = apiClient.getUserMedia(id)
                        val mediaJson = if (mediaResult is ApiResult.Success) mediaResult.data else null""")

with open('app/src/main/java/com/example/xdownloader/ui/screens/SearchAuthorScreen.kt', 'w') as f:
    f.write(content)
