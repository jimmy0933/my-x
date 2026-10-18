with open('app/src/main/java/com/example/xdownloader/api/XApiClient.kt', 'r') as f:
    content = f.read()

content = content.replace("return@withContext client.newCall(request).execute().use { response ->", "client.newCall(request).execute().use { response ->")
content = content.replace("""        } catch (e: Exception) {
            Log.e("XApiClient", "Error getting user ID", e)
            ApiResult.NetworkError(e.message ?: "網路錯誤 (Network Error)")
        }
        return@withContext ApiResult.NetworkError("Unknown Error")""", """        } catch (e: Exception) {
            Log.e("XApiClient", "Error getting user ID", e)
            ApiResult.NetworkError(e.message ?: "網路錯誤 (Network Error)")
        }""")

content = content.replace("""        } catch (e: Exception) {
            Log.e("XApiClient", "Error getting user media", e)
            ApiResult.NetworkError(e.message ?: "網路錯誤 (Network Error)")
        }
        return@withContext ApiResult.NetworkError("Unknown Error")""", """        } catch (e: Exception) {
            Log.e("XApiClient", "Error getting user media", e)
            ApiResult.NetworkError(e.message ?: "網路錯誤 (Network Error)")
        }""")

content = content.replace("""        } catch (e: Exception) {
            Log.e("XApiClient", "Error getting tweet", e)
            ApiResult.NetworkError(e.message ?: "網路錯誤 (Network Error)")
        }
        return@withContext ApiResult.NetworkError("Unknown Error")""", """        } catch (e: Exception) {
            Log.e("XApiClient", "Error getting tweet", e)
            ApiResult.NetworkError(e.message ?: "網路錯誤 (Network Error)")
        }""")

content = content.replace("""        } catch (e: Exception) {
            ApiResult.NetworkError(e.message ?: "網路錯誤")
        }
        return@withContext ApiResult.NetworkError("Unknown Error")""", """        } catch (e: Exception) {
            ApiResult.NetworkError(e.message ?: "網路錯誤")
        }""")

with open('app/src/main/java/com/example/xdownloader/api/XApiClient.kt', 'w') as f:
    f.write(content)
