package com.example.toutiao.di

import com.example.toutiao.data.repository.MallRepositoryImpl
import com.example.toutiao.data.repository.NewsRepositoryImpl
import com.example.toutiao.data.repository.ReadPositionRepositoryImpl
import com.example.toutiao.domain.repository.MallRepository
import com.example.toutiao.domain.repository.NewsRepository
import com.example.toutiao.domain.repository.ReadPositionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindNewsRepository(impl: NewsRepositoryImpl): NewsRepository

    @Binds
    @Singleton
    abstract fun bindReadPositionRepository(impl: ReadPositionRepositoryImpl): ReadPositionRepository

    @Binds
    @Singleton
    abstract fun bindMallRepository(impl: MallRepositoryImpl): MallRepository
}
