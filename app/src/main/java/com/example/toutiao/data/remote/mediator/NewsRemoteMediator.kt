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
//       Paging3 在需要数据时调用 load()，NewsRemoteMediator 负责：
//         1. 从 RemoteDataSource 获取一页数据（网络/Mock）
//         2. 写入 Room（feed_items + remote_keys 两张表）
//         3. 返回成功/失败结果
//
// 调用时机（由 Paging3 框架自动触发，无需手动调用）：
//   LoadType.REFRESH → 下拉刷新 或 首次加载 或 Tab 切换
//   LoadType.APPEND  → 滑动到底部，加载下一页
//   LoadType.PREPEND → 向前翻页（通常不触发）
//
// 数据流向：
//   RemoteDataSource.getNewsFeed(channel, page)
//     → NewsFeedResponse (DTO)
//       → toEntity()  → FeedItemEntity (Room Entity)
//         → insertAll() → Room 数据库
//           → FeedDao.getFeedPagingSource() 自动感知变化
//             → Flow<PagingData<FeedCard>> 通知 UI
//
// remote_keys 表的作用：
//   记录每一条数据的"下一页页码"和"上一页页码"。
//   下次 REFRESH/APPEND/PREPEND 时从 remote_keys 查询当前锚点位置对应的页码，
//   从而知道该请求哪一页数据。
// =============================================================================
@OptIn(ExperimentalPagingApi::class)
class NewsRemoteMediator(
    private val channel: String,
    private val remoteDataSource: RemoteDataSource,
    private val feedDao: FeedDao,
    private val remoteKeyDao: RemoteKeyDao,
) : RemoteMediator<Int, FeedItemEntity>() {

    // Paging3 启动时调用，决定初始行为：
    // LAUNCH_INITIAL_REFRESH → 立即触发一次 REFRESH 加载首页数据
    override suspend fun initialize(): InitializeAction {
        return InitializeAction.LAUNCH_INITIAL_REFRESH
    }

    // Paging3 核心回调：当需要加载数据时由框架调用
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
            // A. 从数据源获取一页数据（MockDataSource 或 RealRemoteDataSource）
            val response = remoteDataSource.getNewsFeed(channel = channel, page = page)
            Timber.d("NewsRemoteMediator.load — response code=${response.code}, items=${response.data.list.size}, hasMore=${response.data.hasMore}")

            if (response.code != 0) {
                return MediatorResult.Error(IOException("API error code: ${response.code}"))
            }

            val items = response.data.list
            val hasMore = response.data.hasMore
            val endOfPaginationReached = !hasMore
            // B. DTO → Entity（NewsItemDto → FeedItemEntity，通过 NewsMapper.toEntity）
            val entities = items.map { it.toEntity(channel) }

            // C. 计算 remote_keys：记录 prev/next 页码，供下次 REFRESH/APPEND 查询
            val prevKey = if (page == 0) null else page - 1
            val nextKey = if (endOfPaginationReached) null else page + 1
            val keys = entities.map {
                RemoteKeyEntity(id = it.id, prevKey = prevKey, nextKey = nextKey, channel = channel)
            }

            // D. 写入 Room：
            //    REFRESH 时先清旧数据再写入新数据（在 load 成功后执行，避免清空后加载失败导致 UI 空态）
            //    APPEND 时直接追加
            //    Room 写入后，FeedDao.getFeedPagingSource() 自动感知变化，
            //    通过 Flow 发射新数据，触发 UI 重组
            if (loadType == LoadType.REFRESH) {
                feedDao.deleteByChannel(channel)
                remoteKeyDao.deleteByChannel(channel)
            }
            feedDao.insertAll(entities)
            remoteKeyDao.insertAll(keys)

            Timber.d("NewsRemoteMediator.load — inserted ${entities.size} entities, prevKey=$prevKey, nextKey=$nextKey")
            // F. 返回成功，Paging3 继续从 Room 读取并通知 UI
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
