package com.example.xdownloader.api

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
}

class XApiClient(private val context: Context) {
    private val prefs = context.getSharedPreferences("x_auth", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val authToken = prefs.getString("auth_token", "") ?: ""
            val ct0 = prefs.getString("ct0", "") ?: ""

            val requestBuilder = chain.request().newBuilder()
                .header("Authorization", "Bearer AAAAAAAAAAAAAAAAAAAAANRILgAAAAAAnNwIzUejRCOuH5E6I8xnZz4puTs%3D1Zv7ttfk8LF81IUq16cHjhLTvJu4FA33AGWWjCpTnA")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("x-twitter-active-user", "yes")
                .header("x-twitter-client-language", "en")

            if (ct0.isNotEmpty()) {
                requestBuilder.header("x-csrf-token", ct0)
            }
            if (authToken.isNotEmpty() || ct0.isNotEmpty()) {
                requestBuilder.header("Cookie", "auth_token=$authToken; ct0=$ct0")
            }

            chain.proceed(requestBuilder.build())
        }
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun isConfigured(): Boolean {
        return prefs.getString("auth_token", "")?.isNotEmpty() == true &&
               prefs.getString("ct0", "")?.isNotEmpty() == true
    }

    suspend fun getUserIdByScreenName(screenName: String): ApiResult<String> = withContext(Dispatchers.IO) {
        try {
            val variables = """{"screen_name":"$screenName","withSafetyModeUserFields":true}"""
            val features = """{"hidden_profile_likes_enabled":true,"hidden_profile_subscriptions_enabled":true,"responsive_web_graphql_exclude_directive_enabled":true,"verified_phone_label_enabled":false,"subscriptions_verification_info_is_identity_verified_enabled":true,"subscriptions_verification_info_verified_since_enabled":true,"highlights_tweets_tab_ui_enabled":true,"creator_subscriptions_tweet_preview_api_enabled":true,"responsive_web_graphql_skip_user_profile_image_extensions_enabled":false,"responsive_web_graphql_timeline_navigation_graphql_enabled":true}"""

            val url = "https://twitter.com/i/api/graphql/sbs1yV0KkXQ2hQ6-vSj4HQ/UserByScreenName" +
                    "?variables=${URLEncoder.encode(variables, "UTF-8")}" +
                    "&features=${URLEncoder.encode(features, "UTF-8")}"

            val request = Request.Builder().url(url).build()
            return@withContext client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext when (response.code) {
                        401 -> ApiResult.Unauthorized("未授權，請檢查憑證 (Unauthorized)")
                        403 -> ApiResult.Unauthorized("拒絕存取，憑證可能失效 (Forbidden)")
                        429 -> ApiResult.RateLimited("請求過於頻繁，請稍後再試 (Too Many Requests)")
                        else -> ApiResult.NetworkError("伺服器錯誤: ${response.code} (Server Error)")
                    }
                }

                val bodyStr = response.body?.string() ?: return@withContext ApiResult.ParsingError("空的回應 (Empty Response)")
                val json = try {
                    gson.fromJson(bodyStr, JsonObject::class.java)
                } catch (e: Exception) {
                    return@withContext ApiResult.ParsingError("JSON解析錯誤: ${e.message}")
                }

                val restId = json.getAsJsonObject("data")
                    ?.getAsJsonObject("user")
                    ?.getAsJsonObject("result")
                    ?.get("rest_id")?.asString

                if (restId != null) ApiResult.Success(restId) else ApiResult.ParsingError("找不到使用者 ID (User ID not found)")
            }
        } catch (e: Exception) {
            Log.e("XApiClient", "Error getting user ID", e)
            ApiResult.NetworkError(e.message ?: "網路錯誤 (Network Error)")
        }
    }

    suspend fun getUserMedia(userId: String, cursor: String? = null): ApiResult<JsonObject> = withContext(Dispatchers.IO) {
        try {
            val variables = buildString {
                append("""{"userId":"$userId","count":20,"includePromotedContent":false,"withClientEventToken":false,"withBirdwatchNotes":false,"withVoice":true,"withV2Timeline":true""")
                if (cursor != null) append(""","cursor":"$cursor"""")
                append("}")
            }
            val features = """{"responsive_web_graphql_exclude_directive_enabled":true,"verified_phone_label_enabled":false,"creator_subscriptions_tweet_preview_api_enabled":true,"responsive_web_graphql_timeline_navigation_graphql_enabled":true,"responsive_web_graphql_skip_user_profile_image_extensions_enabled":false,"c9s_tweet_anatomy_moderator_badge_enabled":true,"tweetypie_unmention_optimization_enabled":true,"responsive_web_edit_tweet_api_enabled":true,"graphql_is_translatable_rweb_tweet_is_translatable_enabled":true,"view_counts_everywhere_api_enabled":true,"longform_notetweets_consumption_enabled":true,"responsive_web_twitter_article_tweet_consumption_enabled":true,"tweet_awards_web_tipping_enabled":false,"freedom_of_speech_not_reach_fetch_enabled":true,"standardized_nudges_misinfo":true,"tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled":true,"rweb_video_timestamps_enabled":true,"longform_notetweets_rich_text_read_enabled":true,"longform_notetweets_inline_media_enabled":true,"responsive_web_media_download_video_enabled":false,"responsive_web_enhance_cards_enabled":false}"""

            val url = "https://twitter.com/i/api/graphql/1ofjyCpc4GgT1I7I1EwA8w/UserMedia" +
                    "?variables=${URLEncoder.encode(variables, "UTF-8")}" +
                    "&features=${URLEncoder.encode(features, "UTF-8")}"

            val request = Request.Builder().url(url).build()
            return@withContext client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext when (response.code) {
                        401 -> ApiResult.Unauthorized("未授權，請檢查憑證 (Unauthorized)")
                        403 -> ApiResult.Unauthorized("拒絕存取，憑證可能失效 (Forbidden)")
                        429 -> ApiResult.RateLimited("請求過於頻繁，請稍後再試 (Too Many Requests)")
                        else -> ApiResult.NetworkError("伺服器錯誤: ${response.code} (Server Error)")
                    }
                }

                val bodyStr = response.body?.string() ?: return@withContext ApiResult.ParsingError("空的回應 (Empty Response)")
                try {
                    val jsonObj = gson.fromJson(bodyStr, JsonObject::class.java)
                    if (jsonObj.has("errors")) {
                        val errors = jsonObj.getAsJsonArray("errors")
                        val msg = if (errors.size() > 0) errors[0].asJsonObject.get("message")?.asString else "Unknown GraphQL Error"
                        val user = jsonObj.getAsJsonObject("data")?.getAsJsonObject("user")
                        if (user == null || user.isJsonNull || user.keySet().isEmpty()) {
                            ApiResult.ParsingError("GraphQL Error: $msg")
                        } else {
                            // Data exists partially, we can try to pass it to the parser which has stricter checks
                            ApiResult.Success(jsonObj)
                        }
                    } else {
                        ApiResult.Success(jsonObj)
                    }
                } catch (e: Exception) {
                    ApiResult.ParsingError("JSON解析錯誤: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("XApiClient", "Error getting user media", e)
            ApiResult.NetworkError(e.message ?: "網路錯誤 (Network Error)")
        }
    }

    suspend fun getTweet(tweetId: String): ApiResult<JsonObject> = withContext(Dispatchers.IO) {
        try {
            val variables = """{"tweetId":"$tweetId","withCommunity":false,"includePromotedContent":false,"withVoice":false}"""
            val features = """{"creator_subscriptions_tweet_preview_api_enabled":true,"c9s_tweet_anatomy_moderator_badge_enabled":true,"tweetypie_unmention_optimization_enabled":true,"responsive_web_edit_tweet_api_enabled":true,"graphql_is_translatable_rweb_tweet_is_translatable_enabled":true,"view_counts_everywhere_api_enabled":true,"longform_notetweets_consumption_enabled":true,"responsive_web_twitter_article_tweet_consumption_enabled":true,"tweet_awards_web_tipping_enabled":false,"freedom_of_speech_not_reach_fetch_enabled":true,"standardized_nudges_misinfo":true,"tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled":true,"longform_notetweets_rich_text_read_enabled":true,"longform_notetweets_inline_media_enabled":true,"responsive_web_graphql_exclude_directive_enabled":true,"verified_phone_label_enabled":false,"responsive_web_media_download_video_enabled":false,"responsive_web_graphql_skip_user_profile_image_extensions_enabled":false,"responsive_web_graphql_timeline_navigation_graphql_enabled":true,"responsive_web_enhance_cards_enabled":false}"""

            val url = "https://twitter.com/i/api/graphql/Qh9J6P2-8JWeN7d30o7E7w/TweetResultByRestId" +
                    "?variables=${URLEncoder.encode(variables, "UTF-8")}" +
                    "&features=${URLEncoder.encode(features, "UTF-8")}"

            val request = Request.Builder().url(url).build()
            return@withContext client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext when (response.code) {
                        401 -> ApiResult.Unauthorized("未授權，請檢查憑證 (Unauthorized)")
                        403 -> ApiResult.Unauthorized("拒絕存取，憑證可能失效 (Forbidden)")
                        429 -> ApiResult.RateLimited("請求過於頻繁，請稍後再試 (Too Many Requests)")
                        else -> ApiResult.NetworkError("伺服器錯誤: ${response.code} (Server Error)")
                    }
                }

                val bodyStr = response.body?.string() ?: return@withContext ApiResult.ParsingError("空的回應 (Empty Response)")
                try {
                    val jsonObj = gson.fromJson(bodyStr, JsonObject::class.java)
                    if (jsonObj.has("errors")) {
                        val errors = jsonObj.getAsJsonArray("errors")
                        val msg = if (errors.size() > 0) errors[0].asJsonObject.get("message")?.asString else "Unknown GraphQL Error"
                        val user = jsonObj.getAsJsonObject("data")?.getAsJsonObject("user")
                        if (user == null || user.isJsonNull || user.keySet().isEmpty()) {
                            ApiResult.ParsingError("GraphQL Error: $msg")
                        } else {
                            // Data exists partially, we can try to pass it to the parser which has stricter checks
                            ApiResult.Success(jsonObj)
                        }
                    } else {
                        ApiResult.Success(jsonObj)
                    }
                } catch (e: Exception) {
                    ApiResult.ParsingError("JSON解析錯誤: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("XApiClient", "Error getting tweet", e)
            ApiResult.NetworkError(e.message ?: "網路錯誤 (Network Error)")
        }
    }

    suspend fun verifyCredentials(): ApiResult<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = "https://twitter.com/i/api/1.1/account/verify_credentials.json"
            val request = Request.Builder().url(url).build()
            return@withContext client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    ApiResult.Success(true)
                } else {
                    when (response.code) {
                        401, 403 -> ApiResult.Unauthorized("驗證失敗，憑證無效")
                        429 -> ApiResult.RateLimited("請求頻繁")
                        else -> ApiResult.NetworkError("伺服器錯誤 ${response.code}")
                    }
                }
            }
        } catch (e: Exception) {
            ApiResult.NetworkError(e.message ?: "網路錯誤")
        }
    }
}
