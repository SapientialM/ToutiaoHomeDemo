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
 * 每个 channel 有独立的数据集：
 * - recommend: 综合资讯（科技/财经/教育/体育混排）
 * - hot: 热榜（高评论数、"热议"/"爆"标记标题）
 * - video: 纯视频卡片
 * - society: 社会/民生/法治类新闻
 *
 * 支持通过 [DebugControls] 配置：
 * - 网络延迟模拟（0～5s）
 * - 网络错误模拟（抛出 IOException）
 */
class MockDataSource : RemoteDataSource {

    override suspend fun getNewsFeed(channel: String, page: Int, size: Int): NewsFeedResponse {
        val delayMs = DebugControls.networkDelayMs
        if (delayMs > 0) {
            Timber.d("MockDataSource — simulating network delay: ${delayMs}ms")
            delay(delayMs)
        }

        if (DebugControls.shouldSimulateError) {
            Timber.w("MockDataSource — simulating network error")
            throw IOException(DebugControls.DEFAULT_ERROR_MESSAGE)
        }

        Timber.d("MockDataSource — generating data: channel=$channel, page=$page")
        val items = when (channel) {
            "recommend" -> recommendItems(page)
            "hot" -> hotItems(page)
            "video" -> videoItems(page)
            "society" -> societyItems(page)
            else -> recommendItems(page)
        }
        val hasMore = page < 2

        return NewsFeedResponse(
            code = 0,
            data = NewsFeedData(list = items, hasMore = hasMore),
        )
    }

    // ── recommend: 综合资讯（科技 + 财经 + 教育 + 体育混排） ──────────────────
    private fun recommendItems(page: Int): List<NewsItemDto> {
        if (page == 0) return listOf(
            NewsItemDto("rec_1", "text_top", "上合组织成员国元首理事会：习近平发表重要讲话强调深化合作", "新华网", 12876, null, null, null, "3小时前", isTop = true),
            NewsItemDto("rec_2", "left_text_right_image", "华为发布2026年Q1财报：营收同比增长18%，汽车业务成新增长极", "36氪", 5432, "https://picsum.photos/seed/rec2/400/300", null, null, "5小时前"),
            NewsItemDto("rec_3", "large_image", "SpaceX星舰完成第六次轨道试飞，首次实现上面级在轨推进剂转移", "环球时报", 9876, "https://picsum.photos/seed/rec3/800/450", null, null, "1小时前"),
            NewsItemDto("rec_4", "video", "黑神话悟空主创独家专访：DLC开发进度过半，将引入全新战斗系统", "游研社", 23456, "https://picsum.photos/seed/rec4/800/450", "", "08:25", "2小时前"),
            NewsItemDto("rec_5", "left_text_right_image", "北京二手房成交量连续3个月破万套，住建委或将出台新调控政策", "财经网", 3456, "https://picsum.photos/seed/rec5/400/300", null, null, "6小时前"),
            NewsItemDto("rec_6", "text_top", "2026年高考报名人数突破1400万，教育部部署考试安全工作", "教育部", 5678, null, null, null, "4小时前", isTop = true),
            NewsItemDto("rec_7", "large_image", "苹果WWDC 2026前瞻：iOS 20或引入AI原生交互，Vision Pro 2有望亮相", "虎嗅", 7890, "https://picsum.photos/seed/rec7/800/450", null, null, "2小时前"),
            NewsItemDto("rec_8", "video", "世界女排联赛：中国队苦战五局逆转巴西队，龚翔宇末局独得8分", "央视体育", 15678, "https://picsum.photos/seed/rec8/800/450", "", "12:40", "1小时前"),
        )
        if (page == 1) return listOf(
            NewsItemDto("rec_p1_1", "left_text_right_image", "OpenAI发布GPT-5：多模态能力大幅提升，推理成本降低90%", "量子位", 8765, "https://picsum.photos/seed/recp1a/400/300", null, null, "8小时前"),
            NewsItemDto("rec_p1_2", "video", "世界杯预选赛：国足2:1逆转泰国，武磊绝杀建功", "央视体育", 45678, "https://picsum.photos/seed/recp1b/800/450", "", "05:33", "9小时前"),
            NewsItemDto("rec_p1_3", "left_text_right_image", "央行宣布下调LPR利率10个基点，房贷利率降至历史新低", "经济日报", 6789, "https://picsum.photos/seed/recp1c/400/300", null, null, "10小时前"),
            NewsItemDto("rec_p1_4", "text_top", "国务院印发《人工智能产业发展三年行动计划》", "人民日报", 3456, null, null, null, "11小时前", isTop = true),
        )
        return listOf(
            NewsItemDto("rec_p2_1", "large_image", "深圳出台低空经济促进条例：无人机配送将全面商用", "南方日报", 2345, "https://picsum.photos/seed/recp2a/800/450", null, null, "昨天"),
            NewsItemDto("rec_p2_2", "left_text_right_image", "阿里云宣布全线产品降价30%，打响云计算价格战", "虎嗅", 4567, "https://picsum.photos/seed/recp2b/400/300", null, null, "昨天"),
        )
    }

