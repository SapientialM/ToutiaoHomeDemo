# AGENTS.md — ToutiaoFeedDemo

> 今日头条首页信息流 Demo | 字节跳动客户端工程训练营
> 给 AI Agent 的开发约束与协作协议

目标只有两个：

1. 让首次进入仓库的线程，能在几分钟内建立对项目的正确认识。
2. 把后续协作规则沉淀到一个稳定入口，避免规则只存在于历史对话里。

如果本仓库的行为、目录结构、约定或协作方式发生了稳定变化，请在同一次改动里同步更新本文件。

---

## 信息优先级

当不同文档描述不一致时，按下面的优先级理解：

1. 代码实际行为
2. `AGENTS.md`
3. `README.md`
4. `docs/02_技术设计文档.md`
5. `docs/01_需求分析文档.md`

`docs/04_项目进度文档.md` 中的里程碑和周报代表演进方向，不代表已经交付。

---

## 项目快照

- **项目名**：`ToutiaoFeedDemo`
- **定位**：仿今日头条首页信息流 Android 应用，用于展示 MVI + Clean Architecture + Jetpack Compose 的现代 Android 开发能力，为字节跳动客户端工程训练营答辩提供技术展示载体
- **当前阶段**：骨架已落地，Mock 数据流已跑通，核心 UI（4 种卡片 + Tab + 底部导航）已完成，新闻详情页与多个子页面已接入
- **当前已交付**：
  - ✅ MVI + Clean Architecture 完整包结构（domain / data / presentation / di）
  - ✅ 4 种卡片类型渲染（TextTop / LeftTextRightImage / LargeImage / Video）
  - ✅ 顶部 Tab 切换（8 个频道：关注 / 推荐 / 热榜 / 深圳 / 小说 / 发现 / 视频 / 财经）
  - ✅ 底部导航栏页面切换（首页 / 视频 / 赚钱 / 商城 / 我的，5 个独立页面）
  - ✅ Paging3 + RemoteMediator 混合分页（替换手动分页，支持下拉刷新与自动加载更多）
  - ✅ Room 缓存优先离线展示（PagingSource 读取 Room，RemoteMediator 写入 Room，断网展示缓存）
  - ✅ Room 三表设计（feed_items + remote_keys + news_content_cache）+ DAO/Database/Entity + WAL 模式
  - ✅ MockDataSource 组件化 Mock 方案：Repository 层通过 RemoteDataSource 接口获取数据，不感知数据真伪
  - ✅ Hilt + KSP 依赖注入（NetworkModule / DatabaseModule / RepositoryModule）
  - ✅ Timber 全链路日志（ViewModel → Repository → MockDataSource → NewsRemoteMediator）
  - ✅ Compose 多状态 Preview（Loading / Success / Error / Empty / Refreshing）
  - ✅ 搜索功能双入口（首页顶部搜索栏 + 独立 `SearchScreen` 子页面，均展示 Mock 搜索结果）
  - ✅ Video 视频列表页（独立页面，展示视频封面、播放按钮、时长、作者、播放量）
  - ✅ 性能优化基础（FeedCard @Immutable、Room channel 索引、Coil ImageLoader 内存缓存配置）
  - ✅ MCP Skill 增强：新增 `measure_app_launch` 启动速度测量工具（冷启动/热启动/页面跳转，TTID/TTFD/TotalTime/WaitTime 多指标，多次采样统计，智能评分 A/B/C_D）
  - ✅ MCP Skill 优化：全部 57 个工具 description 质量提升（统一 6 要素模板：功能 / 何时用 / 何时不用 / 返回结构 / 耗时 / 示例）
  - ✅ MCP Skill 扩展：新增 `dump_hierarchy` / `find_element` / `wait_for_element`（uiautomator 元素查找）；`logcat_search` / `parse_crash`（崩溃归因）；`screenshot_region`（区域截图）；`apk_metadata`（aapt 自省）；`set_orientation` / `set_gps` / `animation_scale`（环境模拟）
  - ✅ MCP Skill 设计稿能力：新增 `list_design_files` / `extract_design_spec` / `extract_design_tokens` / `extract_design_components` / `design_to_compose`，把 design/ 下的 14 张设计稿转换为 Agent 可读的结构化规范
  - ✅ MCP Skill 多 LLM 支持：从 Kimi k2.6 切换到 Minimax（M3 / M2.7 / M2.7-highspeed），耗时 3-25x 提速，JSON 解析 0/3 → 3/3
  - ✅ 「上次看到这里」按 tab 独立持久化（`ReadPositionRepository` / SharedPreferences），回到首页时在原阅读位置上方显示「X 分钟前看过，点击回到原位置」提示，点击跳回原位置
  - ✅ 字体对齐设计稿：首页 Tab 选中 18sp→16sp Bold / LargeImage & TextTop 标题 18sp→17sp Medium / VideoScreen Tab 17sp/14sp→15sp Medium/Regular / Earn 大数字 40sp→36sp / Profile 用户名 22sp→17sp / Mall 商城 Tab 18sp→16sp / BrandTopRow Logo 18sp→17sp Medium / 豆包AI 9sp→10sp / Mall 商品卡 12sp→14sp 价格 15sp→18sp / Profile 6月幸运签 17sp→15sp
  - ✅ 热榜频道差异化：`HotListView` 顶部 4 个圆角胶囊快捷入口 + 序号+🔥/爆/热/新/辟谣 标签徽标的纯文字榜单
  - ✅ 深圳频道差异化：天气条（29° 阴 26°/30° + 切换城市）+ 本地热榜横条
  - ✅ 财经频道差异化：风险提示条（投资有风险）+ 股票指数卡（上证/深证/创业板 22sp Bold 数字）
  - ⚠️ 军事/畅听/体育频道差异化组件已编码（`MilitaryRankView` / `AudioChannelView` / `SportsChannelView`），但未接入顶部 Tab 切换，当前不可达
  - ✅ 小说频道差异化：书架入口 + 3 本推荐书（"为你推荐" 标签）+ 排行榜（推荐榜/完结榜...）+ 双列榜项（1-3 红色 Bold 排名）+ 猜你喜欢大卡（金色评分）
  - ✅ 视频频道沉浸式改造：VerticalPager 全屏 + 顶部 Tab（关注/精选/推荐/找短剧）+ 右上 + 号 + 右下垂直操作栏（头像+关注/点赞/评论/收藏/分享）+ 左下账号+描述+话题
  - ✅ 赚钱页"看头条赚金币"6 格时间奖励网格（3x2 红底方格 16 待领取/16 3分钟/14 10分钟/20 20分钟/50 45分钟/90 90分钟）
  - ✅ 首页 Tab 间距收紧：edgePadding 16dp→12dp，下划线 padding 8dp→6dp
  - ✅ 首页右下角悬浮提示卡（设计稿「高考作文题来了，去热榜看详情 ›」），点击跳转热榜频道
  - ✅ 商城页官方商城标签 + 商品双列（含"旗舰"/"618"红色角标）+ 你可能喜欢 2x2
  - ✅ 性能优化：ToutiaoApplication 显式声明 CachePolicy（ENABLED × 3 个维度）+ BottomInfoRow 用 remember 缓存评论数格式化结果
  - ✅ 搜索页增强：SharedPreferences 持久化搜索历史（最长 12 条）+ 每条带 X 删除 + 「搜索发现」横向 Chip 流（10 个个性化推荐词）+ 热搜前 3 名 Hot/Boom/New 徽标
  - ✅ 我的页作品 Tab 内容填充：6 个 Tab（作品/收藏/赞过/短剧/草稿/推荐）各自独立空状态页（图标 + 标题 + 副标题 + 行动按钮）
  - ⬜ ktlint 代码规范配置：尚未在 `build.gradle.kts` / `libs.versions.toml` 中接入，`./gradlew ktlintCheck` 当前不可用
  - ✅ Compose 性能 baseline 报告（`docs/performance-baseline.md`）：冷启动 1.3-1.6s（TTID A 级 / TTFD B 级），热启动 < 400ms，滚动 60fps，PSS 142MB
  - ✅ 新闻详情页：点击非视频卡片 → HTTP 抓取源 URL → Jsoup 手动解析（失败回退 LLM / Mock 兜底） → 渲染详情页框架；含 OkHttp 抓取、Jsoup 解析、Minimax LLM 解析、MockFallback、NewsContentRepository 编排、NewsDetailViewModel 状态机、NewsDetailScreen 全屏 UI
  - ✅ 视频详情页：Video 卡片点击进入 `VideoDetailScreen`，含沉浸式播放占位 + 评论列表
  - ✅ AI 聊天页（`AiChatScreen`）：首页顶部「豆包AI」入口进入，支持会话与嵌入新闻卡片点击跳转详情
  - ✅ 热榜专题子页（`HotTopicScreen`）：热榜频道顶部快捷入口进入，展示对应主题榜单
  - ✅ 搜索页：首页顶部搜索栏与独立 `SearchScreen` 双入口，支持搜索历史持久化、搜索发现 Chip、热搜徽标
  - ✅ 个人中心子页面：消息中心 / 钱包 / 任务中心 / 浏览历史 / 书架 / 创作者中心 / 全部功能（`NotificationScreen` / `WalletScreen` / `TaskScreen` / `HistoryScreen` / `BookshelfScreen` / `CreatorCenterScreen` / `AllFunctionsScreen`）
  - ✅ 商城子页面：订单列表 / 优惠券 / 关注店铺（`OrderListScreen` / `CouponsScreen` / `FollowedShopsScreen`）
  - ✅ 评论组件（`CommentSection`）：视频详情页接入评论列表与评论发布
  - ✅ 发现频道小红书式双列网格（`XhsGridList`）
