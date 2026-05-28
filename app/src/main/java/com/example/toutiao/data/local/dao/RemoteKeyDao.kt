package com.example.toutiao.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.toutiao.data.local.entity.RemoteKeyEntity

@Dao
interface RemoteKeyDao {
    @Query("SELECT * FROM remote_keys WHERE id = :id")
    suspend fun getRemoteKey(id: String): RemoteKeyEntity?

    @Query("SELECT * FROM remote_keys WHERE channel = :channel ORDER BY next_key DESC LIMIT 1")
    suspend fun getLastRemoteKeyByChannel(channel: String): RemoteKeyEntity?

    @Transaction
    suspend fun replaceByChannel(channel: String, keys: List<RemoteKeyEntity>) {
        deleteByChannel(channel)
        insertAll(keys)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(keys: List<RemoteKeyEntity>)

    @Query("DELETE FROM remote_keys WHERE channel = :channel")
    suspend fun deleteByChannel(channel: String)
}
