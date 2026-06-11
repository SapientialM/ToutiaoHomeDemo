package com.example.toutiao.di

import com.example.toutiao.data.llm.MinimaxChatClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

// =============================================================================
// AiModule — 豆包 AI 对话相关依赖
//
// MinimaxChatClient 用 @Inject constructor 也可工作，
// 这里用 @Provides 是为了：
//  1. 显式把现有 OkHttpClient 注入进去（NetworkModule 提供的单例）
//  2. 跟 MinimaxNewsContentParser 风格保持一致
// =============================================================================
@Module
@InstallIn(SingletonComponent::class)
object AiModule {

    @Provides
    @Singleton
    fun provideMinimaxChatClient(
        okHttpClient: OkHttpClient,
    ): MinimaxChatClient = MinimaxChatClient(okHttpClient)
}