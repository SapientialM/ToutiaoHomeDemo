package com.example.toutiao.domain.repository

import com.example.toutiao.domain.model.NewsContent
import com.example.toutiao.domain.model.ParseStrategy

/**
 * 新闻内容解析器
 *
 * 抽象层，便于在 Domain 测试中替换为 Mock 实现，也便于未来切换不同
 * 的解析后端（Jsoup / Readability / LLM）。
 *
 * 调用流程（NewsContentRepository 编排）：
 *  1. fetch(url) → 拿到 HTML 字符串
 *  2. parseManual(html) → 成功则返回
 *  3. parseWithLlm(html) → LLM 智能解析（手动失败时回退）
 */
interface NewsContentParser {

    /**
     * 手动解析（Jsoup 等基于 HTML 标签的启发式提取）
     *
     * @return [ParseResult.Success] 解析成功；[ParseResult.ManualFailed] 内容不足，
     *         上层应回退到 LLM
     */
    suspend fun parseManual(html: String, sourceUrl: String): ParseResult

    /**
     * LLM 智能解析（当手动失败时调用）
     *
     * @return [ParseResult.Success] 解析成功；[ParseResult.Failed] 彻底失败（含错误信息）
     */
    suspend fun parseWithLlm(html: String, sourceUrl: String, fallbackTitle: String?): ParseResult

    sealed class ParseResult {
        data class Success(val content: NewsContent) : ParseResult()
        /** 手动解析内容不足，可回退 LLM */
        data class ManualFailed(val reason: String) : ParseResult()
        /** 整体失败（含手动 + LLM 都失败） */
        data class Failed(val error: String) : ParseResult()
    }
}