    // ── hot: 热榜（高评论数 + 标题带热度标记） ─────────────────────────────────
    private fun hotItems(page: Int): List<NewsItemDto> {
        if (page == 0) return listOf(
            NewsItemDto("hot_1", "text_top", "[爆] 东方甄选直播间争议持续发酵：主播公开道歉，股价单日跌超15%", "财经网", 98765, null, null, null, "30分钟前", isTop = true),
            NewsItemDto("hot_2", "left_text_right_image", "[热议] 多地发布高温红色预警：华北华东多地气温突破40℃创新高", "央视新闻", 76543, "https://picsum.photos/seed/hot2/400/300", null, null, "1小时前"),
            NewsItemDto("hot_3", "video", "[爆] 五一档票房突破50亿：《速度与激情12》单日破8亿夺冠", "猫眼电影", 65432, "https://picsum.photos/seed/hot3/800/450", "", "03:15", "2小时前"),
            NewsItemDto("hot_4", "large_image", "[热议] 新一线城市排名出炉：成都蝉联榜首，合肥首次进入前十", "第一财经", 54321, "https://picsum.photos/seed/hot4/800/450", null, null, "2小时前"),
            NewsItemDto("hot_5", "text_top", "[爆] 某知名艺人涉嫌偷逃税款被立案调查，工作室发文否认", "新京报", 87654, null, null, null, "3小时前", isTop = true),
            NewsItemDto("hot_6", "left_text_right_image", "[热议] 苹果与谷歌达成AI搜索合作协议：Safari默认引擎或生变", "36氪", 43210, "https://picsum.photos/seed/hot6/400/300", null, null, "4小时前"),
            NewsItemDto("hot_7", "video", "[爆] 欧冠决赛：皇马点球大战击败曼城，第16次捧起大耳朵杯", "央视体育", 98765, "https://picsum.photos/seed/hot7/800/450", "", "15:40", "5小时前"),
            NewsItemDto("hot_8", "large_image", "[热议] 特斯拉Optimus机器人开始在工厂实际工作：可自主搬运20kg物料", "懂车帝", 34567, "https://picsum.photos/seed/hot8/800/450", null, null, "6小时前"),
        )
        if (page == 1) return listOf(
            NewsItemDto("hot_p1_1", "text_top", "[爆] 某P2P平台暴雷案宣判：主犯被判无期徒刑，涉案金额超千亿", "新华社", 87654, null, null, null, "7小时前", isTop = true),
            NewsItemDto("hot_p1_2", "large_image", "[热议] 各地公考报名人数再创新高：最热岗位竞争比达5800:1", "中公教育", 54321, "https://picsum.photos/seed/hotp1b/800/450", null, null, "8小时前"),
            NewsItemDto("hot_p1_3", "video", "[热议] 周杰伦新专辑《时空旅行》全球首发：首日销量突破800万张", "QQ音乐", 45678, "https://picsum.photos/seed/hotp1c/800/450", "", "04:55", "9小时前"),
        )
        return listOf(
            NewsItemDto("hot_p2_1", "text_top", "[热议] 国考补录公告发布：计划补录3800人，5月28日起报名", "人社部", 23456, null, null, null, "昨天", isTop = true),
            NewsItemDto("hot_p2_2", "left_text_right_image", "[热议] 多地公积金政策调整：二套房首付比例降至20%", "经济日报", 12345, "https://picsum.photos/seed/hotp2b/400/300", null, null, "昨天"),
        )
    }

