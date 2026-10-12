package com.example.xdownloader.downloader

import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

object HlsDownloader {

    enum class StreamFormat(val extension: String, val mimeType: String) {
        MP4(".mp4", "video/mp4"),
        TS(".ts", "video/mp2t")
    }

    data class AudioTrack(
        val groupId: String,
        val name: String,
        val url: String
    )

    data class HlsVariant(
        val url: String,
        val bandwidth: Long,
        val resolution: String,
        val audioGroupId: String? = null
    )

    data class Segment(
        val url: String,
        val byteRangeLength: Long? = null,
        val byteRangeOffset: Long? = null
    )

    data class MediaPlaylist(
        val initSegmentUrl: String?,
        val initByteRangeLength: Long? = null,
        val initByteRangeOffset: Long? = null,
        val segments: List<Segment>,
        val format: StreamFormat
    )

    data class ResolvedStream(
        val videoPlaylistUrl: String,
        val audioPlaylistUrl: String?,
        val format: StreamFormat
    )

    fun resolveStream(masterUrl: String): ResolvedStream {
        try {
            val content = fetchUrlText(masterUrl) ?: return ResolvedStream(masterUrl, null, StreamFormat.MP4)
            if (!content.contains("#EXT-X-STREAM-INF")) {
                // Direct media playlist
                val mediaPlaylist = parseMediaPlaylist(content, masterUrl)
                return ResolvedStream(masterUrl, null, mediaPlaylist.format)
            }

            val audioTracks = parseAudioTracks(content, masterUrl)
            val variants = parseMasterPlaylist(content, masterUrl)
            val bestVariant = variants.maxByOrNull { it.bandwidth }

            val videoUrl = bestVariant?.url ?: masterUrl
            val audioUrl = bestVariant?.audioGroupId?.let { audioTracks[it]?.url }

            val videoContent = fetchUrlText(videoUrl)
            val format = if (videoContent != null) {
                parseMediaPlaylist(videoContent, videoUrl).format
            } else {
                StreamFormat.MP4
            }

            return ResolvedStream(videoUrl, audioUrl, format)
        } catch (e: Exception) {
            e.printStackTrace()
            return ResolvedStream(masterUrl, null, StreamFormat.MP4)
        }
    }