- **当前未开始**：
  - ⬜ 视频实际播放能力（VideoCard / VideoDetailScreen 仅封面 + 播放按钮 UI，无真实播放器）
  - ⬜ Compose 重组深度分析、图片尺寸严格限制
  - ⬜ ktlint / detekt 代码规范检查与自动化
  - ⬜ GitHub Actions CI/CD 流水线
  - ⬜ 军事 / 畅听 / 体育频道接入顶部 Tab 切换
- **结论**：项目当前已完整实现首页信息流、5 个底部导航页面、新闻/视频详情页、Paging3 + RemoteMediator 分页、Room 离线缓存、搜索双入口、AI 聊天与多个子页面等核心功能。如果你看到底部导航只是纯视觉展示而没有页面切换，说明你看的是旧版本代码。

---

## 运行前提

- Android Studio Ladybug（2024.2.1）或更高版本，或等效 IDE（IntelliJ IDEA + Android 插件）
- JDK 17+（Gradle Kotlin DSL 编译需要）
- Android SDK API 36（compileSdk），最低运行设备 API 26（Android 8.0）
- 可用的 Android 模拟器或真机（API 26+）

本地构建无需真实后端，MockDataSource 会从 `assets/news_data.json` 加载 600 条合成新闻数据并返回分页结果。

---

## 常用命令

```bash
# 构建 Debug APK
./gradlew assembleDebug

# 清理构建
./gradlew clean assembleDebug

# 运行单元测试
./gradlew test

# 运行 Android 仪器化测试
./gradlew connectedAndroidTest

# 仅编译（不打包）
./gradlew compileDebugKotlin

# ktlint / detekt 尚未配置，以下命令暂不可用
# ./gradlew ktlintCheck
# ./gradlew ktlintFormat
# ./gradlew detekt
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`

### MCP Skill：android-dev-assist

`skills/` 目录包含一个 MCP Server（`android-dev-assist`），`package.json` version 为 `1.0.0`，`server.ts` 内部版本为 `3.1.0`，当前注册 **57 个工具**（模块数量随 PM 相关工具扩展有所增加）：

| 模块 | 工具 | 数量 |
|------|------|------|
| 基础交互 | `screenshot` / `screenshot_region` / `tap` / `swipe` / `input_text` / `press_key` | 6 |
| UI 层级 | `dump_hierarchy` / `find_element` / `wait_for_element` | 3 |
| 构建部署 | `build` / `install_and_launch` / `build_deploy` | 3 |
| UI 验证 | `verify_ui` / `analyze_screenshot` / `compare_screenshots` / `vision_action` | 4 |
| 日志调试 | `get_logs` / `logcat_search` / `parse_crash` / `clear_logs` | 4 |
| 设备管理 | `list_devices` / `device_info` / `shell_command` | 3 |
| 应用管理 | `list_apps` / `app_info` / `uninstall_app` / `clear_app_data` / `stop_app` | 5 |
| 性能监控 | `performance_metrics` / `measure_app_launch` / `record_screen` | 3 |
| 设备控制 | `set_orientation` / `set_gps` / `animation_scale` | 3 |
| 代码质量 | `code_quality` / `run_tests` | 2 |
| UI 测试 | `ui_test` / `regression_test` | 2 |
| 项目报告 | `project_report` | 1 |
| 文件操作 | `push_file` / `pull_file` | 2 |
| 网络调试 | `network_state` / `set_network` | 2 |
| APK 元数据 | `apk_metadata` | 1 |
| **设计稿** | `list_design_files` / `extract_design_spec` / `extract_design_tokens` / `extract_design_components` / `design_to_compose` | **5** |

**所有工具输出均为标准 JSON 结构**，`isError` 字段表示错误状态。每个工具的 description 遵循 6 要素模板：功能 / 何时用 / 何时不用 / 返回结构 / 耗时 / 示例。下表仅列出主要模块，完整工具列表以 `skills/src/server.ts` 注册为准。

