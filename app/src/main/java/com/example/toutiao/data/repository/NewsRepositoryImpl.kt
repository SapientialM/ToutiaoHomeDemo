package com.example.toutiao.data.repository

import com.example.toutiao.data.local.dao.FeedDao
import com.example.toutiao.data.mapper.toDomain
import com.example.toutiao.data.mapper.toEntity
import com.example.toutiao.data.remote.api.NewsApi
import com.example.toutiao.domain.model.FeedCard
import com.example.toutiao.domain.repository.NewsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class NewsRepositoryImpl @Inject constructor(
    private val newsApi: NewsApi,
    private val feedDao: FeedDao,
) : NewsRepository {

    override suspend fun getNewsFeed(channel: String, page: Int, size: Int): List<FeedCard> {
        return try {
            Timber.d("getNewsFeed — calling API: channel=$channel, page=$page, size=$size")
            val response = newsApi.getNewsFeed(channel, page, size)
            Timber.d("getNewsFeed — API response: code=${response.code}, listSize=${response.data.list.size}")
            if (response.code == 0) {
                val entities = response.data.list.map { it.toEntity(channel) }
                Timber.d("getNewsFeed — mapped ${entities.size} entities, inserting into Room")
                feedDao.insertAll(entities)
                Timber.d("getNewsFeed — Room insert done")
                val cards = entities.map { it.toDomain() }
                Timber.d("getNewsFeed — returning ${cards.size} FeedCard items")
                cards
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
            val response = newsApi.getNewsFeed(channel, page, 1)
            response.data.hasMore
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
}
