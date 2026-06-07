package com.example.toutiao.data.remote.datasource

import android.content.Context
import com.example.toutiao.data.remote.dto.NewsFeedData
import com.example.toutiao.data.remote.dto.NewsFeedResponse
import com.example.toutiao.data.remote.dto.NewsItemDto
import com.example.toutiao.data.remote.dto.RawNewsItem
import com.example.toutiao.data.remote.dto.ToutiaoMockItem
import com.example.toutiao.data.remote.dto.ToutiaoMockWrapper
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.IOException
import kotlin.math.absoluteValue
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// =============================================================================
// MockDataSource — 数据链路的起点
//
// 角色：实现 RemoteDataSource 接口，从 assets/news_data.json 加载**合成** mock 数据。
//       数据用模板+种子随机生成, 不含真实信息, 详见 data/NEWS_DATA_README.md.
//
// 调用链中的位置：
//   assets/news_data.json  ← 唯一数据来源（~2400 条合成, 10 频道）
//          ↓ loadFromAssets()
//   List<RawNewsItem>      ← 内部归一化结构（兼容旧字段：source/datetime/content）
//          ↓ filterByChannel()
//          ↓ sortedByDescending (按 generatedDate 倒序)
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

    // 从全部新闻中提取所有真实图片 URL，并将 HTTP 升级为 HTTPS，供无图新闻循环复用
    private val imageUrlPool: List<String> by lazy {
        allItems.mapNotNull { it.imageUrl.takeIf { url -> url.isNotBlank() } }
            .map { it.replace(Regex("^http://", RegexOption.IGNORE_CASE), "https://") }
            .distinct()
            .also { Timber.d("MockDataSource — imageUrlPool size = ${it.size}") }
    }

    // 视频专用数据池 - 从所有新闻中筛选出适合视频的条目
    private val videoItems: List<RawNewsItem> by lazy {
        allItems.filter { it.category in setOf("视频", "娱乐", "体育", "科技", "社会") }
            .shuffled()
            .take(50)
            .also { Timber.d("MockDataSource — videoItems size = ${it.size}") }
    }

    // 搜索专用数据池
    private fun getSearchResults(query: String, size: Int): List<RawNewsItem> {
        val lowerQuery = query.lowercase()
        return allItems.filter { item ->
            item.title.lowercase().contains(lowerQuery) ||
            item.source.lowercase().contains(lowerQuery) ||
            item.category.lowercase().contains(lowerQuery)
        }.take(size)
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
        Timber.i("MockDataSource — About to map ${pageItems.size} items to DTO")
        val dtoList = pageItems.mapIndexed { index, raw ->
            val globalIndex = offset + index
            Timber.i("MockDataSource — Mapping item $index: ${raw.title.take(20)}...")
            mapToDto(raw, channel, globalIndex)
        }
        Timber.i("MockDataSource — Mapped ${dtoList.size} items, first image URL: ${dtoList.firstOrNull()?.imageUrl}")
        Timber.i("MockDataSource — Mapped ${dtoList.size} items, first image URL: ${dtoList.firstOrNull()?.imageUrl}")

        // 步骤 6：包装为 NewsFeedResponse（这是 Retrofit API 的标准响应格式）
        return NewsFeedResponse(
            code = 0,
            data = NewsFeedData(list = dtoList, hasMore = hasMore),
        )
    }

    // ── JSON 加载 ──────────────────────────────────────────────────────────────
    /**
     * 加载并归一化为 RawNewsItem。
     *
     * 优先尝试新数据源 schema（{新闻: [{源URL, 封面URL, 标题, 类别}]}），
     * 失败则回退到旧 schema（[{标题, 分类, 时间日期, 新闻来源, 封面URL, 新闻链接, 文本内容}]）。
     */
    private fun loadFromAssets(context: Context): List<RawNewsItem> {
        return try {
            val jsonStr = context.assets.open("news_data.json")
                .bufferedReader().use { it.readText() }
            val json = Json { ignoreUnknownKeys = true; isLenient = true }

            // 尝试新数据源 schema
            val newSchema = runCatching { json.decodeFromString<ToutiaoMockWrapper>(jsonStr) }.getOrNull()
            if (newSchema != null && newSchema.items.isNotEmpty()) {
                val normalized = newSchema.items.mapIndexed { idx, it -> normalizeNewSchema(it, idx) }
                Timber.d("MockDataSource — loaded ${normalized.size} items (新 schema) from assets/news_data.json")
                return normalized
            }

            // 回退旧 schema
            val oldSchema = runCatching { json.decodeFromString<List<RawNewsItem>>(jsonStr) }.getOrNull()
            if (oldSchema != null && oldSchema.isNotEmpty()) {
                Timber.d("MockDataSource — loaded ${oldSchema.size} items (旧 schema) from assets/news_data.json")
                return oldSchema
            }

            Timber.w("MockDataSource — neither schema matched, returning empty list")
            emptyList()
        } catch (e: Exception) {
            Timber.e(e, "MockDataSource — failed to load news_data.json, falling back to empty")
            emptyList()
        }
    }

    /**
     * 新 schema → RawNewsItem 归一化：
     *  - 时间字段为空，按 index 倒推（idx=0 视为最新，依次递减 1 小时）
     *  - 来源字段为空，按 category 推断（如 "时尚" → "时尚资讯"）
     *  - 文本内容留空，由详情页 HTTP 访问源 URL 后解析
     */
    private fun normalizeNewSchema(item: ToutiaoMockItem, index: Int): RawNewsItem {
        val now = LocalDateTime.now()
        // 倒推 1 小时间隔，让首条数据保持"最新"语义
        val generatedDatetime = now.minusHours(index.toLong()).format(datetimeFormatter)
        val inferredSource = if (item.category.isNotBlank()) "${item.category}资讯" else "未知来源"
        return RawNewsItem(
            title = item.title,
            category = item.category,
            content = "", // 留空，详情页会通过 sourceUrl 拉取并解析
            datetime = generatedDatetime,
            source = inferredSource,
            imageUrl = item.imageUrl,
            sourceUrl = item.sourceUrl,
        )
    }

    // ── 频道分类映射 ──────────────────────────────────────────────────────────
    private fun filterByChannel(items: List<RawNewsItem>, channel: String): List<RawNewsItem> {
        // 兼容旧 + 新数据源的混合类别。新数据源类别：时尚/宠物/游戏/军事/娱乐/旅游/教育/科技/数码/深圳/美食/健康/文化/氢能
        val categories: Set<String>? = when (channel) {
            "recommend" -> null // 全量
            "follow" -> setOf("关注") // 关注频道暂无独立数据源，返回空列表
            "hot" -> setOf("社会", "财经", "科技", "娱乐", "体育", "国际", "国内", "军事", "美食", "健康", "数码")
            "video" -> setOf("视频", "娱乐", "游戏")
            "society" -> setOf("社会", "法治", "法律", "时政", "国内", "中国", "地方", "教育", "健康", "环境", "环保")
            "tech" -> setOf("科技", "互联网", "数码", "AI", "人工智能", "氢能")
            "newera" -> setOf("时政", "国内", "中国", "新时代", "党建", "政策")
            "novel" -> setOf("小说", "文学", "文化", "读书", "故事")
            // 新数据源专用频道
            "shenzhen" -> setOf("深圳", "推荐") // 兜底: web 版无深圳频道, 拉推荐当本地
            "discover" -> setOf("科技", "数码", "娱乐", "美食", "旅游", "时尚")
            "video_channel" -> setOf("视频", "娱乐", "游戏")
            "changting" -> setOf("科技", "娱乐", "财经", "视频") // 畅听 web 无独立, 代理
            else -> null
        }
        return if (categories == null) items else items.filter { it.category in categories }
    }

    // ── RawNewsItem → NewsItemDto 映射（核心转换逻辑） ─────────────────────────
    // 这里的转换决定了每条新闻最终以哪种卡片类型渲染：
    //   text_top → TextTopCard（置顶纯文字）
    //   left_text_right_image → LeftTextRightImageCard（左文右图）
    //   large_image → LargeImageCard（上文下大图）
    //
    // 推断规则：
    //   首页首条权威来源 → text_top（每页最多 1 条纯文本）
    //   其余新闻 → 按 index 1:1 分配 large_image 和 left_text_right_image
    //
    // 图片策略：
    //   1. 原始 URL 存在时，自动将 HTTP 升级为 HTTPS（避免 Android 9+ 明文流量拦截）
    //   2. 原始 URL 为空时，从 imageUrlPool 中循环分配真实新闻图片，确保所有图文卡片都有图
    private fun mapToDto(raw: RawNewsItem, channel: String, index: Int): NewsItemDto {
        println("MockDataSource — mapToDto called for: ${raw.title.take(30)}...")
        val pinned = isPinned(raw.source)
        val type = determineType(raw.imageUrl, index, pinned)
        val relativeTime = formatRelativeTime(raw.datetime)
        val commentCount = generateCommentCount(raw.category, index)

        // 优先使用数据源自带的 imageUrl（新数据源字段 "封面URL"），
        // 兜底用 picsum.photos 生成确定性图片（保证同一新闻总是显示同一张图）
        val resolvedImageUrl = when {
            type == "text_top" -> null
            raw.imageUrl.isNotBlank() -> {
                // 新数据源的封面 URL 已经是 picsum.photos
                val url = raw.imageUrl.replace(Regex("^http://", RegexOption.IGNORE_CASE), "https://")
                url
            }
            else -> {
                val seed = raw.title.hashCode().absoluteValue
                "https://picsum.photos/seed/$seed/800/450"
            }
        }

        val videoUrl = if (type == "video") "https://example.com/video/$index" else null
        val duration = if (type == "video") {
            val secs = (raw.title.hashCode().absoluteValue) % 600
            "${secs / 60}:${String.format("%02d", secs % 60)}"
        } else null

        return NewsItemDto(
            id = "${channel}_${index}_${raw.datetime.hashCode()}",
            type = type,
            title = raw.title,
            source = raw.source,
            commentCount = commentCount,
            imageUrl = resolvedImageUrl,
            videoUrl = videoUrl,
            duration = duration,
            publishTime = relativeTime,
            isTop = pinned,
            createdAt = parseDatetimeToMillis(raw.datetime),
            sourceUrl = raw.sourceUrl.ifBlank { null }, // 详情页会用到此 URL
        )
    }

    /**
     * 卡片类型轮转（5 个一组）：
     * 0 → text_top（仅当首条且为权威来源）
     * 1 → large_image（大图）
     * 2 → left_text_right_image（左文右图）
     * 3 → video（视频缩略图 + 播放按钮 + 时长）
     * 4 → left_text_right_image（左文右图）
     * 循环 → 模拟真实头条「图文+视频+图文」混合流
     */
    private fun determineType(imageUrl: String?, index: Int, isPinned: Boolean): String {
        if (isPinned && index == 0) return "text_top"
        // 娱乐/体育/社会类内容多设为视频
        return when (index % 5) {
            0 -> "large_image"
            1 -> "left_text_right_image"
            2 -> "video"
            3 -> "left_text_right_image"
            else -> "left_text_right_image"
        }
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
            Timber.w(e, "MockDataSource — failed to parse datetime: $datetime")
            LocalDateTime.MIN
        }
    }

    private fun parseDatetimeToMillis(datetime: String): Long {
        return try {
            java.time.ZoneId.systemDefault()
                .let { LocalDateTime.parse(datetime, datetimeFormatter).atZone(it).toInstant().toEpochMilli() }
        } catch (e: Exception) {
            Timber.w(e, "MockDataSource — failed to parse datetime to millis: $datetime")
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
            Timber.w(e, "MockDataSource — failed to format relative time: $datetime")
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

    // =========================================================================
    // 视频频道数据 - 从视频分类中筛选
    // =========================================================================
    override suspend fun getVideoFeed(tab: String, page: Int, size: Int): NewsFeedResponse {
        val delayMs = DebugControls.networkDelayMs
        if (delayMs > 0) delay(delayMs)
        if (DebugControls.shouldSimulateError) throw IOException(DebugControls.DEFAULT_ERROR_MESSAGE)

        // 按 tab 过滤 categories。空 / "all" / "推荐" 不过滤
        val filtered = if (tab.isBlank() || tab == "all" || tab == "推荐") {
            videoItems
        } else {
            val targetCategories = tabToCategories(tab)
            videoItems.filter { it.category in targetCategories }
                .ifEmpty { videoItems } // 该 tab 无数据时 fallback 全部
        }

        val offset = page * size
        val pageItems = if (offset >= filtered.size) {
            emptyList()
        } else {
            filtered.drop(offset).take(size)
        }
        val hasMore = (offset + size) < filtered.size

        val dtoList = pageItems.mapIndexed { index, raw ->
            mapToVideoDto(raw, offset + index)
        }

        return NewsFeedResponse(
            code = 0,
            data = NewsFeedData(list = dtoList, hasMore = hasMore),
        )
    }

    private fun tabToCategories(tab: String): Set<String> = when (tab) {
        "说" -> setOf("娱乐", "科技")
        "发现" -> setOf("科技", "财经", "社会")
        "视频" -> setOf("视频", "娱乐")
        "体育" -> setOf("体育")
        "畅听" -> setOf("娱乐", "科技")
        "短剧" -> setOf("娱乐")
        else -> setOf(tab) // 兜底按字面量匹配
    }

    private fun mapToVideoDto(raw: RawNewsItem, index: Int): NewsItemDto {
        val seed = raw.title.hashCode().absoluteValue
        // 同样优先用数据源封面 URL
        val cover = if (raw.imageUrl.isNotBlank()) {
            raw.imageUrl.replace(Regex("^http://", RegexOption.IGNORE_CASE), "https://")
        } else {
            "https://picsum.photos/seed/$seed/800/450"
        }
        return NewsItemDto(
            id = "video_${index}_${raw.datetime.hashCode()}",
            type = "video",
            title = raw.title,
            source = raw.source,
            commentCount = generateCommentCount(raw.category, index),
            imageUrl = cover,
            videoUrl = "https://example.com/video/$seed",
            duration = "${(seed % 300) / 60}:${String.format("%02d", (seed % 300) % 60)}",
            publishTime = formatRelativeTime(raw.datetime),
            isTop = false,
            createdAt = parseDatetimeToMillis(raw.datetime),
            sourceUrl = raw.sourceUrl.ifBlank { null },
        )
    }

    // =========================================================================
    // 搜索功能 - 从所有新闻中匹配
    // =========================================================================
    override suspend fun searchNews(query: String, page: Int, size: Int): NewsFeedResponse {
        val delayMs = DebugControls.networkDelayMs
        if (delayMs > 0) delay(delayMs)
        if (DebugControls.shouldSimulateError) throw IOException(DebugControls.DEFAULT_ERROR_MESSAGE)

        val results = getSearchResults(query, 100) // 最多返回100条
        val offset = page * size
        val pageItems = if (offset >= results.size) {
            emptyList()
        } else {
            results.drop(offset).take(size)
        }
        val hasMore = (offset + size) < results.size

        val dtoList = pageItems.mapIndexed { index, raw ->
            mapToDto(raw, "search", offset + index)
        }

        return NewsFeedResponse(
            code = 0,
            data = NewsFeedData(list = dtoList, hasMore = hasMore),
        )
    }

}
