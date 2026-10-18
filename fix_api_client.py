with open('app/src/main/java/com/example/xdownloader/api/XApiClient.kt', 'r') as f:
    content = f.read()

content = content.replace("""    }
}

    suspend fun verifyCredentials(): ApiResult<Boolean> = withContext(Dispatchers.IO) {""", """
    suspend fun verifyCredentials(): ApiResult<Boolean> = withContext(Dispatchers.IO) {""")

with open('app/src/main/java/com/example/xdownloader/api/XApiClient.kt', 'w') as f:
    f.write(content)
