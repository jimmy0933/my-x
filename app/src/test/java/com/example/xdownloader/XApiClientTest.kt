package com.example.xdownloader

import com.example.xdownloader.api.ApiResult
import com.example.xdownloader.api.XApiClient
import com.google.gson.JsonObject
import org.junit.Test
import org.junit.Assert.*

class XApiClientTest {
    // Tests for ApiResult structure
    @Test
    fun testApiResultSuccess() {
        val success = ApiResult.Success("test_data")
        assertEquals("test_data", success.data)
    }

    @Test
    fun testApiResultErrorTypes() {
        val unauthorized = ApiResult.Unauthorized("unauth")
        assertEquals("unauth", unauthorized.message)

        val rateLimited = ApiResult.RateLimited("rate")
        assertEquals("rate", rateLimited.message)

        val parsing = ApiResult.ParsingError("parsing")
        assertEquals("parsing", parsing.message)
    }
}
