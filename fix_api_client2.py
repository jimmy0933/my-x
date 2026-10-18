import re

with open('app/src/main/java/com/example/xdownloader/api/XApiClient.kt', 'r') as f:
    content = f.read()

# Fix imports in wrong place
content = content.replace("package com.example.xdownloader.api\n\nimport android.content.Context\nimport android.util.Log\nimport okhttp3.Interceptor\nimport okhttp3.OkHttpClient\nimport okhttp3.Request\nimport okhttp3.Response\nimport java.util.concurrent.TimeUnit\nimport com.google.gson.Gson\nimport com.google.gson.JsonObject\nimport kotlinx.coroutines.Dispatchers\nimport kotlinx.coroutines.withContext\nimport java.net.URLEncoder\n\nsealed class ApiResult<out T> {\n    data class Success<out T>(val data: T) : ApiResult<T>()\n    data class Unauthorized(val message: String) : ApiResult<Nothing>()\n    data class RateLimited(val message: String) : ApiResult<Nothing>()\n    data class NetworkError(val message: String) : ApiResult<Nothing>()\n    data class ParsingError(val message: String) : ApiResult<Nothing>()\n}\n\nimport android.content.Context", """package com.example.xdownloader.api

import android.content.Context
import android.util.Log
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

sealed class ApiResult<out T> {
    data class Success<out T>(val data: T) : ApiResult<T>()
    data class Unauthorized(val message: String) : ApiResult<Nothing>()
    data class RateLimited(val message: String) : ApiResult<Nothing>()
    data class NetworkError(val message: String) : ApiResult<Nothing>()
    data class ParsingError(val message: String) : ApiResult<Nothing>()
}""")

content = re.sub(r"\}\s*import android\.content\.Context", "}", content)

if content.count("{") > content.count("}"):
    content += "\n}\n"

with open('app/src/main/java/com/example/xdownloader/api/XApiClient.kt', 'w') as f:
    f.write(content)