**核心亮点工具**：
- `vision_action`（**视觉驱动交互**）：截图 → 视觉 LLM 识别元素 → 返回坐标 → ADB 执行 → 截图确认。支持 `prompt` (单步) 和 `prompts` (多步串联)
- `analyze_screenshot`：3 阶段分析（PIL 像素测量 + 视觉 AI + 卡片精确验证）
- `measure_app_launch`（**启动速度测量**）：冷启动/热启动/页面跳转，TTID/TTFD/TotalTime/WaitTime，多次采样统计，智能评分 A/B/C/D
- `extract_design_spec`（**设计稿转规范**）：把 design/ 下的截图转换为 Agent 可读的 JSON 规范（colorTokens / typography / components / layout / interactions）
- `design_to_compose`：设计稿直接生成 Jetpack Compose Screen.kt 骨架
- `parse_crash`：从 logcat 提取并按事件归类 Java 崩溃 / ANR / Native crash

**运行要求**：
- `MINIMAX_API_KEY` 环境变量（Minimax 视觉 API，默认提供商）
- ADB 可用 + Android 设备/模拟器连接
- `pip install pillow`（PIL 像素分析，analyze_screenshot 用）

**可选配置**：
- `VISION_PROVIDER=minimax|kimi`：切换 LLM 提供商（默认 minimax）
- `MINIMAX_INSECURE_TLS=1` / `MOONSHOT_INSECURE_TLS=1`：公司代理/MITM 自签名证书时启用
- `DESIGN_DIR=./design`：design_spec 工具的设计稿目录（默认 ./design）

**构建与测试**：
```bash
cd skills && npm run build     # 构建
cd skills && npm test          # 27 个测试（Minimax 模式下 ~30s；Kimi 模式下自动 skip vision 测试）
cd skills && npm run test:vision-bench  # 多模型基准（~5 分钟，默认跳过 Kimi）
```

**注意**：`vision_action` 内部使用的 system prompt 已将 JSON 输出格式固化到模型层，Agent 调用时只需传入自然语言指令。Minimax-M3 支持 `thinking:disabled` 加速 5-10x。

---

## 当前产品行为

### 1. 首页信息流

- 主入口在 `MainActivity.kt` → `HomeScreen(viewModel)` → `HomeScreenContent`
- 使用 Material3 `Scaffold` 骨架：顶部栏（品牌 Logo + 搜索占位 + 豆包AI 入口 + TabRow）/ 内容区 / 底部导航栏
- MVI 单向数据流：用户操作 → `HomeUiEvent` → `HomeViewModel.onEvent()` → Repository → 更新 `StateFlow<HomeUiState>` → Compose 重组
- `collectAsStateWithLifecycle()` 以生命周期感知方式订阅 StateFlow，Activity 后台时自动暂停收集

### 2. 顶部 Tab 切换

- 8 个频道：关注(follow) / 推荐(recommend) / 热榜(hot) / 深圳(shenzhen) / 小说(novel) / 发现(discover) / 视频(video) / 财经(finance)
- 切换 Tab 时通过 `key(currentTab)` 销毁旧 Paging 流，新 Tab 从 page=0 开始加载
- 当前选中 Tab 字体 16sp Bold 红字+红色下划线；未选中 15sp Regular 黑字（设计稿对齐）
- 搜索栏支持点击展开输入框、输入关键词、提交搜索、返回取消，展示 Mock 搜索结果
- 顶部品牌栏右侧有「豆包AI」入口，点击打开 `AiChatScreen`
- **频道差异化渲染**：
  - `recommend` / `follow` / `finance` → `PagingFeedList`（标准 4 种 FeedCard）
  - `hot` → `HotListView`（顶部 4 个圆角胶囊快捷入口 + 序号+🔥/爆/热/新/辟谣 徽标的纯文字榜单）
  - `shenzhen` → `PagingFeedList` + 顶部 `ShenzhenWeatherStrip` + `ShenzhenLocalHotBanner`
  - `novel` → `PagingFeedList` + 顶部 `NovelBookshelfRow` + 排行榜（`NovelRankingTabs` + `NovelRankItem`）+ 猜你喜欢（`NovelRecommendItem`）
  - `discover` → `XhsGridList` 小红书式双列网格（把 FeedCard 映射为图文卡片）
  - `video` / 其他未差异化频道 → `PagingFeedList`（标准 4 种 FeedCard）
  - `military` / `audio` / `sports` 差异化组件（`MilitaryRankView` / `AudioChannelView` / `SportsChannelView`）已编码，但**未接入顶部 Tab 切换**，当前不可达

### 3. 下拉刷新

- `PullToRefreshBox`（Material3 Experimental API）包裹 `LazyColumn`
- 下拉时触发 `HomeUiEvent.OnRefresh` → ViewModel 请求 page=0 数据 → `isRefreshing` 状态控制刷新指示器
- 刷新完成后保持当前 Tab，列表回到顶部由调用方控制

### 4. 加载更多（Paging3 自动处理）

- Paging3 `LazyPagingItems` 自动检测到底部并触发 `RemoteMediator.LoadType.APPEND`
- `NewsRemoteMediator` 从 `remote_keys` 表获取下一页页码，请求网络后写入 Room
- `FeedDao.getFeedPagingSource()` 感知 Room 数据变化，自动刷新列表
- 底部显示 Loading Footer 由 `loadState.append is LoadState.Loading` 控制

### 5. 四种卡片类型

使用 `FeedCard` 密封类 + `when` 表达式实现类型安全的多卡片渲染：

| 卡片类型 | 对应组件 | 特征 |
|----------|----------|------|
| TextTop | `TextTopCard` | 标题 + "置顶"标签 + 来源 + 评论数 + 时间（极少出现） |
| LeftTextRightImage | `LeftTextRightImageCard` | 左侧文字区 + 右侧缩略图 |
| LargeImage | `LargeImageCard` | 标题 + 底部大图 |
| Video | `VideoCardItem` | 视频封面(16:9) + 播放按钮 + 时长标签 + 标题 + 作者/播放量 |

> 注：4 种卡片类型均已实现。TextTop 仅在首页首条且为权威来源时出现，列表以左文右图和大图为主。VideoCard 仅展示封面和播放按钮 UI，无实际视频播放能力。

所有卡片统一包裹 `clickable`：
- 非 `FeedCard.Video` 卡片：点击时若 `sourceUrl` 非空，则打开 `NewsDetailScreen`（HTTP 抓取 → Jsoup → LLM / Mock 兜底）
- `FeedCard.Video` 卡片：点击打开 `VideoDetailScreen`（沉浸式视频详情 + 评论）
- 热榜榜单项、AI 聊天中的新闻卡片、搜索结果卡片均复用同一份跳转逻辑

### 6. Mock 数据流

- `MockDataSource` 实现 `RemoteDataSource` 接口，从 `assets/news_data.json` 加载 600 条合成新闻数据
- 支持 `channel` 和 `page` 参数，按频道过滤后分页返回（page=0 返回前 8 条，依此类推）
- `hasMore` 根据实际数据量动态计算
- 图片使用新闻自带的封面图 URL；无封面图时自动使用 `picsum.photos` 占位图
- 卡片类型分配策略：TextTop 仅保留在首页首条权威来源，其余按 1:1 分配 LargeImage 与 LeftTextRightImage，整体以图文卡片为主
- 切换真实后端时，只需在 `di/DataSourceModule.kt` 中替换 `RemoteDataSource` 的实现类，零业务代码改动

