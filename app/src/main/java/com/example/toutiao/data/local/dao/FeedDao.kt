package com.example.toutiao.data.local.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.toutiao.data.local.entity.FeedItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedDao {
    // 排序规则：
    // 1. 按日期（天）降序 —— 最新日期在前
    // 2. 同一天内，置顶新闻优先 —— is_top=1 在前
    // 3. 同一天内置顶/非置顶内部，再按精确时间戳降序 —— 时间越晚越靠前
    @Query("SELECT * FROM feed_items WHERE channel = :channel ORDER BY date(created_at / 1000, 'unixepoch') DESC, is_top DESC, created_at DESC")
    fun getFeedByChannel(channel: String): Flow<List<FeedItemEntity>>

    @Query("SELECT * FROM feed_items WHERE channel = :channel ORDER BY date(created_at / 1000, 'unixepoch') DESC, is_top DESC, created_at DESC")
    fun getFeedPagingSource(channel: String): PagingSource<Int, FeedItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<FeedItemEntity>)

    @Transaction
    suspend fun replaceByChannel(channel: String, items: List<FeedItemEntity>) {
        deleteByChannel(channel)
        insertAll(items)
    }

    @Query("DELETE FROM feed_items WHERE channel = :channel")
    suspend fun deleteByChannel(channel: String)

    @Query("DELETE FROM feed_items WHERE created_at < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)
}
