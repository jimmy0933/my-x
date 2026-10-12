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
}
