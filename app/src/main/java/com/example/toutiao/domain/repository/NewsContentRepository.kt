package com.example.toutiao.domain.repository

import com.example.toutiao.domain.model.NewsContent
import kotlinx.coroutines.flow.Flow

/**
 * 新闻内容仓库接口
 *
 * 编排三步流程：fetch → manual → LLM（手动失败时回退）
 * 暴露为 Flow<Stage>，让 UI 层逐步展示加载状态
 */
interface NewsContentRepository {

    /**
     * 加载新闻详情内容
     *
     * @param sourceUrl 源 URL（必填）
     * @param fallbackTitle 备用标题（LLM 解析时如果 HTML 找不到标题可参考）
     * @return Flow<Stage> 流式状态：Fetching → ManualParsing → (LLM Parsing) → ContentReady / Error
     */
    fun loadContent(sourceUrl: String, fallbackTitle: String? = null): Flow<Stage>

    /**
     * 三步流程的每个阶段
     */
    sealed class Stage {
        data class Fetching(val url: String) : Stage()
        data class ManualParsing(val url: String) : Stage()
        data class LlmParsing(val url: String) : Stage()
        data class MockParsing(val url: String) : Stage()
        /**
         * 终态：内容加载完成
         * @param byLlm 是否由非手动路径解析（false = Jsoup 成功，true = LLM/Mock 回退）
         */
        data class ContentReady(val content: NewsContent, val byLlm: Boolean) : Stage()
        data class Error(val message: String) : Stage()
    }
}
