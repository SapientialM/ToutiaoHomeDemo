package com.example.toutiao.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.Transaction
import com.example.toutiao.data.local.dao.FeedDao
import com.example.toutiao.data.local.dao.RemoteKeyDao
import com.example.toutiao.data.local.entity.FeedItemEntity
import com.example.toutiao.data.local.entity.RemoteKeyEntity

@Database(
    entities = [FeedItemEntity::class, RemoteKeyEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun feedDao(): FeedDao
    abstract fun remoteKeyDao(): RemoteKeyDao

    /**
     * 跨 DAO 原子事务：同时替换 feed_items 和 remote_keys，确保数据一致性。
     * 避免 feedDao 写入成功但 remoteKeyDao 写入失败导致的数据不一致。
     */
    @Transaction
    open suspend fun replaceFeedAndKeys(
        channel: String,
        entities: List<FeedItemEntity>,
        keys: List<RemoteKeyEntity>,
    ) {
        feedDao().replaceByChannel(channel, entities)
        remoteKeyDao().replaceByChannel(channel, keys)
    }

    @Transaction
    open suspend fun insertFeedAndKeys(
        entities: List<FeedItemEntity>,
        keys: List<RemoteKeyEntity>,
    ) {
        feedDao().insertAll(entities)
        remoteKeyDao().insertAll(keys)
    }
}
