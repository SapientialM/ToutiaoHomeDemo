package com.example.toutiao.data.remote.mediator

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.example.toutiao.data.local.database.AppDatabase
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
    private val appDatabase: AppDatabase,
) : RemoteMediator<Int, FeedItemEntity>() {

    // LAUNCH_INITIAL_REFRESH 确保首次安装/DB清空时自动加载数据。
    // 用户下拉刷新时也会触发 REFRESH 清空并重新加载。
    // Tab 切换重建 Pager 时同样会触发初始刷新，因 MockDataSource 是本地数据源，
    // 延迟极低（<50ms），不会造成可见的闪烁。
    override suspend fun initialize(): InitializeAction {
        return InitializeAction.LAUNCH_INITIAL_REFRESH
    }

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, FeedItemEntity>,
    ): MediatorResult {
        val pageSize = state.config.pageSize  // 用 Pager 实际配置的 pageSize
        val page = when (loadType) {
            LoadType.REFRESH -> 0
            LoadType.PREPEND -> {
                return MediatorResult.Success(endOfPaginationReached = true)
            }
            LoadType.APPEND -> {
                // 从 lastItemOrNull().id 解析 page：id 格式 "${channel}_p${page}_${index}_${hash}"
                val lastId = state.lastItemOrNull()?.id
                val nextPage = lastId?.let { parsePageFromId(it) + 1 } ?: 1
                nextPage
            }
        }

        try {
            Timber.d("NewsRemoteMediator.load — loadType=$loadType, channel=$channel, page=$page")
            val response = remoteDataSource.getNewsFeed(channel = channel, page = page, size = pageSize)
            Timber.d("NewsRemoteMediator.load — response code=${response.code}, items=${response.data.list.size}, hasMore=${response.data.hasMore}")

            if (response.code != 0) {
                return MediatorResult.Error(IOException("API error code: ${response.code}"))
            }

            val items = response.data.list
            // 关键：永远返回 endOfPaginationReached = false，让 Pager 持续 append
            // （数据来源有限，靠 page 无限递增 + 循环复用保证用户感觉无限下拉）
            val endOfPaginationReached = false
            val entities = items.map { it.toEntity(channel) }

            val prevKey = if (page == 0) null else page - 1
            val nextKey = page + 1
            val keys = entities.map {
                RemoteKeyEntity(id = it.id, prevKey = prevKey, nextKey = nextKey, channel = channel)
            }

            if (loadType == LoadType.REFRESH) {
                appDatabase.replaceFeedAndKeys(channel, entities, keys)
            } else {
                appDatabase.insertFeedAndKeys(entities, keys)
            }

            Timber.d("NewsRemoteMediator.load — inserted ${entities.size} entities, prevKey=$prevKey, nextKey=$nextKey, endOfPage=$endOfPaginationReached")
            return MediatorResult.Success(endOfPaginationReached = endOfPaginationReached)
        } catch (e: IOException) {
            Timber.e(e, "NewsRemoteMediator.load — IOException")
            return MediatorResult.Error(e)
        } catch (e: HttpException) {
            Timber.e(e, "NewsRemoteMediator.load — HttpException")
            return MediatorResult.Error(e)
        }
    }

    /**
     * 从 entity.id 解析出 page 编号
     * id 格式："${channel}_p${page}_${index}_${title.hashCode()}"
     * 例如："recommend_p3_42_xxxxx" → 3
     */
    private fun parsePageFromId(id: String): Int {
        val match = Regex("""_p(\d+)_""").find(id)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }
}
