package com.example.toutiao.data.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.example.toutiao.data.local.dao.FeedDao
import com.example.toutiao.data.local.database.AppDatabase
import com.example.toutiao.data.mapper.toDomain
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
    private val appDatabase: AppDatabase,
) : NewsRepository {

    // =========================================================================
    // getFeedPagingData — Paging3 分页数据流的主入口
    //
    // 这是 ViewModel 调用的核心方法，返回 Flow<PagingData<FeedCard>>。
    //
    // Pager 是 Paging3 的核心组件，它组合了两个数据通道：
    //
    //   ┌─────────────────────────────────────────────────┐
    //   │                    Pager                         │
    //   │                                                  │
    //   │  remoteMediator: NewsRemoteMediator              │
    //   │    ↓ 负责：从网络/Mock 获取数据 → 写入 Room      │
    //   │    ↓ 触发时机：REFRESH / APPEND / PREPEND        │
    //   │                                                  │
    //   │  pagingSourceFactory: FeedDao.getFeedPagingSource│
    //   │    ↓ 负责：从 Room 读取数据                      │
    //   │    ↓ 触发时机：Room 数据变化时自动通知            │
    //   │                                                  │
    //   │  两者协作：                                       │
    //   │    RemoteMediator 写入 Room                      │
    //   │    → PagingSource 感知 Room 变化                 │
    //   │    → 发射新 PagingData                           │
    //   │    → UI 自动重组                                 │
    //   └─────────────────────────────────────────────────┘
    //
    // pageSize = 20: 与 RemoteDataSource 默认 size 一致，消除因页大小不匹配
    //   导致的"刚 REFRESH 完就触发 APPEND"抖动，滚动位置更稳定。
    // prefetchDistance = 5: 当前可见项距离底部 5 条时触发 APPEND
    // enablePlaceholders = false: 不显示占位骨架屏
    //
    // .flow.map { pagingData -> pagingData.map { it.toDomain() } }
    //   将 FeedItemEntity 类型的 PagingData 转换为 FeedCard 类型
    // =========================================================================
    @OptIn(ExperimentalPagingApi::class)
    override fun getFeedPagingData(channel: String): Flow<PagingData<FeedCard>> {
        Timber.d("getFeedPagingData — creating Pager for channel=$channel")
        return Pager(
            config = PagingConfig(
                // pageSize 越小，每次触发 APPEND 时同时加载的图片越少，
                // 带宽被分散到更多次的请求里，单张图片加载更快、首屏更轻。
                pageSize = 20,                  // 与 MockDataSource 默认 size 一致
                prefetchDistance = 8,           // 距底 8 条触发下一页（避免用户滑到底才加载）
                enablePlaceholders = false,
                initialLoadSize = 30,           // 首次加载 30 条，铺满首屏 + 留少量滚动余量
            ),
            remoteMediator = NewsRemoteMediator(
                channel = channel,
                remoteDataSource = remoteDataSource,
                appDatabase = appDatabase,
            ),
            pagingSourceFactory = { appDatabase.feedDao().getFeedPagingSource(channel) },
        ).flow.map { pagingData ->
            pagingData.map { it.toDomain() } // Entity → Domain 映射
        }
    }

    override suspend fun searchNews(query: String): List<FeedCard> {
        Timber.d("searchNews — query=$query")
        val response = remoteDataSource.searchNews(query, 0, 20)
        return response.data.list.map { dto ->
            when (dto.type) {
                "text_top" -> FeedCard.TextTop(
                    id = dto.id,
                    title = dto.title,
                    source = dto.source,
                    commentCount = dto.commentCount,
                    publishTime = dto.publishTime ?: "",
                    isTop = dto.isTop,
                )
                "left_text_right_image" -> FeedCard.LeftTextRightImage(
                    id = dto.id,
                    title = dto.title,
                    source = dto.source,
                    commentCount = dto.commentCount,
                    publishTime = dto.publishTime ?: "",
                    imageUrl = dto.imageUrl ?: "",
                    isTop = dto.isTop,
                )
                "large_image" -> FeedCard.LargeImage(
                    id = dto.id,
                    title = dto.title,
                    source = dto.source,
                    commentCount = dto.commentCount,
                    publishTime = dto.publishTime ?: "",
                    imageUrl = dto.imageUrl ?: "",
                    isTop = dto.isTop,
                )
                else -> FeedCard.LeftTextRightImage(
                    id = dto.id,
                    title = dto.title,
                    source = dto.source,
                    commentCount = dto.commentCount,
                    publishTime = dto.publishTime ?: "",
                    imageUrl = dto.imageUrl ?: "",
                    isTop = dto.isTop,
                )
            }
        }
    }

    override suspend fun getVideoFeed(page: Int, size: Int): List<FeedCard.Video> {
        Timber.d("getVideoFeed — page=$page, size=$size")
        val response = remoteDataSource.getVideoFeed("", page, size)
        return response.data.list.map { dto ->
            FeedCard.Video(
                id = dto.id,
                title = dto.title,
                source = dto.source,
                commentCount = dto.commentCount,
                publishTime = dto.publishTime ?: "",
                imageUrl = dto.imageUrl ?: "",
                videoUrl = dto.videoUrl,
                duration = dto.duration,
                isTop = dto.isTop,
                sourceUrl = dto.sourceUrl,
            )
        }
    }

    override suspend fun getHotList(): Pair<List<com.example.toutiao.domain.model.HotQuickAction>, List<com.example.toutiao.domain.model.HotListItem>> {
        // 模拟数据 — 真实实现需要从 API 拉取
        val quickActions = listOf(
            com.example.toutiao.domain.model.HotQuickAction("1", "🔥", "头条热榜", "实时更新"),
            com.example.toutiao.domain.model.HotQuickAction("2", "🔥", "2026高考", "作文题目出炉"),
            com.example.toutiao.domain.model.HotQuickAction("3", "🔥", "美伊局势迷雾", "谈判陷僵局"),
            com.example.toutiao.domain.model.HotQuickAction("4", "🔥", "实测中", "7x24小时"),
        )
        val items = (1..13).map { i ->
            com.example.toutiao.domain.model.HotListItem(
                id = "hot_$i",
                rank = i,
                title = when (i) {
                    1 -> "青春正好 踏在脚下"
                    2 -> "高考作文题来了"
                    3 -> "矿难致82死后当地应急管理局局长被查"
                    4 -> "与世界共谋 探寻中国乡村绿色转型密码"
                    5 -> "警号\"985\"已被盘出包浆"
                    6 -> "2026高考第一批显眼包出现了"
                    7 -> "杭二中考点第一个出来考生评语文卷"
                    8 -> "女子掀头发前判若两人"
                    9 -> "男生高考从考场发现教室就自己1人"
                    10 -> "中专生成北大博士:这不是逆袭"
                    11 -> "家长看上去比考生们还要紧张"
                    12 -> "官方辟谣游客三亚拍照遭遇拦路收费"
                    else -> "高考首日多地暴雨 考生撑伞赴考"
                },
                badge = when (i) {
                    1 -> com.example.toutiao.domain.model.HotBadge.Fire
                    2 -> com.example.toutiao.domain.model.HotBadge.Boom
                    3, 6, 7, 9, 13 -> com.example.toutiao.domain.model.HotBadge.Hot
                    5, 11 -> com.example.toutiao.domain.model.HotBadge.New
                    12 -> com.example.toutiao.domain.model.HotBadge.Rumor
                    else -> com.example.toutiao.domain.model.HotBadge.None
                },
            )
        }
        return Pair(quickActions, items)
    }
}
