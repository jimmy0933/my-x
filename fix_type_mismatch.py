with open('app/src/main/java/com/example/xdownloader/api/XApiClient.kt', 'r') as f:
    content = f.read()

content = content.replace("""        } catch (e: Exception) {
            Log.e("XApiClient", "Error getting user media", e)
            ApiResult.NetworkError(e.message ?: "網路錯誤 (Network Error)")
        }""", """        } catch (e: Exception) {
            Log.e("XApiClient", "Error getting user media", e)
            ApiResult.NetworkError(e.message ?: "網路錯誤 (Network Error)")
        }
        return@withContext ApiResult.NetworkError("Unknown Error")""")

content = content.replace("""        } catch (e: Exception) {
            Log.e("XApiClient", "Error getting tweet", e)
            ApiResult.NetworkError(e.message ?: "網路錯誤 (Network Error)")
        }""", """        } catch (e: Exception) {
            Log.e("XApiClient", "Error getting tweet", e)
            ApiResult.NetworkError(e.message ?: "網路錯誤 (Network Error)")
        }
        return@withContext ApiResult.NetworkError("Unknown Error")""")

# Fix the early returns that don't return correctly
content = content.replace("""            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->""", """            val request = Request.Builder().url(url).build()
            return@withContext client.newCall(request).execute().use { response ->""")

content = content.replace("""    suspend fun verifyCredentials(): ApiResult<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = "https://twitter.com/i/api/1.1/account/verify_credentials.json"
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->""", """    suspend fun verifyCredentials(): ApiResult<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = "https://twitter.com/i/api/1.1/account/verify_credentials.json"
            val request = Request.Builder().url(url).build()
            return@withContext client.newCall(request).execute().use { response ->""")

with open('app/src/main/java/com/example/xdownloader/api/XApiClient.kt', 'w') as f:
    f.write(content)
