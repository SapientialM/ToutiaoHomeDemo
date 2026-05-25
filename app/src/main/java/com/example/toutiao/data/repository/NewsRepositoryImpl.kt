package com.example.toutiao.data.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.example.toutiao.data.local.dao.FeedDao
import com.example.toutiao.data.local.dao.RemoteKeyDao
import com.example.toutiao.data.mapper.toDomain
import com.example.toutiao.data.mapper.toEntity
import com.example.toutiao.data.remote.datasource.RemoteDataSource
import com.example.toutiao.data.remote.mediator.NewsRemoteMediator
import com.example.toutiao.domain.model.FeedCard
import com.example.toutiao.domain.repository.NewsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class NewsRepositoryImpl @Inject constructor(
    private val remoteDataSource: RemoteDataSource,
    private val feedDao: FeedDao,
    private val remoteKeyDao: RemoteKeyDao,
) : NewsRepository {

    override suspend fun getNewsFeed(channel: String, page: Int, size: Int): List<FeedCard> {
        return try {
            Timber.d("getNewsFeed — channel=$channel, page=$page")
            val response = remoteDataSource.getNewsFeed(channel, page, size)
            if (response.code == 0) {
                val entities = response.data.list.map { it.toEntity(channel) }
                feedDao.insertAll(entities)
                entities.map { it.toDomain() }
            } else {
                Timber.w("getNewsFeed — API returned non-zero code: ${response.code}")
                emptyList()
            }
        } catch (e: Exception) {
            Timber.e(e, "getNewsFeed failed")
            emptyList()
        }
    }

    override suspend fun hasMore(channel: String, page: Int): Boolean {
        return try {
            remoteDataSource.getNewsFeed(channel, page, 1).data.hasMore
        } catch (e: Exception) {
            Timber.e(e, "hasMore failed")
            false
        }
    }

    override fun getCachedFeed(channel: String): Flow<List<FeedCard>> {
        return feedDao.getFeedByChannel(channel).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    @OptIn(ExperimentalPagingApi::class)
    override fun getFeedPagingData(channel: String): Flow<PagingData<FeedCard>> {
        Timber.d("getFeedPagingData — creating Pager for channel=$channel")
        return Pager(
            config = PagingConfig(
                pageSize = 8,
                prefetchDistance = 2,
                enablePlaceholders = false,
            ),
            remoteMediator = NewsRemoteMediator(
                channel = channel,
                remoteDataSource = remoteDataSource,
                feedDao = feedDao,
                remoteKeyDao = remoteKeyDao,
            ),
            pagingSourceFactory = { feedDao.getFeedPagingSource(channel) },
        ).flow.map { pagingData ->
            pagingData.map { it.toDomain() }
        }
    }

    override suspend fun searchNews(query: String): List<FeedCard> {
        Timber.d("searchNews — query=$query")
        return listOf(
            FeedCard.TextTop(
                id = "search_1",
                title = "\"$query\" 相关置顶新闻",
                source = "搜索",
                commentCount = 123,
                publishTime = "刚刚",
            ),
            FeedCard.LeftTextRightImage(
                id = "search_2",
                title = "$query 最新动态：市场反应积极",
                source = "财经网",
                commentCount = 456,
                publishTime = "1小时前",
                imageUrl = "https://picsum.photos/seed/search2/400/300",
            ),
            FeedCard.LargeImage(
                id = "search_3",
                title = "深度解析：$query 背后的真相",
                source = "虎嗅",
                commentCount = 789,
                publishTime = "2小时前",
                imageUrl = "https://picsum.photos/seed/search3/800/450",
            ),
        )
    }
}
