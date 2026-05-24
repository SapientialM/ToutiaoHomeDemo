package com.example.toutiao.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
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

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "toutiao.db",
        )
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideFeedDao(database: AppDatabase): FeedDao {
        return database.feedDao()
    }

    @Provides
    fun provideRemoteKeyDao(database: AppDatabase): RemoteKeyDao {
        return database.remoteKeyDao()
    }
}
