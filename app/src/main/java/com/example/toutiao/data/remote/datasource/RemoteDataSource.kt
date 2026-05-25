package com.example.toutiao.data.remote.datasource

import com.example.toutiao.data.remote.dto.NewsFeedResponse

/**
 * 远程数据源抽象接口。
 * Repository 层依赖此接口而非具体的 NewsApi，实现网络数据源的可替换性。
 */
interface RemoteDataSource {
    suspend fun getNewsFeed(channel: String, page: Int, size: Int = 20): NewsFeedResponse
}