### 7. 数据层

- **网络**：Retrofit + OkHttp + Kotlinx Serialization，`NewsApi.getNewsFeed(channel, page, size)`
- **本地**：Room，`AppDatabase` 含 `feed_items`、`remote_keys`、`news_content_cache` 三张表，WAL 模式已开启
- **Repository**：`NewsRepositoryImpl` 实现 `NewsRepository` 接口
  - `getFeedPagingData(channel)`：返回 `Flow<PagingData<FeedCard>>`，内部使用 `Pager` + `NewsRemoteMediator`
  - `getNewsFeed()` / `hasMore()`：保留用于兼容和直接调用
  - `getCachedFeed()`：返回 `Flow<List<FeedCard>>`
  - `searchNews(query)`：Mock 搜索结果
  - `getHotList()`：Mock 热榜数据（快捷入口 + 榜单项）
- **RemoteMediator**：`NewsRemoteMediator` 处理 `REFRESH` / `APPEND`，网络成功后写入 Room
- **Mapper**：`NewsMapper.kt` 负责 DTO ↔ Entity ↔ Domain 三层转换

### 8. 底部导航栏

- 5 个 Tab：首页 / 视频 / 赚钱 / 商城 / 我的
- `selectedIndex` 是纯本地 UI 状态（`remember { mutableIntStateOf(0) }`），不经过 ViewModel
- 使用 `when (selectedBottomNav)` 在 `MainActivity.kt` 中切换 5 个独立页面：
  - 0 → `HomeScreen`（首页信息流 + 8 个频道差异化）
  - 1 → `VideoScreen`（抖音式沉浸式全屏流）
  - 2 → `EarnScreen`（任务中心 + 6 格时间奖励网格）
  - 3 → `MallScreen`（商城 + 订单/优惠券/关注店铺子页面入口）
  - 4 → `ProfileScreen`（个人中心 + 消息/钱包/任务/历史/书架/创作者中心/全部功能入口）
- 每个页面都有独立的 Screen 组件和 ViewModel（Earn/Profile 为静态展示页）

### 9. 子页面导航

除底部导航 5 个主页面外，`MainActivity.kt` 通过 `SubPage` 状态管理一组顶层覆盖子页面：

- `SearchOverlay` → `SearchScreen`（搜索页，支持结果卡片跳转详情/视频）
- `AiChat` → `AiChatScreen`（AI 聊天页，嵌入新闻卡片可跳转详情）
- `Notifications` → `NotificationScreen`（消息中心）
- `Wallet` → `WalletScreen`（钱包）
- `OrderList` / `Coupons` / `FollowedShops` → 商城相关子页面
- `Tasks` → `TaskScreen`（任务中心）
- `History` / `Bookshelf` / `CreatorCenter` / `AllFunctions` → 个人中心相关子页面

子页面与详情页均在 `Box` 中最后渲染，覆盖在主页面之上；详情页层级高于子页面，保证从 Search / AI 聊天中点击卡片能正确进入详情。

### 10. 「上次看到这里」功能

- 持久化：`ReadPositionRepository`（`SharedPreferences`，按 channel 维度存 `last_seen_id_<channel>` 和 `last_seen_at_<channel>`）
- 写入时机：用户滚动列表，`firstVisibleItemIndex > 0` 且首条 card id 变化时，上报 `HomeUiEvent.OnFirstVisibleCardChanged(id)`，ViewModel 写入
- 读取时机：进入 tab（`switchTab` / init）时 ViewModel 同步到 `UiState.Success.lastSeenCardId / lastSeenAt`
- 展示条件：UI 端
  - `lastSeenCardId != null` 且在当前 `LazyPagingItems` 中能定位到 index（≥0）
  - `firstVisibleItemIndex <= lastSeenIndex`（用户尚未滚过该位置）
- 交互：点击 `LastSeenHint` 触发 `animateScrollToItem(lastSeenIndex)` 跳回原位置
- 隐藏时机：用户滚过 `lastSeenIndex` 后自动隐藏（不擦除持久化，再次进入仍可恢复）

### 11. 新闻详情页「点击 → 解析 → 渲染」全链路

**触发**：首页/搜索结果中点击带 `sourceUrl` 的卡片（`HomeScreen` → `MainActivity.onCardClick` → `NewsDetailScreen`）

**四步流程**（`NewsContentRepositoryImpl` 编排，Flow<Stage> 流式推进）：

| 步骤 | 解析器 | 成功条件 | 失败回退 |
|------|--------|----------|----------|
| 1. HTTP 抓取 | `OkHttpContentFetcher` | 200 + HTML | 直接 Error |
| 2. 手动解析 | `JsoupNewsContentParser` | ≥3 段 ≥100 字 | 步骤 3 |
| 3. LLM 解析 | `MinimaxNewsContentParser` | chat completion 返回合法 JSON | 步骤 4 |
| 4. 兜底 Mock | `MockFallbackNewsContentParser` | 永远 Success（生成 5 段占位内容 + picsum 配图） | 永不发生 |

**状态机**（`NewsDetailViewModel` → `NewsDetailScreen`）：

`Idle` → `Loading(Fetching)` → `Loading(ManualParsing)` → `Loading(LlmParsing)?` → `Loading(MockParsing)?` → `ContentReady(byLlm: Boolean)` / `Error(message)`

- Loading 阶段显示「正在做什么」+ 二级文案（如「Jsoup 提取标题/段落/配图…」）
- LLM 解析成功的页面顶部有「LLM 解析」红色徽标
- 兜底 Mock 内容用占位段落 + picsum 占位图，标注「Mock 兜底」标识

**LLM 配置**（`local.properties`，gitignore）：

```properties
LLM_API_KEY=sk-cp-...（来自 skills/.env 的 MINIMAX_API_KEY）
LLM_BASE_URL=https://api.minimaxi.com/v1
LLM_MODEL=MiniMax-Text-01
```

通过 `BuildConfig` 注入到 `MinimaxNewsContentParser`。

**依赖**：
- `okhttp` 抓取（已存在）
- `jsoup` HTML 解析（libs.versions.toml 1.17.2）

**已知陷阱（调试时遇到）**：

