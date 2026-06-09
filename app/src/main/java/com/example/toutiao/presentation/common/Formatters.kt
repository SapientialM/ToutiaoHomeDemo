package com.example.toutiao.presentation.common

import java.util.concurrent.TimeUnit

/**
 * 共享的格式化工具，集中管理数字、时间等 UI 文案的展示规则。
 *
 * 设计原则：
 * - 全 App 只有一套数字格式（评论数/粉丝数），避免「8,642评」 vs 「2万评论」混用
 * - 全 App 只有一套时间格式（「X小时前」无空格），避免「2 小时前」 vs 「3小时前」混用
 * - 任何 Card 拼 meta 行时统一通过本类，PM 审查 ISSUE-002 修复
 */
object Formatters {

    /**
     * 评论数 / 粉丝数 紧凑显示：
     * - < 10_000：阿拉伯数字 + 千位逗号（8,642）
     * - >= 10_000：「X.X万」（2.1万）
     */
    fun compactCount(n: Int): String = when {
        n < 10_000 -> "%,d".format(n)
        else -> "%.1f万".format(n / 10_000.0)
    }

    /**
     * 相对时间（无空格紧凑格式）：
     * - < 1 分钟：「刚刚」
     * - < 1 小时：「X分钟前」
     * - 今日（< 24h）：「X小时前」
     * - 昨日：仍用「X小时前」即可（避免 24h 边界突变）
     * - >= 24h：「X天前」
     */
    fun relativeTime(timeMillis: Long, now: Long = System.currentTimeMillis()): String {
        if (timeMillis <= 0L) return ""
        val deltaMs = (now - timeMillis).coerceAtLeast(0L)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(deltaMs)
        return when {
            minutes < 1 -> "刚刚"
            minutes < 60 -> "${minutes}分钟前"
            else -> "${TimeUnit.MILLISECONDS.toHours(deltaMs)}小时前"
        }
    }

    /**
     * 拼接元信息行：来源 · 时间 · 评论数
     *
     * 用法：`val meta = Formatters.metaLine(source = "新华社", timeMs = news.publishTime, comments = news.commentCount)`
     */
    fun metaLine(source: String, timeMs: Long, comments: Int): String =
        "$source · ${relativeTime(timeMs)} · ${compactCount(comments)}评"
}
