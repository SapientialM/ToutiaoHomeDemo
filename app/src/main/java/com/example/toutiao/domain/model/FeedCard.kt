package com.example.toutiao.domain.model

import androidx.compose.runtime.Immutable

@Immutable
sealed class FeedCard {
    abstract val id: String
    abstract val title: String
    abstract val source: String
    abstract val commentCount: Int
    abstract val publishTime: String
    /**
     * 新闻源 URL。点击卡片进入详情页时，详情页会 HTTP 访问此 URL，
     * 先用 Jsoup 手动解析 HTML，失败再用 LLM 智能解析。
     *
     * 新数据源（app/src/main/assets/news_data.json，合成 mock）必有此字段；
     * 旧 schema 可选。
     */
    abstract val sourceUrl: String?

    data class TextTop(
        override val id: String,
        override val title: String,
        override val source: String,
        override val commentCount: Int,
        override val publishTime: String,
        override val sourceUrl: String? = null,
        val isTop: Boolean = true,
    ) : FeedCard()

    data class LeftTextRightImage(
        override val id: String,
        override val title: String,
        override val source: String,
        override val commentCount: Int,
        override val publishTime: String,
        val imageUrl: String,
        override val sourceUrl: String? = null,
        val isTop: Boolean = false,
    ) : FeedCard()

    data class LargeImage(
        override val id: String,
        override val title: String,
        override val source: String,
        override val commentCount: Int,
        override val publishTime: String,
        val imageUrl: String,
        override val sourceUrl: String? = null,
        val isTop: Boolean = false,
    ) : FeedCard()

    data class Video(
        override val id: String,
        override val title: String,
        override val source: String,
        override val commentCount: Int,
        override val publishTime: String,
        val imageUrl: String,
        val videoUrl: String?,
        val duration: String?,
        override val sourceUrl: String? = null,
        val isTop: Boolean = false,
    ) : FeedCard()

    /**
     * 紧凑无图卡片：仅标题 + 灰色信息行（来源 / 时间 / 评论数）
     * 用于 MVPTask #3 推荐频道 "无图且紧凑" 布局
     */
    data class Compact(
        override val id: String,
        override val title: String,
        override val source: String,
        override val commentCount: Int,
        override val publishTime: String,
        override val sourceUrl: String? = null,
        val isTop: Boolean = false,
    ) : FeedCard()
}