    fun parseAudioTracks(content: String, baseUrl: String): Map<String, AudioTrack> {
        val audioTracks = mutableMapOf<String, AudioTrack>()
        for (line in content.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXT-X-MEDIA:") && trimmed.contains("TYPE=AUDIO")) {
                val groupId = parseAttribute(trimmed, "GROUP-ID")
                val name = parseAttribute(trimmed, "NAME") ?: "Audio"
                val uri = parseAttribute(trimmed, "URI")
                if (!groupId.isNullOrEmpty() && !uri.isNullOrEmpty()) {
                    val resolvedUrl = resolveRelativeUrl(baseUrl, uri)
                    audioTracks[groupId] = AudioTrack(groupId, name, resolvedUrl)
                }
            }
        }
        return audioTracks
    }

    fun parseMasterPlaylist(content: String, baseUrl: String): List<HlsVariant> {
        val variants = mutableListOf<HlsVariant>()
        val lines = content.lines()
        var currentBandwidth = 0L
        var currentResolution = ""
        var currentAudioGroupId: String? = null

        for (i in lines.indices) {
            val line = lines[i].trim()
            if (line.startsWith("#EXT-X-STREAM-INF:")) {
                currentBandwidth = parseAttribute(line, "BANDWIDTH")?.toLongOrNull() ?: 0L
                currentResolution = parseAttribute(line, "RESOLUTION") ?: ""
                currentAudioGroupId = parseAttribute(line, "AUDIO")
            } else if (line.isNotEmpty() && !line.startsWith("#")) {
                val variantUrl = resolveRelativeUrl(baseUrl, line)
                variants.add(HlsVariant(variantUrl, currentBandwidth, currentResolution, currentAudioGroupId))
                currentBandwidth = 0L
                currentResolution = ""
                currentAudioGroupId = null
            }
        }
        return variants
    }

    fun parseMediaPlaylist(content: String, baseUrl: String): MediaPlaylist {
        var initSegmentUrl: String? = null
        var initByteRangeLength: Long? = null
        var initByteRangeOffset: Long? = null

        val segments = mutableListOf<Segment>()
        var pendingByteRangeLength: Long? = null
        var pendingByteRangeOffset: Long? = null
        var lastByteRangeOffset: Long = 0L

        var isTs = false
        var isMp4 = false

        for (line in content.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXT-X-MAP:")) {
                isMp4 = true
                val uri = parseAttribute(trimmed, "URI")
                if (!uri.isNullOrEmpty()) {
                    initSegmentUrl = resolveRelativeUrl(baseUrl, uri)
                }
                val byteRangeAttr = parseAttribute(trimmed, "BYTERANGE")
                if (!byteRangeAttr.isNullOrEmpty()) {
                    val (len, offset) = parseByteRangeValue(byteRangeAttr, 0L)
                    initByteRangeLength = len
                    initByteRangeOffset = offset
                }
            } else if (trimmed.startsWith("#EXT-X-BYTERANGE:")) {
                val value = trimmed.substringAfter("#EXT-X-BYTERANGE:").trim()
                val (len, offset) = parseByteRangeValue(value, lastByteRangeOffset)
                pendingByteRangeLength = len
                pendingByteRangeOffset = offset
                lastByteRangeOffset = offset + len
            } else if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                val segmentUrl = resolveRelativeUrl(baseUrl, trimmed)
                if (segmentUrl.contains(".ts", ignoreCase = true)) {
                    isTs = true
                } else if (segmentUrl.contains(".m4s", ignoreCase = true) || segmentUrl.contains(".mp4", ignoreCase = true)) {
                    isMp4 = true
                }

                segments.add(
                    Segment(
                        url = segmentUrl,
                        byteRangeLength = pendingByteRangeLength,
                        byteRangeOffset = pendingByteRangeOffset
                    )
                )

                pendingByteRangeLength = null
                pendingByteRangeOffset = null
            }
        }

        val format = if (isTs && !isMp4) StreamFormat.TS else StreamFormat.MP4

        return MediaPlaylist(
            initSegmentUrl = initSegmentUrl,
            initByteRangeLength = initByteRangeLength,
            initByteRangeOffset = initByteRangeOffset,
            segments = segments,
            format = format
        )
    }

    fun downloadHlsStream(
        playlistUrl: String,
        outputStream: OutputStream,
        onProgress: ((downloadedSegments: Int, totalSegments: Int) -> Unit)? = null
    ): Boolean {
        val resolved = resolveStream(playlistUrl)
        val videoPlaylistContent = fetchUrlText(resolved.videoPlaylistUrl) ?: return false
        val videoPlaylist = parseMediaPlaylist(videoPlaylistContent, resolved.videoPlaylistUrl)

        val audioPlaylist = resolved.audioPlaylistUrl?.let { audioUrl ->
            fetchUrlText(audioUrl)?.let { content ->
                parseMediaPlaylist(content, audioUrl)
            }
        }

        val totalSegments = (if (videoPlaylist.initSegmentUrl != null) 1 else 0) +
                videoPlaylist.segments.size +
                (audioPlaylist?.let { (if (it.initSegmentUrl != null) 1 else 0) + it.segments.size } ?: 0)

        var downloadedCount = 0

        // 1. Download video init segment if present
        if (videoPlaylist.initSegmentUrl != null) {
            val success = downloadSegment(
                videoPlaylist.initSegmentUrl,
                videoPlaylist.initByteRangeLength,
                videoPlaylist.initByteRangeOffset,
                outputStream
            )
            if (!success) return false
            downloadedCount++
            onProgress?.invoke(downloadedCount, totalSegments)
        }

        // 2. Download video segments
        for (seg in videoPlaylist.segments) {
            val success = downloadSegment(seg.url, seg.byteRangeLength, seg.byteRangeOffset, outputStream)
            if (!success) return false
            downloadedCount++
            onProgress?.invoke(downloadedCount, totalSegments)
        }

        // 3. Download audio track segments if separate audio track exists
        audioPlaylist?.let { audio ->
            if (audio.initSegmentUrl != null) {
                val success = downloadSegment(
                    audio.initSegmentUrl,
                    audio.initByteRangeLength,
                    audio.initByteRangeOffset,
                    outputStream
                )
                if (!success) return false
                downloadedCount++
                onProgress?.invoke(downloadedCount, totalSegments)
            }

            for (seg in audio.segments) {
                val success = downloadSegment(seg.url, seg.byteRangeLength, seg.byteRangeOffset, outputStream)
                if (!success) return false
                downloadedCount++
                onProgress?.invoke(downloadedCount, totalSegments)
            }
        }

        return downloadedCount > 0
    }

    fun downloadSegment(
        urlString: String,
        length: Long?,
        offset: Long?,
        outputStream: OutputStream
    ): Boolean {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            if (length != null && offset != null) {
                val start = offset
                val end = offset + length - 1
                connection.setRequestProperty("Range", "bytes=$start-$end")
            }

            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_PARTIAL) {
                connection.inputStream.use { input ->
                    if (length != null && offset != null && responseCode == HttpURLConnection.HTTP_OK) {
                        // Server ignored Range header, manually skip to offset and read length
                        input.skip(offset)
                        copyBytesWithLimit(input, outputStream, length)
                    } else if (length != null) {
                        copyBytesWithLimit(input, outputStream, length)
                    } else {
                        input.copyTo(outputStream)
                    }
                }
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            connection?.disconnect()
        }
    }

    private fun copyBytesWithLimit(input: InputStream, output: OutputStream, limit: Long) {
        val buffer = ByteArray(8192)
        var remaining = limit
        while (remaining > 0) {
            val readLen = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (readLen <= 0) break
            output.write(buffer, 0, readLen)
            remaining -= readLen
        }
    }

    private fun parseByteRangeValue(value: String, previousOffset: Long): Pair<Long, Long> {
        val parts = value.split("@")
        val length = parts[0].trim().toLongOrNull() ?: 0L
        val offset = if (parts.size > 1) {
            parts[1].trim().toLongOrNull() ?: previousOffset
        } else {
            previousOffset
        }
        return Pair(length, offset)
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
