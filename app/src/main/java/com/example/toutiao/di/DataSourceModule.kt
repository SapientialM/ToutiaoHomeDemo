package com.example.toutiao.di

import com.example.toutiao.data.remote.datasource.MockDataSource
import com.example.toutiao.data.remote.datasource.RemoteDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataSourceModule {

    @Provides
    @Singleton
    fun provideRemoteDataSource(): RemoteDataSource = MockDataSource()
}