1. **Toutiao.com 是 SPA**（Vue/React 客户端渲染）：服务端返回的 HTML 只含 JS 框架代码，没有正文。Jsoup 拿不到内容，会回退到 LLM。LLM 也会识别出"这是 JS 代码非新闻"并返回解析结果。
2. **KSP + Hilt 第三个 @Binds 解析失败**：当一个接口有 ≥3 个 @Binds 绑定时，Hilt 处理器偶尔报 "type could not be resolved"（实际编译通过但 KSP 找不到符号）。解决：第三个绑定用 `@Provides` 模式。详见 `NewsContentProvidesModule.provideMockParser`。
3. **`sourceUrl` 必须经过 Room 透传**：原始 bug 是 `FeedItemEntity` 没有 `sourceUrl` 字段，导致 `toDomain()` 后 `FeedCard.sourceUrl = null`，详情页点击无响应。修复：Entity 加 `@ColumnInfo("source_url") val sourceUrl: String? = null` + Mapper 显式传 `sourceUrl = sourceUrl`。
4. **AppDatabase 版本**：加 `sourceUrl` 字段后需升级 DB version（当前 v4 → v5），或靠 `fallbackToDestructiveMigration()` 重建。
  - Minimax 复用 `OkHttpClient` + `kotlinx.serialization.json`
- **Hilt 绑定**：`NewsContentModule` 用 `@ManualParser` / `@LlmParser` 限定符区分同一个 `NewsContentParser` 接口的两个实现

### 12. 主题

- 头条红白配色：主色 `Color(0xFFD81E06)`
- 背景色 `Color(0xFFF5F5F5)`（浅灰）
- 使用 Material3 动态配色方案

---

## 核心产品约束

### 技术栈约束（不可偏离）

| 类别 | 技术 | 备注 |
|------|------|------|
| 语言 | Kotlin | 必须使用 Kotlin，禁止 Java |
| UI | Jetpack Compose + Material3 | 禁止使用 XML View 体系（除 Manifest/资源文件） |
| 架构 | MVI + Clean Architecture | 单向数据流，UiState/UiEvent 均为 sealed class |
| 状态管理 | StateFlow | 禁止使用 LiveData |
| 依赖注入 | Hilt + KSP | 编译期注入，禁止手动创建工厂 |
| 网络 | Retrofit2 + OkHttp3 + Kotlinx Serialization | 禁止使用 Gson |
| 图片 | Coil Compose | 禁止使用 Glide |
| 数据库 | Room + KSP | 禁止使用 raw SQLite |
| 分页 | Paging3 + RemoteMediator | 混合网络+本地分页方案（已落地） |
| 构建 | Gradle Kotlin DSL + Version Catalog | 依赖版本统一在 `libs.versions.toml` |

### 架构约束

```
Presentation (Screen/ViewModel) → Domain (Model/Repository接口)
                                         ↑
                                    Data (Repository实现/API/DAO)
```

- **Domain 层**：纯 Kotlin，不依赖任何 Android 框架
- **Data 层**：实现 Domain 的 Repository 接口，内部可依赖 Android SDK
- **Presentation 层**：持有 ViewModel，ViewModel 持有 Repository
- **严禁**：Presentation 直接引用 Data 层的类；Domain 引用 Android 框架

### 包结构

```
com.example.toutiao/
├── domain/model/          # FeedCard 密封类（4种子类型）/ HotListItem / Product / NewsContent
├── domain/repository/     # NewsRepository / ReadPositionRepository / NewsContentRepository / MallRepository 等接口
├── data/remote/api/       # Retrofit API 接口
├── data/remote/dto/       # Kotlinx Serialization DTO
├── data/remote/datasource/# RemoteDataSource 接口 + MockDataSource + RealRemoteDataSource
├── data/remote/mediator/  # NewsRemoteMediator（Paging3 混合分页）
├── data/local/entity/     # Room Entity（FeedItemEntity + RemoteKeyEntity + NewsContentCacheEntity）
├── data/local/dao/        # Room DAO（FeedDao + RemoteKeyDao + NewsContentCacheDao）
├── data/local/database/   # AppDatabase
├── data/repository/       # NewsRepositoryImpl / ReadPositionRepositoryImpl / NewsContentRepositoryImpl / MallRepositoryImpl
├── data/mapper/           # DTO ↔ Entity ↔ Domain 转换
├── data/parser/           # OkHttpContentFetcher / JsoupNewsContentParser / MinimaxNewsContentParser / MockFallbackNewsContentParser
├── data/llm/              # MinimaxChatClient
├── presentation/home/     # HomeScreen/ViewModel/UiState/UiEvent/components
├── presentation/video/    # VideoScreen/VideoViewModel/VideoDetailScreen
├── presentation/search/   # SearchScreen/SearchViewModel
├── presentation/detail/   # NewsDetailScreen/NewsDetailViewModel
├── presentation/ai/       # AiChatScreen/AiChatViewModel
├── presentation/earn/     # EarnScreen
├── presentation/mall/     # MallScreen + sub/ 子页面
├── presentation/profile/  # ProfileScreen（个人中心）
├── presentation/task/     # TaskScreen
├── presentation/tools/    # AllFunctionsScreen/CreatorCenterScreen/HistoryScreen/BookshelfScreen
├── presentation/notification/ # NotificationScreen
├── presentation/wallet/   # WalletScreen
├── presentation/hot/      # HotTopicScreen
├── presentation/comment/  # CommentSection
├── presentation/common/   # AppBottomNav/FeedCardItem/AppToast/Formatters（公共组件）
├── di/                    # Hilt Module（Network/Database/Repository/DataSource/NewsContent/Ai）
└── ui/theme/              # Compose Theme
```

### MVI 契约

```kotlin
// ViewModel 暴露：
val uiState: StateFlow<HomeUiState>    // 唯一状态出口
fun onEvent(event: HomeUiEvent)        // 唯一事件入口

// UiState 必须完整描述 UI 所需的所有状态：
sealed class HomeUiState {
    Loading                            // 首次加载
    Success(feedItems, isRefreshing, isLoadingMore, hasMore, currentTab)
    Error(message, retryable)          // 错误状态
    Empty                              // 空数据
}
```

### 代码规范

- 遵循 Kotlin 官方编码规范（`kotlin.code.style=official`）
- 禁止使用 `!!` 强制解包，必须使用 `?.let` / `?:` 安全调用
- 函数体不超过 30 行，Complex 组件拆分为子 Composable
- 数据类使用 `data class`，接口方法不超过 5 个
- 禁止在 Composable 中发起网络请求或数据库操作，所有副作用在 ViewModel/LaunchedEffect 中处理
- Compose 预览函数单独提取，不混入业务 Composable
- `LazyColumn` 的 `items` 必须指定 `key`

### 功能边界

**必须实现**：
- 4 种卡片类型渲染（TextTop / LeftTextRightImage / LargeImage / Video）
- 顶部 Tab 切换（8 个频道：关注 / 推荐 / 热榜 / 深圳 / 小说 / 发现 / 视频 / 财经）
- 底部导航栏页面切换（5 个独立页面）
- 下拉刷新 + 加载更多
- Room 本地缓存 + 离线展示
- 搜索功能（双入口）
- 新闻详情页（HTTP → Jsoup → LLM → Mock 兜底）
- 视频详情页（沉浸式播放占位 + 评论）
- Loading / Empty / Error 状态切换

