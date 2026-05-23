package com.example.toutiao.data.mapper

import com.example.toutiao.data.local.entity.FeedItemEntity
import com.example.toutiao.data.remote.dto.NewsItemDto
import com.example.toutiao.domain.model.FeedCard

fun NewsItemDto.toEntity(channel: String): FeedItemEntity = FeedItemEntity(
    id = id,
    type = type,
    title = title,
    source = source,
    commentCount = commentCount,
    imageUrl = imageUrl,
    videoUrl = videoUrl,
    duration = duration,
    publishTime = publishTime,
    isTop = isTop,
    channel = channel,
)

fun FeedItemEntity.toDomain(): FeedCard = when (type) {
    "text_top" -> FeedCard.TextTop(
        id = id,
        title = title,
        source = source,
        commentCount = commentCount,
        publishTime = publishTime ?: "",
        isTop = isTop,
    )
    "left_text_right_image" -> FeedCard.LeftTextRightImage(
        id = id,
        title = title,
        source = source,
        commentCount = commentCount,
        publishTime = publishTime ?: "",
        imageUrl = imageUrl ?: "",
    )
    "large_image" -> FeedCard.LargeImage(
        id = id,
        title = title,
        source = source,
        commentCount = commentCount,
        publishTime = publishTime ?: "",
        imageUrl = imageUrl ?: "",
    )
    "video" -> FeedCard.Video(
        id = id,
        title = title,
        source = source,
        commentCount = commentCount,
        publishTime = publishTime ?: "",
        imageUrl = imageUrl ?: "",
        videoUrl = videoUrl ?: "",
        duration = duration ?: "",
    )
    else -> FeedCard.LeftTextRightImage(
        id = id,
        title = title,
        source = source,
        commentCount = commentCount,
        publishTime = publishTime ?: "",
        imageUrl = imageUrl ?: "",
    )
}
