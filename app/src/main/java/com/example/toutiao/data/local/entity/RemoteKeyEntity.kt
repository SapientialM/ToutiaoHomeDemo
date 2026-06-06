package com.example.toutiao.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "remote_keys",
    primaryKeys = ["id", "channel"],
    indices = [Index(value = ["channel"])],
)
data class RemoteKeyEntity(
    val id: String,
    @ColumnInfo(name = "prev_key") val prevKey: Int?,
    @ColumnInfo(name = "next_key") val nextKey: Int?,
    val channel: String,
)
