package com.example.toutiao.domain.repository

/**
 * HTTP 内容抓取器
 *
 * 详情页流程的第一步：HTTP 访问源 URL 拿到 HTML 字符串，
 * 再交给 NewsContentParser 做手动 / LLM 解析。
 */
interface HttpContentFetcher {

    /**
     * 抓取 URL 内容
     *
     * @return [Result.Success] 成功（含 HTML 文本 + 最终 URL，处理重定向后）
     *         [Result.HttpError] HTTP 错误（含状态码）
     *         [Result.NetworkError] 网络错误（连接超时、DNS 失败等）
     *         [Result.InvalidUrl] URL 格式不合法
     */
    suspend fun fetch(url: String): Result

    sealed class Result {
        data class Success(val html: String, val finalUrl: String) : Result()
        data class HttpError(val code: Int, val message: String) : Result()
        data class NetworkError(val cause: String) : Result()
        data class InvalidUrl(val url: String) : Result()
    }
}