**明确不做**：
- 用户登录/注册
- 真实视频播放（仅封面 + 播放按钮 UI，无 ExoPlayer/Media3 播放器）
- 真实的评论/点赞/分享后端（评论数据为 Mock，仅本地展示）
- 推送通知
- 军事 / 畅听 / 体育频道接入顶部 Tab（组件已编码但未启用）

---

## 当前已知边界

下面这些内容在技术设计文档或需求文档里出现了，但当前仓库还没有真正交付：

- ~~Paging3 + RemoteMediator~~：已接入，替换手动分页逻辑
- ~~Room 缓存优先离线展示~~：已通过 Paging3 + RemoteMediator 实现，PagingSource 读取 Room，RemoteMediator 自动写入
- ~~搜索栏交互~~：已实现点击展开、输入、提交、取消，展示 Mock 搜索结果
- ~~底部导航页面切换~~：已实现 5 个独立页面（Home/Video/Earn/Mall/Profile）
- ~~Video 视频列表页~~：已实现独立页面，展示视频封面、播放按钮、时长、作者、播放量
- ~~新闻详情页~~：已实现，点击非视频卡片跳转 `NewsDetailScreen`
- ~~ktlint~~：当前未在构建中配置，`./gradlew ktlintCheck` 不可用（AGENTS.md 早期版本曾误标为已配置）
- **视频播放**：VideoCard / VideoDetailScreen 仅展示封面和播放按钮 UI，无真实播放器
- **性能优化**：已做基础优化（FeedCard @Immutable、Room WAL 模式、channel 索引、Coil 内存缓存），未做 Compose 重组深度分析、未做图片尺寸严格限制
- **ktlint / detekt 自动化**：尚未配置
- **GitHub Actions CI/CD**：尚未建立
- **军事 / 畅听 / 体育频道接入顶部 Tab**：组件已编码，但 tabs 列表未包含，当前不可达

不要把 `docs/02_技术设计文档.md` 中"将来要做"误读成"现在已经有"。

---

## 演进规划

### ✅ 已完成：Mock 数据源独立组件化

Mock 数据方案已从 `MockInterceptor`（OkHttp 拦截器）升级为 `MockDataSource`（独立数据源组件），Repository 层通过 `RemoteDataSource` 接口获取数据，完全不感知数据真伪。

**当前架构**：

```
Presentation (HomeScreen/ViewModel)
    ↕
Domain (NewsRepository 接口)
    ↕
Data (NewsRepositoryImpl)
    ↕
RemoteDataSource 接口 ←── MockDataSource（实现）
                         （或 RealRemoteDataSource（实现））
    ↕
Room LocalDataSource
```

**MockDataSource 职责**：
- 实现 `RemoteDataSource` 接口，从 `assets/news_data.json` 加载 600 条合成新闻数据
- 按频道过滤（推荐/热榜/视频/财经等）+ 时间倒序排序 + 分页截取
- 支持模拟网络延迟（通过 `DebugControls.networkDelayMs` 控制）
- 支持模拟错误状态（通过 `DebugControls.shouldSimulateError` 控制）
- Repository 只调用 `remoteDataSource.getNewsFeed(channel, page, size)`，不关心底层是 Mock 还是真实 API

**关键文件**：
- `data/remote/datasource/RemoteDataSource.kt`（接口）
- `data/remote/datasource/MockDataSource.kt`（Mock 实现）
- `data/remote/datasource/RealRemoteDataSource.kt`（真实 API 实现，当前未启用）
- `di/DataSourceModule.kt`（绑定 RemoteDataSource 实现类）
- `data/remote/datasource/DebugControls.kt`（调试控制：延迟/错误模拟开关）

**已验收**：
- [x] RepositoryImpl 不直接持有 NewsApi，只持有 RemoteDataSource 接口
- [x] MockDataSource 支持 REFRESH（page=0）和 APPEND（page>0）
- [x] 支持模拟网络延迟（通过 DebugControls 控制）
- [x] 支持模拟错误状态（通过 DebugControls 控制）
- [x] 支持多 channel 数据隔离（推荐/热榜/视频/社会各自独立数据集）
- [x] Room 缓存流程不变，RemoteMediator 通过 RemoteDataSource 获取数据
- [x] 切换真实后端时，仅需在 `di/DataSourceModule.kt` 中替换实现类，零业务代码改动

---

### 规划：GitHub 工作流与版本管理（项目管理基建）

**目标**：建立规范的项目管理流程，将代码质量门禁、版本管理和协作规范沉淀到 GitHub 工作流中，使 Demo 具备真实商业项目的工程管理素养。

**当前问题**：
- 当前开发直接在 `main` 分支提交，无分支保护、无 PR Review 流程
- 无自动化 CI/CD，代码合并前依赖手动 `./gradlew assembleDebug` 验证
- 无版本 Tag 管理和 CHANGELOG，迭代历史不清晰
- 无 Issue 模板和里程碑管理，需求追踪和 Bug 管理散落在对话中

**目标架构**：

```
GitHub Repository
├── Branches
│   ├── main（受保护，仅通过 PR 合并）
│   ├── develop（集成测试分支，可选）
│   └── feature/* / fix/*（开发分支）
├── Pull Requests（强制 Review + CI 通过）
├── Issues（Bug / Feature / Task 分类）
├── Milestones（迭代规划）
├── Actions（CI/CD 流水线）
│   ├── Build & Lint（assembleDebug + ktlint/detekt）
│   ├── Unit Test（./gradlew test）
│   └── Release（自动打 Tag + 生成 CHANGELOG）
└── Releases（语义化版本 + APK 产物）
```

**Git 工作流规范**：

| 环节 | 规范 |
|------|------|
| 分支策略 | GitHub Flow：`main` 为唯一长期分支，功能开发从 `main` checkout 出 `feature/xxx` 或 `fix/xxx` |
| 分支保护 | `main` 开启保护：禁止直接 push，必须通过 PR 合并；PR 至少 1 个 Review Approval |
| Commit 规范 | `<类型>: <简述>`，类型包括 `feat/fix/docs/refactor/perf/test/chore` |
| PR 模板 | 包含"改动说明 / 影响范围 / 测试方式 / 截图"四部分 |
| 合并策略 | Squash and Merge，保持 `main` 提交历史线性整洁 |

**CI/CD 流水线设计**：

```yaml
# .github/workflows/ci.yml
name: CI
on: [pull_request, push]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with: { java-version: '17', distribution: 'temurin' }
      - name: Grant execute permission for gradlew
        run: chmod +x gradlew
      - name: Build with Gradle
        run: ./gradlew assembleDebug
      - name: Run ktlint
        run: ./gradlew ktlintCheck
      - name: Run Unit Tests
        run: ./gradlew test
```

**版本管理策略**：

- 采用语义化版本（SemVer）：`MAJOR.MINOR.PATCH`
  - `MAJOR`：架构重构或不兼容 API 变更
  - `MINOR`：新功能（如 MockDataSource、搜索功能）
  - `PATCH`：Bug 修复、文档更新
