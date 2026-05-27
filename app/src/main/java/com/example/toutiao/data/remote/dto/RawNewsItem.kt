package com.example.toutiao.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RawNewsItem(
    @SerialName("标题") val title: String,
    @SerialName("分类") val category: String,
    @SerialName("文本内容") val content: String = "",
    @SerialName("时间日期") val datetime: String,
    @SerialName("新闻来源") val source: String,
    @SerialName("封面URL") val imageUrl: String? = null,
    @SerialName("新闻链接") val newsUrl: String? = null,
)
