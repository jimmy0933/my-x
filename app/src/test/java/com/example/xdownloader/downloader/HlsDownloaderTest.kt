package com.example.xdownloader.downloader

import org.junit.Assert.*
import org.junit.Test

class HlsDownloaderTest {

    @Test
    fun parseMasterPlaylist_extractsVariantsAndAudioTracks() {
        val masterPlaylist = """
            #EXTM3U
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio-aac",NAME="English",URI="audio/playlist.m3u8"
            #EXT-X-STREAM-INF:BANDWIDTH=320000,RESOLUTION=320x180,AUDIO="audio-aac"
            vid/320x180/playlist.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2176000,RESOLUTION=1280x720,AUDIO="audio-aac"
            vid/1280x720/playlist.m3u8
        """.trimIndent()

        val baseUrl = "https://video.twimg.com/ext_tw_video/123/pu/pl/master.m3u8"
        val audioTracks = HlsDownloader.parseAudioTracks(masterPlaylist, baseUrl)
        val variants = HlsDownloader.parseMasterPlaylist(masterPlaylist, baseUrl)

        assertEquals(1, audioTracks.size)
        assertTrue(audioTracks.containsKey("audio-aac"))
        assertEquals("https://video.twimg.com/ext_tw_video/123/pu/pl/audio/playlist.m3u8", audioTracks["audio-aac"]?.url)

        assertEquals(2, variants.size)
        val best = variants.maxByOrNull { it.bandwidth }
        assertNotNull(best)
        assertEquals(2176000L, best?.bandwidth)
        assertEquals("https://video.twimg.com/ext_tw_video/123/pu/pl/vid/1280x720/playlist.m3u8", best?.url)
        assertEquals("audio-aac", best?.audioGroupId)
    }

    @Test
    fun parseMediaPlaylist_extractsInitSegmentAndMediaSegmentsWithByteRanges() {
        val mediaPlaylist = """
            #EXTM3U
            #EXT-X-VERSION:7
            #EXT-X-TARGETDURATION:3
            #EXT-X-MEDIA-SEQUENCE:1
            #EXT-X-MAP:URI="init.mp4",BYTERANGE="1200@0"
            #EXT-X-BYTERANGE:5000@1200
            segment.mp4
            #EXT-X-BYTERANGE:4000
            segment.mp4
            #EXT-X-ENDLIST
        """.trimIndent()

        val baseUrl = "https://video.twimg.com/ext_tw_video/123/pu/vid/1280x720/playlist.m3u8"
        val parsed = HlsDownloader.parseMediaPlaylist(mediaPlaylist, baseUrl)

        assertEquals("https://video.twimg.com/ext_tw_video/123/pu/vid/1280x720/init.mp4", parsed.initSegmentUrl)
        assertEquals(1200L, parsed.initByteRangeLength)
        assertEquals(0L, parsed.initByteRangeOffset)
        assertEquals(HlsDownloader.StreamFormat.MP4, parsed.format)

        assertEquals(2, parsed.segments.size)
        val seg0 = parsed.segments[0]
        assertEquals(5000L, seg0.byteRangeLength)
        assertEquals(1200L, seg0.byteRangeOffset)

        val seg1 = parsed.segments[1]
        assertEquals(4000L, seg1.byteRangeLength)
        assertEquals(6200L, seg1.byteRangeOffset)
    }

    @Test
    fun parseMediaPlaylist_detectsTsFormat() {
        val mediaPlaylist = """
            #EXTM3U
            #EXT-X-TARGETDURATION:10
            #EXTINF:10.0,
            segment_0.ts
            #EXTINF:10.0,
            segment_1.ts
        """.trimIndent()

        val baseUrl = "https://example.com/playlist.m3u8"
        val parsed = HlsDownloader.parseMediaPlaylist(mediaPlaylist, baseUrl)

        assertEquals(HlsDownloader.StreamFormat.TS, parsed.format)
        assertEquals(2, parsed.segments.size)
    }

    @Test
    fun resolveRelativeUrl_handlesAbsoluteAndRelativeUrls() {
        val baseUrl = "https://example.com/media/playlist.m3u8"
        val absolute = "https://cdn.example.com/segment1.ts"
        val relative = "segment1.ts"

        assertEquals(absolute, HlsDownloader.resolveRelativeUrl(baseUrl, absolute))
        assertEquals("https://example.com/media/segment1.ts", HlsDownloader.resolveRelativeUrl(baseUrl, relative))
    }

    @Test
    fun parseMediaPlaylist_withByteRanges_parsesOffsetsAndLengths() {
        val mediaPlaylist = """
            #EXTM3U
            #EXT-X-VERSION:4
            #EXT-X-TARGETDURATION:3
            #EXT-X-MAP:URI="main.mp4",BYTERANGE="500@0"
            #EXT-X-BYTERANGE:1000@500
            main.mp4
            #EXT-X-BYTERANGE:2000
            main.mp4
            #EXT-X-ENDLIST
        """.trimIndent()

        val baseUrl = "https://example.com/media/playlist.m3u8"
        val parsed = HlsDownloader.parseMediaPlaylist(mediaPlaylist, baseUrl)

        assertNotNull(parsed.initSegment)
        assertEquals("https://example.com/media/main.mp4", parsed.initSegment?.url)
        assertEquals(0L, parsed.initSegment?.byteRangeOffset)
        assertEquals(500L, parsed.initSegment?.byteRangeLength)

        assertEquals(2, parsed.segments.size)
        assertEquals("https://example.com/media/main.mp4", parsed.segments[0].url)
        assertEquals(500L, parsed.segments[0].byteRangeOffset)
        assertEquals(1000L, parsed.segments[0].byteRangeLength)

        assertEquals("https://example.com/media/main.mp4", parsed.segments[1].url)
        assertEquals(1500L, parsed.segments[1].byteRangeOffset)
        assertEquals(2000L, parsed.segments[1].byteRangeLength)
    }

