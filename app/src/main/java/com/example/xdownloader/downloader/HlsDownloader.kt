package com.example.xdownloader.downloader

import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

object HlsDownloader {

    data class Segment(
        val url: String,
        val byteRangeOffset: Long? = null,
        val byteRangeLength: Long? = null
    )

    data class HlsVariant(
        val url: String,
        val bandwidth: Long,
        val resolution: String,
        val audioUrl: String? = null
    )

    data class MediaPlaylist(
        val initSegment: Segment?,
        val segments: List<Segment>
    ) {
        val initSegmentUrl: String? get() = initSegment?.url
        val segmentUrls: List<String> get() = segments.map { it.url }
    }

    fun resolveMediaPlaylistUrl(masterUrl: String): String {
        return resolveBestVariant(masterUrl).url
    }

    fun resolveBestVariant(masterUrl: String): HlsVariant {
        try {
            val content = fetchUrlText(masterUrl) ?: return HlsVariant(masterUrl, 0, "")
            if (!content.contains("#EXT-X-STREAM-INF")) {
                // Already a media playlist
                return HlsVariant(masterUrl, 0, "")
            }

            val variants = parseMasterPlaylist(content, masterUrl)
            val bestVariant = variants.maxByOrNull { it.bandwidth }
            return bestVariant ?: HlsVariant(masterUrl, 0, "")
        } catch (e: Exception) {
            e.printStackTrace()
            return HlsVariant(masterUrl, 0, "")
        }
    }

