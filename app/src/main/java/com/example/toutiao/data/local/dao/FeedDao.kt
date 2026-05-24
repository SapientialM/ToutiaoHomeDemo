package com.example.toutiao.data.local.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.toutiao.data.local.entity.FeedItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedDao {
    @Query("SELECT * FROM feed_items WHERE channel = :channel ORDER BY created_at DESC")
    fun getFeedByChannel(channel: String): Flow<List<FeedItemEntity>>

    @Query("SELECT * FROM feed_items WHERE channel = :channel ORDER BY created_at DESC")
    fun getFeedPagingSource(channel: String): PagingSource<Int, FeedItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<FeedItemEntity>)

    @Query("DELETE FROM feed_items WHERE channel = :channel")
    suspend fun deleteByChannel(channel: String)

    @Query("DELETE FROM feed_items WHERE created_at < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)
}
