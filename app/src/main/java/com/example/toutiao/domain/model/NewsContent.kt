package com.example.toutiao.domain.model

import androidx.compose.runtime.Immutable

/**
 * 新闻详情页内容
 *
 * 由 NewsContentParser 解析后填充进 NewsDetailScreen。
 * 结构上参考头条 APP 的新闻详情页：
 *  - 标题（多行，置顶）
 *  - 来源 + 发布时间（次要行）
 *  - 正文段落列表（按顺序渲染）
 *  - 配图列表（嵌入段落之间）
 *  - 视频（可选）
 *  - 标签（可选，话题 / 关键词）
 */
@Immutable
data class NewsContent(
    val title: String,
    val source: String,
    val publishTime: String,
    val coverUrl: String? = null,
    /**
     * 段落 + 配图的有序列表。
     *  - [NewsParagraph.Text]  文字段落
     *  - [NewsParagraph.Image] 配图段落
     *  - [NewsParagraph.Quote] 引用块
     *  - [NewsParagraph.Video] 嵌入视频
     */
    val paragraphs: List<NewsParagraph>,
    /**
     * 解析策略：让用户在加载页知道是「手动」还是「LLM」解析出来的
     */
    val parseStrategy: ParseStrategy = ParseStrategy.Manual,
    val sourceUrl: String,
)

sealed class NewsParagraph {
    data class Text(val text: String) : NewsParagraph()
    data class Image(val url: String, val caption: String? = null) : NewsParagraph()
    data class Quote(val text: String, val source: String? = null) : NewsParagraph()
    data class Video(val url: String, val posterUrl: String? = null, val duration: String? = null) : NewsParagraph()
}

enum class ParseStrategy {
    /** Jsoup 手动解析（HTML 标签启发式提取） */
    Manual,
    /** LLM 智能解析（当手动失败时回退） */
    Llm,
}