    fun parseMasterPlaylist(content: String, baseUrl: String): List<HlsVariant> {
        val audioGroupMap = mutableMapOf<String, String>()
        val lines = content.lines()

        // 1. Extract audio group URIs from #EXT-X-MEDIA:TYPE=AUDIO
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXT-X-MEDIA:") && parseAttribute(trimmed, "TYPE") == "AUDIO") {
                val groupId = parseAttribute(trimmed, "GROUP-ID")
                val uri = parseAttribute(trimmed, "URI")
                if (!groupId.isNullOrEmpty() && !uri.isNullOrEmpty()) {
                    val isDefault = parseAttribute(trimmed, "DEFAULT")?.equals("YES", ignoreCase = true) == true
                    if (!audioGroupMap.containsKey(groupId) || isDefault) {
                        audioGroupMap[groupId] = resolveRelativeUrl(baseUrl, uri)
                    }
                }
            }
        }

        // 2. Parse variants
        val variants = mutableListOf<HlsVariant>()
        var currentBandwidth = 0L
        var currentResolution = ""
        var currentAudioGroup: String? = null

        for (i in lines.indices) {
            val line = lines[i].trim()
            if (line.startsWith("#EXT-X-STREAM-INF:")) {
                currentBandwidth = parseAttribute(line, "BANDWIDTH")?.toLongOrNull() ?: 0L
                currentResolution = parseAttribute(line, "RESOLUTION") ?: ""
                currentAudioGroup = parseAttribute(line, "AUDIO")
            } else if (line.isNotEmpty() && !line.startsWith("#")) {
                val variantUrl = resolveRelativeUrl(baseUrl, line)
                val audioUrl = currentAudioGroup?.let { audioGroupMap[it] }
                variants.add(HlsVariant(variantUrl, currentBandwidth, currentResolution, audioUrl))
                currentBandwidth = 0L
                currentResolution = ""
                currentAudioGroup = null
            }
        }
        return variants
    }

    fun parseMediaPlaylist(content: String, baseUrl: String): MediaPlaylist {
        var initSegment: Segment? = null
        val segments = mutableListOf<Segment>()

        var pendingByteRangeLength: Long? = null
        var pendingByteRangeOffset: Long? = null
        var currentByteOffset: Long = 0L
        var lastResourceUrl: String? = null

        for (line in content.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXT-X-MAP:")) {
                val uri = parseAttribute(trimmed, "URI")
                if (!uri.isNullOrEmpty()) {
                    val fullUrl = resolveRelativeUrl(baseUrl, uri)
                    val byteRangeAttr = parseAttribute(trimmed, "BYTERANGE")
                    if (byteRangeAttr != null) {
                        val parts = byteRangeAttr.split("@")
                        val length = parts.getOrNull(0)?.toLongOrNull()
                        val offset = parts.getOrNull(1)?.toLongOrNull() ?: 0L
                        initSegment = Segment(fullUrl, byteRangeOffset = offset, byteRangeLength = length)
                    } else {
                        initSegment = Segment(fullUrl)
                    }
                }
            } else if (trimmed.startsWith("#EXT-X-BYTERANGE:")) {
                val value = trimmed.removePrefix("#EXT-X-BYTERANGE:").trim()
                val parts = value.split("@")
                val length = parts.getOrNull(0)?.toLongOrNull()
                val offset = parts.getOrNull(1)?.toLongOrNull()
                pendingByteRangeLength = length
                pendingByteRangeOffset = offset
            } else if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                val segmentUrl = resolveRelativeUrl(baseUrl, trimmed)
                if (pendingByteRangeLength != null) {
                    val offset = pendingByteRangeOffset ?: if (segmentUrl == lastResourceUrl) currentByteOffset else 0L
                    val length = pendingByteRangeLength
                    segments.add(Segment(segmentUrl, byteRangeOffset = offset, byteRangeLength = length))
                    currentByteOffset = offset + (length ?: 0L)
                    lastResourceUrl = segmentUrl
                    pendingByteRangeLength = null
                    pendingByteRangeOffset = null
                } else {
                    segments.add(Segment(segmentUrl))
                    currentByteOffset = 0L
                    lastResourceUrl = segmentUrl
                }
            }
        }
        return MediaPlaylist(initSegment, segments)
    }

    fun downloadHlsStream(
        playlistUrl: String,
        outputStream: OutputStream,
        onProgress: ((downloadedSegments: Int, totalSegments: Int) -> Unit)? = null
    ): Boolean {
        try {
            val bestVariant = resolveBestVariant(playlistUrl)

            val videoPlaylistContent = fetchUrlText(bestVariant.url) ?: return false
            val videoPlaylist = parseMediaPlaylist(videoPlaylistContent, bestVariant.url)

            val audioPlaylist = if (!bestVariant.audioUrl.isNullOrEmpty()) {
                val audioContent = fetchUrlText(bestVariant.audioUrl)
                if (audioContent != null) parseMediaPlaylist(audioContent, bestVariant.audioUrl) else null
            } else null

            val totalSegments = videoPlaylist.segments.size + (audioPlaylist?.segments?.size ?: 0) + (if (videoPlaylist.initSegment != null) 1 else 0) + (if (audioPlaylist?.initSegment != null) 1 else 0)
            if (totalSegments == 0) return false

            var downloadedSegments = 0

            // Check if segments are fMP4 or TS
            val firstSegment = videoPlaylist.initSegment ?: videoPlaylist.segments.firstOrNull() ?: return false
            val firstSegmentBytes = fetchSegmentBytes(firstSegment) ?: return false

            val isFmp4 = isFmp4Bytes(firstSegmentBytes)

            if (isFmp4 && audioPlaylist == null) {
                // For pure fMP4 without separate audio, write init segment and media segments directly
                outputStream.write(firstSegmentBytes)
                downloadedSegments++
                onProgress?.invoke(downloadedSegments, totalSegments)

                val remainingSegments = if (videoPlaylist.initSegment != null) {
                    videoPlaylist.segments
                } else {
                    videoPlaylist.segments.drop(1)
                }

                for (segment in remainingSegments) {
                    val bytes = fetchSegmentBytes(segment) ?: return false
                    outputStream.write(bytes)
                    downloadedSegments++
                    onProgress?.invoke(downloadedSegments, totalSegments)
                }
                outputStream.flush()
                return true
            }

            // TS stream (or fMP4 with independent audio stream)
            val demuxer = TsDemuxer()

            // 1. Process Video Segments
            if (videoPlaylist.initSegment != null) {
                val initBytes = if (videoPlaylist.initSegment == firstSegment) firstSegmentBytes else fetchSegmentBytes(videoPlaylist.initSegment)
                if (initBytes != null) demuxer.demux(initBytes)
                downloadedSegments++
                onProgress?.invoke(downloadedSegments, totalSegments)
            }

            val videoSegmentsToFetch = if (videoPlaylist.initSegment == null && firstSegment == videoPlaylist.segments.firstOrNull()) {
                demuxer.demux(firstSegmentBytes)
                downloadedSegments++
                onProgress?.invoke(downloadedSegments, totalSegments)
                videoPlaylist.segments.drop(1)
            } else {
                videoPlaylist.segments
            }

            for (segment in videoSegmentsToFetch) {
                val bytes = fetchSegmentBytes(segment) ?: return false
                demuxer.demux(bytes)
                downloadedSegments++
                onProgress?.invoke(downloadedSegments, totalSegments)
            }

            // 2. Process Audio Segments (if independent audio track exists)
            if (audioPlaylist != null) {
                audioPlaylist.initSegment?.let { initSeg ->
                    val bytes = fetchSegmentBytes(initSeg)
                    if (bytes != null) demuxer.demux(bytes)
                    downloadedSegments++
                    onProgress?.invoke(downloadedSegments, totalSegments)
                }
                for (segment in audioPlaylist.segments) {
                    val bytes = fetchSegmentBytes(segment) ?: return false
                    demuxer.demux(bytes)
                    downloadedSegments++
                    onProgress?.invoke(downloadedSegments, totalSegments)
                }
            }

            demuxer.flush()

            // 3. Mux into MP4 and write to outputStream
            Mp4Muxer.mux(demuxer.videoTrack, demuxer.audioTrack, outputStream)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private fun isFmp4Bytes(bytes: ByteArray): Boolean {
        if (bytes.size < 8) return false
        val boxType = String(bytes, 4, 4, Charsets.ISO_8859_1)
        return boxType in setOf("ftyp", "styp", "moof", "mdat", "moov", "sidx")
    }

    fun fetchSegmentBytes(segment: Segment): ByteArray? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(segment.url)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            if (segment.byteRangeLength != null) {
                val start = segment.byteRangeOffset ?: 0L
                val end = start + segment.byteRangeLength - 1
                connection.setRequestProperty("Range", "bytes=$start-$end")
            }
            connection.connect()
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_PARTIAL) {
                val inputStream = connection.inputStream
                if (responseCode == HttpURLConnection.HTTP_OK && segment.byteRangeLength != null) {
                    val start = segment.byteRangeOffset ?: 0L
                    var skipped = 0L
                    while (skipped < start) {
                        val s = inputStream.skip(start - skipped)
                        if (s <= 0) break
                        skipped += s
                    }
                    val length = segment.byteRangeLength.toInt()
                    val buffer = ByteArray(length)
                    var read = 0
                    while (read < length) {
                        val count = inputStream.read(buffer, read, length - read)
                        if (count < 0) break
                        read += count
                    }
                    if (read == length) buffer else buffer.copyOf(read)
                } else {
                    inputStream.readBytes()
                }
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            connection?.disconnect()
        }
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
        val bytes = fetchSegmentBytes(Segment(urlString)) ?: return false
        return try {
            outputStream.write(bytes)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    class BitReader(private val bytes: ByteArray) {
        private var byteOffset = 0
        private var bitOffset = 0

        fun readBit(): Int {
            if (byteOffset >= bytes.size) return 0
            val bit = (bytes[byteOffset].toInt() ushr (7 - bitOffset)) and 1
            bitOffset++
            if (bitOffset == 8) {
                bitOffset = 0
                byteOffset++
            }
            return bit
        }

        fun readBits(n: Int): Int {
            var value = 0
            for (i in 0 until n) {
                value = (value shl 1) or readBit()
            }
            return value
        }

        fun readUe(): Int {
            var zeroCount = 0
            while (readBit() == 0 && zeroCount < 31 && byteOffset < bytes.size) {
                zeroCount++
            }
            return (1 shl zeroCount) - 1 + readBits(zeroCount)
        }
    }

    fun parseSpsDimension(sps: ByteArray): Pair<Int, Int> {
        try {
            if (sps.size < 4) return Pair(1280, 720)
            val br = BitReader(sps)
            br.readBits(8) // NAL header byte
            val profileIdc = br.readBits(8)
            br.readBits(8) // constraint flags
            br.readBits(8) // level idc
            br.readUe() // seq_parameter_set_id

            if (profileIdc in setOf(100, 110, 122, 244, 44, 83, 86, 118, 128, 138, 139, 134, 135)) {
                val chromaFormatIdc = br.readUe()
                if (chromaFormatIdc == 3) br.readBit() // separate_colour_plane_flag
                br.readUe() // bit_depth_luma_minus8
                br.readUe() // bit_depth_chroma_minus8
                br.readBit() // qpprime_y_zero_transform_bypass_flag
                val seqScalingMatrixPresent = br.readBit()
                if (seqScalingMatrixPresent != 0) {
                    val count = if (chromaFormatIdc != 3) 8 else 12
                    for (i in 0 until count) {
                        val seqScalingListPresent = br.readBit()
                        if (seqScalingListPresent != 0) {
                            val size = if (i < 6) 16 else 64
                            var lastScale = 8
                            var nextScale = 8
                            for (j in 0 until size) {
                                if (nextScale != 0) {
                                    val deltaScale = br.readUe()
                                    nextScale = (lastScale + deltaScale + 256) % 256
                                }
                                lastScale = if (nextScale == 0) lastScale else nextScale
                            }
                        }
                    }
                }
            }

            br.readUe() // log2_max_frame_num_minus4
            val picOrderCntType = br.readUe()
            if (picOrderCntType == 0) {
                br.readUe() // log2_max_pic_order_cnt_lsb_minus4
            } else if (picOrderCntType == 1) {
                br.readBit() // delta_pic_order_always_zero_flag
                br.readUe() // offset_for_non_ref_pic
                br.readUe() // offset_for_top_to_bottom_field
                val numRefFramesInPicOrderCntCycle = br.readUe()
                for (i in 0 until numRefFramesInPicOrderCntCycle) {
                    br.readUe()
                }
            }
            br.readUe() // max_num_ref_frames
            br.readBit() // gaps_in_frame_num_value_allowed_flag
            val picWidthInMbsMinus1 = br.readUe()
            val picHeightInMapUnitsMinus1 = br.readUe()
            val frameMbsOnlyFlag = br.readBit()
            if (frameMbsOnlyFlag == 0) br.readBit()
            br.readBit() // direct_8x8_inference_flag
            val frameCroppingFlag = br.readBit()
            var cropLeft = 0
            var cropRight = 0
            var cropTop = 0
            var cropBottom = 0
            if (frameCroppingFlag != 0) {
                cropLeft = br.readUe()
                cropRight = br.readUe()
                cropTop = br.readUe()
                cropBottom = br.readUe()
            }

            val width = ((picWidthInMbsMinus1 + 1) * 16) - (cropLeft + cropRight) * 2
            val height = ((2 - frameMbsOnlyFlag) * (picHeightInMapUnitsMinus1 + 1) * 16) - (cropTop + cropBottom) * 2
            if (width > 0 && height > 0) return Pair(width, height)
        } catch (e: Exception) {
            // Fallback
        }
        return Pair(1280, 720)
    }

    data class DemuxedSample(
        val data: ByteArray,
        val ptsMs: Long,
        val dtsMs: Long,
        val isKeyFrame: Boolean = false
    )

    class DemuxedTrack(
        val isVideo: Boolean
    ) {
        val samples = mutableListOf<DemuxedSample>()
        var sps: ByteArray? = null
        var pps: ByteArray? = null
        var audioSpecificConfig: ByteArray? = null
        var sampleRate: Int = 44100
        var channelCount: Int = 2
    }

    class TsDemuxer {
        val videoTrack = DemuxedTrack(isVideo = true)
        val audioTrack = DemuxedTrack(isVideo = false)

        private var videoPid: Int = -1
        private var audioPid: Int = -1
        private var pmtPid: Int = -1

        private class PesBuffer {
            val stream = java.io.ByteArrayOutputStream()
            var pts: Long = -1L
            var dts: Long = -1L
        }

        private val pesBuffers = mutableMapOf<Int, PesBuffer>()

        fun demux(tsData: ByteArray) {
            var offset = 0
            val len = tsData.size
            while (offset + 188 <= len) {
                if (tsData[offset] != 0x47.toByte()) {
                    offset++
                    continue
                }

                val b1 = tsData[offset + 1].toInt() and 0xFF
                val b2 = tsData[offset + 2].toInt() and 0xFF
                val b3 = tsData[offset + 3].toInt() and 0xFF

                val pusi = (b1 and 0x40) != 0
                val pid = ((b1 and 0x1F) shl 8) or b2
                val afc = (b3 and 0x30) ushr 4

                var payloadStart = offset + 4
                if (afc == 2 || afc == 3) {
                    val afLen = tsData[offset + 4].toInt() and 0xFF
                    payloadStart += 1 + afLen
                }

                if (payloadStart < offset + 188) {
                    val payloadLen = offset + 188 - payloadStart
                    processPacketPayload(pid, pusi, tsData, payloadStart, payloadLen)
                }

                offset += 188
            }
        }

        fun flush() {
            for ((pid, buf) in pesBuffers) {
                if (buf.stream.size() > 0) {
                    processCompletePes(pid, buf.stream.toByteArray(), buf.pts, buf.dts)
                    buf.stream.reset()
                }
            }
        }

        private fun processPacketPayload(pid: Int, pusi: Boolean, data: ByteArray, start: Int, len: Int) {
            if (pid == 0) {
                parsePat(data, start, len)
                return
            }
            if (pid == pmtPid && pmtPid != -1) {
                parsePmt(data, start, len)
                return
            }

            if (pusi && (pid != videoPid && pid != audioPid)) {
                var pStart = start
                if (data[pStart] == 0.toByte()) pStart += 1
                if (pStart + 4 <= start + len && data[pStart] == 0.toByte() && data[pStart + 1] == 0.toByte() && data[pStart + 2] == 1.toByte()) {
                    val streamId = data[pStart + 3].toInt() and 0xFF
                    if (streamId in 0xE0..0xEF && videoPid == -1) {
                        videoPid = pid
                    } else if ((streamId in 0xC0..0xDF || streamId == 0xBD) && audioPid == -1) {
                        audioPid = pid
                    }
                }
            }

            if (pid == videoPid || pid == audioPid) {
                if (pusi) {
                    val buf = pesBuffers[pid]
                    if (buf != null && buf.stream.size() > 0) {
                        processCompletePes(pid, buf.stream.toByteArray(), buf.pts, buf.dts)
                        buf.stream.reset()
                    }
                    val newBuf = pesBuffers.getOrPut(pid) { PesBuffer() }
                    if (start + 9 <= start + len && data[start] == 0.toByte() && data[start + 1] == 0.toByte() && data[start + 2] == 1.toByte()) {
                        val flags = data[start + 7].toInt() and 0xFF
                        val headerLen = data[start + 8].toInt() and 0xFF
                        val ptsDtsFlags = (flags and 0xC0) ushr 6
                        var pts = -1L
                        var dts = -1L
                        if ((ptsDtsFlags == 2 || ptsDtsFlags == 3) && start + 14 <= start + len) {
                            pts = parsePts(data, start + 9)
                            dts = pts
                        }
                        if (ptsDtsFlags == 3 && start + 19 <= start + len) {
                            dts = parsePts(data, start + 14)
                        }
                        newBuf.pts = pts
                        newBuf.dts = dts
                        val payloadOffset = start + 9 + headerLen
                        if (payloadOffset < start + len) {
                            newBuf.stream.write(data, payloadOffset, start + len - payloadOffset)
                        }
                    } else {
                        newBuf.stream.write(data, start, len)
                    }
                } else {
                    val buf = pesBuffers[pid]
                    if (buf != null) {
                        buf.stream.write(data, start, len)
                    }
                }
            }
        }

        private fun parsePts(data: ByteArray, offset: Int): Long {
            val b0 = data[offset].toLong() and 0xFF
            val b1 = data[offset + 1].toLong() and 0xFF
            val b2 = data[offset + 2].toLong() and 0xFF
            val b3 = data[offset + 3].toLong() and 0xFF
            val b4 = data[offset + 4].toLong() and 0xFF
            val pts = ((b0 and 0x0E) shl 29) or
                    ((b1 and 0xFF) shl 22) or
                    ((b2 and 0xFE) shl 14) or
                    ((b3 and 0xFF) shl 7) or
                    ((b4 and 0xFE) ushr 1)
            return pts / 90
        }

        private fun parsePat(data: ByteArray, start: Int, len: Int) {
            try {
                var ptr = start
                val pointerField = data[ptr].toInt() and 0xFF
                ptr += 1 + pointerField
                if (ptr + 8 >= start + len) return
                val sectionLen = ((data[ptr + 1].toInt() and 0x0F) shl 8) or (data[ptr + 2].toInt() and 0xFF)
                var p = ptr + 8
                val end = ptr + 3 + sectionLen - 4
                while (p + 4 <= end) {
                    val progNum = ((data[p].toInt() and 0xFF) shl 8) or (data[p + 1].toInt() and 0xFF)
                    val pPid = ((data[p + 2].toInt() and 0x1F) shl 8) or (data[p + 3].toInt() and 0xFF)
                    if (progNum != 0) {
                        pmtPid = pPid
                        break
                    }
                    p += 4
                }
            } catch (e: Exception) {}
        }

        private fun parsePmt(data: ByteArray, start: Int, len: Int) {
            try {
                var ptr = start
                val pointerField = data[ptr].toInt() and 0xFF
                ptr += 1 + pointerField
                if (ptr + 12 >= start + len) return
                val sectionLen = ((data[ptr + 1].toInt() and 0x0F) shl 8) or (data[ptr + 2].toInt() and 0xFF)
                val programInfoLen = ((data[ptr + 10].toInt() and 0x0F) shl 8) or (data[ptr + 11].toInt() and 0xFF)
                var p = ptr + 12 + programInfoLen
                val end = ptr + 3 + sectionLen - 4
                while (p + 5 <= end) {
                    val streamType = data[p].toInt() and 0xFF
                    val elemPid = ((data[p + 1].toInt() and 0x1F) shl 8) or (data[p + 2].toInt() and 0xFF)
                    val esInfoLen = ((data[p + 3].toInt() and 0x0F) shl 8) or (data[p + 4].toInt() and 0xFF)

                    if (streamType == 0x1B) {
                        videoPid = elemPid
                    } else if (streamType == 0x0F || streamType == 0x11 || streamType == 0x81) {
                        audioPid = elemPid
                    }
                    p += 5 + esInfoLen
                }
            } catch (e: Exception) {}
        }

        private fun processCompletePes(pid: Int, pesData: ByteArray, ptsMs: Long, dtsMs: Long) {
            if (pid == videoPid) {
                parseH264Pes(pesData, ptsMs, dtsMs)
            } else if (pid == audioPid) {
                parseAacPes(pesData, ptsMs, dtsMs)
            }
        }

        private fun parseH264Pes(data: ByteArray, ptsMs: Long, dtsMs: Long) {
            val nals = splitNals(data)
            var isKeyFrame = false
            val frameBaos = java.io.ByteArrayOutputStream()
            val frameDos = java.io.DataOutputStream(frameBaos)

            for (nal in nals) {
                if (nal.isEmpty()) continue
                val nalType = nal[0].toInt() and 0x1F
                if (nalType == 7) {
                    videoTrack.sps = nal
                } else if (nalType == 8) {
                    videoTrack.pps = nal
                } else if (nalType == 5 || nalType == 1) {
                    if (nalType == 5) isKeyFrame = true
                    frameDos.writeInt(nal.size)
                    frameDos.write(nal)
                }
            }

            val frameData = frameBaos.toByteArray()
            if (frameData.isNotEmpty()) {
                videoTrack.samples.add(DemuxedSample(frameData, ptsMs, dtsMs, isKeyFrame))
            }
        }

        private fun splitNals(data: ByteArray): List<ByteArray> {
            val nals = mutableListOf<ByteArray>()
            var start = -1
            var i = 0
            val len = data.size
            while (i < len - 3) {
                if (data[i] == 0.toByte() && data[i + 1] == 0.toByte()) {
                    var prefixLen = 0
                    if (data[i + 2] == 1.toByte()) prefixLen = 3
                    else if (i < len - 3 && data[i + 2] == 0.toByte() && data[i + 3] == 1.toByte()) prefixLen = 4

                    if (prefixLen > 0) {
                        if (start != -1) {
                            nals.add(data.copyOfRange(start, i))
                        }
                        start = i + prefixLen
                        i += prefixLen
                        continue
                    }
                }
                i++
            }
            if (start != -1 && start < len) {
                nals.add(data.copyOfRange(start, len))
            }
            return nals
        }

        private fun parseAacPes(data: ByteArray, ptsMs: Long, dtsMs: Long) {
            val sampleRates = intArrayOf(96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350)
            var i = 0
            val len = data.size
            var currentPts = ptsMs

            while (i + 7 <= len) {
                val b0 = data[i].toInt() and 0xFF
                val b1 = data[i + 1].toInt() and 0xFF
                if (b0 == 0xFF && (b1 and 0xF0) == 0xF0) {
                    val b2 = data[i + 2].toInt() and 0xFF
                    val b3 = data[i + 3].toInt() and 0xFF
                    val b4 = data[i + 4].toInt() and 0xFF
                    val b5 = data[i + 5].toInt() and 0xFF

                    val profile = (b2 ushr 6) and 0x03
                    val srIdx = (b2 ushr 2) and 0x0F
                    val chanCfg = ((b2 and 0x01) shl 2) or ((b3 ushr 6) and 0x03)
                    val frameLen = ((b3 and 0x03) shl 11) or (b4 shl 3) or ((b5 ushr 5) and 0x07)

                    if (frameLen in 7..4096 && i + frameLen <= len) {
                        if (audioTrack.audioSpecificConfig == null) {
                            val sampleRate = sampleRates.getOrElse(srIdx) { 44100 }
                            audioTrack.sampleRate = sampleRate
                            audioTrack.channelCount = if (chanCfg > 0) chanCfg else 2
                            val objectType = profile + 1
                            val asc0 = ((objectType shl 3) or (srIdx ushr 1)).toByte()
                            val asc1 = (((srIdx and 1) shl 7) or (chanCfg shl 3)).toByte()
                            audioTrack.audioSpecificConfig = byteArrayOf(asc0, asc1)
                        }

                        val headerSize = if ((b1 and 0x01) == 0) 9 else 7
                        if (frameLen > headerSize) {
                            val payload = data.copyOfRange(i + headerSize, i + frameLen)
                            val samplePts = if (currentPts >= 0) currentPts else 0L
                            audioTrack.samples.add(DemuxedSample(payload, samplePts, samplePts, true))
                            if (currentPts >= 0) {
                                currentPts += (1024L * 1000L / audioTrack.sampleRate)
                            }
                        }
                        i += frameLen
                        continue
                    }
                }
                i++
            }
        }
    }

    object Mp4Muxer {

        class BoxWriter {
            val baos = java.io.ByteArrayOutputStream()
            val dos = java.io.DataOutputStream(baos)

            fun writeBox(type: String, block: BoxWriter.() -> Unit) {
                val child = BoxWriter()
                child.block()
                val data = child.baos.toByteArray()
                dos.writeInt(data.size + 8)
                dos.writeBytes(type)
                dos.write(data)
            }

            fun writeFullBox(type: String, version: Int, flags: Int, block: BoxWriter.() -> Unit) {
                val child = BoxWriter()
                child.block()
                val data = child.baos.toByteArray()
                dos.writeInt(data.size + 12)
                dos.writeBytes(type)
                dos.writeByte(version)
                dos.writeByte((flags ushr 16) and 0xFF)
                dos.writeByte((flags ushr 8) and 0xFF)
                dos.writeByte(flags and 0xFF)
                dos.write(data)
            }

            fun writeBytes(s: String) {
                dos.write(s.toByteArray(Charsets.ISO_8859_1))
            }
        }

        fun mux(
            videoTrack: DemuxedTrack?,
            audioTrack: DemuxedTrack?,
            outputStream: OutputStream
        ) {
            val tracks = mutableListOf<DemuxedTrack>()
            if (videoTrack != null && videoTrack.samples.isNotEmpty()) tracks.add(videoTrack)
            if (audioTrack != null && audioTrack.samples.isNotEmpty()) tracks.add(audioTrack)

            if (tracks.isEmpty()) return

            val ftypWriter = BoxWriter()
            ftypWriter.writeBox("ftyp") {
                writeBytes("isom")
                dos.writeInt(512)
                writeBytes("isom")
                writeBytes("iso2")
                writeBytes("mp41")
            }
            val ftypBytes = ftypWriter.baos.toByteArray()

            class SampleMeta(
                val offset: Long,
                val size: Int,
                val delta: Int,
                val compositionOffset: Int,
                val isKeyFrame: Boolean
            )

            class TrackMeta(
                val track: DemuxedTrack,
                val trackId: Int,
                val sampleMetas: List<SampleMeta>,
                val durationMs: Long
            )

            var minPts = Long.MAX_VALUE
            for (tr in tracks) {
                for (s in tr.samples) {
                    if (s.ptsMs in 0 until minPts) minPts = s.ptsMs
                }
            }
            if (minPts == Long.MAX_VALUE) minPts = 0L

            var currentFileOffset = ftypBytes.size.toLong() + 16L
            val mdatBaos = java.io.ByteArrayOutputStream()

            val trackMetas = mutableListOf<TrackMeta>()

            for (trIndex in tracks.indices) {
                val tr = tracks[trIndex]
                val sampleMetas = mutableListOf<SampleMeta>()
                val samples = tr.samples

                var trackMinPts = Long.MAX_VALUE
                var trackMaxPts = 0L

                for (i in samples.indices) {
                    val s = samples[i]
                    val pts = Math.max(0L, s.ptsMs - minPts)
                    val dts = Math.max(0L, s.dtsMs - minPts)

                    if (pts < trackMinPts) trackMinPts = pts
                    if (pts > trackMaxPts) trackMaxPts = pts

                    val nextPts = if (i + 1 < samples.size) Math.max(0L, samples[i + 1].ptsMs - minPts) else pts + 40L
                    val delta = (nextPts - pts).toInt().coerceAtLeast(1)
                    val ctsOffset = (pts - dts).toInt()

                    val sampleOffset = currentFileOffset + mdatBaos.size()
                    mdatBaos.write(s.data)
                    sampleMetas.add(SampleMeta(sampleOffset, s.data.size, delta, ctsOffset, s.isKeyFrame))
                }

                val trackDuration = if (samples.isNotEmpty()) trackMaxPts - trackMinPts + 40L else 0L
                trackMetas.add(TrackMeta(tr, trIndex + 1, sampleMetas, trackDuration))
            }

            val mdatPayload = mdatBaos.toByteArray()
            val totalMdatSize = 16L + mdatPayload.size

            outputStream.write(ftypBytes)

            val mdatHeaderDos = java.io.DataOutputStream(outputStream)
            mdatHeaderDos.writeInt(1)
            mdatHeaderDos.write("mdat".toByteArray(Charsets.ISO_8859_1))
            mdatHeaderDos.writeLong(totalMdatSize)
            outputStream.write(mdatPayload)

            val maxDurationMs = trackMetas.maxOfOrNull { it.durationMs } ?: 1000L

            val moovWriter = BoxWriter()
            moovWriter.writeBox("moov") {
                writeFullBox("mvhd", 0, 0) {
                    dos.writeInt(0)
                    dos.writeInt(0)
                    dos.writeInt(1000)
                    dos.writeInt(maxDurationMs.toInt())
                    dos.writeInt(0x00010000)
                    dos.writeShort(0x0100)
                    dos.write(ByteArray(10))
                    val matrix = intArrayOf(0x00010000, 0, 0, 0, 0x00010000, 0, 0, 0, 0x40000000)
                    for (m in matrix) dos.writeInt(m)
                    dos.write(ByteArray(24))
                    dos.writeInt(tracks.size + 1)
                }

                for (tm in trackMetas) {
                    writeBox("trak") {
                        writeFullBox("tkhd", 0, 3) {
                            dos.writeInt(0)
                            dos.writeInt(0)
                            dos.writeInt(tm.trackId)
                            dos.writeInt(0)
                            dos.writeInt(tm.durationMs.toInt())
                            dos.write(ByteArray(8))
                            dos.writeShort(0)
                            dos.writeShort(0)
                            dos.writeShort(if (tm.track.isVideo) 0 else 0x0100)
                            dos.writeShort(0)
                            val matrix = intArrayOf(0x00010000, 0, 0, 0, 0x00010000, 0, 0, 0, 0x40000000)
                            for (m in matrix) dos.writeInt(m)

                            if (tm.track.isVideo) {
                                val sps = tm.track.sps ?: byteArrayOf()
                                val (w, h) = parseSpsDimension(sps)
                                dos.writeInt(w shl 16)
                                dos.writeInt(h shl 16)
                            } else {
                                dos.writeInt(0)
                                dos.writeInt(0)
                            }
                        }

                        writeBox("mdia") {
                            val timescale = if (tm.track.isVideo) 1000 else tm.track.sampleRate
                            val trackDurationTs = if (tm.track.isVideo) tm.durationMs else (tm.durationMs * tm.track.sampleRate / 1000)

                            writeFullBox("mdhd", 0, 0) {
                                dos.writeInt(0)
                                dos.writeInt(0)
                                dos.writeInt(timescale)
                                dos.writeInt(trackDurationTs.toInt())
                                dos.writeShort(0x55C4)
                                dos.writeShort(0)
                            }

                            writeFullBox("hdlr", 0, 0) {
                                dos.writeInt(0)
                                writeBytes(if (tm.track.isVideo) "vide" else "soun")
                                dos.write(ByteArray(12))
                                writeBytes(if (tm.track.isVideo) "VideoHandler\u0000" else "SoundHandler\u0000")
                            }

                            writeBox("minf") {
                                if (tm.track.isVideo) {
                                    writeFullBox("vmhd", 0, 1) {
                                        dos.writeShort(0)
                                        dos.writeShort(0)
                                        dos.writeShort(0)
                                        dos.writeShort(0)
                                    }
                                } else {
                                    writeFullBox("smhd", 0, 0) {
                                        dos.writeShort(0)
                                        dos.writeShort(0)
                                    }
                                }

                                writeBox("dinf") {
                                    writeFullBox("dref", 0, 0) {
                                        dos.writeInt(1)
                                        writeFullBox("url ", 0, 1) {}
                                    }
                                }

                                writeBox("stbl") {
                                    writeFullBox("stsd", 0, 0) {
                                        dos.writeInt(1)
                                        if (tm.track.isVideo) {
                                            writeBox("avc1") {
                                                dos.write(ByteArray(6))
                                                dos.writeShort(1)
                                                dos.writeShort(0)
                                                dos.writeShort(0)
                                                dos.write(ByteArray(12))
                                                val sps = tm.track.sps ?: byteArrayOf()
                                                val (w, h) = parseSpsDimension(sps)
                                                dos.writeShort(w)
                                                dos.writeShort(h)
                                                dos.writeInt(0x00480000)
                                                dos.writeInt(0x00480000)
                                                dos.writeInt(0)
                                                dos.writeShort(1)
                                                dos.write(ByteArray(32))
                                                dos.writeShort(0x0018)
                                                dos.writeShort(0xFFFF)

                                                writeBox("avcC") {
                                                    val spsArr = tm.track.sps ?: byteArrayOf(0x67.toByte(), 0x42.toByte(), 0x00.toByte(), 0x1E.toByte())
                                                    val ppsArr = tm.track.pps ?: byteArrayOf(0x68.toByte(), 0xCE.toByte(), 0x3C.toByte(), 0x80.toByte())
                                                    dos.writeByte(1)
                                                    dos.writeByte(if (spsArr.size > 1) spsArr[1].toInt() else 0x42)
                                                    dos.writeByte(if (spsArr.size > 2) spsArr[2].toInt() else 0x00)
                                                    dos.writeByte(if (spsArr.size > 3) spsArr[3].toInt() else 0x1E)
                                                    dos.writeByte(0xFF)
                                                    dos.writeByte(0xE1)
                                                    dos.writeShort(spsArr.size)
                                                    dos.write(spsArr)
                                                    dos.writeByte(1)
                                                    dos.writeShort(ppsArr.size)
                                                    dos.write(ppsArr)
                                                }
                                            }
                                        } else {
                                            writeBox("mp4a") {
                                                dos.write(ByteArray(6))
                                                dos.writeShort(1)
                                                dos.write(ByteArray(8))
                                                dos.writeShort(tm.track.channelCount)
                                                dos.writeShort(16)
                                                dos.writeShort(0)
                                                dos.writeShort(0)
                                                dos.writeInt(tm.track.sampleRate shl 16)

                                                writeFullBox("esds", 0, 0) {
                                                    val asc = tm.track.audioSpecificConfig ?: byteArrayOf(0x12, 0x10)
                                                    dos.writeByte(0x03)
                                                    dos.writeByte(23 + asc.size)
                                                    dos.writeShort(tm.trackId)
                                                    dos.writeByte(0)
                                                    dos.writeByte(0x04)
                                                    dos.writeByte(15 + asc.size)
                                                    dos.writeByte(0x40)
                                                    dos.writeByte(0x15)
                                                    dos.writeByte(0)
                                                    dos.writeByte(0)
                                                    dos.writeByte(0)
                                                    dos.writeInt(0)
                                                    dos.writeInt(0)
                                                    dos.writeByte(0x05)
                                                    dos.writeByte(asc.size)
                                                    dos.write(asc)
                                                    dos.writeByte(0x06)
                                                    dos.writeByte(1)
                                                    dos.writeByte(0x02)
                                                }
                                            }
                                        }
                                    }

                                    writeFullBox("stts", 0, 0) {
                                        val samples = tm.sampleMetas
                                        class SttsEntry(var count: Int, val delta: Int)
                                        val entries = mutableListOf<SttsEntry>()
                                        for (s in samples) {
                                            val deltaTs = if (tm.track.isVideo) s.delta else 1024
                                            if (entries.isNotEmpty() && entries.last().delta == deltaTs) {
                                                entries.last().count++
                                            } else {
                                                entries.add(SttsEntry(1, deltaTs))
                                            }
                                        }
                                        dos.writeInt(entries.size)
                                        for (e in entries) {
                                            dos.writeInt(e.count)
                                            dos.writeInt(e.delta)
                                        }
                                    }

                                    writeFullBox("stsz", 0, 0) {
                                        dos.writeInt(0)
                                        dos.writeInt(tm.sampleMetas.size)
                                        for (s in tm.sampleMetas) {
                                            dos.writeInt(s.size)
                                        }
                                    }

                                    writeFullBox("stsc", 0, 0) {
                                        dos.writeInt(1)
                                        dos.writeInt(1)
                                        dos.writeInt(tm.sampleMetas.size)
                                        dos.writeInt(1)
                                    }

                                    writeFullBox("co64", 0, 0) {
                                        dos.writeInt(1)
                                        val firstOffset = tm.sampleMetas.firstOrNull()?.offset ?: 0L
                                        dos.writeLong(firstOffset)
                                    }

                                    if (tm.track.isVideo) {
                                        writeFullBox("stss", 0, 0) {
                                            val keyIndices = mutableListOf<Int>()
                                            for (i in tm.sampleMetas.indices) {
                                                if (tm.sampleMetas[i].isKeyFrame) {
                                                    keyIndices.add(i + 1)
                                                }
                                            }
                                            if (keyIndices.isEmpty() && tm.sampleMetas.isNotEmpty()) {
                                                keyIndices.add(1)
                                            }
                                            dos.writeInt(keyIndices.size)
                                            for (idx in keyIndices) dos.writeInt(idx)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            outputStream.write(moovWriter.baos.toByteArray())
            outputStream.flush()
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
