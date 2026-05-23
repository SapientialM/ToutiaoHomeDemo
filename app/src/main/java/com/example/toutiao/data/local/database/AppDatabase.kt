package com.example.toutiao.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.toutiao.data.local.dao.FeedDao
import com.example.toutiao.data.local.dao.RemoteKeyDao
import com.example.toutiao.data.local.entity.FeedItemEntity
import com.example.toutiao.data.local.entity.RemoteKeyEntity

@Database(
    entities = [FeedItemEntity::class, RemoteKeyEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun feedDao(): FeedDao
    abstract fun remoteKeyDao(): RemoteKeyDao
}
