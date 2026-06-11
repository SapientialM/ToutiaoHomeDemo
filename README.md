# 今日头条首页信息流 Demo

> 字节跳动客户端工程训练营课题 — 仿今日头条 App（首页信息流 + 详情页 + 视频 + 商城 + 任务/钱包/AI 等 14 个页面）
>
> Kotlin + Jetpack Compose + MVI + Clean Architecture

---

## 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| 语言 | Kotlin | 2.2.10 |
| UI | Jetpack Compose + Material3 | BOM 2026.02.01 |
| 架构 | MVI + Clean Architecture | — |
| 状态管理 | StateFlow | — |
| DI | Hilt + KSP | 2.59.2 |
| 网络 | Retrofit2 + OkHttp3 | 2.11.0 / 4.12.0 |
| JSON | Kotlinx Serialization | 1.8.0 |
| 图片 | Coil Compose（cache policy 调优） | 2.7.0 |
| 数据库 | Room + KSP（3 表：feed / remote_keys / news_content_cache） | 2.7.0 |
| 分页 | Paging3 + RemoteMediator | 3.3.0 |
| 网页解析 | Jsoup（详情页正文手动解析） | — |
| LLM | Minimax M3（豆包 AI 对话 + 详情页兜底解析） | — |
| 日志 | Timber | 5.0.1 |

## 架构

```
Presentation (Compose Screen + MVI ViewModel × 14 页面)
    ↕ StateFlow<UiState> / (UiEvent) → Unit + LazyPagingItems
Domain (FeedCard / HotListItem / Product / NewsContent + Repository 接口)
    ↕
Data
├── repository/        NewsRepositoryImpl / NewsContentRepositoryImpl / MallRepositoryImpl / ReadPositionRepositoryImpl
├── remote/datasource/  RemoteDataSource 抽象：MockDataSource / RealRemoteDataSource / MallDataSource / CommentDataSource
├── remote/api/         Retrofit NewsApi
├── remote/mediator/    NewsRemoteMediator（Paging3）
├── remote/interceptor/ OkHttp 拦截器
├── parser/             OkHttpContentFetcher + JsoupNewsContentParser + MinimaxNewsContentParser + MockFallback
├── llm/                MinimaxChatClient（OkHttp + SSE 流式）
├── local/              Room（3 表 + 3 DAO，WAL 模式）
└── mapper/             DTO ↔ Entity ↔ Domain
    ↕
Mock 数据（合成生成，零真实信息，详见 docs/data/）
├── app/src/main/assets/news_data/*.json    6 频道 × 1000+ 条
└── app/src/main/assets/mall_products.json  650+ 商品
```

**关键设计决策：**
- `RemoteDataSource` 接口抽象数据来源，Repository 零感知 Mock/Real 切换
- 详情页正文走**三级兜底**：HTTP 抓取（OkHttp）→ Jsoup 手动解析 → Minimax LLM 解析 → Mock 兜底
- 详情页 LLM 解析结果用 `NewsContentCacheEntity` 持久化，二次进入秒开
- 频道「上次看到这里」按 tab 独立持久化阅读位置（`ReadPositionRepository`）
- Paging3 `RemoteMediator` 写入 Room，`PagingSource` 只读 Room，天然实现离线缓存
- 6 频道（推荐/热榜/视频/深圳/财经/社会）各自独立数据集 + 子分页（热榜 top10 真实榜）

```
app/src/main/java/com/example/toutiao/
├── MainActivity.kt                       # @AndroidEntryPoint
├── ToutiaoApplication.kt                 # @HiltAndroidApp + Coil ImageLoader + Timber
├── domain/model/                         # FeedCard / HotListItem / Product / NewsContent
├── domain/repository/                    # 4 个仓库接口
├── data/
│   ├── repository/                       # 4 个仓库实现
│   ├── remote/api/NewsApi.kt             # Retrofit 接口
│   ├── remote/dto/                       # 4 个 DTO
│   ├── remote/datasource/                # 4 个数据源 + DebugControls
│   ├── remote/mediator/NewsRemoteMediator.kt
│   ├── remote/interceptor/
│   ├── parser/                           # 4 个解析器（HTTP/Jsoup/LLM/Mock）
│   ├── llm/MinimaxChatClient.kt          # AI 对话 SSE
│   ├── local/database/AppDatabase.kt     # Room（3 表，WAL 模式）
│   ├── local/entity/                     # 3 个 Entity
│   ├── local/dao/                        # 3 个 DAO
│   └── mapper/NewsMapper.kt
├── di/                                   # Hilt（Network/Database/DataSource/Repository/Ai）
├── ui/theme/                             # 头条红 #D81E06 主题
└── presentation/                         # 14 个页面模块
    ├── home/                             # 首页（Screen/ViewModel/State/Event + 21 个 components）
    ├── home/components/                  # 卡片组件（TextTop / LeftTextRightImage / LargeImage / HotListView / 等）
    ├── detail/                           # 新闻详情（WebView + LLM 解析）
    ├── video/                            # 视频频道 + 视频详情（全屏）
    ├── search/                           # 搜索 + AI 入口
    ├── ai/                               # 豆包 AI 对话全屏页
    ├── comment/                          # 评论系统（列表内嵌 + 悬浮输入框）
    ├── hot/                              # 热榜频道
    ├── mall/                             # 商城（频道 + 3 个子页：优惠券/关注店铺/订单）
    ├── notification/                     # 通知中心
    ├── profile/                          # "我的"页面（含 ProfileMockData）
    ├── task/                             # 任务中心
    ├── earn/                             # 赚钱（带时间奖励）
    ├── wallet/                           # 钱包
    ├── tools/                            # 工具集（CreatorCenter / ToolsHub）
    └── common/                           # AppBottomNav / AppToast / FeedCardItem / Formatters / ImagePlaceholders
```

