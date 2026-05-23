package com.example.toutiao.data.remote.api

import com.example.toutiao.data.remote.dto.NewsFeedResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface NewsApi {
    @GET("api/news/feed")
    suspend fun getNewsFeed(
        @Query("channel") channel: String,
        @Query("page") page: Int,
        @Query("size") size: Int = 20,
    ): NewsFeedResponse
}
