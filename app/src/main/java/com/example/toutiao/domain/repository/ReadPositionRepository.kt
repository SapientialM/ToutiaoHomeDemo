package com.example.toutiao.domain.repository

/**
 * 阅读位置仓库
 *
 * 持久化每个频道的"上次看到这里"位置。用来在用户重新进入首页时，
 * 在上次中断处之上展示「上次看到这里，点击回到 N 楼」提示。
 *
 * 持久化策略：
 *  - 粒度：按 channel 独立（推荐/热榜/深圳/小说/... 各自一份）
 *  - 存储内容：上次阅读到的首条 feed card id（而非 index，因为分页后 index 会变）
 *  - 触发时机：列表滚动停止 + 首条可见项变化时记录
 *
 * 为什么不存 index：
 *  分页数据会随时间/刷新变化，index=5 之后会指向不同的 card。
 *  用 card id 配合当前列表的查找，结果稳定。
 */
interface ReadPositionRepository {
    /** 获取某频道上次看到的首条 card id，未曾记录过返回 null */
    fun getLastSeenId(channel: String): String?

    /** 获取某频道上次记录的时间戳（毫秒），未曾记录返回 0 */
    fun getLastSeenAt(channel: String): Long

    /** 记录某频道当前阅读到的首条 card id（滚动停止时调用） */
    fun setLastSeenId(channel: String, cardId: String, timestamp: Long = System.currentTimeMillis())

    /** 清除某频道的阅读位置（如用户主动"已读完"时） */
    fun clear(channel: String)
}
