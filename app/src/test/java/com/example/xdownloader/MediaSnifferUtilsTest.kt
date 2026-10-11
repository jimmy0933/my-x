package com.example.xdownloader

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
    fun isVideoUrl_identifiesTwimgVideos() {
        val videoUrl = "https://video.twimg.com/ext_tw_video/1234/pu/vid/1280x720/sample.mp4"
        val imageUrl = "https://pbs.twimg.com/media/ABCDEF.jpg"

        assertTrue(MediaSnifferUtils.isVideoUrl(videoUrl))
        assertFalse(MediaSnifferUtils.isVideoUrl(imageUrl))
    }

    @Test
    fun extractBestVideoUrl_selectsHighestResolutionMp4() {
        val urls = listOf(
            "https://video.twimg.com/ext_tw_video/123/vid/320x180/small.mp4",
            "https://video.twimg.com/ext_tw_video/123/vid/1280x720/hd.mp4",
            "https://video.twimg.com/ext_tw_video/123/vid/640x360/sd.mp4"
        )
        val best = MediaSnifferUtils.extractBestVideoUrl(urls)
        assertEquals("https://video.twimg.com/ext_tw_video/123/vid/1280x720/hd.mp4", best)
    }
}
