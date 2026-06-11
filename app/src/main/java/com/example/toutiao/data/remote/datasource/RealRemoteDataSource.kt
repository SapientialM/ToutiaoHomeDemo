package com.example.toutiao.data.remote.datasource

import com.example.toutiao.data.remote.api.NewsApi
import com.example.toutiao.data.remote.dto.NewsFeedResponse
import com.example.toutiao.domain.model.HotListItem
import com.example.toutiao.domain.model.HotQuickAction
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

    override suspend fun getVideoFeed(tab: String, page: Int, size: Int): NewsFeedResponse {
        // TODO: 接入真实视频API
        return newsApi.getNewsFeed(tab.ifBlank { "video" }, page, size)
    }

    override suspend fun searchNews(query: String, page: Int, size: Int): NewsFeedResponse {
        // TODO: 接入真实搜索API
        return newsApi.getNewsFeed("recommend", page, size)
    }

    override suspend fun getHotList(): Pair<List<HotQuickAction>, List<HotListItem>> {
        // TODO: 接入真实热榜 API
        return emptyList<HotQuickAction>() to emptyList()
    }
}
