# 今日头条首页信息流 Demo

> 字节跳动客户端工程训练营课题 — 仿今日头条首页列表页
>
> Kotlin + Jetpack Compose + MVI + Clean Architecture

<p align="center">
  <img src="img/Screenshot_20260523_141421.png" alt="首页效果图" width="320">
</p>

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
| 图片 | Coil Compose | 2.7.0 |
| 数据库 | Room + KSP | 2.7.0 |
| 分页 | Paging3 | 3.3.0 |
| 日志 | Timber | 5.0.1 |

## 架构

```
Presentation (Compose Screen + MVI ViewModel)
    ↕ StateFlow<UiState> / (UiEvent) → Unit
Domain (FeedCard sealed class + NewsRepository interface)
    ↕
Data (NewsRepositoryImpl → NewsApi + FeedDao)
    ↕
MockInterceptor (Demo 阶段拦截 API 请求返回 Mock JSON)
```

```
app/src/main/java/com/example/toutiao/
├── domain/model/FeedCard.kt             # 4 种卡片类型密封类
├── domain/repository/NewsRepository.kt  # 仓库接口
├── data/remote/api/NewsApi.kt           # Retrofit API
├── data/remote/dto/NewsFeedResponse.kt  # Kotlinx Serialization DTO
├── data/remote/interceptor/MockInterceptor.kt  # OkHttp Mock 拦截器
├── data/local/entity/                   # Room 实体
├── data/local/dao/                      # Room DAO
├── data/local/database/AppDatabase.kt   # Room 数据库
├── data/repository/NewsRepositoryImpl.kt # 仓库实现
├── data/mapper/NewsMapper.kt            # DTO ↔ Entity ↔ Domain
├── presentation/home/                   # 首页 MVI (Screen/ViewModel/State/Event)
├── presentation/home/components/        # 4 种卡片 + BottomInfoRow
├── di/                                  # Hilt 模块 (Network/Database/Repository)
├── ui/theme/                            # 头条红白主题
├── ToutiaoApplication.kt               # @HiltAndroidApp
└── MainActivity.kt                      # @AndroidEntryPoint
```

## 功能

- [x] 4 种卡片类型渲染（置顶 / 左文右图 / 大图 / 视频）
- [x] 顶部 Tab 栏频道切换（推荐 / 热榜 / 视频 / 社会）
- [x] 下拉刷新 + 滑动加载更多
- [x] Loading / Success / Error / Empty 状态管理
- [x] 底部导航栏（首页 / 视频 / 搜索 / 任务 / 我的）
- [x] 搜索栏 UI 占位
- [x] MockInterceptor — Demo 模式零延迟返回 Mock JSON
- [x] Room 本地数据库缓存
- [x] Compose Preview 多状态预览
- [ ] Paging3 RemoteMediator 分页集成
- [ ] 离线缓存展示

## 构建与运行

```bash
# 环境要求
# - Android Studio 2025.1+
# - JDK 17+
# - Android SDK API 36

# 克隆
git clone <repo-url> && cd ToutiaoFeedDemo

# 构建
./gradlew assembleDebug

# 安装
adb install app/build/outputs/apk/debug/app-debug.apk

# 查看日志
adb logcat -s Timber:D Toutiao:D MockInterceptor:D
```

## 文档

| 文档 | 说明 |
|------|------|
| [AGENT.md](AGENT.md) | AI Agent 协作协议与项目约束 |
| [需求分析](docs/01_需求分析文档.md) | 功能需求、非功能需求、需求边界 |
| [技术设计](docs/02_技术设计文档.md) | 架构设计、状态管理、数据库、分页策略 |
| [开发文档](docs/03_开发文档.md) | 项目结构、API 接口、构建发布、调试指南 |
| [项目进度](docs/04_项目进度文档.md) | 里程碑、周报、日报 |

## License

MIT
