with open('app/src/main/java/com/example/xdownloader/api/XApiClient.kt', 'r') as f:
    content = f.read()

# Fix missing closing brace
content = content.replace("""        } catch (e: Exception) {
            Log.e("XApiClient", "Error getting tweet", e)
            ApiResult.NetworkError(e.message ?: "網路錯誤 (Network Error)")
        }

    suspend fun verifyCredentials(): ApiResult<Boolean> = withContext(Dispatchers.IO) {""", """        } catch (e: Exception) {
            Log.e("XApiClient", "Error getting tweet", e)
            ApiResult.NetworkError(e.message ?: "網路錯誤 (Network Error)")
        }
    }

    suspend fun verifyCredentials(): ApiResult<Boolean> = withContext(Dispatchers.IO) {""")

# Fix missing returns inside .use{}
content = content.replace("""            client.newCall(request).execute().use { response ->""", """            return@withContext client.newCall(request).execute().use { response ->""")
content = content.replace("""            return@withContext return@withContext client.newCall(request).execute().use { response ->""", """            return@withContext client.newCall(request).execute().use { response ->""")

# Fix extra braces at end
lines = content.split('\n')
while lines[-1].strip() == "}":
    lines.pop()
lines.append("}")
content = "\n".join(lines)

with open('app/src/main/java/com/example/xdownloader/api/XApiClient.kt', 'w') as f:
    f.write(content)
