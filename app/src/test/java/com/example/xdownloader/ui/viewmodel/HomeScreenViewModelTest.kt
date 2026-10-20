package com.example.xdownloader.ui.viewmodel

import com.example.xdownloader.api.ApiResult
import com.example.xdownloader.api.TweetParser
import com.example.xdownloader.api.XApiClient
import com.example.xdownloader.database.AppDatabase
import com.example.xdownloader.database.FollowDao
import com.example.xdownloader.database.FollowEntity
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class HomeScreenViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testPartialFailureRetainsCursors() = runTest(testDispatcher) {
        val db = mock(AppDatabase::class.java)
        val followDao = mock(FollowDao::class.java)
        val apiClient = mock(XApiClient::class.java)

        `when`(db.followDao()).thenReturn(followDao)
        `when`(followDao.getAllFollowing()).thenReturn(
            flowOf(
                listOf(
                    FollowEntity("user1", "User 1", "user1", ""),
                    FollowEntity("user2", "User 2", "user2", "")
                )
            )
        )

        `when`(apiClient.isConfigured()).thenReturn(true)

        val successJson = JsonObject()
        val dataObj = JsonObject()
        val userObj = JsonObject()
        val resultObj = JsonObject()
        val timelineV2Obj = JsonObject()
        val timelineObj = JsonObject()

        timelineObj.add("instructions", com.google.gson.JsonArray())
        timelineV2Obj.add("timeline", timelineObj)
        resultObj.add("timeline_v2", timelineV2Obj)
        userObj.add("result", resultObj)
        dataObj.add("user", userObj)
        successJson.add("data", dataObj)

        `when`(apiClient.getUserMedia("user1", null)).thenReturn(ApiResult.Success(successJson))
        `when`(apiClient.getUserMedia("user2", null)).thenReturn(ApiResult.NetworkError("Failed"))

        val viewModel = HomeScreenViewModel(db, apiClient)
        viewModel.loadTimeline(isRefresh = true)

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Failed", viewModel.errorMessage.value)
    }
}
