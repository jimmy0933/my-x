package com.example.xdownloader

import com.example.xdownloader.api.ApiResult
import org.junit.Test
import org.junit.Assert.*

class ApiResultTest {
    @Test
    fun testApiResultStates() {
        val success = ApiResult.Success("data")
        assertTrue(success is ApiResult.Success)
        assertEquals("data", success.data)

        val error1 = ApiResult.Unauthorized("auth")
        assertTrue(error1 is ApiResult.Unauthorized)

        val error2 = ApiResult.RateLimited("limit")
        assertTrue(error2 is ApiResult.RateLimited)
    }
}