- 每个 Release 对应一个 Git Tag（如 `v1.3.0`）
- 自动生成 CHANGELOG.md，按类型分类（Features / Fixes / Refactors）

**验收标准**：
- [ ] `main` 分支开启保护规则，禁止直接 push
- [ ] PR 必须至少 1 个 Review Approval 才能合并
- [ ] CI 流水线在 PR 时自动触发：编译 + ktlint + 单元测试
- [ ] 建立 Issue 模板（Bug Report / Feature Request）和 PR 模板
- [ ] 创建至少 1 个 Milestone 管理 MockDataSource 开发迭代
- [ ] 建立 CHANGELOG.md 并记录已有版本变更
- [ ] 发布至少一个 GitHub Release（含 APK 产物）

---

## 修改硬约束

### 1. 改行为，不只改代码

如果你改的是用户可见行为，需要同步检查这些文档是否要更新：

- `AGENTS.md`
- `README.md`
- `docs/02_技术设计文档.md`
- `docs/01_需求分析文档.md`（仅在功能边界变化时）
- `docs/04_项目进度文档.md`（日报/周报）

### 2. 改 UI 状态或交互，要联动这几处

如果修改 `HomeUiState` 密封类或 `HomeUiEvent`：

- `HomeUiState.kt`
- `HomeUiEvent.kt`
- `HomeViewModel.kt`
- `HomeScreen.kt` 中所有 `when (uiState)` 分支和 `when (event)` 分支
- 所有 Preview 函数（它们通常硬编码了旧的 State 构造）
- `AGENTS.md`

### 3. 改数据层，要联动这几处

如果修改 Entity / DTO / API 接口 / Mapper：

- 修改的文件本身
- 对应 Mapper（三层转换必须同步）
- `FeedCard` 密封类（如果 Domain Model 变化）
- 卡片组件（如果新增/删除字段）
- `MockDataSource`（assets/news_data.json 的字段结构必须与 DTO 一致）
- `AGENTS.md`

### 4. 改 Repository 接口或实现，要联动这几处

- `domain/repository/NewsRepository.kt`
- `data/repository/NewsRepositoryImpl.kt`
- `HomeViewModel.kt`
- 对应测试

### 5. 改依赖版本，要联动这几处

- `gradle/libs.versions.toml`
- `app/build.gradle.kts`（如新增依赖别名）
- `AGENTS.md`

禁止在未明确要求时引入新第三方库。

### 6. 改 Mock 数据源逻辑

- `MockDataSource.kt`
- 注意 `assets/news_data.json` 的字段名必须与 `RawNewsItem` / `NewsItemDto` 的 `@SerialName` 一致
- 注意 `filterByChannel`、`hasMore` 逻辑与 ViewModel 分页逻辑的协同
- 改延迟/错误模拟行为时同步检查 `DebugControls.kt`

### 7. 禁止提交敏感信息或产物垃圾

- 不提交 `.gradle/`、`build/`、`.idea/`、`local.properties`、`*.keystore`
- 不提交含密钥、Token 的内容
- 分支命名：`feature/<功能名>` 或 `fix/<问题描述>`
- Commit 信息：中文描述，格式 `<类型>: <简述>`，如 `feat: 添加TextTopCard组件`

### 8. 保持代码可读性

- 结构清晰、逻辑可读、文件职责明确、少量但必要的注释
- 函数体不超过 30 行
- 不要为了"工程炫技"把简单逻辑过度抽象到难以理解和维护

### 9. 禁止项

- 禁止修改 `.gitignore` 中已排除的规则
- 禁止修改 Gradle Wrapper 版本
- 禁止在生产代码中使用 `TODO()` 会导致崩溃的占位符（用注释 `// TODO:` 代替）
- 禁止使用 `@Suppress` 注解绕过编译检查
- 禁止在 ViewModel 中持有 Context/View 引用
- 禁止提交 `.env` 或含真实 API Key 的内容

---

## 内容安全与敏感词过滤约束

### 背景

项目从 `assets/news_data.json` 加载 600 条合成新闻数据，新闻标题 / 正文 / 作者 / 来源可能涉及以下内容，调用 LLM 视觉 API（`analyze_screenshot` / `vision_action` / `compare_screenshots`）时容易触发模型内容安全策略导致请求失败：

- 政治敏感（领导人、政策、事件等）
- 暴力血腥（灾难、事故、凶杀、战争）
- 色情低俗（露骨描写、性相关）
- 歧视性内容（地域、性别、民族、宗教）
- 自残 / 药物滥用
- 违法犯罪细节

### 硬约束

**1. 读取新闻文本只读结构，不读值**

读取 `assets/news_data.json` / `MockDataSource.kt` 中硬编码数据 / Room 数据库时，**只读取 JSON 字段名、类型、Schema**，不读取实际 `title` / `content` / `author` 文本值。

- ✅ 合法：复述结构（如"含字段：title, content, source, publishTime"）
- ❌ 禁止：在对话中复述具体新闻标题或正文
- ❌ 禁止：把 `news_data.json` 整段 `cat` 出来给 LLM 看
- 需要引用具体内容时使用占位符：`<title>` / `<content>` / `<source>` / `<N 字摘要>`

**2. 调用 LLM 视觉 / 分析 API 前必须脱敏**

调用以下 MCP 工具时，prompt 中不得包含新闻原文：

| 工具 | 错误用法 | 正确用法 |
|------|----------|----------|
| `analyze_screenshot` | "分析屏幕上'某条新闻标题'是否正确" | "分析卡片间距与对齐是否符合设计稿" |
| `vision_action` | "点击标题为'某条新闻'的卡片" | "点击列表中第 3 个 LargeImage 卡片" |
| `compare_screenshots` | "对比两张图中'某条新闻标题'的字体大小" | "对比两张图第一张卡片的整体布局" |
| `extract_design_spec` | 用于分析带新闻的截图 | 只用于 `./design/` 下的设计稿 |

**3. 日志与异常信息处理**

- logcat / Timber 日志中含新闻原文时，**不把整段日志原样粘贴给 LLM**
- 异常堆栈中出现新闻内容时，**只保留堆栈结构**（类名 + 行号 + 方法名），省略消息值
- 调试代码中禁止 `Log.d(tag, newsItem.title)` 形式的裸日志，使用 `Log.d(tag, "<news redacted>")`

**4. UI 文本验证的替代方法**

需要确认某条新闻是否在 UI 上正确渲染时：

- ✅ 合法：`find_element(text = "<title 前 4 字>")` 验证存在性
- ✅ 合法：`verify_ui(type=color)` 做颜色 / 位置校验
- ✅ 合法：`dump_hierarchy` 后用结构描述（"找到 N 个 TextView，bounds 在 ... 区域"）
- ❌ 禁止：把整条新闻标题拼到 LLM prompt 里要求确认
- ❌ 禁止：`analyze_screenshot` 不带 `prompt` 让 LLM 自由识别（容易触发策略）

**5. 设计稿 MCP 工具输出必须脱敏**

