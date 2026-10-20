package com.example.xdownloader.api

import com.example.xdownloader.data.MediaItem
import com.example.xdownloader.data.MediaType
import com.google.gson.JsonArray
import java.text.SimpleDateFormat
import java.util.Locale
import com.google.gson.JsonObject

object TweetParser {

    data class ParsedTweet(
        val tweetId: String,
        val text: String,
        val authorName: String,
        val authorScreenName: String,
        val authorAvatar: String,
        val createdAt: Long,
        val media: List<MediaItem>
    )

    fun parseMediaTimeline(json: JsonObject): Pair<List<ParsedTweet>, String?> {
        val tweets = mutableListOf<ParsedTweet>()
        var nextCursor: String? = null

        // If there are errors, we should check if data is null OR if it's missing the necessary nodes.
        if (json.has("errors")) {
            val errors = json.getAsJsonArray("errors")
            if (errors.size() > 0) {
                val msg = errors[0].asJsonObject.get("message")?.asString ?: "Unknown GraphQL Error"

                // If it's a permission error or some data is fundamentally missing (like data.user == null)
                val user = json.getAsJsonObject("data")?.getAsJsonObject("user")
                if (user == null || user.isJsonNull || user.keySet().isEmpty()) {
                    throw Exception("GraphQL Error: $msg")
                }
            }
        }

        try {
            val data = json.getAsJsonObject("data") ?: throw Exception("Missing data node")
            val user = data.getAsJsonObject("user") ?: throw Exception("Missing user node")
            val result = user.getAsJsonObject("result") ?: throw Exception("Missing result node (Possible rate limit or block)")

            // Note: If timeline_v2 is missing, but user/result are there, it might just be an empty user or we have partial failure.
            val timelineV2 = result.getAsJsonObject("timeline_v2")
            if (timelineV2 == null) {
                if (json.has("errors")) {
                    val errors = json.getAsJsonArray("errors")
                    val msg = if (errors.size() > 0) errors[0].asJsonObject.get("message")?.asString else "Unknown GraphQL Error"
                    throw Exception("GraphQL Error: $msg")
                } else {
                    return Pair(emptyList(), null)
                }
            }

            val timeline = timelineV2.getAsJsonObject("timeline") ?: return Pair(emptyList(), null)
            val instructions = timeline.getAsJsonArray("instructions") ?: return Pair(emptyList(), null)

            for (i in 0 until instructions.size()) {
                val inst = instructions[i].asJsonObject
                if (inst.get("type")?.asString == "TimelineAddEntries") {
                    val entries = inst.getAsJsonArray("entries") ?: continue
                    for (j in 0 until entries.size()) {
                        val entry = entries[j].asJsonObject
                        val entryId = entry.get("entryId")?.asString ?: ""

                        if (entryId.startsWith("tweet-")) {
                            val tweetResult = entry.getAsJsonObject("content")
                                ?.getAsJsonObject("itemContent")
                                ?.getAsJsonObject("tweet_results")
                                ?.getAsJsonObject("result")

                            if (tweetResult != null) {
                                val parsed = parseSingleTweetResult(tweetResult)
                                if (parsed != null && parsed.media.isNotEmpty()) {
                                    tweets.add(parsed)
                                }
                            }
                        } else if (entryId.startsWith("cursor-bottom")) {
                            nextCursor = entry.getAsJsonObject("content")
                                ?.get("value")?.asString
                        }
                    }
                }
            }
        } catch (e: Exception) {
            throw e // Throw so that calling code (ViewModel/ApiClient) knows it's an error and not an empty timeline
        }

        return Pair(tweets, nextCursor)
    }

    fun parseSingleTweetResult(tweetResult: JsonObject): ParsedTweet? {
        try {
            // Check for retweet
            var actualTweet = tweetResult
            if (tweetResult.get("__typename")?.asString == "TweetWithVisibilityResults") {
                 actualTweet = tweetResult.getAsJsonObject("tweet")
            }

            val legacy = actualTweet.getAsJsonObject("legacy") ?: return null
            val core = actualTweet.getAsJsonObject("core")?.getAsJsonObject("user_results")?.getAsJsonObject("result")
            val userLegacy = core?.getAsJsonObject("legacy") ?: return null

            val tweetId = legacy.get("id_str")?.asString ?: return null
            val text = legacy.get("full_text")?.asString ?: ""

            // Wait, created_at string to timestamp parsing might be needed. We'll use 0 for now.
                        val createdAtStr = legacy.get("created_at")?.asString
            var createdAt = 0L
            if (createdAtStr != null) {
                try {
                    val sdf = SimpleDateFormat("EEE MMM dd HH:mm:ss Z yyyy", Locale.US)
                    val date = sdf.parse(createdAtStr)
                    if (date != null) createdAt = date.time
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val authorName = userLegacy.get("name")?.asString ?: ""
            val authorScreenName = userLegacy.get("screen_name")?.asString ?: ""
            val authorAvatar = userLegacy.get("profile_image_url_https")?.asString ?: ""

            val mediaEntities = legacy.getAsJsonObject("extended_entities")?.getAsJsonArray("media") ?: JsonArray()
            val mediaList = mutableListOf<MediaItem>()

            for (i in 0 until mediaEntities.size()) {
                val media = mediaEntities[i].asJsonObject
                val typeStr = media.get("type")?.asString
                val mediaUrl = media.get("media_url_https")?.asString ?: continue

                if (typeStr == "photo") {
                    mediaList.add(MediaItem(
                        id = "${tweetId}_$i",
                        url = "$mediaUrl?name=orig",
                        thumbnailUrl = "$mediaUrl?name=small",
                        type = MediaType.IMAGE,
                        quality = "Original"
                    ))
                } else if (typeStr == "video" || typeStr == "animated_gif") {
                    val videoInfo = media.getAsJsonObject("video_info")
                    val variants = videoInfo?.getAsJsonArray("variants")

                    var bestVideoUrl = ""
                    var highestBitrate = -1

                    if (variants != null) {
                        for (v in 0 until variants.size()) {
                            val variant = variants[v].asJsonObject
                            val contentType = variant.get("content_type")?.asString
                            if (contentType == "video/mp4" || contentType == "application/x-mpegURL") {
                                val bitrate = variant.get("bitrate")?.asInt ?: 0
                                if (bitrate > highestBitrate) {
                                    highestBitrate = bitrate
                                    bestVideoUrl = variant.get("url")?.asString ?: ""
                                }
                            }
                        }
                    }

                    if (bestVideoUrl.isNotEmpty()) {
                        mediaList.add(MediaItem(
                            id = "${tweetId}_$i",
                            url = bestVideoUrl,
                            thumbnailUrl = mediaUrl,
                            type = MediaType.VIDEO,
                            quality = "HD"
                        ))
                    }
                }
            }

            return ParsedTweet(tweetId, text, authorName, authorScreenName, authorAvatar, createdAt, mediaList)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
