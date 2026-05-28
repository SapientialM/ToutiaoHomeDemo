package com.example.toutiao.data.mapper

import com.example.toutiao.data.local.entity.FeedItemEntity
import com.example.toutiao.data.remote.dto.NewsItemDto
import com.example.toutiao.domain.model.FeedCard

// =============================================================================
// NewsMapper — DTO ↔ Entity ↔ Domain 三层双向转换
//
// 数据在三层模型之间流转：
//
//   NewsItemDto (network DTO)       ← RemoteDataSource 返回的结构
//        ↓ toEntity()
//   FeedItemEntity (Room Entity)   ← 写入 Room 数据库的结构
//        ↓ toDomain()
//   FeedCard (Domain Model)        ← ViewModel 和 UI 使用的结构
//
// 为什么需要三层：
//   - DTO 依赖 Kotlinx Serialization（@SerialName 注解），不宜透传到 UI
//   - Entity 依赖 Room（@Entity/@ColumnInfo 注解），不宜透传到 UI
//   - Domain 是纯 Kotlin sealed class，UI 层唯一依赖的模型
// =============================================================================

// ── DTO → Entity：网络数据转为 Room 表行 ──────────────────────────────────────
// 调用方：NewsRemoteMediator.load() 或 NewsRepositoryImpl.getNewsFeed()
// 在网络请求成功后调用，将每条 DTO 映射为 Entity 然后 batch insert 到 Room
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
    createdAt = createdAt,
)

// ── Entity → Domain：Room 数据转为 UI 模型 ────────────────────────────────────
// 调用方：Pager.flow.map { pagingData -> pagingData.map { it.toDomain() } }
//         在 PagingData 流经 pipeline 时，将每条 Entity 转为 FeedCard
//
// when 分支按 type 字段分发到对应的 FeedCard 子类型：
//   "text_top"              → FeedCard.TextTop
//   "left_text_right_image" → FeedCard.LeftTextRightImage
//   "large_image"           → FeedCard.LargeImage
//   "video"                 → FeedCard.Video
//   其它（兜底）             → FeedCard.LeftTextRightImage
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
    // 未知 type 按 LeftTextRightImage 兜底渲染，避免崩溃
    else -> FeedCard.LeftTextRightImage(
        id = id,
        title = title,
        source = source,
        commentCount = commentCount,
        publishTime = publishTime ?: "",
        imageUrl = imageUrl ?: "",
    )
}
