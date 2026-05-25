package com.example.toutiao.data.remote.datasource

import com.example.toutiao.data.remote.dto.NewsFeedData
import com.example.toutiao.data.remote.dto.NewsFeedResponse
import com.example.toutiao.data.remote.dto.NewsItemDto
import kotlinx.coroutines.delay
import timber.log.Timber
import java.io.IOException

/**
 * Demo 阶段的 Mock 远程数据源，直接生成数据，无需网络请求。
 * 替代原有的 MockInterceptor（OkHttp 层面拦截），在 Repository 层直接注入。
 *
 * 支持通过 [DebugControls] 配置：
 * - 网络延迟模拟（0～5s）
 * - 网络错误模拟（抛出 IOException）
 */
class MockDataSource : RemoteDataSource {

    override suspend fun getNewsFeed(channel: String, page: Int, size: Int): NewsFeedResponse {
        // 延迟模拟
        val delayMs = DebugControls.networkDelayMs
        if (delayMs > 0) {
            Timber.d("MockDataSource — simulating network delay: ${delayMs}ms")
            delay(delayMs)
        }

        // 错误模拟
        if (DebugControls.shouldSimulateError) {
            Timber.w("MockDataSource — simulating network error")
            throw IOException(DebugControls.DEFAULT_ERROR_MESSAGE)
        }

        Timber.d("MockDataSource — generating data: channel=$channel, page=$page")
        val items = buildMockItems(channel, page)
        val hasMore = page < 2

        return NewsFeedResponse(
            code = 0,
            data = NewsFeedData(list = items, hasMore = hasMore),
        )
    }

    private fun buildMockItems(channel: String, page: Int): List<NewsItemDto> {
        if (page == 0) return page0Items(channel)
        return pagedItems(channel, page)
    }

    private fun page0Items(channel: String) = listOf(
        NewsItemDto("${channel}_1", "text_top", "上合组织成员国元首理事会：习近平发表重要讲话强调深化合作", "新华网", 12876, null, null, null, "3小时前", isTop = true),
        NewsItemDto("${channel}_2", "left_text_right_image", "华为发布2026年Q1财报：营收同比增长18%，汽车业务成新增长极", "36氪", 5432, "https://picsum.photos/seed/n2/400/300", null, null, "5小时前"),
        NewsItemDto("${channel}_3", "large_image", "SpaceX星舰完成第六次轨道试飞，首次实现上面级在轨推进剂转移", "环球时报", 9876, "https://picsum.photos/seed/n3/800/450", null, null, "1小时前"),
        NewsItemDto("${channel}_4", "video", "黑神话悟空主创独家专访：DLC开发进度过半，将引入全新战斗系统", "游研社", 23456, "https://picsum.photos/seed/n4/800/450", "", "08:25", "2小时前"),
        NewsItemDto("${channel}_5", "left_text_right_image", "北京二手房成交量连续3个月破万套，住建委或将出台新调控政策", "财经网", 3456, "https://picsum.photos/seed/n5/400/300", null, null, "6小时前"),
        NewsItemDto("${channel}_6", "text_top", "2026年高考报名人数突破1400万，教育部部署考试安全工作", "教育部", 5678, null, null, null, "4小时前", isTop = true),
        NewsItemDto("${channel}_7", "large_image", "苹果WWDC 2026前瞻：iOS 20或引入AI原生交互，Vision Pro 2有望亮相", "虎嗅", 7890, "https://picsum.photos/seed/n7/800/450", null, null, "2小时前"),
        NewsItemDto("${channel}_8", "video", "世界女排联赛：中国队苦战五局逆转巴西队，龚翔宇末局独得8分", "央视体育", 15678, "https://picsum.photos/seed/n8/800/450", "", "12:40", "1小时前"),
    )

    private fun pagedItems(channel: String, page: Int) = listOf(
        NewsItemDto("${channel}_p${page}_1", "left_text_right_image", "(第${page + 1}页) 国家统计局发布5月CPI数据：同比上涨0.3%，猪肉价格环比回落", "经济日报", 2345, "https://picsum.photos/seed/np${page}a/400/300", null, null, "7小时前"),
        NewsItemDto("${channel}_p${page}_2", "large_image", "(第${page + 1}页) 特斯拉宣布全系车型降价：Model 3起售价降至22.99万元", "懂车帝", 6543, "https://picsum.photos/seed/np${page}b/800/450", null, null, "4小时前"),
    )
}
