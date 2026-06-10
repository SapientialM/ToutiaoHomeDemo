package com.example.toutiao.data.remote.datasource

import android.content.Context
import com.example.toutiao.data.remote.dto.NewsFeedData
import com.example.toutiao.data.remote.dto.NewsFeedResponse
import com.example.toutiao.data.remote.dto.NewsItemDto
import com.example.toutiao.data.remote.dto.RawNewsItem
import com.example.toutiao.data.remote.dto.RawRealNewsItem
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
class MockDataSource(private val context: Context) : RemoteDataSource {

    // by lazy：首次调用 getNewsFeed() 时才从 assets 读 JSON，不阻塞 App 启动
    private val allItems: List<RawNewsItem> by lazy {
        loadFromAssets(context)
    }

    // ── 真实数据：每频道独立缓存 ──
    // assets/news_data/{channel}.json 由 build 前置任务从项目根 news_data/ 复制
    // （也允许手动 cp），每频道 100 条真实 URL（头条/B站抓取）。
    // 优先于 allItems（合成 mock）使用；若缺失则回退到合成数据。
    private val realChannelCache: MutableMap<String, List<RawRealNewsItem>> = mutableMapOf()

    private fun loadRealChannelData(channel: String): List<RawRealNewsItem> {
        realChannelCache[channel]?.let { return it }
        val fileName = channelFileMap[channel] ?: return emptyList()
        return try {
            val jsonStr = context.assets.open("news_data/$fileName")
                .bufferedReader().use { it.readText() }
            val json = Json { ignoreUnknownKeys = true; isLenient = true }
            val items = json.decodeFromString<List<RawRealNewsItem>>(jsonStr)
            Timber.d("MockDataSource — loaded ${items.size} real items for channel=$channel from $fileName")
            realChannelCache[channel] = items
            items
        } catch (e: Exception) {
            Timber.d("MockDataSource — no real data for channel=$channel (file=$fileName): ${e.message}")
            emptyList()
        }
    }

    private val channelFileMap: Map<String, String> = mapOf(
        "recommend" to "推荐.json",
        "follow" to "推荐.json",
        "hot" to "热榜.json",
        "shenzhen" to "深圳.json",
        "discover" to "发现.json",
        "video" to "视频.json",
        "finance" to "财经.json",
    )

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

        // 优先使用真实数据（assets/news_data/{channel}.json，含真实 URL）
        val realItems = loadRealChannelData(channel)
        if (realItems.isNotEmpty()) {
            return buildResponseFromReal(channel, page, size, realItems)
        }

        // 回退：合成数据（旧逻辑）
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

    /**
     * 用真实数据构造响应（循环复用）。
     *
     * 每频道 100 条真实数据。当用户滑动到底（page*size >= totalSize），
     * 继续按 offset % totalSize 取数据，从而实现"无限下拉 + 循环复用"。
     * 同时限制最多加载 [REAL_MAX_PAGES] 页（避免 Room 无限增长），
     * 达到上限后返回 hasMore=false，UI 停止自动加载，用户可下拉刷新重新开始。
     */
    private fun buildResponseFromReal(
        channel: String,
        page: Int,
        size: Int,
        items: List<RawRealNewsItem>,
    ): NewsFeedResponse {
        val totalSize = items.size
        val offset = page * size
        if (offset >= totalSize * REAL_MAX_PAGES) {
            Timber.d("MockDataSource(real) — channel=$channel, page=$page, reached maxPages=$REAL_MAX_PAGES, stop")
            return NewsFeedResponse(code = 0, data = NewsFeedData(list = emptyList(), hasMore = false))
        }
        // 循环：offset % totalSize 让页码超出后从头开始
        val cycleOffset = offset % totalSize
        val endIndex = minOf(cycleOffset + size, totalSize)
        val pageItems = if (cycleOffset >= totalSize) {
            emptyList()
        } else {
            items.subList(cycleOffset, endIndex)
        }
        val hasMore = (page + 1) < REAL_MAX_PAGES || endIndex < totalSize
        Timber.d("MockDataSource(real) — channel=$channel, page=$page, total=$totalSize, returned=${pageItems.size}, cycleOffset=$cycleOffset, hasMore=$hasMore")
        val dtoList = pageItems.mapIndexed { index, raw ->
            mapRealToDto(raw, channel, offset + index)
        }
        return NewsFeedResponse(code = 0, data = NewsFeedData(list = dtoList, hasMore = hasMore))
    }

