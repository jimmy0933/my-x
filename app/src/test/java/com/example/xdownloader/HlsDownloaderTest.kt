package com.example.xdownloader

import com.example.xdownloader.downloader.HlsDownloader
import org.junit.Test
import org.junit.Assert.*

class HlsDownloaderTest {
    @Test
    fun testUrlResolution() {
        val downloader = HlsDownloader
        val resolvedUrl = downloader.resolveRelativeUrl("https://example.com/base/playlist.m3u8", "segment.ts")
        assertEquals("https://example.com/base/segment.ts", resolvedUrl)

        val absoluteResolvedUrl = downloader.resolveRelativeUrl("https://example.com/base/playlist.m3u8", "https://other.com/segment.ts")
        assertEquals("https://other.com/segment.ts", absoluteResolvedUrl)
    }
}
