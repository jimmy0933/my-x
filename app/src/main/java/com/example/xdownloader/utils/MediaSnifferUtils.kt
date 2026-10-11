package com.example.xdownloader.utils

import com.example.xdownloader.data.MediaItem
import com.example.xdownloader.data.MediaType
import java.net.URLDecoder

object MediaSnifferUtils {

    /**
     * Standardizes X image URLs to high quality orig/large format
     * Example input: https://pbs.twimg.com/media/G123456?format=jpg&name=900x900
     * Output: https://pbs.twimg.com/media/G123456?format=jpg&name=orig
     */
    fun sanitizeImageUrl(url: String): String {
        if (!url.contains("pbs.twimg.com/media/")) return url

        return if (url.contains("name=")) {
            url.replace(Regex("name=[a-zA-Z0-9_]+"), "name=orig")
        } else if (url.contains("?")) {
            "$url&name=orig"
        } else {
            "$url?format=jpg&name=orig"
        }
    }

    /**
     * Filters out avatars, UI icons, and non-content images
     */
    fun isContentImage(url: String): Boolean {
        if (!url.contains("pbs.twimg.com/media/")) return false
        if (url.contains("profile_images") || url.contains("emoji") || url.contains("card_img")) return false
        return true
    }

    /**
     * Checks if a URL is a valid X video or media stream URL
     */
    fun isVideoUrl(url: String): Boolean {
        return (url.contains("video.twimg.com") || url.contains(".mp4") || url.contains(".m3u8")) &&
               !url.contains(".png") && !url.contains(".jpg") && !url.contains(".jpeg")
    }

    /**
     * Extracts high quality video stream MP4 URL from HLS or playlist variants if available
     */
    fun extractBestVideoUrl(urls: List<String>): String? {
        val mp4s = urls.filter { it.contains(".mp4") }
        if (mp4s.isNotEmpty()) {
            // Sort by resolution hint in URL if present (e.g. /1280x720/)
            return mp4s.maxByOrNull { url ->
                val match = Regex("(\\d+)x(\\d+)").find(url)
                if (match != null) {
                    val (w, h) = match.destructured
                    w.toIntOrDefault(0) * h.toIntOrDefault(0)
                } else 0
            }
        }
        return urls.firstOrNull { isVideoUrl(it) }
    }

    private fun String.toIntOrDefault(default: Int): Int = toIntOrNull() ?: default
}
