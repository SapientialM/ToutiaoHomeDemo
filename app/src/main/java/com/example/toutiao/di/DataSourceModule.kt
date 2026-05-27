package com.example.toutiao.di

import android.content.Context
import com.example.toutiao.data.remote.datasource.MockDataSource
import com.example.toutiao.data.remote.datasource.RemoteDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataSourceModule {

    @Provides
    @Singleton
    // 如果要切换真实API，直接切为 RealRemoteDataSource 即可
    fun provideRemoteDataSource(@ApplicationContext context: Context): RemoteDataSource =
        MockDataSource(context)
}