    private fun mapRealToDto(raw: RawRealNewsItem, channel: String, index: Int): NewsItemDto {
        val resolvedImageUrl = if (raw.coverUrl.isNotBlank()) {
            raw.coverUrl.replace(Regex("^http://", RegexOption.IGNORE_CASE), "https://")
        } else null
        // 来源展示：用 source_name（头条号/账号名），缺省回退 source
        val resolvedSource = raw.sourceName.ifBlank { raw.source.ifBlank { "未知来源" } }
        val type = determineRealType(raw, channel, index)
        val now = LocalDateTime.now()
        val generatedDateTime = now.minusMinutes(index.toLong() * 7L)
        // video 类型的 videoUrl 直接用真实 B 站 URL（VideoDetailScreen 用 WebView + bilibili player.html 解析播放）
        val resolvedVideoUrl = if (type == "video") raw.url.takeIf { it.isNotBlank() } else null
        return NewsItemDto(
            id = "${channel}_${index}_${raw.title.hashCode()}",
            type = type,
            title = raw.title,
            source = resolvedSource,
            commentCount = generateCommentCountFromScore(raw.hotScore),
            imageUrl = resolvedImageUrl,
            videoUrl = resolvedVideoUrl,
            duration = if (type == "video") formatVideoDuration(raw.hotScore) else null,
            publishTime = formatRelativeTime(generatedDateTime.format(datetimeFormatter)),
            isTop = false,
            createdAt = generatedDateTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
            sourceUrl = raw.url.takeIf { it.isNotBlank() },
        )
    }

    /**
     * 从本地 assets 资源中轮转选择 sample.mp4。
     * 使用 asset:/// URI 路径（Android 原生 ContentResolver 支持），
     * 避免外网 HTTP 超时问题。
     */
    private fun pickPlayableVideoUrl(index: Int): String =
        "android.resource://" + PLAYABLE_PACKAGE + "/" +
            PLAYABLE_RAW_IDS[index.absoluteValue % PLAYABLE_RAW_IDS.size]

    private val PLAYABLE_PACKAGE = "com.example.toutiao"
    private val PLAYABLE_RAW_IDS: List<Int> = listOf(
        // res/raw/sample.mp4
        com.example.toutiao.R.raw.sample,
    )

    /**
     * 真实数据的卡片类型：
     *  - 推荐频道前 5 条 → text_top（5 条置顶）
     *  - 视频频道全部 → video
     *  - 其他 → left_text_right_image
     *
     * 当 imageUrl 为空时强制降级为 text_top（避免占位图泛滥）。
     */
    private fun determineRealType(raw: RawRealNewsItem, channel: String, index: Int): String {
        if (channel == "video") return "video"
        if (raw.isVideo) return "video"
        if (channel == "recommend" && index < 5) return "text_top"
        // 没有封面图：兜底用 text_top（紧凑无图）
        if (raw.coverUrl.isBlank()) return "text_top"
        return "left_text_right_image"
    }

    private fun generateCommentCountFromScore(hotScore: Long): Int {
        // hot_score → 评论数映射：score 越高，评论越多
        return when {
            hotScore >= 1_000_000 -> (30000 + hotScore % 30000).toInt()
            hotScore >= 100_000 -> (5000 + hotScore % 15000).toInt()
            hotScore >= 10_000 -> (1000 + hotScore % 4000).toInt()
            hotScore > 0 -> (200 + hotScore % 800).toInt()
            else -> (50..500).random()
        }
    }

    private fun formatVideoDuration(seed: Long): String {
        val secs = (seed.toInt().absoluteValue) % 600
        return "%d:%02d".format(secs / 60, secs % 60)
    }

