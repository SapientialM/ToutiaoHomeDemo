package com.example.toutiao.domain.model

import androidx.compose.runtime.Immutable

/**
 * 热榜列表项
 *
 * 与 [FeedCard] 的区别：热榜是纯文字榜单，没有图片/视频/来源等元信息，
 * 强调"序号 + 标题 + 热度标签"三要素。
 */
@Immutable
data class HotListItem(
    val id: String,
    val rank: Int,
    val title: String,
    val badge: HotBadge,
    /**
     * 新闻源 URL。点击热榜项进入详情页时使用，详情页会 HTTP 抓取此 URL
     * 走 Jsoup → LLM → Mock 三级回退解析。
     */
    val sourceUrl: String? = null,
    /**
     * 数据来源（头条/B站等），详情页可展示「来源：xxx」。
     */
    val source: String = "",
)

/**
 * 热榜徽标。设计稿支持 5 种：火焰 / 爆 / 热 / 新 / 辟谣
 *
 * 视觉差异：
 *  - [Fire]  橙色实心火焰图标，常驻热门
 *  - [Hot]   红底白字小方块，表示"热度高"
 *  - [Boom]  红底白字大圆角，强调"爆款"
 *  - [New]   红底白字小方块，表示"新上榜"
 *  - [Rumor] 蓝底白字方块（头条特征色），表示"官方辟谣"
 */
@Immutable
sealed class HotBadge {
    data object Fire : HotBadge()
    data object Hot : HotBadge()
    data object Boom : HotBadge()
    data object New : HotBadge()
    data object Rumor : HotBadge()
    data object None : HotBadge()
}

/**
 * 热榜顶部"快捷入口"（4 个圆角胶囊）
 */
@Immutable
data class HotQuickAction(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: String = "🔥",
)
