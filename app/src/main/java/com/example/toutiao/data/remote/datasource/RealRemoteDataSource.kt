package com.example.toutiao.data.remote.datasource

import com.example.toutiao.data.remote.api.NewsApi
import com.example.toutiao.data.remote.dto.NewsFeedResponse
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 真实网络数据源，委托给 Retrofit [NewsApi]。
 * 对接真实后端时切换至此实现。
 */
@Singleton
class RealRemoteDataSource @Inject constructor(
    private val newsApi: NewsApi,
) : RemoteDataSource {

    override suspend fun getNewsFeed(channel: String, page: Int, size: Int): NewsFeedResponse {
        return newsApi.getNewsFeed(channel, page, size)
    }
}