    companion object {
        // 真实数据循环复用上限：每个频道最多加载 50 页 = 1000 条，
        // 避免 Room 表无限增长。下拉刷新触发 REFRESH 重新加载。
        private const val REAL_MAX_PAGES = 50
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
     *  - 时间字段为空，按 index 倒推（idx=0 视为最新，依次递减 30 分钟）
     *  - source 字段：优先用数据自带的 _source（头条号/账号），缺省按 category 推断
     *  - content 字段：用 _summary（摘要），详情页再 HTTP 抓取源 URL 完整正文
     */
    private fun normalizeNewSchema(item: ToutiaoMockItem, index: Int): RawNewsItem {
        val now = LocalDateTime.now()
        val generatedDatetime = now.minusMinutes(index.toLong() * 30L).format(datetimeFormatter)
        val resolvedSource = item.source.ifBlank {
            if (item.category.isNotBlank()) "${item.category}资讯" else "未知来源"
        }
        return RawNewsItem(
            title = item.title,
            category = item.category,
            content = item.summary,
            datetime = generatedDatetime,
            source = resolvedSource,
            imageUrl = item.imageUrl,
            sourceUrl = item.sourceUrl,
        )
    }

    // ── 频道分类映射 ──────────────────────────────────────────────────────────
    private fun filterByChannel(items: List<RawNewsItem>, channel: String): List<RawNewsItem> {
        // 新数据源 (assets/news_data.json): 每条新闻的 category 字段 = channel 名
        //   ("推荐"/"热榜"/"深圳"/"发现"/"视频"/"财经")，直接按 channel 过滤即可
        // 频道别名映射：UI tab 英文 key → 数据 category 中文名
        val targetCategory = when (channel) {
            "recommend", "follow" -> "推荐" // 关注频道暂时复用推荐数据
            "hot" -> "热榜"
            "shenzhen" -> "深圳"
            "discover" -> "发现"
            "video" -> "视频"
            "finance" -> "财经"
            "novel" -> "推荐" // 小说频道暂无独立数据，临时用推荐
            "society", "tech", "newera", "video_channel", "changting", "military", "audio", "sports" -> "推荐"
            else -> null // null = 全量
        }
        return if (targetCategory == null) items else items.filter { it.category == targetCategory }
    }

    // ── RawNewsItem → NewsItemDto 映射（核心转换逻辑） ─────────────────────────
    // 这里的转换决定了每条新闻最终以哪种卡片类型渲染：
    //   text_top → TextTopCard（置顶纯文字）
    //   left_text_right_image → LeftTextRightImageCard（左文右图）
    //   large_image → LargeImageCard（上文下大图）
    //   video → VideoCard（上文下视频封面）
    //   compact → CompactCard（紧凑无图）
    //
    // 推断规则：
    //   推荐频道前 5 条 → text_top（5 条置顶）
    //   其余新闻 → 按 index % 6 轮转 4 种图文 + video + compact
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
            type == "compact" -> null
            raw.imageUrl.isNotBlank() -> {
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
            sourceUrl = raw.sourceUrl.ifBlank { null },
        )
    }

    /**
     * 卡片类型轮转：
     * 推荐频道 index < 5 → text_top（5 条置顶纯文字）
     * 其余按 index % 6 轮转：左文右图 → 大图 → 视频 → 左文右图 → 紧凑无图
     * → 模拟真实头条「图文+视频+图文」混合流
     */
    private fun determineType(imageUrl: String?, index: Int, isPinned: Boolean): String {
        if (isPinned && index == 0) return "text_top"
        // 推荐频道前 5 条统一为置顶纯文字（HomeScreen 也会把前 5 条用作置顶）
        if (index < 5) return "text_top"
        return when (index % 6) {
            0 -> "large_image"            // 上文下大图
            1 -> "left_text_right_image"  // 左文右图
            2 -> "video"                  // 上文下视频
            3 -> "left_text_right_image"  // 左文右图
            4 -> "compact"                // 紧凑无图（小标题 + 灰色信息行）
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
