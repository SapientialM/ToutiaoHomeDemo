package com.example.toutiao.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.toutiao.data.local.dao.FeedDao
import com.example.toutiao.data.local.dao.RemoteKeyDao
import com.example.toutiao.data.local.database.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    // v1 → v2 升级时重建表
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS feed_items")
            db.execSQL("DROP TABLE IF EXISTS remote_keys")
        }
    }

    // v3 → v4: RemoteKeyEntity 主键改为复合主键 ["id", "channel"]
    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 重建 remote_keys 表以应用新的复合主键
            db.execSQL("DROP TABLE IF EXISTS remote_keys")
            db.execSQL(
                """
                CREATE TABLE remote_keys (
                    id TEXT NOT NULL,
                    prev_key INTEGER,
                    next_key INTEGER,
                    channel TEXT NOT NULL,
                    PRIMARY KEY(id, channel)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX index_remote_keys_channel ON remote_keys(channel)")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "toutiao.db",
        )
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .addMigrations(MIGRATION_1_2, MIGRATION_3_4)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideFeedDao(database: AppDatabase): FeedDao {
        return database.feedDao()
    }

    @Provides
    @Singleton
    fun provideRemoteKeyDao(database: AppDatabase): RemoteKeyDao {
        return database.remoteKeyDao()
    }
}
