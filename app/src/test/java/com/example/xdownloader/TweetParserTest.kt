package com.example.xdownloader

import com.example.xdownloader.api.TweetParser
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Test
import org.junit.Assert.*

class TweetParserTest {
    @Test
    fun testParseSingleTweetResult() {
        val jsonStr = """
        {
            "legacy": {
                "id_str": "12345",
                "full_text": "Hello World",
                "created_at": "Mon Oct 28 12:00:00 +0000 2024"
            },
            "core": {
                "user_results": {
                    "result": {
                        "legacy": {
                            "name": "Test User",
                            "screen_name": "testuser",
                            "profile_image_url_https": "http://example.com/avatar.jpg"
                        }
                    }
                }
            }
        }
        """
        val json = JsonParser.parseString(jsonStr).asJsonObject
        val parsed = TweetParser.parseSingleTweetResult(json)

        assertNotNull(parsed)
        assertEquals("12345", parsed?.tweetId)
        assertEquals("Hello World", parsed?.text)
        assertEquals("Test User", parsed?.authorName)
        assertEquals("testuser", parsed?.authorScreenName)
        assertTrue(parsed!!.createdAt > 0L)
    }
}