    @Test
    fun parseMasterPlaylist_withAudioGroup_associatesAudioUrl() {
        val masterPlaylist = """
            #EXTM3U
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio-aac",NAME="English",DEFAULT=YES,URI="audio/aac/playlist.m3u8"
            #EXT-X-STREAM-INF:BANDWIDTH=2176000,RESOLUTION=1280x720,AUDIO="audio-aac"
            vid/1280x720/playlist.m3u8
        """.trimIndent()

        val baseUrl = "https://example.com/master.m3u8"
        val variants = HlsDownloader.parseMasterPlaylist(masterPlaylist, baseUrl)

        assertEquals(1, variants.size)
        val variant = variants[0]
        assertEquals("https://example.com/vid/1280x720/playlist.m3u8", variant.url)
        assertEquals("https://example.com/audio/aac/playlist.m3u8", variant.audioUrl)
    }

    @Test
    fun downloadHlsStream_remuxesTsToMp4() {
        val videoTrack = HlsDownloader.DemuxedTrack(isVideo = true).apply {
            sps = byteArrayOf(0x67.toByte(), 0x42.toByte(), 0x00.toByte(), 0x1E.toByte())
            pps = byteArrayOf(0x68.toByte(), 0xCE.toByte(), 0x3C.toByte(), 0x80.toByte())
            samples.add(HlsDownloader.DemuxedSample(byteArrayOf(0, 0, 0, 4, 1, 2, 3, 4), 0L, 0L, true))
        }
        val audioTrack = HlsDownloader.DemuxedTrack(isVideo = false).apply {
            audioSpecificConfig = byteArrayOf(0x12.toByte(), 0x10.toByte())
            samples.add(HlsDownloader.DemuxedSample(byteArrayOf(1, 2, 3, 4), 0L, 0L, true))
        }

        val baos = java.io.ByteArrayOutputStream()
        HlsDownloader.Mp4Muxer.mux(videoTrack, audioTrack, baos)

        val bytes = baos.toByteArray()
        assertTrue(bytes.size > 32)
        val headerString = String(bytes, 0, bytes.size.coerceAtMost(200), Charsets.ISO_8859_1)
        assertTrue(headerString.contains("ftyp"))
        assertTrue(headerString.contains("isom"))
        val fullString = String(bytes, Charsets.ISO_8859_1)
        assertTrue(fullString.contains("mdat"))
        assertTrue(fullString.contains("moov"))
    }

    @Test
    fun fetchSegmentBytes_withTruncatedData_returnsNull() {
        val segment = HlsDownloader.Segment("https://invalid.domain.xyz/segment.ts", byteRangeOffset = 0, byteRangeLength = 1000)
        val result = HlsDownloader.fetchSegmentBytes(segment)
        assertNull(result)
    }

    @Test
    fun mp4Muxer_withBFrames_writesCttsBox() {
        val videoTrack = HlsDownloader.DemuxedTrack(isVideo = true).apply {
            sps = byteArrayOf(0x67.toByte(), 0x42.toByte(), 0x00.toByte(), 0x1E.toByte())
            pps = byteArrayOf(0x68.toByte(), 0xCE.toByte(), 0x3C.toByte(), 0x80.toByte())
            samples.add(HlsDownloader.DemuxedSample(byteArrayOf(0, 0, 0, 4, 1, 2, 3, 4), ptsMs = 80L, dtsMs = 0L, isKeyFrame = true))
            samples.add(HlsDownloader.DemuxedSample(byteArrayOf(0, 0, 0, 4, 5, 6, 7, 8), ptsMs = 40L, dtsMs = 40L, isKeyFrame = false))
        }

        val baos = java.io.ByteArrayOutputStream()
        HlsDownloader.Mp4Muxer.mux(videoTrack, null, baos)

        val fullString = String(baos.toByteArray(), Charsets.ISO_8859_1)
        assertTrue(fullString.contains("ctts"))
    }

    @Test
    fun downloadHlsStream_withFmp4AndIndependentAudio_muxesSuccessfully() {
        val demuxer = HlsDownloader.Fmp4Demuxer()
        demuxer.videoTrack.sps = byteArrayOf(0x67.toByte(), 0x42.toByte(), 0x00.toByte(), 0x1E.toByte())
        demuxer.videoTrack.samples.add(HlsDownloader.DemuxedSample(byteArrayOf(0, 0, 0, 4, 1, 2, 3, 4), 0L, 0L, true))
        demuxer.audioTrack.audioSpecificConfig = byteArrayOf(0x12.toByte(), 0x10.toByte())
        demuxer.audioTrack.samples.add(HlsDownloader.DemuxedSample(byteArrayOf(1, 2, 3, 4), 0L, 0L, true))

        val baos = java.io.ByteArrayOutputStream()
        HlsDownloader.Mp4Muxer.mux(demuxer.videoTrack, demuxer.audioTrack, baos)

        val bytes = baos.toByteArray()
        assertTrue(bytes.size > 32)
        val fullString = String(bytes, Charsets.ISO_8859_1)
        assertTrue(fullString.contains("ftyp"))
        assertTrue(fullString.contains("mdat"))
        assertTrue(fullString.contains("moov"))
    }
}
