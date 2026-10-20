package com.example.xdownloader.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.xdownloader.api.ApiResult
import com.example.xdownloader.api.TweetParser
import com.example.xdownloader.api.XApiClient
import com.example.xdownloader.database.AppDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class HomeScreenViewModel(
    private val db: AppDatabase,
    private val apiClient: XApiClient
) : ViewModel() {

    private val _tweets = MutableStateFlow<List<TweetParser.ParsedTweet>>(emptyList())
    val tweets: StateFlow<List<TweetParser.ParsedTweet>> = _tweets

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _paginationError = MutableStateFlow<String?>(null)
    val paginationError: StateFlow<String?> = _paginationError

    val cursors = mutableMapOf<String, String>()
    val hasMore = mutableMapOf<String, Boolean>()

    private var currentFetchGeneration = 0

    fun loadTimeline(isRefresh: Boolean, isLoadMore: Boolean = false) {
        if (!apiClient.isConfigured()) {
            _errorMessage.value = "請先至設定填寫登入憑證 (Please configure credentials in settings)"
            return
        }

        viewModelScope.launch {
            val myGeneration = ++currentFetchGeneration

            if (!isLoadMore) {
                _isLoading.value = true
            } else {
                _isLoadingMore.value = true
            }

            try {
                val following = db.followDao().getAllFollowing().first()
                if (following.isEmpty()) {
                    _errorMessage.value = "目前沒有追蹤任何人 (No follows yet)"
                } else {
                    val allTweets = if (isRefresh) mutableListOf() else _tweets.value.toMutableList()
                    var partialFailureMsg: String? = null
                    var anySuccess = false

                    val newCursors = mutableMapOf<String, String>()
                    val newHasMore = mutableMapOf<String, Boolean>()

                    for (author in following) {
                        if (!isRefresh && hasMore[author.authorId] == false) continue

                        val cursor = if (isRefresh) null else cursors[author.authorId]
                        when (val result = apiClient.getUserMedia(author.authorId, cursor)) {
                            is ApiResult.Success -> {
                                anySuccess = true
                                val (parsedTweets, nextCursor) = TweetParser.parseMediaTimeline(result.data)
                                allTweets.addAll(parsedTweets)

                                if (nextCursor != null && nextCursor.isNotEmpty() && nextCursor != cursor) {
                                    newCursors[author.authorId] = nextCursor
                                    newHasMore[author.authorId] = true
                                } else {
                                    newHasMore[author.authorId] = false
                                }
                            }
                            is ApiResult.Unauthorized -> {
                                partialFailureMsg = result.message
                                if (isRefresh) allTweets.addAll(_tweets.value.filter { it.authorScreenName == author.authorName || it.authorScreenName == author.authorScreenName })
                            }
                            is ApiResult.RateLimited -> {
                                partialFailureMsg = result.message
                                if (isRefresh) allTweets.addAll(_tweets.value.filter { it.authorScreenName == author.authorName || it.authorScreenName == author.authorScreenName })
                            }
                            is ApiResult.NetworkError -> {
                                partialFailureMsg = result.message
                                if (isRefresh) allTweets.addAll(_tweets.value.filter { it.authorScreenName == author.authorName || it.authorScreenName == author.authorScreenName })
                            }
                            is ApiResult.ParsingError -> {
                                partialFailureMsg = result.message
                                if (isRefresh) allTweets.addAll(_tweets.value.filter { it.authorScreenName == author.authorName || it.authorScreenName == author.authorScreenName })
                            }
                        }
                    }

                    if (myGeneration == currentFetchGeneration) {
                        if (isRefresh) {
                            cursors.clear()
                            hasMore.clear()
                        }
                        cursors.putAll(newCursors)
                        for ((k, v) in newHasMore) hasMore[k] = v

                        val distinctTweets = allTweets.distinctBy { it.tweetId }.sortedByDescending { it.createdAt }

                        val parsedTweetsAdded = distinctTweets.size - (if (isRefresh) 0 else _tweets.value.size)

                        if (anySuccess || (isRefresh && !anySuccess && distinctTweets.isNotEmpty())) {
                            _tweets.value = distinctTweets
                        }

                        if (distinctTweets.isEmpty() && partialFailureMsg != null && !anySuccess) {
                            if (isLoadMore) _paginationError.value = partialFailureMsg else _errorMessage.value = partialFailureMsg
                        } else if (partialFailureMsg == null) {
                            _errorMessage.value = null
                            _paginationError.value = null
                        } else if (partialFailureMsg != null) {
                            if (isLoadMore) _paginationError.value = partialFailureMsg else _errorMessage.value = partialFailureMsg
                        }

                        if (isLoadMore && parsedTweetsAdded == 0 && hasMore.values.any { it } && partialFailureMsg == null) {
                            if (distinctTweets.isEmpty()) {
                                loadTimeline(isRefresh = false, isLoadMore = true)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                if (myGeneration == currentFetchGeneration) {
                    if (_tweets.value.isEmpty()) _errorMessage.value = "發生非預期錯誤: ${e.message}"
                    else if (isLoadMore) _paginationError.value = "發生非預期錯誤: ${e.message}"
                }
            } finally {
                if (myGeneration == currentFetchGeneration) {
                    _isLoading.value = false
                    _isLoadingMore.value = false
                }
            }
        }
    }

    fun loadMore() {
        if (!_isLoadingMore.value && !_isLoading.value && _paginationError.value == null) {
            loadTimeline(isRefresh = false, isLoadMore = true)
        }
    }

    fun dismissPaginationError() {
        _paginationError.value = null
    }
}
