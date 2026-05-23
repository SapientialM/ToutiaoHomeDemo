package com.example.toutiao.domain.model

sealed class FeedCard {
    abstract val id: String
    abstract val title: String
    abstract val source: String
    abstract val commentCount: Int
    abstract val publishTime: String

    data class TextTop(
        override val id: String,
        override val title: String,
        override val source: String,
        override val commentCount: Int,
        override val publishTime: String,
        val isTop: Boolean = true,
    ) : FeedCard()

    data class LeftTextRightImage(
        override val id: String,
        override val title: String,
        override val source: String,
        override val commentCount: Int,
        override val publishTime: String,
        val imageUrl: String,
    ) : FeedCard()

    data class LargeImage(
        override val id: String,
        override val title: String,
        override val source: String,
        override val commentCount: Int,
        override val publishTime: String,
        val imageUrl: String,
    ) : FeedCard()

    data class Video(
        override val id: String,
        override val title: String,
        override val source: String,
        override val commentCount: Int,
        override val publishTime: String,
        val imageUrl: String,
        val videoUrl: String,
        val duration: String,
    ) : FeedCard()
}
