package com.example.toutiao.presentation.profile

// =============================================================================
// ProfileMockData — 我的页 + 通知 + 作品 Tab 的合成数据
//
// 全部为模板+种子随机的合成数据, 不含真实信息, 详见
// app/src/main/java/com/example/toutiao/data/NEWS_DATA_README.md
//
// 存在形式: 单例 object + data class, 不走 Repository/DataSource 层
// (UI 端 mock, 没必要走完整 DI 链路)
// =============================================================================

/** 当前登录用户 (合成 demo 账号) */
data class CurrentUser(
    val userId: String,
    val nickname: String,
    val avatarEmoji: String,
    val avatarBgColor: Long,
    val bio: String,
    val following: Int,
    val follower: Int,
    val likeCount: Int,
    val verified: Boolean,
    val verifiedLabel: String,
)

val DemoUser = CurrentUser(
    userId = "u_4770313",
    nickname = "用户4770313",
    avatarEmoji = "🐱",
    avatarBgColor = 0xFFFFB347,
    bio = "头条 demo 用户 · 喜欢科技 / 美食 / 旅行",
    following = 128,
    follower = 2_415,
    likeCount = 18_602,
    verified = false,
    verifiedLabel = "申请认证",
)

/** 顶部 Tab 数据: 6 类内容 (作品/收藏/赞过/短剧/草稿/推荐) */

/** 作品 Tab 的合成动态 */
data class UserPost(
    val id: String,
    val title: String,
    val content: String,
    val imageUrl: String?,
    val likeCount: Int,
    val commentCount: Int,
    val shareCount: Int,
    val timeText: String, // "3 分钟前" / "昨天" 等
)

val SamplePosts: List<UserPost> = listOf(
    UserPost(
        id = "p1",
        title = "深中通道首周通勤实测",
        content = "从宝安到前海只要 25 分钟, 比走沿江高速省了 15 分钟, 通勤成本直降 30%。深莞惠一小时生活圈真的来了。",
        imageUrl = "https://picsum.photos/seed/post1/640/360",
        likeCount = 1_245,
        commentCount = 89,
        shareCount = 156,
        timeText = "2 小时前",
    ),
    UserPost(
        id = "p2",
        title = "周末深圳湾公园骑行",
        content = "夕阳 + 骑行的组合, 治愈了一整周的班味。深圳的公园基础设施真的全国领先。",
        imageUrl = "https://picsum.photos/seed/post2/640/360",
        likeCount = 532,
        commentCount = 41,
        shareCount = 28,
        timeText = "昨天 18:32",
    ),
    UserPost(
        id = "p3",
        title = "推荐一家藏在城中村的老店",
        content = "15 年的老字号烧鹅濑, 35 块一碗, 老板娘还记得我大学时常点的那份双拼。",
        imageUrl = null,
        likeCount = 234,
        commentCount = 67,
        shareCount = 12,
        timeText = "3 天前",
    ),
)

/** 收藏 Tab 的合成数据: 复用新闻 (但用 url 做主键, 模拟收藏) */
data class UserFavorite(
    val id: String,
    val newsTitle: String,
    val newsSource: String,
    val newsCover: String,
    val savedAt: String,
)

val SampleFavorites: List<UserFavorite> = listOf(
    UserFavorite("f1", "比亚迪 6 月销量同比 +32%, 新能源市占率创新高", "汽车之家", "https://picsum.photos/seed/fav1/640/360", "5 分钟前"),
    UserFavorite("f2", "美联储降息预期被击穿, 加息交易归来?", "华尔街见闻", "https://picsum.photos/seed/fav2/640/360", "1 小时前"),
    UserFavorite("f3", "高考作文题来了 (2026 全国卷)", "澎湃新闻", "https://picsum.photos/seed/fav3/640/360", "3 小时前"),
    UserFavorite("f4", "iPhone 18 发布会日期曝光", "IT 之家", "https://picsum.photos/seed/fav4/640/360", "昨天"),
    UserFavorite("f5", "中外合作办学新规: 学费上限 8 万/年", "光明日报", "https://picsum.photos/seed/fav5/640/360", "2 天前"),
)

/** 赞过 Tab 的合成数据 */
val SampleLiked: List<UserFavorite> = listOf(
    UserFavorite("l1", "广州家常菜太绝了! 8 道广东下饭菜做法", "美食杰", "https://picsum.photos/seed/lik1/640/360", "30 分钟前"),
    UserFavorite("l2", "全国田径锦标赛战报", "央视体育", "https://picsum.photos/seed/lik2/640/360", "2 小时前"),
    UserFavorite("l3", "ChatGPT-5 内部测试曝光, 推理能力突破", "量子位", "https://picsum.photos/seed/lik3/640/360", "5 小时前"),
)

