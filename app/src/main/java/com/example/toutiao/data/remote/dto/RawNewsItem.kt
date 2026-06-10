package com.example.toutiao.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 新数据源 (assets/news_data.json, 合成) Schema：
 * {
 *   "新闻": [
 *     { "源URL": "...", "封面URL": "...", "标题": "...", "类别": "...",
 *       "_source": "...", "_summary": "..." },
 *     ...
 *   ]
 * }
 */
@Serializable
data class ToutiaoMockWrapper(
    @SerialName("新闻") val items: List<ToutiaoMockItem> = emptyList(),
)

@Serializable
data class ToutiaoMockItem(
    @SerialName("源URL") val sourceUrl: String = "",
    @SerialName("封面URL") val imageUrl: String = "",
    @SerialName("标题") val title: String = "",
    @SerialName("类别") val category: String = "",
    @SerialName("_source") val source: String = "",
    @SerialName("_summary") val summary: String = "",
)

/**
 * 旧数据源 Schema（保留向后兼容，但新项目以 ToutiaoMockItem 为主）
 */
@Serializable
data class RawNewsItem(
    @SerialName("标题") val title: String,
    @SerialName("分类") val category: String = "",
    @SerialName("文本内容") val content: String = "",
    @SerialName("时间日期") val datetime: String = "",
    @SerialName("新闻来源") val source: String = "",
    @SerialName("封面URL") val imageUrl: String = "",
    @SerialName("新闻链接") val sourceUrl: String = "",
)