    // ── video: 纯视频内容 ──────────────────────────────────────────────────────
    private fun videoItems(page: Int): List<NewsItemDto> {
        if (page == 0) return listOf(
            NewsItemDto("vid_1", "video", "【纪录片】走进华西村：中国最富农村的兴衰三十年", "新华社", 45678, "https://picsum.photos/seed/vid1/800/450", "", "45:20", "1小时前"),
            NewsItemDto("vid_2", "video", "2026 F1摩纳哥大奖赛正赛集锦：勒克莱尔主场夺冠", "腾讯体育", 34567, "https://picsum.photos/seed/vid2/800/450", "", "12:15", "2小时前"),
            NewsItemDto("vid_3", "video", "小米SU7 Ultra纽北圈速实测：6分35秒打破四门电动车纪录", "易车网", 56789, "https://picsum.photos/seed/vid3/800/450", "", "08:42", "3小时前"),
            NewsItemDto("vid_4", "video", "【深度】日本核污水排海一周年：福岛海域最新核素检测结果公布", "环球时报", 23456, "https://picsum.photos/seed/vid4/800/450", "", "18:30", "4小时前"),
            NewsItemDto("vid_5", "video", "苹果Vision Pro 2开箱体验：重量减轻40%，视场角提升至120度", "科技美学", 67890, "https://picsum.photos/seed/vid5/800/450", "", "06:50", "5小时前"),
            NewsItemDto("vid_6", "video", "【综艺】乘风破浪的姐姐第六季总决赛：宁静组《山海》燃爆全场", "芒果TV", 78901, "https://picsum.photos/seed/vid6/800/450", "", "1:05:30", "6小时前"),
            NewsItemDto("vid_7", "video", "嫦娥七号月球南极着陆全程直播回放：首次发现水冰沉积层", "央视新闻", 89012, "https://picsum.photos/seed/vid7/800/450", "", "2:15:40", "7小时前"),
            NewsItemDto("vid_8", "video", "2026年NBA总决赛G7：湖人vs凯尔特人全场比赛回放", "腾讯体育", 90123, "https://picsum.photos/seed/vid8/800/450", "", "2:48:30", "8小时前"),
        )
        if (page == 1) return listOf(
            NewsItemDto("vid_p1_1", "video", "【Vlog】我在南极科考站的一天：零下60度的日常工作与生活", "新华社", 23456, "https://picsum.photos/seed/vidp1a/800/450", "", "22:10", "9小时前"),
            NewsItemDto("vid_p1_2", "video", "歌手2026总决赛：刀郎《罗刹海市》Live首唱，评委集体起立鼓掌", "湖南卫视", 56789, "https://picsum.photos/seed/vidp1b/800/450", "", "06:30", "10小时前"),
            NewsItemDto("vid_p1_3", "video", "比亚迪仰望U9量产版全球首试：四电机1300马力零百1.9秒", "懂车帝", 34567, "https://picsum.photos/seed/vidp1c/800/450", "", "15:20", "11小时前"),
        )
        return listOf(
            NewsItemDto("vid_p2_1", "video", "【纪录片】三北防护林工程40年：从荒漠到绿洲的生态奇迹", "央视纪录", 12345, "https://picsum.photos/seed/vidp2a/800/450", "", "52:00", "昨天"),
            NewsItemDto("vid_p2_2", "video", "2026年英雄联盟全球总决赛T1 vs BLG决胜局复盘分析", "哔哩哔哩", 67890, "https://picsum.photos/seed/vidp2b/800/450", "", "28:15", "昨天"),
        )
    }

