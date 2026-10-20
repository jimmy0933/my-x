package com.example.xdownloader.api

import com.example.xdownloader.api.TweetParser
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Test
import org.junit.Assert.*

class TweetParserTest {
    @Test
    fun testParseMediaTimelineErrors() {
        // Test case 1: Errors with entirely missing data block
        val jsonStr1 = """
        {
            "errors": [
                {
                    "message": "Some error"
                }
            ]
        }
        """
        try {
            TweetParser.parseMediaTimeline(JsonParser.parseString(jsonStr1).asJsonObject)
            fail("Should throw Exception")
        } catch (e: Exception) {
            assertEquals("GraphQL Error: Some error", e.message)
        }

        // Test case 2: Errors with data present but missing required nodes (e.g. user)
        val jsonStr2 = """
        {
            "data": {},
            "errors": [
                {
                    "message": "Missing user"
                }
            ]
        }
        """
        try {
            TweetParser.parseMediaTimeline(JsonParser.parseString(jsonStr2).asJsonObject)
            fail("Should throw Exception")
        } catch (e: Exception) {
            assertEquals("GraphQL Error: Missing user", e.message)
        }

        // Test case 3: Data present, timeline_v2 missing, errors present
        val jsonStr3 = """
        {
            "data": {
                "user": {
                    "result": {}
                }
            },
            "errors": [
                {
                    "message": "Rate limited"
                }
            ]
        }
        """
        try {
            TweetParser.parseMediaTimeline(JsonParser.parseString(jsonStr3).asJsonObject)
            fail("Should throw Exception")
        } catch (e: Exception) {
            assertEquals("GraphQL Error: Rate limited", e.message)
        }

        // Test case 4: Valid empty list (no errors, timeline missing instructions)
        val jsonStr4 = """
        {
            "data": {
                "user": {
                    "result": {
                        "timeline_v2": {
                            "timeline": {}
                        }
                    }
                }
            }
        }
        """
        val (tweets, cursor) = TweetParser.parseMediaTimeline(JsonParser.parseString(jsonStr4).asJsonObject)
        assertTrue(tweets.isEmpty())
        assertNull(cursor)
    }

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