/** 短剧 Tab 的合成数据 */
data class UserDrama(
    val id: String,
    val title: String,
    val cover: String,
    val latestEpisode: Int,
    val totalEpisodes: Int,
    val watchedAt: String,
    val durationLabel: String, // "剩 12 分钟" / "已完结"
)

val SampleDramas: List<UserDrama> = listOf(
    UserDrama("d1", "被退婚后, 我诗仙的身份瞒不住了", "https://picsum.photos/seed/dra1/640/360", 18, 60, "今早 08:12", "剩 12 分钟"),
    UserDrama("d2", "回到明朝当王爷之锦绣未央", "https://picsum.photos/seed/dra2/640/360", 42, 80, "昨天 22:45", "剩 38 分钟"),
    UserDrama("d3", "闪婚总裁契约妻", "https://picsum.photos/seed/dra3/640/360", 24, 24, "3 天前", "已完结"),
    UserDrama("d4", "重生之都市仙尊", "https://picsum.photos/seed/dra4/640/360", 8, 100, "上周", "剩 92 分钟"),
)

/** 草稿 Tab 的合成数据 (用 UserPost 类型复用) */
val SampleDrafts: List<UserPost> = listOf(
    UserPost(
        id = "dr1",
        title = "(无标题)",
        content = "最近在整理一些关于新能源行业的观察, 还没写完, 先存为草稿...",
        imageUrl = null,
        likeCount = 0,
        commentCount = 0,
        shareCount = 0,
        timeText = "昨天 23:14 草稿",
    ),
    UserPost(
        id = "dr2",
        title = "618 数码选购清单 (草稿)",
        content = "手机 / 电脑 / 耳机 / 充电宝 各品类的推荐, 等价格稳定再发...",
        imageUrl = null,
        likeCount = 0,
        commentCount = 0,
        shareCount = 0,
        timeText = "3 天前 草稿",
    ),
)

/** 推荐 Tab 的合成数据: 用新闻池子 (直接复用 news_data.json) */
val RecommendedNewsCategories = listOf("推荐", "科技", "财经", "体育", "视频")

// =============================================================================
// 通知中心数据
// =============================================================================

/** 通知类型 */
enum class NotifType(val label: String) {
    System("系统"),
    Like("赞"),
    Comment("评论"),
    Follow("关注"),
    Video("视频更新"),
    Earn("收益"),
    Mall("商城"),
}

/** 通知条目 */
data class NotificationItem(
    val id: String,
    val type: NotifType,
    val avatarEmoji: String,
    val avatarBg: Long,
    val title: String,
    val content: String,
    val timeText: String, // "5 分钟前" 等
    val isRead: Boolean,
    val actionLabel: String? = null, // "查看" / "去完成" 等
)

/** 通知中心 5 大类, 每类若干条 */
data class NotificationCategory(
    val type: NotifType,
    val items: List<NotificationItem>,
)

val SampleNotifications: List<NotificationCategory> = listOf(
    // 互动通知 (赞 + 评论 + 关注)
    NotificationCategory(
        NotifType.Like,
        items = listOf(
            NotificationItem("n1", NotifType.Like, "🌟", 0xFFFFB347, "财经观察家 等 12 人赞了你的微头条",
                "《深中通道首周通勤实测》", "刚刚", false, "查看"),
            NotificationItem("n2", NotifType.Like, "🌙", 0xFF7BB7F7, "老王爱数码 等 8 人赞了你的评论",
                "iPhone 18 的标准版这次挺有诚意", "10 分钟前", false, null),
            NotificationItem("n3", NotifType.Comment, "💬", 0xFF7CD17C, "深圳土著 1 号 评论了你",
                "前海这片的便利店密度已经超过香港了", "30 分钟前", false, "回复"),
            NotificationItem("n4", NotifType.Follow, "👤", 0xFFFF9A9A, "美食探店阿强 关注了你",
                "", "1 小时前", false, "回关"),
            NotificationItem("n5", NotifType.Like, "🌟", 0xFFFFB347, "湾区楼市 等 5 人赞了你的微头条",
                "《周末深圳湾公园骑行》", "3 小时前", true, null),
        ),
    ),
    // 系统通知
    NotificationCategory(
        NotifType.System,
        items = listOf(
            NotificationItem("n6", NotifType.System, "📢", 0xFFFF7575, "账号安全提醒",
                "你的账号在新设备登录, 如非本人操作请及时修改密码", "2 小时前", false, "查看"),
            NotificationItem("n7", NotifType.System, "⚙️", 0xFFB0B0B0, "服务条款更新",
                "头条用户协议 v3.2 已更新, 请查看变更内容", "昨天", true, null),
        ),
    ),
    // 视频更新
    NotificationCategory(
        NotifType.Video,
        items = listOf(
            NotificationItem("n8", NotifType.Video, "🎬", 0xFFEF6C8A, "你关注的「科技日报」更新了",
                "《iPhone 18 全系列爆料: 折叠屏款终于来了》", "30 分钟前", false, "去看"),
            NotificationItem("n9", NotifType.Video, "🎬", 0xFFEF6C8A, "你关注的「央视新闻」更新了",
                "《高考首日直击: 全国 1342 万考生赴考》", "2 小时前", false, "去看"),
        ),
    ),
    // 收益通知
    NotificationCategory(
        NotifType.Earn,
        items = listOf(
            NotificationItem("n10", NotifType.Earn, "💰", 0xFFFFC83A, "今日金币收益 +320",
                "看新闻 +200 金币, 看视频 +80 金币, 签到 +40 金币", "今天 08:00", true, "去查看"),
            NotificationItem("n11", NotifType.Earn, "💰", 0xFFFFC83A, "你已累计获得 1 万金币",
                "可兑换 ¥10 话费券, 7 天内有效", "昨天", true, "去兑换"),
        ),
    ),
    // 商城通知
    NotificationCategory(
        NotifType.Mall,
        items = listOf(
            NotificationItem("n12", NotifType.Mall, "🛍️", 0xFF7B61FF, "618 大促今天 20:00 开抢",
                "你关注的店铺已开始预售", "3 小时前", false, "去看看"),
            NotificationItem("n13", NotifType.Mall, "📦", 0xFF7B61FF, "你关注的「小米官方」上新",
                "小米 15 Ultra 影像旗舰", "昨天", true, "去查看"),
        ),
    ),
)

