package com.example.xdownloader

import com.example.xdownloader.data.MediaItem
import com.example.xdownloader.data.MediaType
import com.example.xdownloader.utils.MediaSnifferUtils
import org.junit.Assert.*
import org.junit.Test

class MediaSnifferUtilsTest {

    @Test
    fun sanitizeImageUrl_transformsToOrigFormat() {
        val rawUrl = "https://pbs.twimg.com/media/G123456?format=jpg&name=900x900"
        val expected = "https://pbs.twimg.com/media/G123456?format=jpg&name=orig"
        val result = MediaSnifferUtils.sanitizeImageUrl(rawUrl)
        assertEquals(expected, result)
    }

    @Test
    fun sanitizeImageUrl_appendsOrigIfMissing() {
        val rawUrl = "https://pbs.twimg.com/media/G123456?format=jpg"
        val expected = "https://pbs.twimg.com/media/G123456?format=jpg&name=orig"
        val result = MediaSnifferUtils.sanitizeImageUrl(rawUrl)
        assertEquals(expected, result)
    }

    @Test
    fun isContentImage_filtersProfileAndEmoji() {
        val mediaImg = "https://pbs.twimg.com/media/ABCDEF.jpg"
        val profileImg = "https://pbs.twimg.com/profile_images/1234/avatar.jpg"
        val emojiImg = "https://abs.twimg.com/emoji/v2/72x72/1f600.png"

        assertTrue(MediaSnifferUtils.isContentImage(mediaImg))
        assertFalse(MediaSnifferUtils.isContentImage(profileImg))
        assertFalse(MediaSnifferUtils.isContentImage(emojiImg))
    }

    @Test
    fun isVideoUrl_identifiesMp4AndHlsPlaylistAndExcludesSegments() {
        val mp4Url = "https://video.twimg.com/ext_tw_video/1234/pu/vid/1280x720/sample.mp4"
        val m3u8Url = "https://video.twimg.com/ext_tw_video/1234/pu/pl/playlist.m3u8"
        val tsUrl = "https://video.twimg.com/ext_tw_video/1234/pu/vid/segment_0.ts"
        val m4sUrl = "https://video.twimg.com/ext_tw_video/1234/pu/vid/segment_1.m4s?tag=10"
        val initMp4Url = "https://video.twimg.com/ext_tw_video/1234/pu/vid/init.mp4?tag=10"
        val imageUrl = "https://pbs.twimg.com/media/ABCDEF.jpg"

        assertTrue(MediaSnifferUtils.isVideoUrl(mp4Url))
        assertTrue(MediaSnifferUtils.isVideoUrl(m3u8Url))
        assertFalse(MediaSnifferUtils.isVideoUrl(tsUrl))
        assertFalse(MediaSnifferUtils.isVideoUrl(m4sUrl))
        assertFalse(MediaSnifferUtils.isVideoUrl(initMp4Url))
        assertFalse(MediaSnifferUtils.isVideoUrl(imageUrl))
    }

    @Test
    fun extractBestVideoUrl_prefersMp4OverHlsAndFallsBackToHls() {
        val mp4AndHlsUrls = listOf(
            "https://video.twimg.com/ext_tw_video/123/vid/320x180/small.mp4",
            "https://video.twimg.com/ext_tw_video/123/vid/1280x720/hd.mp4",
            "https://video.twimg.com/ext_tw_video/123/vid/1920x1080/init.mp4",
            "https://video.twimg.com/ext_tw_video/123/vid/1920x1080/segment_1.m4s",
            "https://video.twimg.com/ext_tw_video/123/pu/pl/playlist.m3u8",
            "https://video.twimg.com/ext_tw_video/123/vid/640x360/sd.mp4"
        )
        val bestMp4 = MediaSnifferUtils.extractBestVideoUrl(mp4AndHlsUrls)
        assertEquals("https://video.twimg.com/ext_tw_video/123/vid/1280x720/hd.mp4", bestMp4)

        val hlsOnlyUrls = listOf(
            "https://video.twimg.com/ext_tw_video/123/vid/1920x1080/init.mp4",
            "https://video.twimg.com/ext_tw_video/123/vid/1920x1080/segment_1.m4s",
            "https://video.twimg.com/ext_tw_video/123/pu/pl/playlist.m3u8"
        )
        val fallbackHls = MediaSnifferUtils.extractBestVideoUrl(hlsOnlyUrls)
        assertEquals("https://video.twimg.com/ext_tw_video/123/pu/pl/playlist.m3u8", fallbackHls)
    }

    @Test
    fun generateUniqueFileName_producesUniqueNamesForSameItem() {
        val item = MediaItem(
            id = "test_item",
            url = "https://video.twimg.com/ext_tw_video/123/sample.mp4",
            thumbnailUrl = "",
            type = MediaType.VIDEO
        )

        val names = (1..50).map { MediaSnifferUtils.generateUniqueFileName(item) }.toSet()
        assertEquals(50, names.size)
        assertTrue(names.all { it.startsWith("X_") && it.endsWith(".mp4") })
    }
}
