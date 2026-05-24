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
- **当前阶段**：骨架已落地，Mock 数据流已跑通，核心 UI（4 种卡片 + Tab + 底部导航）已完成
- **当前已交付**：
  - ✅ MVI + Clean Architecture 完整包结构（domain / data / presentation / di）
  - ✅ 4 种卡片类型渲染（TextTop / LeftTextRightImage / LargeImage / Video）
  - ✅ 顶部 Tab 切换（推荐 / 热榜 / 视频 / 社会）
  - ✅ 底部导航栏 UI（首页 / 视频 / 搜索 / 任务 / 我的，纯视觉）
  - ✅ Paging3 + RemoteMediator 混合分页（替换手动分页，支持下拉刷新与自动加载更多）
  - ✅ Room 缓存优先离线展示（PagingSource 读取 Room，RemoteMediator 写入 Room，断网展示缓存）
  - ✅ Room 双表设计（feed_items + remote_keys）+ DAO/Database/Entity + WAL 模式
  - ✅ MockInterceptor 零侵入 Mock 方案：不修改业务代码即可跑通完整数据流
  - ✅ Hilt + KSP 依赖注入（NetworkModule / DatabaseModule / RepositoryModule）
  - ✅ Timber 全链路日志（ViewModel → Repository → MockInterceptor → NewsRemoteMediator）
  - ✅ Compose 多状态 Preview（Loading / Success / Error / Empty / Refreshing）
  - ✅ 搜索栏交互（点击展开、输入、提交、取消，展示 Mock 搜索结果）
  - ✅ 性能优化基础（FeedCard @Immutable、Room channel 索引、Coil ImageLoader 内存缓存配置）
- **当前未开始**：
  - ⬜ 新闻详情页 / 视频播放
  - ⬜ Compose 重组深度分析、图片尺寸严格限制
  - ⬜ ktlint / detekt 代码规范检查
- **结论**：如果你看到 HomeScreen 能正常展示 4 种卡片、Tab 切换、下拉刷新和加载更多，但还没有 Paging3 的 RemoteMediator，这是当前仓库的真实状态，不是你看错

---

## 运行前提

- Android Studio Ladybug（2024.2.1）或更高版本，或等效 IDE（IntelliJ IDEA + Android 插件）
- JDK 17+（Gradle Kotlin DSL 编译需要）
- Android SDK API 36（compileSdk），最低运行设备 API 26（Android 8.0）
- 可用的 Android 模拟器或真机（API 26+）

