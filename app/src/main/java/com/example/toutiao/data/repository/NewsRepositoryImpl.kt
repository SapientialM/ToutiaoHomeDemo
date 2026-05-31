package com.example.toutiao.data.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.example.toutiao.data.local.dao.FeedDao
import com.example.toutiao.data.local.dao.RemoteKeyDao
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
    private val feedDao: FeedDao,
    private val remoteKeyDao: RemoteKeyDao,
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
    // pageSize = 20: 与 MockDataSource 每页返回量一致，消除因页大小不匹配
    //   导致的"刚 REFRESH 完就触发 APPEND"抖动，滚动位置更稳定。
    // prefetchDistance = 2: 当前可见项距离底部 2 条时触发 APPEND
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
                pageSize = 20,
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
            pagingData.map { it.toDomain() } // Entity → Domain 映射
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