设计稿截图（`./design/*.jpg`）通常包含示例新闻文本（标题、来源、正文），传给视觉 LLM 时同样可能触发内容安全策略。`skills/src/utils/design-extractor.ts` 中的 4 个 system prompt（`JSON_SYSTEM_PROMPT` / `MARKDOWN_SYSTEM_PROMPT` / `extractComponents` / `designToComposeSkeleton`）已统一为「**禁止原样转录截图中的文字**」规则：

- ✅ 输出中的所有文字位置一律用占位符代替：`<title>` / `<subtitle>` / `<source>` / `<time>` / `<count>` / `<content>` / `<tab>` / `<nav>` / `<button>` / `<placeholder>` / `<badge>` / `<keyword>` / `<X>`
- ✅ 颜色、布局、组件类型、坐标、尺寸、形状可照常提取（无敏感内容）
- ❌ 禁止：让 LLM 在 JSON / Markdown / Compose 骨架里输出截图里的中英文字符串
- ❌ 禁止：在 Agent 后处理时把这些占位符替换成真实新闻文本

修改这 4 个 prompt 时必须同步更新：
- `skills/src/utils/design-extractor.ts` 顶部的「内容安全与脱敏策略」注释（含占位符约定表）
- `AGENTS.md` 本节（本条规则）
- 涉及的工具描述：`extract_design_spec` / `extract_design_components` / `design_to_compose` 在 MCP 注册表中的 description

### 适用场景速查

| 场景 | 读取新闻文本？ | 调 LLM？ | 处理方式 |
|------|----------------|----------|----------|
| 检查 JSON Schema | 只读结构 | 否 | 复述字段名 |
| 单元测试 mock 数据 | 读取示例 | 否 | 用 mock 数据 |
| `dump_hierarchy` 调试 | 读取 elements | 否 | 不传文本到 LLM |
| `analyze_screenshot` UI 审查 | 截图含新闻 | 是 | 描述 UI 不描述文本 |
| 视觉对比设计稿 vs 实现 | 是 | 是 | 只描述布局差异 |
| 排查新闻渲染 Bug | 读取 title 摘要 | 否 | 用前 4 字 + `…` |
| logcat 异常归因 | 读取堆栈 | 是 | 屏蔽消息值 |
| **设计稿 MCP（`extract_design_spec` / `_components` / `_tokens` / `design_to_compose`）** | 截图含示例文本 | 是 | system prompt 强制用占位符，输出中无原文 |

---

## 建议验证路径

### 每次代码修改后（质量门禁）

```bash
./gradlew assembleDebug
```

必须编译通过，无 Kotlin 编译错误。

### 修改了 UI 或状态管理

- 在 Android Studio 的 Preview 面板检查所有 Preview 函数是否正常渲染
- 真机/模拟器运行，验证：
  1. 首次进入是否显示 Loading → Success
  2. Tab 切换是否正确重置并加载新数据
  3. 下拉刷新是否触发并收回
  4. 滑动到底部是否触发加载更多
  5. Error 状态是否展示重试按钮

### 修改了数据层或 Mapper

- 验证 `MockDataSource` 返回的数据能否被正确解析（检查 assets/news_data.json 格式）
- 验证 Room 写入和读取是否一致（可通过 Database Inspector）
- 运行单元测试：

```bash
./gradlew test
```

### 修改了依赖或构建配置

```bash
./gradlew clean assembleDebug
```

### 修改了 Hilt Module

- 确保 `@Binds` 和 `@Provides` 的返回类型与注入点匹配
- Hilt 编译错误通常表现为 `kspDebugKotlin` 失败，需查看详细错误信息

---

## 新线程工作建议

进入仓库后，建议按这个顺序建立上下文：

1. 先看本文件（`AGENTS.md`）
2. 再看 `README.md`
3. 再看 `docs/01_需求分析文档.md` 和 `docs/02_技术设计文档.md`
4. 再看 `MainActivity.kt` → `HomeScreen.kt` → `HomeViewModel.kt`
5. 然后根据任务进入对应模块

如果用户提的是：

- **UI/交互问题**：先看 `HomeScreen.kt` + `HomeViewModel.kt` + `HomeUiState.kt`/`HomeUiEvent.kt`
- **卡片组件问题**：先看 `presentation/home/components/` 下对应组件
- **数据流/网络问题**：先看 `data/remote/api/NewsApi.kt` + `data/repository/NewsRepositoryImpl.kt` + `data/remote/datasource/MockDataSource.kt`
- **数据库问题**：先看 `data/local/entity/` + `data/local/dao/` + `data/local/database/AppDatabase.kt`
- **Mapper/模型问题**：先看 `domain/model/FeedCard.kt` + `data/mapper/NewsMapper.kt` + `data/remote/dto/`
- **DI 配置问题**：先看 `di/NetworkModule.kt` + `di/DatabaseModule.kt` + `di/RepositoryModule.kt`
- **分页问题**：先看 `HomeViewModel.kt` 中 `loadMore()` 和 `loadFeed()`，再对照 `docs/02_技术设计文档.md` 第 8 章
- **主题/样式问题**：先看 `ui/theme/Color.kt` + `ui/theme/Theme.kt` + `ui/theme/Type.kt`
- **读取新闻数据 / 调用 LLM 视觉 API**：必看 [内容安全与敏感词过滤约束](#内容安全与敏感词过滤约束)，新闻文本只读结构不读值，传给 LLM 的 prompt 必须脱敏

---

## 持续维护约定

### 开发流程

1. **接到任务后**：先阅读相关现有代码，理解当前实现
2. **修改代码前**：明确影响范围，列出需改动的文件
3. **代码改动**：实现功能 → 跑通编译 → 修复 lint 问题
4. **完成后**：更新 `docs/04_项目进度文档.md` 的日报和周报

### 质量门禁

- 每次代码修改后必须执行 `./gradlew assembleDebug` 确保编译通过
- 新功能必须使用 MVI 模式（UiState/UiEvent/ViewModel）
- 新增依赖必须在 `libs.versions.toml` 中声明版本
- 禁止在未明确要求时引入新第三方库

### 文档约定

- 进度记录：`docs/04_项目进度文档.md`
- 需求参考：`docs/01_需求分析文档.md`
- 技术设计：`docs/02_技术设计文档.md`
- 不要创建额外的 .md 文档，除非用户明确要求

---

## 附录：构建配置速查

| 配置项 | 值 |
|--------|-----|
| compileSdk | API 36 |
| minSdk | API 26（Android 8.0）|
| targetSdk | API 36 |
| AGP | 9.2.1 |
| Kotlin | 2.2.10 |
| KSP | 2.3.8 |
| Compose BOM | 2026.02.01 |
| Hilt | 2.59.2 |
| Room | 2.7.0 |
| Paging | 3.3.0 |
| Retrofit | 2.11.0 |
| Coil | 2.7.0 |
| OkHttp | 4.12.0 |
| Jsoup | 1.17.2 |
| kotlinx-serialization-json | 1.8.0 |
