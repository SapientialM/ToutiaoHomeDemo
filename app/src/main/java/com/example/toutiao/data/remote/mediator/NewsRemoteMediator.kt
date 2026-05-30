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

// =============================================================================
// NewsRemoteMediator — Paging3 的远程数据加载器
//
// 角色：连接"网络层"和"本地数据库"，实现 Paging3 的 RemoteMediator 协议。
//
// 数据流向：
//   RemoteDataSource.getNewsFeed(channel, page)
//     → NewsFeedResponse (DTO)
//       → toEntity() → FeedItemEntity (Room Entity)
//         → insertAll() → Room 数据库
//           → FeedDao.getFeedPagingSource() 自动感知变化
//             → Flow<PagingData<FeedCard>> 通知 UI
//
// 页码由 Room 的 remote_keys 表持久化管理：
//   - REFRESH 时清理旧数据并写入 page=0 的 remote_keys
//   - APPEND 时查询该 channel 最后一条记录的 nextKey 作为下一页页码
//   - 避免了内存变量在 Paging3 复杂调度下的可见性问题
// =============================================================================
@OptIn(ExperimentalPagingApi::class)
class NewsRemoteMediator(
    private val channel: String,
    private val remoteDataSource: RemoteDataSource,
    private val feedDao: FeedDao,
    private val remoteKeyDao: RemoteKeyDao,
) : RemoteMediator<Int, FeedItemEntity>() {

    // 优先使用本地缓存，避免每次 Pager 创建时（Tab 切换）都清空缓存。
    // 这样断网时 PagingSource 能直接读到 Room 缓存数据，实现离线展示。
    // 用户主动下拉刷新时，load(REFRESH) 会清空并重新加载最新数据。
    override suspend fun initialize(): InitializeAction {
        return InitializeAction.SKIP_INITIAL_REFRESH
    }

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, FeedItemEntity>,
    ): MediatorResult {
        val page = when (loadType) {
            LoadType.REFRESH -> 0
            LoadType.PREPEND -> {
                return MediatorResult.Success(endOfPaginationReached = true)
            }
            LoadType.APPEND -> {
                val remoteKey = remoteKeyDao.getLastRemoteKeyByChannel(channel)
                remoteKey?.nextKey
                    ?: return MediatorResult.Success(endOfPaginationReached = true)
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

            val prevKey = if (page == 0) null else page - 1
            val nextKey = if (endOfPaginationReached) null else page + 1
            val keys = entities.map {
                RemoteKeyEntity(id = it.id, prevKey = prevKey, nextKey = nextKey, channel = channel)
            }

            if (loadType == LoadType.REFRESH) {
                feedDao.replaceByChannel(channel, entities)
                remoteKeyDao.replaceByChannel(channel, keys)
            } else {
                feedDao.insertAll(entities)
                remoteKeyDao.insertAll(keys)
            }

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
}
