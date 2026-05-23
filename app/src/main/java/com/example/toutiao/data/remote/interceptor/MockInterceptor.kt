package com.example.toutiao.data.remote.interceptor

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import timber.log.Timber

/**
 * OkHttp 拦截器，拦截所有 [baseUrl] 开头的请求并返回 Mock JSON 响应。
 * Demo 阶段无需真实后端即可跑通完整 MVI 数据流。
 */
class MockInterceptor(private val baseUrl: String) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()

        Timber.d("MockInterceptor — intercepted request: $url")

        if (!url.startsWith(baseUrl)) {
            Timber.d("MockInterceptor — URL doesn't match baseUrl, passing through")
            return chain.proceed(request)
        }

        val channel = request.url.queryParameter("channel") ?: "recommend"
        val page = (request.url.queryParameter("page")?.toIntOrNull() ?: 0)
        val hasMore = page < 2
        val itemsJson = buildMockItems(channel, page)

        val body = """
            {
              "code": 0,
              "data": {
                "list": $itemsJson,
                "hasMore": $hasMore
              }
            }
        """.trimIndent()

        Timber.d("MockInterceptor — returning mock response for channel=$channel, page=$page")
        Timber.v("MockInterceptor — response body: $body")

        return Response.Builder()
            .code(200)
            .message("OK (Mock)")
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .addHeader("Content-Type", "application/json; charset=utf-8")
            .body(body.toResponseBody("application/json; charset=utf-8".toMediaType()))
            .build()
    }
}

private fun buildMockItems(channel: String, page: Int): String {
    val items = if (page == 0) {
        listOf(
            """{"id":"${channel}_1","type":"text_top","title":"上合组织成员国元首理事会：习近平发表重要讲话强调深化合作","source":"新华网","commentCount":12876,"publishTime":"3小时前","isTop":true}""",
            """{"id":"${channel}_2","type":"left_text_right_image","title":"华为发布2026年Q1财报：营收同比增长18%，汽车业务成新增长极","source":"36氪","commentCount":5432,"publishTime":"5小时前","imageUrl":"https://picsum.photos/seed/n2/400/300"}""",
            """{"id":"${channel}_3","type":"large_image","title":"SpaceX星舰完成第六次轨道试飞，首次实现上面级在轨推进剂转移","source":"环球时报","commentCount":9876,"publishTime":"1小时前","imageUrl":"https://picsum.photos/seed/n3/800/450"}""",
            """{"id":"${channel}_4","type":"video","title":"黑神话悟空主创独家专访：DLC开发进度过半，将引入全新战斗系统","source":"游研社","commentCount":23456,"publishTime":"2小时前","imageUrl":"https://picsum.photos/seed/n4/800/450","duration":"08:25"}""",
            """{"id":"${channel}_5","type":"left_text_right_image","title":"北京二手房成交量连续3个月破万套，住建委或将出台新调控政策","source":"财经网","commentCount":3456,"publishTime":"6小时前","imageUrl":"https://picsum.photos/seed/n5/400/300"}""",
            """{"id":"${channel}_6","type":"text_top","title":"2026年高考报名人数突破1400万，教育部部署考试安全工作","source":"教育部","commentCount":5678,"publishTime":"4小时前","isTop":true}""",
            """{"id":"${channel}_7","type":"large_image","title":"苹果WWDC 2026前瞻：iOS 20或引入AI原生交互，Vision Pro 2有望亮相","source":"虎嗅","commentCount":7890,"publishTime":"2小时前","imageUrl":"https://picsum.photos/seed/n7/800/450"}""",
            """{"id":"${channel}_8","type":"video","title":"世界女排联赛：中国队苦战五局逆转巴西队，龚翔宇末局独得8分","source":"央视体育","commentCount":15678,"publishTime":"1小时前","imageUrl":"https://picsum.photos/seed/n8/800/450","duration":"12:40"}""",
        )
    } else {
        listOf(
            """{"id":"${channel}_p${page}_1","type":"left_text_right_image","title":"国家统计局发布5月CPI数据：同比上涨0.3%，猪肉价格环比回落","source":"经济日报","commentCount":2345,"publishTime":"7小时前","imageUrl":"https://picsum.photos/seed/np${page}a/400/300"}""",
            """{"id":"${channel}_p${page}_2","type":"large_image","title":"特斯拉宣布全系车型降价：Model 3起售价降至22.99万元","source":"懂车帝","commentCount":6543,"publishTime":"4小时前","imageUrl":"https://picsum.photos/seed/np${page}b/800/450"}""",
        )
    }
    return "[${items.joinToString(",")}]"
}
