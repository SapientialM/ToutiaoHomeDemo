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
    fun provideRemoteDataSource(@ApplicationContext context: Context): RemoteDataSource =
        MockDataSource(context)
}