/** 通知中心顶部 4 快捷入口 */
data class NotifQuickAction(
    val type: NotifType,
    val label: String,
    val icon: String, // emoji
    val unreadCount: Int,
)

val NotifQuickActions: List<NotifQuickAction> = listOf(
    NotifQuickAction(NotifType.Like, "赞和评论", "💬", 5),
    NotifQuickAction(NotifType.Follow, "新粉丝", "👥", 1),
    NotifQuickAction(NotifType.System, "系统通知", "📢", 1),
    NotifQuickAction(NotifType.Mall, "商城消息", "🛍️", 2),
)

// =============================================================================
// 我的钱包
// =============================================================================

data class WalletItem(
    val icon: String,
    val label: String,
    val value: String,
    val badge: String? = null,
)

val WalletItems: List<WalletItem> = listOf(
    WalletItem("💰", "金币余额", "18,602", null),
    WalletItem("🎫", "优惠券", "12 张", "3 张即将过期"),
    WalletItem("💵", "现金余额", "¥ 0.00", "满 100 可提现"),
    WalletItem("📦", "订单", "3 单", "1 单待发货"),
    WalletItem("⭐", "收藏夹", "24 项", null),
    WalletItem("👁", "浏览历史", "186 条", null),
)

// =============================================================================
// 热门作者 (HomeScreen 用)
// =============================================================================

data class HotAuthor(
    val userId: String,
    val nickname: String,
    val avatarEmoji: String,
    val avatarBg: Long,
    val followers: Int,
    val posts: Int,
    val isFollowed: Boolean,
    val latestPostTitle: String,
    val latestPostCover: String,
)

val HotAuthors: List<HotAuthor> = listOf(
    HotAuthor("a1", "财经观察家", "📊", 0xFFFFB347, 1_245_000, 358, false,
        "本周 A 股复盘: 三大主线浮出水面", "https://picsum.photos/seed/aut1/640/360"),
    HotAuthor("a2", "深圳土著 1 号", "🌴", 0xFF7CD17C, 856_000, 1_204, false,
        "深圳地铁 16 号线二期试乘体验", "https://picsum.photos/seed/aut2/640/360"),
    HotAuthor("a3", "老王爱数码", "📱", 0xFF7BB7F7, 2_104_000, 512, false,
        "618 千元机选购指南 (2026 版)", "https://picsum.photos/seed/aut3/640/360"),
    HotAuthor("a4", "美食探店阿强", "🍜", 0xFFEF6C8A, 3_412_000, 892, false,
        "藏在城中村的 10 家老店", "https://picsum.photos/seed/aut4/640/360"),
    HotAuthor("a5", "科技日报", "🚀", 0xFF7B61FF, 5_280_000, 4_215, false,
        "iPhone 18 全系列爆料汇总", "https://picsum.photos/seed/aut5/640/360"),
    HotAuthor("a6", "湾区楼市", "🏢", 0xFFFF9A9A, 624_000, 186, false,
        "深莞惠通勤圈实地踩盘", "https://picsum.photos/seed/aut6/640/360"),
)
