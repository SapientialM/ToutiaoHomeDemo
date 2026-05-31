package com.example.toutiao.data.remote.datasource

import android.content.Context
import com.example.toutiao.data.remote.dto.NewsFeedData
import com.example.toutiao.data.remote.dto.NewsFeedResponse
import com.example.toutiao.data.remote.dto.NewsItemDto
import com.example.toutiao.data.remote.dto.RawNewsItem
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// =============================================================================
// MockDataSource — 数据链路的起点
//
// 角色：实现 RemoteDataSource 接口，从 assets/news_data.json 加载真实新闻数据，
//       按频道过滤 + 时间排序 + 分页，返回 NewsFeedResponse（DTO）。
//
// 调用链中的位置：
//   assets/news_data.json  ← 这是唯一的数据来源（1421 条真实新闻）
//          ↓ loadFromAssets()
//   List<RawNewsItem>      ← 全部原始数据缓存在内存中（by lazy，首次调用时加载）
//          ↓ filterByChannel()
//          ↓ sortedByDescending (时间倒序)
//          ↓ drop(offset).take(size)  ← 基于 page 的分页截取
//   List<NewsItemDto>      ← mapToDto() 完成 RawNewsItem → NewsItemDto 转换
//          ↓
//   NewsFeedResponse       ← 返回给调用方（NewsRemoteMediator 或 NewsRepositoryImpl）
//
// 谁调用这里：
//   NewsRemoteMediator.load()  → Paging3 分页时调用（唯一调用路径）
// =============================================================================
class MockDataSource(context: Context) : RemoteDataSource {

    // by lazy：首次调用 getNewsFeed() 时才从 assets 读 JSON，不阻塞 App 启动
    private val allItems: List<RawNewsItem> by lazy {
        loadFromAssets(context)
    }

    // 这是 RemoteDataSource 接口的唯一方法，也是数据流的唯一切入点。
    // channel 来自 ViewModel 的当前 Tab（recommend/hot/video/society）
    // page 来自 Paging3 的 LoadType（REFRESH=0, APPEND=N, PREPEND=N-1）
    override suspend fun getNewsFeed(channel: String, page: Int, size: Int): NewsFeedResponse {
        val delayMs = DebugControls.networkDelayMs
        if (delayMs > 0) {
            Timber.d("MockDataSource — simulating network delay: ${delayMs}ms")
            delay(delayMs)
        }

        if (DebugControls.shouldSimulateError) {
            Timber.w("MockDataSource — simulating network error")
            throw IOException(DebugControls.DEFAULT_ERROR_MESSAGE)
        }

        // 步骤 2：按频道过滤（1421 条 → 约 N 条，取决于频道映射）
        val filtered = filterByChannel(allItems, channel)
        // 步骤 3：排序 — 先按日期（天）倒序，同一日内置顶在前，同日内按精确时间倒序
        // 必须与 Room 查询排序一致：ORDER BY date(created_at/1000, 'unixepoch') DESC, is_top DESC, created_at DESC
        val sorted = filtered.sortedWith(
            compareByDescending<RawNewsItem> { parseDatetime(it.datetime).toLocalDate() }
                .thenByDescending { isPinned(it.source) }
                .thenByDescending { parseDatetime(it.datetime) }
        )
        // 步骤 4：基于 page 的分页截取（page=0 取前 8 条，page=1 取第 9~16 条...）
        val offset = page * size
        val pageItems = if (offset >= sorted.size) {
            emptyList()
        } else {
            sorted.drop(offset).take(size)
        }
        val hasMore = (offset + size) < sorted.size

        Timber.d("MockDataSource — channel=$channel, page=$page, total=${sorted.size}, returned=${pageItems.size}, hasMore=$hasMore")

        // 步骤 5：RawNewsItem → NewsItemDto（原始 JSON 结构 → Retrofit 期望的 DTO 结构）
        val dtoList = pageItems.mapIndexed { index, raw ->
            val globalIndex = offset + index
            mapToDto(raw, channel, globalIndex)
        }

        // 步骤 6：包装为 NewsFeedResponse（这是 Retrofit API 的标准响应格式）
        return NewsFeedResponse(
            code = 0,
            data = NewsFeedData(list = dtoList, hasMore = hasMore),
        )
    }

    // ── JSON 加载 ──────────────────────────────────────────────────────────────
    private fun loadFromAssets(context: Context): List<RawNewsItem> {
        return try {
            val jsonStr = context.assets.open("news_data.json")
                .bufferedReader().use { it.readText() }
            val items = Json { ignoreUnknownKeys = true; isLenient = true }
                .decodeFromString<List<RawNewsItem>>(jsonStr)
            Timber.d("MockDataSource — loaded ${items.size} items from assets/news_data.json")
            items
        } catch (e: Exception) {
            Timber.e(e, "MockDataSource — failed to load news_data.json, falling back to empty")
            emptyList()
        }
    }

