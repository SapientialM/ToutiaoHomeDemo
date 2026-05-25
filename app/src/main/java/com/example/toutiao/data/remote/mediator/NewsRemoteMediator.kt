package com.example.toutiao.data.remote.mediator

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.example.toutiao.data.local.dao.FeedDao
import com.example.toutiao.data.local.dao.RemoteKeyDao
import com.example.toutiao.data.local.entity.FeedItemEntity
import com.example.toutiao.data.local.entity.RemoteKeyEntity
import com.example.toutiao.data.mapper.toEntity
import com.example.toutiao.data.remote.datasource.RemoteDataSource
import retrofit2.HttpException
import timber.log.Timber
import java.io.IOException

@OptIn(ExperimentalPagingApi::class)
class NewsRemoteMediator(
    private val channel: String,
    private val remoteDataSource: RemoteDataSource,
    private val feedDao: FeedDao,
    private val remoteKeyDao: RemoteKeyDao,
) : RemoteMediator<Int, FeedItemEntity>() {

    override suspend fun initialize(): InitializeAction {
        return InitializeAction.LAUNCH_INITIAL_REFRESH
    }

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, FeedItemEntity>,
    ): MediatorResult {
        val page = when (loadType) {
            LoadType.REFRESH -> {
                val remoteKey = getRemoteKeyClosestToCurrentPosition(state)
                remoteKey?.nextKey?.minus(1) ?: 0
            }

            LoadType.PREPEND -> {
                val remoteKey = getRemoteKeyForFirstItem(state)
                val prevKey = remoteKey?.prevKey
                    ?: return MediatorResult.Success(endOfPaginationReached = remoteKey != null)
                prevKey
            }

            LoadType.APPEND -> {
                val remoteKey = getRemoteKeyForLastItem(state)
                val nextKey = remoteKey?.nextKey
                    ?: return MediatorResult.Success(endOfPaginationReached = remoteKey != null)
                nextKey
            }
        }

        try {
            Timber.d("NewsRemoteMediator.load — loadType=$loadType, channel=$channel, page=$page")
            val response = remoteDataSource.getNewsFeed(channel = channel, page = page)
            Timber.d("NewsRemoteMediator.load — response code=${response.code}, items=${response.data.list.size}, hasMore=${response.data.hasMore}")

            if (response.code != 0) {
                return MediatorResult.Error(IOException("API error code: ${response.code}"))
            }

            val items = response.data.list
            val hasMore = response.data.hasMore
            val endOfPaginationReached = !hasMore
            val entities = items.map { it.toEntity(channel) }

            if (loadType == LoadType.REFRESH) {
                feedDao.deleteByChannel(channel)
                remoteKeyDao.deleteByChannel(channel)
            }

            val prevKey = if (page == 0) null else page - 1
            val nextKey = if (endOfPaginationReached) null else page + 1
            val keys = entities.map {
                RemoteKeyEntity(id = it.id, prevKey = prevKey, nextKey = nextKey, channel = channel)
            }

            feedDao.insertAll(entities)
            remoteKeyDao.insertAll(keys)

            Timber.d("NewsRemoteMediator.load — inserted ${entities.size} entities, prevKey=$prevKey, nextKey=$nextKey")
            return MediatorResult.Success(endOfPaginationReached = endOfPaginationReached)
        } catch (e: IOException) {
            Timber.e(e, "NewsRemoteMediator.load — IOException")
            return MediatorResult.Error(e)
        } catch (e: HttpException) {
            Timber.e(e, "NewsRemoteMediator.load — HttpException")
            return MediatorResult.Error(e)
        }
    }

    private suspend fun getRemoteKeyForLastItem(state: PagingState<Int, FeedItemEntity>): RemoteKeyEntity? {
        return state.pages.lastOrNull { it.data.isNotEmpty() }?.data?.lastOrNull()
            ?.let { feed -> remoteKeyDao.getRemoteKey(feed.id) }
    }

    private suspend fun getRemoteKeyForFirstItem(state: PagingState<Int, FeedItemEntity>): RemoteKeyEntity? {
        return state.pages.firstOrNull { it.data.isNotEmpty() }?.data?.firstOrNull()
            ?.let { feed -> remoteKeyDao.getRemoteKey(feed.id) }
    }

    private suspend fun getRemoteKeyClosestToCurrentPosition(state: PagingState<Int, FeedItemEntity>): RemoteKeyEntity? {
        return state.anchorPosition?.let { position ->
            state.closestItemToPosition(position)?.id?.let { id ->
                remoteKeyDao.getRemoteKey(id)
            }
        }
    }
}