## 功能矩阵

| 模块 | 功能 | 状态 |
|------|------|------|
| **首页** | TextTop / LeftTextRightImage / LargeImage / Video 4 种基础卡片 | ✅ |
| | 21 个差异化组件（HotListView / XhsGridList / AudioChannel / MilitaryRank / NovelChannel / SportsChannel / ShenzhenVideoCarousel / FinanceTopExtras / 等） | ✅ |
| | 6 频道 Tab（推荐/热榜/视频/深圳/财经/社会） | ✅ |
| | 5 条置顶 + 资讯速递卡 + 断点续读 hint + 浮卡（可关闭） | ✅ |
| | 按 tab 独立持久化阅读位置 | ✅ |
| | 自定义 Toast 样式 + ToC 文案 | ✅ |
| | 无限下滑加载（修复 append 触发） | ✅ |
| **详情页** | HTTP 抓取 → Jsoup 手动解析 → LLM 兜底 → Mock 兜底 | ✅ |
| | 解析结果 Room 持久化（二次进入秒开） | ✅ |
| | WebView 视频内嵌播放 | ✅ |
| | 评论系统（列表内嵌 + 悬浮输入框） | ✅ |
| **搜索 + AI** | 顶部搜索框点击展开 | ✅ |
| | 提交搜索 → Mock 结果列表 | ✅ |
| | 豆包 AI 对话全屏页（MinimaxChatClient SSE 流式） | ✅ |
| **视频** | 视频频道列表 + 全屏视频详情页 | ✅ |
| **热榜** | 每行浅红渐变背景 + 顶部快捷入口 | ✅ |
| | 真实 top10 榜单（`热榜_top10.json`）+ 点击跳详情 | ✅ |
| **商城** | 商品列表 + 官方商城频道 | ✅ |
| | 3 个子页：优惠券 / 关注店铺 / 订单 | ✅ |
| **任务/赚钱** | 任务中心 + 赚钱页（带时间奖励机制） | ✅ |
| **钱包/通知/工具** | 钱包页 / 通知中心 / 工具集（创作者中心 + 工具集合） | ✅ |
| **"我的"** | 未登录占位（头像 + 登录按钮 + 完整功能入口） | ✅ |
| **底部导航** | 5 Tab：首页 / 视频 / 搜索 / 任务 / 我的 | ✅ |
| **通用** | PullToRefreshBox + lazyPagingItems.refresh() | ✅ |
| | Paging3 LoadType.APPEND 自动触发 | ✅ |
| | MVI: Loading / Error / Empty / Success（loadState 驱动） | ✅ |
| | Room 双表缓存 + Paging3 离线读取 | ✅ |
| | 调试面板（齿轮 → AlertDialog：网络延迟 0~5s + 模拟错误） | ✅ |
| | Compose Preview 多状态预览 | ✅ |

## 数据流

```
┌─────────────────────────────────────────────────────────────┐
│ Mock 数据源（合成生成，零真实信息）                          │
│                                                              │
│ news_data/  (v1: 6 频道旧 JSON)                              │
│   └─ fetch_news_v2.py → clean_v2_gentle.py → *_v2.json       │
│                                                              │
│ app/src/main/assets/news_data/  ← 手动 cp 同步（不入 git）   │
│ mall_products.json  ← synthesize_mock.js（650+ 商品）         │
└────────────────────┬────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────────────────┐
│ Data Layer（Repository + Paging3 RemoteMediator）            │
│   NewsRepositoryImpl / MallRepositoryImpl                    │
│   写入 Room（feed_items + remote_keys + news_content_cache） │
└────────────────────┬────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────────────────┐
│ UI Layer（Compose + StateFlow + LazyPagingItems）            │
│   HomeScreen / DetailScreen / VideoScreen / MallScreen / ... │
└─────────────────────────────────────────────────────────────┘

详情页正文（NewsDetailScreen）三级兜底：
  OkHttpContentFetcher  →  JsoupNewsContentParser  →  MinimaxNewsContentParser  →  MockFallback
                                                                                       ↓
                                                                       NewsContentCacheEntity 持久化
```

