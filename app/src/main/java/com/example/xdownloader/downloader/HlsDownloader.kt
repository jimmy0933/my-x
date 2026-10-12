package com.example.xdownloader.downloader

import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

object HlsDownloader {

    data class HlsVariant(
        val url: String,
        val bandwidth: Long,
        val resolution: String
    )

    data class MediaPlaylist(
        val initSegmentUrl: String?,
        val segmentUrls: List<String>
    )

    fun resolveMediaPlaylistUrl(masterUrl: String): String {
        try {
            val content = fetchUrlText(masterUrl) ?: return masterUrl
            if (!content.contains("#EXT-X-STREAM-INF")) {
                // Already a media playlist
                return masterUrl
            }

            val variants = parseMasterPlaylist(content, masterUrl)
            val bestVariant = variants.maxByOrNull { it.bandwidth }
            return bestVariant?.url ?: masterUrl
        } catch (e: Exception) {
            e.printStackTrace()
            return masterUrl
        }
    }

    fun parseMasterPlaylist(content: String, baseUrl: String): List<HlsVariant> {
        val variants = mutableListOf<HlsVariant>()
        val lines = content.lines()
        var currentBandwidth = 0L
        var currentResolution = ""

        for (i in lines.indices) {
            val line = lines[i].trim()
            if (line.startsWith("#EXT-X-STREAM-INF:")) {
                currentBandwidth = parseAttribute(line, "BANDWIDTH")?.toLongOrNull() ?: 0L
                currentResolution = parseAttribute(line, "RESOLUTION") ?: ""
            } else if (line.isNotEmpty() && !line.startsWith("#")) {
                val variantUrl = resolveRelativeUrl(baseUrl, line)
                variants.add(HlsVariant(variantUrl, currentBandwidth, currentResolution))
                currentBandwidth = 0L
                currentResolution = ""
            }
        }
        return variants
    }

    fun parseMediaPlaylist(content: String, baseUrl: String): MediaPlaylist {
        var initSegmentUrl: String? = null
        val segmentUrls = mutableListOf<String>()

        for (line in content.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXT-X-MAP:")) {
                val uri = parseAttribute(trimmed, "URI")
                if (!uri.isNullOrEmpty()) {
                    initSegmentUrl = resolveRelativeUrl(baseUrl, uri)
                }
            } else if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                segmentUrls.add(resolveRelativeUrl(baseUrl, trimmed))
            }
        }
        return MediaPlaylist(initSegmentUrl, segmentUrls)
    }

    fun downloadHlsStream(
        playlistUrl: String,
        outputStream: OutputStream,
        onProgress: ((downloadedSegments: Int, totalSegments: Int) -> Unit)? = null
    ): Boolean {
        val mediaPlaylistUrl = resolveMediaPlaylistUrl(playlistUrl)
        val playlistContent = fetchUrlText(mediaPlaylistUrl) ?: return false
        val mediaPlaylist = parseMediaPlaylist(playlistContent, mediaPlaylistUrl)

        val totalSegments = (if (mediaPlaylist.initSegmentUrl != null) 1 else 0) + mediaPlaylist.segmentUrls.size
        var downloadedCount = 0

        // 1. Download init segment if present
        mediaPlaylist.initSegmentUrl?.let { initUrl ->
            val success = downloadAndWriteUrl(initUrl, outputStream)
            if (!success) return false
            downloadedCount++
            onProgress?.invoke(downloadedCount, totalSegments)
        }

        // 2. Download media segments in order
        for (segmentUrl in mediaPlaylist.segmentUrls) {
            val success = downloadAndWriteUrl(segmentUrl, outputStream)
            if (!success) return false
            downloadedCount++
            onProgress?.invoke(downloadedCount, totalSegments)
        }

        return downloadedCount > 0
    }

    fun fetchUrlText(urlString: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.connect()
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun downloadAndWriteUrl(urlString: String, outputStream: OutputStream): Boolean {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.connect()
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.use { input ->
                    input.copyTo(outputStream)
                }
                true
            } else false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            connection?.disconnect()
        }
    }

    fun resolveRelativeUrl(baseUrlString: String, relativeUrl: String): String {
        if (relativeUrl.startsWith("http://") || relativeUrl.startsWith("https://")) {
            return relativeUrl
        }
        return try {
            val baseUrl = URL(baseUrlString)
            URL(baseUrl, relativeUrl).toString()
        } catch (e: Exception) {
            relativeUrl
        }
    }

    private fun parseAttribute(line: String, attrName: String): String? {
        val pattern = Regex("$attrName=(?:\"([^\"]*)\"|([^,\\s]+))")
        val match = pattern.find(line) ?: return null
        return match.groupValues[1].ifEmpty { match.groupValues[2] }
    }
}
