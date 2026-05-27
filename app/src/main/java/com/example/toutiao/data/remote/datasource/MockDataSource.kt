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

/**
 * Mock 远程数据源，从 `assets/news_data.json` 读取 1421 条真实新闻数据，
 * 按时间排序、按频道分类，支持分页拉取。
 *
 * 频道映射：
 *   recommend → 全量（综合推荐）
 *   hot → 社会/财经/科技/娱乐/体育/国际/国内/军事
 *   video → 仅「视频」分类
 *   society → 社会/法治/法律/时政/国内/中国/地方/教育/健康
 *
 * 分页策略：
 *   时间倒序排列（最新在前），每页 8 条，按 page 偏移取数据。
 */
class MockDataSource(context: Context) : RemoteDataSource {

    private val allItems: List<RawNewsItem> by lazy {
        loadFromAssets(context)
    }

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

        val filtered = filterByChannel(allItems, channel)
        // 按时间倒序排列
        val sorted = filtered.sortedByDescending { parseDatetime(it.datetime) }
        val offset = page * size
        val pageItems = if (offset >= sorted.size) {
            emptyList()
        } else {
            sorted.drop(offset).take(size)
        }
        val hasMore = (offset + size) < sorted.size

        Timber.d("MockDataSource — channel=$channel, page=$page, total=${sorted.size}, returned=${pageItems.size}, hasMore=$hasMore")

        val dtoList = pageItems.mapIndexed { index, raw ->
            val globalIndex = offset + index
            mapToDto(raw, channel, globalIndex)
        }

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
            "hot" -> setOf("社会", "财经", "科技", "娱乐", "体育", "国际", "国内", "军事", "NBA", "中超", "英超")
            "video" -> setOf("视频")
            "society" -> setOf("社会", "法治", "法律", "时政", "国内", "中国", "地方", "教育", "健康", "环境", "环保")
            else -> null
        }
        return if (categories == null) items else items.filter { it.category in categories }
    }

    // ── RawNewsItem → NewsItemDto 映射 ────────────────────────────────────────
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
            isTop = index == 0,
        )
    }

    private fun determineType(category: String, imageUrl: String?, index: Int): String {
        if (category == "视频") return "video"
        if (imageUrl.isNullOrBlank()) return "text_top"
        return if (index % 3 == 0) "large_image" else "left_text_right_image"
    }

    // ── 辅助函数 ──────────────────────────────────────────────────────────────

    private val datetimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    private fun parseDatetime(datetime: String): LocalDateTime {
        return try {
            LocalDateTime.parse(datetime, datetimeFormatter)
        } catch (e: Exception) {
            LocalDateTime.MIN
        }
    }

    private fun formatRelativeTime(datetime: String): String {
        return try {
            val dt = parseDatetime(datetime)
            val now = LocalDateTime.of(2026, 5, 25, 23, 59)
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
