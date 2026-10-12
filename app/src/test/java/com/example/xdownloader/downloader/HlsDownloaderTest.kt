package com.example.xdownloader.downloader

import org.junit.Assert.*
import org.junit.Test

class HlsDownloaderTest {

    @Test
    fun parseMasterPlaylist_extractsVariantsAndResolvesUrls() {
        val masterPlaylist = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=320000,RESOLUTION=320x180
            vid/320x180/playlist.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2176000,RESOLUTION=1280x720
            vid/1280x720/playlist.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=832000,RESOLUTION=640x360
            vid/640x360/playlist.m3u8
        """.trimIndent()

        val baseUrl = "https://video.twimg.com/ext_tw_video/123/pu/pl/master.m3u8"
        val variants = HlsDownloader.parseMasterPlaylist(masterPlaylist, baseUrl)

        assertEquals(3, variants.size)
        val best = variants.maxByOrNull { it.bandwidth }
        assertNotNull(best)
        assertEquals(2176000L, best?.bandwidth)
        assertEquals("https://video.twimg.com/ext_tw_video/123/pu/pl/vid/1280x720/playlist.m3u8", best?.url)
    }

    @Test
    fun parseMediaPlaylist_extractsInitSegmentAndMediaSegments() {
        val mediaPlaylist = """
            #EXTM3U
            #EXT-X-VERSION:7
            #EXT-X-TARGETDURATION:3
            #EXT-X-MEDIA-SEQUENCE:1
            #EXT-X-MAP:URI="init.mp4"
            #EXTINF:3.000,
            segment_1.m4s
            #EXTINF:3.000,
            segment_2.m4s
            #EXT-X-ENDLIST
        """.trimIndent()

        val baseUrl = "https://video.twimg.com/ext_tw_video/123/pu/vid/1280x720/playlist.m3u8"
        val parsed = HlsDownloader.parseMediaPlaylist(mediaPlaylist, baseUrl)

        assertEquals("https://video.twimg.com/ext_tw_video/123/pu/vid/1280x720/init.mp4", parsed.initSegmentUrl)
        assertEquals(2, parsed.segmentUrls.size)
        assertEquals("https://video.twimg.com/ext_tw_video/123/pu/vid/1280x720/segment_1.m4s", parsed.segmentUrls[0])
        assertEquals("https://video.twimg.com/ext_tw_video/123/pu/vid/1280x720/segment_2.m4s", parsed.segmentUrls[1])
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
