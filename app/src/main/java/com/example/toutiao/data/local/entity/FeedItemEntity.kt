package com.example.toutiao.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "feed_items",
    indices = [Index(value = ["channel"])],
)
data class FeedItemEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "source") val source: String,
    @ColumnInfo(name = "comment_count") val commentCount: Int = 0,
    @ColumnInfo(name = "image_url") val imageUrl: String? = null,
    @ColumnInfo(name = "video_url") val videoUrl: String? = null,
    @ColumnInfo(name = "duration") val duration: String? = null,
    @ColumnInfo(name = "publish_time") val publishTime: String? = null,
    @ColumnInfo(name = "is_top") val isTop: Boolean = false,
    @ColumnInfo(name = "channel") val channel: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)
