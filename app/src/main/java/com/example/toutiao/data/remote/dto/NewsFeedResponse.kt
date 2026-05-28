package com.example.toutiao.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NewsFeedResponse(
    @SerialName("code") val code: Int,
    @SerialName("data") val data: NewsFeedData,
)

@Serializable
data class NewsFeedData(
    @SerialName("list") val list: List<NewsItemDto>,
    @SerialName("hasMore") val hasMore: Boolean,
)

@Serializable
data class NewsItemDto(
    @SerialName("id") val id: String,
    @SerialName("type") val type: String,
    @SerialName("title") val title: String,
    @SerialName("source") val source: String,
    @SerialName("commentCount") val commentCount: Int = 0,
    @SerialName("imageUrl") val imageUrl: String? = null,
    @SerialName("videoUrl") val videoUrl: String? = null,
    @SerialName("duration") val duration: String? = null,
    @SerialName("publishTime") val publishTime: String? = null,
    @SerialName("isTop") val isTop: Boolean = false,
    @SerialName("createdAt") val createdAt: Long = 0L,
)