本地构建无需真实后端，MockInterceptor 会拦截所有 API 请求返回预置 JSON。

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
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`

---

## 当前产品行为

### 1. 首页信息流

- 主入口在 `MainActivity.kt` → `HomeScreen(viewModel)` → `HomeScreenContent`
- 使用 Material3 `Scaffold` 骨架：顶部栏（搜索占位 + TabRow）/ 内容区 / 底部导航栏
- MVI 单向数据流：用户操作 → `HomeUiEvent` → `HomeViewModel.onEvent()` → Repository → 更新 `StateFlow<HomeUiState>` → Compose 重组
- `collectAsStateWithLifecycle()` 以生命周期感知方式订阅 StateFlow，Activity 后台时自动暂停收集

### 2. 顶部 Tab 切换

- 4 个频道：推荐(recommend) / 热榜(hot) / 视频(video) / 社会(society)
- 切换 Tab 时重置 page=0，进入 Loading 状态后请求新数据
- 当前选中 Tab 字体更大(18sp)、加粗、纯白；未选中略小(15sp)、半透明
- 搜索栏支持点击展开输入框、输入关键词、提交搜索、返回取消，展示 Mock 搜索结果

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
| TextTop | `TextTopCard` | 标题 + "置顶"标签 + 来源 + 评论数 + 时间 |
| LeftTextRightImage | `LeftTextRightImageCard` | 左侧文字区 + 右侧缩略图 |
| LargeImage | `LargeImageCard` | 标题 + 底部大图 |
| Video | `VideoCard` | 标题 + 封面图 + 播放按钮 + 时长 |

所有卡片统一包裹 `clickable`，点击事件通过 `HomeUiEvent.OnCardClick(id)` 上报，当前仅打印日志，未跳转详情页。

### 6. Mock 数据流

- `MockInterceptor` 拦截所有匹配 `baseUrl` 的请求，返回预置 JSON
- 支持 `channel` 和 `page` 参数，page=0 返回 8 条数据，page>0 返回 2 条数据
- `hasMore` 在 page < 2 时为 true
- 图片使用 `picsum.photos` 占位图服务
- 移除 MockInterceptor 只需删除 `NetworkModule` 中的 `.addInterceptor()` 一行，即可切换到真实 API

### 7. 数据层

- **网络**：Retrofit + OkHttp + Kotlinx Serialization，`NewsApi.getNewsFeed(channel, page, size)`
- **本地**：Room，`AppDatabase` 含 `feed_items` 和 `remote_keys` 两张表，WAL 模式已开启
- **Repository**：`NewsRepositoryImpl` 实现 `NewsRepository` 接口
  - `getFeedPagingData(channel)`：返回 `Flow<PagingData<FeedCard>>`，内部使用 `Pager` + `NewsRemoteMediator`
  - `getNewsFeed()` / `hasMore()`：保留用于兼容和直接调用
  - `getCachedFeed()`：返回 `Flow<List<FeedCard>>`
  - `searchNews(query)`：Mock 搜索结果
- **RemoteMediator**：`NewsRemoteMediator` 处理 `REFRESH` / `APPEND`，网络成功后写入 Room
- **Mapper**：`NewsMapper.kt` 负责 DTO ↔ Entity ↔ Domain 三层转换

### 8. 底部导航栏

- 5 个 Tab：首页 / 视频 / 搜索 / 任务 / 我的
- `selectedIndex` 是纯本地 UI 状态（`remember { mutableIntStateOf(0) }`），不经过 ViewModel
- 当前仅做视觉展示，不触发页面切换

### 9. 主题

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
├── domain/model/          # FeedCard 密封类（4种子类型）
├── domain/repository/     # NewsRepository 接口
├── data/remote/api/       # Retrofit API 接口
├── data/remote/dto/       # Kotlinx Serialization DTO
├── data/local/entity/     # Room Entity
├── data/local/dao/        # Room DAO
├── data/local/database/   # AppDatabase
├── data/repository/       # NewsRepositoryImpl
├── data/mapper/           # DTO ↔ Entity ↔ Domain 转换
├── presentation/home/     # HomeScreen/ViewModel/UiState/UiEvent/components
├── di/                    # Hilt Module（Network/Database/Repository）
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
- 顶部 Tab 切换（至少推荐、热榜两个频道）
- 下拉刷新 + 加载更多
- Room 本地缓存 + 离线展示
- Loading / Empty / Error 状态切换

**明确不做**：
- 用户登录/注册
- 新闻详情页（仅预留点击事件）
- 视频播放（仅封面 + 播放按钮 UI）
- 评论/点赞/分享
- 推送通知

---

## 当前已知边界

下面这些内容在技术设计文档或需求文档里出现了，但当前仓库还没有真正交付：

- ~~Paging3 + RemoteMediator~~：已接入，替换手动分页逻辑
- ~~Room 缓存优先离线展示~~：已通过 Paging3 + RemoteMediator 实现，PagingSource 读取 Room，RemoteMediator 自动写入
- ~~搜索栏交互~~：已实现点击展开、输入、提交、取消，展示 Mock 搜索结果
- **新闻详情页**：点击卡片仅上报事件，无页面跳转
- **视频播放**：VideoCard 仅展示封面和播放按钮 UI，无实际播放能力
- **底部导航页面切换**：5 个 Tab 仅改变本地 selectedIndex，不切换页面
- **性能优化**：已做基础优化（FeedCard @Immutable、Room WAL 模式、channel 索引、Coil 内存缓存），未做 Compose 重组深度分析、未做图片尺寸严格限制
- **ktlint / detekt**：规划中但尚未配置

不要把 `docs/02_技术设计文档.md` 中"将来要做"误读成"现在已经有"。

---

## 演进规划

### 规划：Mock 数据源独立组件化（下一个重点方向）

**目标**：将当前 `MockInterceptor`（OkHttp 拦截器方案）升级为独立的 `MockDataSource`，使 Repository 层完全不感知数据真伪，Demo 具备真实新闻 App 的完整数据流。

**当前问题**：
- `MockInterceptor` 是 OkHttp 拦截器，逻辑耦合在网络层，Repository 通过 Retrofit 间接"感知"到 Mock
- 错误状态、延迟模拟、分页边界等行为难以通过拦截器精细控制
- 无法模拟真实场景：网络波动、空数据、服务端错误码等

**目标架构**：

```
Presentation (HomeScreen/ViewModel)
    ↕