## MCP Skill 子项目

`skills/` 目录下有 **android-dev-assist** MCP Server（v3.1.0，44 工具 / 15 模块），为 Agent 提供完整 Android 开发辅助：

- **基础交互**（6）：screenshot、screenshot_region、tap、swipe、input_text、press_key
- **UI 层级**（3）：dump_hierarchy、find_element、wait_for_element
- **视觉驱动**（4）：vision_action、analyze_screenshot、compare_screenshots、verify_ui
- **设计稿**（5）：list_design_files、extract_design_spec、extract_design_tokens、extract_design_components、design_to_compose
- **PM Agent**（5）：pm_review、pm_explore、pm_discuss、pm_check、pm_mark_fixed（独立 PM 角色，多轮设计审查 + LLM 视觉评估）
- **日志/崩溃**（4）：get_logs、logcat_search、parse_crash、clear_logs
- **性能/启动**（3）：performance_metrics、measure_app_launch、record_screen
- **其它**：构建/部署、设备/应用管理、网络调试、APK 元数据、UI 自动化、代码质量、项目报告、文件操作

**PM Agent** 是新增的核心子系统：拥有项目记忆（`skills/.pm_memory.json`），能基于多轮上下文对设计稿做深度审查，给出可执行的修复建议。详见 [PM Agent MCP 方案](docs/07_PM-Agent-MCP-设计方案.md)。

`design/` 下 7 张设计稿（首页 3 频道 + 视频 + 商城 + 详情页 3 状态）通过视觉 LLM（Minimax M3）转换为 Agent 可读的结构化规范。详见 [Vision Benchmark 报告](docs/llm-api/vision-benchmark.md)。

## 构建与运行

```bash
# 环境要求
# - Android Studio 2025.1+
# - JDK 17+
# - Android SDK API 36（compileSdk 36.1）

# 配置 LLM API Key（详情页兜底 + AI 对话）
echo "LLM_API_KEY=your_key_here" >> local.properties
# 可选：LLM_BASE_URL（默认 https://api.minimaxi.com/v1）、LLM_MODEL（默认 MiniMax-M3）

# 构建
./gradlew assembleDebug

# 安装 + 启动
adb install app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.example.toutiao/.MainActivity

# 首次运行：手动同步数据到 assets（不入 git）
mkdir -p app/src/main/assets/news_data
cp news_data/*_v2.json news_data/热榜_top10.json app/src/main/assets/news_data/

# 查看日志（Timber 全链路）
adb logcat -s Timber:D MockDataSource:D NewsRemoteMediator:D JsoupNewsContentParser:D MinimaxChatClient:D
```

### 自动化脚本

```bash
# 每小时自动跑 PM 审查（监控 UI 回归）
./scripts/pm-hourly-check.sh
# 安装为 launchd 定时任务
cp scripts/com.example.toutiao.pm-hourly.plist ~/Library/LaunchAgents/
launchctl load ~/Library/LaunchAgents/com.example.toutiao.pm-hourly.plist
```

## 文档

| 文档 | 说明 |
|------|------|
| [AGENTS.md](AGENTS.md) | AI Agent 协作协议与项目约束 |
| [需求分析](docs/01_需求分析文档.md) | 功能需求、非功能需求、需求边界 |
| [技术设计](docs/02_技术设计文档.md) | 架构设计、状态管理、数据库、分页策略 |
| [开发文档](docs/03_开发文档.md) | 项目结构、API 接口、构建发布、调试指南 |
| [项目进度](docs/04_项目进度文档.md) | 里程碑、周报、日报 |
| [优化路线图 v2.0](docs/roadmap.md) | 五维评估体系 + 6 天冲刺计划 |
| [性能 Baseline 报告](docs/performance-baseline.md) | 启动 / 内存 / FPS 测量数据 |
| [PM Agent MCP 方案](docs/07_PM-Agent-MCP-设计方案.md) | PM 角色设计 + 多轮对话协议 |
| [技术债清单](docs/DEBT.md) | 已识别待优化项 |
| [MCP Skill 技术文档](skills/README.md) | `android-dev-assist` MCP Server 44 工具说明 |
| [Vision Benchmark 报告](docs/llm-api/vision-benchmark.md) | 视觉 LLM 多模型对比结果 |

## License

MIT — Copyright (c) 2026 SapientialM
