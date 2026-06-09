package com.example.toutiao.di

import com.example.toutiao.data.parser.JsoupNewsContentParser
import com.example.toutiao.data.parser.MinimaxNewsContentParser
import com.example.toutiao.data.parser.MockFallbackNewsContentParser
import com.example.toutiao.data.parser.OkHttpContentFetcher
import com.example.toutiao.data.repository.NewsContentRepositoryImpl
import com.example.toutiao.domain.repository.HttpContentFetcher
import com.example.toutiao.domain.repository.NewsContentParser
import com.example.toutiao.domain.repository.NewsContentRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 新闻内容解析依赖绑定
 *
 * 实现类在 data/parser/ 下，接口在 domain/repository/ 下。
 * 由于 Hilt @Binds + KSP 在某些场景下会出现 "type could not be resolved"
 * 的伪问题（实际编译通过但 Hilt 处理器找不到），这里对第三个绑定（Mock）
 * 改用 @Provides 模式，规避该问题。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NewsContentModule {

    @Binds
    @Singleton
    abstract fun bindHttpContentFetcher(impl: OkHttpContentFetcher): HttpContentFetcher

    @Binds
    @Singleton
    @ManualParser
    abstract fun bindManualParser(impl: JsoupNewsContentParser): NewsContentParser

    @Binds
    @Singleton
    @LlmParser
    abstract fun bindLlmParser(impl: MinimaxNewsContentParser): NewsContentParser

    @Binds
    @Singleton
    abstract fun bindNewsContentRepository(impl: NewsContentRepositoryImpl): NewsContentRepository
}

/**
 * Mock 解析器用 @Provides 模式绑定（避免 Hilt + KSP 对 @Binds 第三绑定的
 * "type could not be resolved" 已知问题）。
 */
@Module
@InstallIn(SingletonComponent::class)
object NewsContentProvidesModule {

    @Provides
    @Singleton
    @MockParser
    fun provideMockParser(impl: MockFallbackNewsContentParser): NewsContentParser = impl
}

@javax.inject.Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ManualParser

@javax.inject.Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LlmParser

@javax.inject.Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MockParser