Domain (NewsRepository 接口)
    ↕
Data (NewsRepositoryImpl)
    ↕
RemoteDataSource 接口 ←── MockDataSource（实现）
                         （或 RetrofitDataSource（实现））
    ↕
Room LocalDataSource
```

**MockDataSource 职责**：
- 实现 `RemoteDataSource` 接口，与 `RetrofitDataSource` 互换
- 模拟真实后端行为：分页（REFRESH/APPEND）、延迟 200-800ms、channel 参数过滤
- 支持错误模拟：按概率返回 500/超时/空数据，用于验证 Error 态 UI
- 本地预置 JSON / 代码生成数据，不依赖网络层
- Repository 只调用 `remoteDataSource.fetchFeed(channel, page, size)`，不关心底层是 Mock 还是真实 API

**需要改动的文件**：
- `data/remote/datasource/RemoteDataSource.kt`（新增接口）
- `data/remote/datasource/MockDataSource.kt`（新增实现）
- `data/remote/datasource/RetrofitDataSource.kt`（新增实现，包装 NewsApi）
- `data/repository/NewsRepositoryImpl.kt`（注入 RemoteDataSource 接口而非直接调用 NewsApi）
- `di/NetworkModule.kt`（提供 MockDataSource 或 RetrofitDataSource 的切换绑定）
- 删除 `data/remote/interceptor/MockInterceptor.kt`（由 MockDataSource 替代）

**验收标准**：
- [ ] RepositoryImpl 不直接持有 NewsApi，只持有 RemoteDataSource 接口
- [ ] MockDataSource 支持 REFRESH（page=0，清空旧数据感）和 APPEND（page>0，追加感）
- [ ] 支持模拟网络延迟（200-800ms 随机）
- [ ] 支持模拟错误状态（可控概率返回 NetworkError / EmptyResponse / ServerError）
- [ ] 支持多 channel 数据隔离（推荐/热榜/视频/社会各自独立数据集）
- [ ] Room 缓存流程不变，RemoteMediator 通过 RemoteDataSource 获取数据
- [ ] 切换真实后端时，仅需在 DI 模块中替换 RemoteDataSource 实现类，零业务代码改动

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
- `MockInterceptor`（Mock JSON 结构必须与 DTO 一致）
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

### 6. 改 Mock 数据或拦截器逻辑

- `MockInterceptor.kt`
- 注意 `buildMockItems` 中 JSON 字符串的字段名必须与 `NewsItemDto` 的 `@SerialName` 一致
- 注意 `hasMore` 逻辑与 ViewModel 分页逻辑的协同

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

- 验证 `MockInterceptor` 返回的 JSON 能否被正确解析
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
- **数据流/网络问题**：先看 `data/remote/api/NewsApi.kt` + `data/repository/NewsRepositoryImpl.kt` + `MockInterceptor.kt`
- **数据库问题**：先看 `data/local/entity/` + `data/local/dao/` + `data/local/database/AppDatabase.kt`
- **Mapper/模型问题**：先看 `domain/model/FeedCard.kt` + `data/mapper/NewsMapper.kt` + `data/remote/dto/`
- **DI 配置问题**：先看 `di/NetworkModule.kt` + `di/DatabaseModule.kt` + `di/RepositoryModule.kt`
- **分页问题**：先看 `HomeViewModel.kt` 中 `loadMore()` 和 `loadFeed()`，再对照 `docs/02_技术设计文档.md` 第 8 章
- **主题/样式问题**：先看 `ui/theme/Color.kt` + `ui/theme/Theme.kt` + `ui/theme/Type.kt`

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
| Kotlin | 2.2.10 |
| Compose BOM | 2026.02.01 |
| Hilt | 2.59.2 |
| Room | 2.7.0 |
| Paging | 3.3.0 |
| Retrofit | 2.11.0 |
| Coil | 2.7.0 |
| OkHttp | 4.12.0 |
