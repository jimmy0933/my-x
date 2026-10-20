package com.example.xdownloader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.xdownloader.api.ApiResult
import com.example.xdownloader.api.XApiClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import java.lang.reflect.Field
import org.mockito.Mockito.*
import org.mockito.kotlin.any

@RunWith(RobolectricTestRunner::class)
class XApiClientParsingLogicTest {

    private lateinit var mockContext: Context
    private lateinit var apiClient: XApiClient
    private lateinit var mockCall: Call

    @Before
    fun setup() {
        mockContext = ApplicationProvider.getApplicationContext<Context>()
        apiClient = XApiClient(mockContext)
        mockCall = mock(Call::class.java)
    }

    private fun injectMockClientAndReturnResult(response: Response): ApiResult<String> {
        val mockClient = mock(OkHttpClient::class.java)
        val mockBuilder = mock(OkHttpClient.Builder::class.java)

        `when`(mockClient.newBuilder()).thenReturn(mockBuilder)
        `when`(mockBuilder.addInterceptor(any<Interceptor>())).thenReturn(mockBuilder)
        `when`(mockBuilder.build()).thenReturn(mockClient)

        `when`(mockClient.newCall(any())).thenReturn(mockCall)
        `when`(mockCall.execute()).thenReturn(response)

        // Inject the mocked client using reflection
        val clientField: Field = XApiClient::class.java.getDeclaredField("client")
        clientField.isAccessible = true
        clientField.set(apiClient, mockClient)

        return runBlocking {
            apiClient.verifyCredentials("dummy_auth", "dummy_ct0")
        }
    }

    private fun createMockResponse(code: Int, body: String): Response {
        val responseBody = body.toResponseBody("application/json".toMediaTypeOrNull())
        return Response.Builder()
            .request(Request.Builder().url("https://twitter.com").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("Mock response")
            .body(responseBody)
            .build()
    }

    @Test
    fun testValidJsonResponse() {
        val response = createMockResponse(200, """{"screen_name": "testuser"}""")
        val result = injectMockClientAndReturnResult(response)
        assertTrue(result is ApiResult.Success)
        assertEquals("testuser", (result as ApiResult.Success).data)
    }

    @Test
    fun testHtmlResponse() {
        val response = createMockResponse(200, "<!DOCTYPE html><html><body>Error</body></html>")
        val result = injectMockClientAndReturnResult(response)
        assertTrue(result is ApiResult.ParsingError)
        assertEquals("收到 HTML 回應，非預期的 JSON。可能已被阻擋或需重新登入。", (result as ApiResult.ParsingError).message)
    }

    @Test
    fun testMissingScreenName() {
        val response = createMockResponse(200, """{"some_other_field": "value"}""")
        val result = injectMockClientAndReturnResult(response)
        assertTrue(result is ApiResult.ParsingError)
        assertEquals("無法找到帳號資訊 (missing screen_name)", (result as ApiResult.ParsingError).message)
    }

    @Test
    fun testErrorResponse() {
        val response = createMockResponse(200, """{"errors": [{"message": "Bad Token"}]}""")
        val result = injectMockClientAndReturnResult(response)
        assertTrue(result is ApiResult.ParsingError)
        assertEquals("回應包含錯誤訊息", (result as ApiResult.ParsingError).message)
    }

    @Test
    fun testHttpErrorCodes() {
        val response401 = createMockResponse(401, "")
        val result401 = injectMockClientAndReturnResult(response401)
        assertTrue(result401 is ApiResult.Unauthorized)

        val response429 = createMockResponse(429, "")
        val result429 = injectMockClientAndReturnResult(response429)
        assertTrue(result429 is ApiResult.RateLimited)
    }
}
