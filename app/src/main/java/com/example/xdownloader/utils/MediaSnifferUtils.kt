package com.example.xdownloader.utils

import com.example.xdownloader.data.MediaItem
import com.example.xdownloader.data.MediaType
import java.util.UUID

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
     * Checks if a URL is a downloadable direct MP4 or HLS playlist video URL.
     * Excludes chunk segments (.ts, .m4s) and initialization fragments (init.mp4).
     */
    fun isVideoUrl(url: String): Boolean {
        if (!url.contains("video.twimg.com") && !url.contains(".mp4") && !url.contains(".m3u8")) return false
        if (url.endsWith(".ts") || url.contains(".ts?")) return false
        if (url.contains(".m4s", ignoreCase = true) || url.contains("init.mp4", ignoreCase = true)) return false
        if (url.contains(".png") || url.contains(".jpg") || url.contains(".jpeg")) return false
        return true
    }

    fun isHlsUrl(url: String): Boolean {
        return url.contains(".m3u8", ignoreCase = true)
    }

    /**
     * Extracts high quality video stream URL. Prefers direct MP4 streams over HLS playlists if available.
     */
    fun extractBestVideoUrl(urls: List<String>): String? {
        val mp4s = urls.filter { isVideoUrl(it) && !isHlsUrl(it) }
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

        val hlsPlaylists = urls.filter { isVideoUrl(it) && isHlsUrl(it) }
        if (hlsPlaylists.isNotEmpty()) {
            return hlsPlaylists.first()
        }

        return null
    }

    /**
     * Generates a unique filename using timestamp, media ID, and UUID
     */
    fun generateUniqueFileName(item: MediaItem): String {
        val extension = if (item.type == MediaType.IMAGE) {
            if (item.url.contains("format=png")) ".png" else ".jpg"
        } else {
            ".mp4"
        }
        val uuidSegment = UUID.randomUUID().toString().substring(0, 8)
        return "X_${System.currentTimeMillis()}_${uuidSegment}$extension"
    }

    private fun String.toIntOrDefault(default: Int): Int = toIntOrNull() ?: default
}
