package com.example.toutiao.data.remote.datasource

/**
 * 调试控制单例，用于 Mock 数据源的错误/延迟模拟。
 * 仅在 MockDataSource 中生效，不影响 RealRemoteDataSource。
 */
object DebugControls {
    @Volatile var networkDelayMs: Long = 0L
    @Volatile var shouldSimulateError: Boolean = false
    const val DEFAULT_ERROR_MESSAGE: String = "模拟网络错误，请重试"

    fun reset() {
        networkDelayMs = 0L
        shouldSimulateError = false
    }

    val delayOptions: List<Long> = listOf(0L, 500L, 1000L, 2000L, 3000L, 5000L)

    fun delayLabel(ms: Long): String = when (ms) {
        0L -> "无延迟"
        500L -> "500ms"
        1000L -> "1s"
        2000L -> "2s"
        3000L -> "3s"
        5000L -> "5s"
        else -> "${ms}ms"
    }
}