    // ── 频道分类映射 ──────────────────────────────────────────────────────────
    private fun filterByChannel(items: List<RawNewsItem>, channel: String): List<RawNewsItem> {
        val categories = when (channel) {
            "recommend" -> null // 全量
            "follow" -> setOf("关注") // 关注频道暂无独立数据源，返回空列表
            "hot" -> setOf("社会", "财经", "科技", "娱乐", "体育", "国际", "国内", "军事", "NBA", "中超", "英超")
            "video" -> setOf("视频")
            "society" -> setOf("社会", "法治", "法律", "时政", "国内", "中国", "地方", "教育", "健康", "环境", "环保")
            "tech" -> setOf("科技", "互联网", "数码", "AI", "人工智能")
            else -> null
        }
        return if (categories == null) items else items.filter { it.category in categories }
    }

    // ── RawNewsItem → NewsItemDto 映射（核心转换逻辑） ─────────────────────────
    // 这里的转换决定了每条新闻最终以哪种卡片类型渲染：
    //   text_top → TextTopCard（纯文字置顶）
    //   left_text_right_image → LeftTextRightImageCard（左文右图）
    //   large_image → LargeImageCard（大图）
    //   video → VideoCard（视频播放按钮 + 时长）
    //
    // 推断规则：
    //   分类 == "视频" → video
    //   无封面图 → text_top
    //   有封面图 → 按 index % 3 交替分配 large_image 和 left_text_right_image
    private fun mapToDto(raw: RawNewsItem, channel: String, index: Int): NewsItemDto {
        val type = determineType(raw.category, raw.imageUrl, index)
        val relativeTime = formatRelativeTime(raw.datetime)
        val commentCount = generateCommentCount(raw.category, index)

        return NewsItemDto(
            id = "${channel}_${index}_${raw.datetime.hashCode()}",
            type = type,
            title = raw.title,
            source = raw.source,
            commentCount = commentCount,
            imageUrl = if (type != "text_top") raw.imageUrl else null,
            videoUrl = if (type == "video") "" else null,
            duration = if (type == "video") generateDuration(index) else null,
            publishTime = relativeTime,
            isTop = isPinned(raw.source),
            createdAt = parseDatetimeToMillis(raw.datetime),
        )
    }

    private fun determineType(category: String, imageUrl: String?, index: Int): String {
        if (category == "视频") return "video"
        if (imageUrl.isNullOrBlank()) return "text_top"
        return if (index % 3 == 0) "large_image" else "left_text_right_image"
    }

    // ── 辅助函数 ──────────────────────────────────────────────────────────────

    // 权威来源标记为置顶，模拟真实新闻客户端"编辑推荐"行为
    private fun isPinned(source: String): Boolean = when (source) {
        "新华网", "新华社", "人民日报", "央视新闻", "央视体育", "央视纪录", "央视法治",
        "教育部", "人社部", "法治日报", "国防部", "国务院" -> true
        else -> false
    }

    private val datetimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    private fun parseDatetime(datetime: String): LocalDateTime {
        return try {
            LocalDateTime.parse(datetime, datetimeFormatter)
        } catch (e: Exception) {
            LocalDateTime.MIN
        }
    }

    private fun parseDatetimeToMillis(datetime: String): Long {
        return try {
            java.time.ZoneId.systemDefault()
                .let { LocalDateTime.parse(datetime, datetimeFormatter).atZone(it).toInstant().toEpochMilli() }
        } catch (e: Exception) {
            0L
        }
    }

    private fun formatRelativeTime(datetime: String): String {
        return try {
            val dt = parseDatetime(datetime)
            val now = LocalDateTime.now()
            val days = ChronoUnit.DAYS.between(dt.toLocalDate(), now.toLocalDate())
            when {
                days == 0L -> {
                    val hours = ChronoUnit.HOURS.between(dt, now).coerceAtLeast(1)
                    "${hours}小时前"
                }
                days == 1L -> "昨天"
                else -> "${days}天前"
            }
        } catch (e: Exception) {
            datetime
        }
    }

    private fun generateCommentCount(category: String, index: Int): Int {
        val base = when (category) {
            "娱乐" -> 30000; "体育" -> 25000; "科技" -> 15000; "社会" -> 12000
            "财经" -> 10000; "国际" -> 9000; "国内" -> 8000; "NBA" -> 20000
            "教育" -> 5000; "健康" -> 4000
            else -> 3000
        }
        return base + (index * 137 % 9000)
    }

    private fun generateDuration(index: Int): String {
        val minutes = (index * 7 + 3) % 60
        val seconds = (index * 13 + 7) % 60
        return "%02d:%02d".format(minutes, seconds)
    }
}