    // ── society: 社会/民生/法治 ─────────────────────────────────────────────────
    private fun societyItems(page: Int): List<NewsItemDto> {
        if (page == 0) return listOf(
            NewsItemDto("soc_1", "text_top", "最高法发布反家庭暴力典型案例：人身安全保护令签发率提升至85%", "法治日报", 5678, null, null, null, "2小时前", isTop = true),
            NewsItemDto("soc_2", "left_text_right_image", "杭州亚运村赛后转型：将作为人才公寓向市民开放租赁", "浙江日报", 3456, "https://picsum.photos/seed/soc2/400/300", null, null, "3小时前"),
            NewsItemDto("soc_3", "large_image", "全国多地推行现房销售试点：取消商品房预售制呼声再起", "经济日报", 7890, "https://picsum.photos/seed/soc3/800/450", null, null, "4小时前"),
            NewsItemDto("soc_4", "left_text_right_image", "幼儿园关停潮调查：去年全国减少1.48万所，多地探索转型托育", "新京报", 4567, "https://picsum.photos/seed/soc4/400/300", null, null, "5小时前"),
            NewsItemDto("soc_5", "text_top", "医保局：2026年居民医保人均财政补助标准再提高30元", "人民日报", 3456, null, null, null, "6小时前", isTop = true),
            NewsItemDto("soc_6", "video", "【新闻调查】外卖骑手被困在系统里：算法优化后日均配送单量反增40%", "央视新闻", 23456, "https://picsum.photos/seed/soc6/800/450", "", "14:20", "7小时前"),
            NewsItemDto("soc_7", "large_image", "哈尔滨冰雪大世界宣布全年运营：夏季将打造室内冰雪主题乐园", "黑龙江日报", 5678, "https://picsum.photos/seed/soc7/800/450", null, null, "8小时前"),
            NewsItemDto("soc_8", "left_text_right_image", "个人养老金制度扩围：试点城市增至100个，年缴费上限提至2万元", "21世纪经济报道", 6789, "https://picsum.photos/seed/soc8/400/300", null, null, "9小时前"),
        )
        if (page == 1) return listOf(
            NewsItemDto("soc_p1_1", "large_image", "城镇新增就业1200万人目标过半：服务业吸纳就业能力持续增强", "人社部", 4567, "https://picsum.photos/seed/socp1a/800/450", null, null, "10小时前"),
            NewsItemDto("soc_p1_2", "text_top", "国务院办公厅印发《关于进一步减轻义务教育阶段学生作业负担的意见》", "教育部", 3456, null, null, null, "11小时前", isTop = true),
            NewsItemDto("soc_p1_3", "left_text_right_image", "多地启动60岁以上老人免费接种带状疱疹疫苗项目", "健康时报", 2345, "https://picsum.photos/seed/socp1c/400/300", null, null, "12小时前"),
            NewsItemDto("soc_p1_4", "video", "【今日说法】网络暴力入刑首案宣判：造谣者被判有期徒刑一年", "央视法治", 12345, "https://picsum.photos/seed/socp1d/800/450", "", "25:40", "13小时前"),
        )
        return listOf(
            NewsItemDto("soc_p2_1", "text_top", "民政部：2026年城乡低保标准分别提高8%和10%", "人民日报", 2345, null, null, null, "昨天", isTop = true),
            NewsItemDto("soc_p2_2", "left_text_right_image", "长三角一体化示范区三周年：跨省公交实现全覆盖", "解放日报", 1234, "https://picsum.photos/seed/socp2b/400/300", null, null, "昨天"),
        )
    }
}
