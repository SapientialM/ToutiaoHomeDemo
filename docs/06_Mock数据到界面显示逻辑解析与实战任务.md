# Mock 数据到界面显示逻辑解析与实战任务

> 本文档用于帮助开发者快速理解 `ToutiaoFeedDemo` 的 Mock 数据流，并通过修改代码来掌握项目结构。阅读完本文档后，请按文末的「实战任务」逐步动手完成。

---

## 一、Mock 数据到界面显示的完整逻辑流程

本项目采用 **MVI + Clean Architecture + Paging3** 架构，数据流向是单向的：

```
MockDataSource (生成数据)
    ↓
NewsRemoteMediator (分页调度)
    ↓
Room 数据库 (本地缓存)
    ↓
FeedDao.getFeedPagingSource() (PagingSource)
    ↓
Pager.flow → Repository → ViewModel → Compose UI
```

以下按调用顺序逐层解析。

---

### 第 1 层：Mock 数据生成（Data 层）

**功能解释**：
`MockDataSource` 不请求真实网络，而是在代码中直接按 `channel` 和 `page` 返回预置数据。支持模拟网络延迟和模拟错误。

**代码位置**：
```
app/src/main/java/com/example/toutiao/data/remote/datasource/MockDataSource.kt
```

**关键代码**：
```kotlin
class MockDataSource : RemoteDataSource {
    override suspend fun getNewsFeed(channel: String, page: Int, size: Int): NewsFeedResponse {
        // 1. 模拟延迟
        delay(DebugControls.networkDelayMs)
        // 2. 模拟错误
        if (DebugControls.shouldSimulateError) throw IOException("模拟网络错误")
        // 3. 按 channel 返回不同数据
        val items = when (channel) {
            "recommend" -> recommendItems(page)
            "hot" -> hotItems(page)
            "video" -> videoItems(page)
            "society" -> societyItems(page)
            else -> recommendItems(page)
        }
        return NewsFeedResponse(code = 0, data = NewsFeedData(list = items, hasMore = page < 2))
    }
}
```

**学习要点**：
- `recommendItems(page)` 中，`page=0` 返回 8 条，`page=1` 返回 4 条，`page=2` 返回 2 条，`hasMore = page < 2` 表示第 2 页后没有更多数据。
- `DebugControls` 是全局单例，控制延迟和错误开关，可在 UI 调试面板中实时切换。

---

### 第 2 层：远程数据源接口与 DI 绑定

**功能解释**：
`RemoteDataSource` 是接口，`MockDataSource` 是实现。通过 Hilt 模块注入，使 Repository 不直接依赖具体实现，方便后续替换为真实 API。

**代码位置**：
```
app/src/main/java/com/example/toutiao/data/remote/datasource/RemoteDataSource.kt   (接口)
app/src/main/java/com/example/toutiao/data/remote/datasource/MockDataSource.kt       (实现)
app/src/main/java/com/example/toutiao/di/DataSourceModule.kt                         (DI绑定)
```

**关键代码**（DataSourceModule.kt）：
```kotlin
@Provides
@Singleton
fun provideRemoteDataSource(): RemoteDataSource = MockDataSource()
```

**学习要点**：
- 如需切换真实 API，只需修改这一行：`= MockDataSource()` → `= RealRemoteDataSource(api)`，业务代码零改动。

---

### 第 3 层：RemoteMediator 分页调度

**功能解释**：
`NewsRemoteMediator` 是 Paging3 的核心组件，负责决定何时加载下一页数据。它有三种 `LoadType`：
- `REFRESH`：下拉刷新，清空旧数据，从 page=0 开始
- `APPEND`：滑动到底部，加载下一页
- `PREPEND`：向前加载（本项目基本不用）

**代码位置**：
```
app/src/main/java/com/example/toutiao/data/remote/mediator/NewsRemoteMediator.kt
```

**关键流程**（load 方法）：
```kotlin
override suspend fun load(loadType: LoadType, state: PagingState<Int, FeedItemEntity>): MediatorResult {
    // 1. 计算当前页码
    val page = when (loadType) {
        LoadType.REFRESH -> 0
        LoadType.APPEND -> remoteKey.nextKey ?: return Success(endOfPaginationReached = true)
        LoadType.PREPEND -> ...
    }
    // 2. 请求 Mock 数据
    val response = remoteDataSource.getNewsFeed(channel, page)
    // 3. REFRESH 时清空该 channel 的旧数据
    if (loadType == LoadType.REFRESH) {
        feedDao.deleteByChannel(channel)
        remoteKeyDao.deleteByChannel(channel)
    }
    // 4. 写入 Room
    feedDao.insertAll(entities)
    remoteKeyDao.insertAll(keys)
    // 5. 返回是否还有更多数据
    return MediatorResult.Success(endOfPaginationReached = !response.data.hasMore)
}
```

**学习要点**：
- `remote_keys` 表保存了每页数据的 `prevKey` 和 `nextKey`，用于追踪分页位置。
- 网络请求成功后**一定写入 Room**，UI 从 Room 读取，实现离线优先。

---

### 第 4 层：Room 数据库与 DAO

**功能解释**：
Room 作为本地缓存，PagingSource 直接读取 Room，RemoteMediator 负责写入 Room。双表设计：`feed_items`（内容）+ `remote_keys`（分页键）。

**代码位置**：
```
app/src/main/java/com/example/toutiao/data/local/entity/FeedItemEntity.kt
app/src/main/java/com/example/toutiao/data/local/entity/RemoteKeyEntity.kt
app/src/main/java/com/example/toutiao/data/local/dao/FeedDao.kt
app/src/main/java/com/example/toutiao/data/local/dao/RemoteKeyDao.kt
app/src/main/java/com/example/toutiao/data/local/database/AppDatabase.kt
```

**FeedDao 关键方法**：
```kotlin
@Query("SELECT * FROM feed_items WHERE channel = :channel ORDER BY created_at DESC")
fun getFeedPagingSource(channel: String): PagingSource<Int, FeedItemEntity>

@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun insertAll(items: List<FeedItemEntity>)
```

**学习要点**：
- `PagingSource<Int, FeedItemEntity>` 是 Paging3 与 Room 的桥梁，数据变化会自动触发 UI 刷新。
- `channel` 字段带索引，不同 Tab 数据互不干扰。

---

### 第 5 层：DTO / Entity / Domain Model 转换

**功能解释**：
三层数据模型隔离：
- **DTO**（`NewsItemDto`）：网络层数据结构，用 `@Serializable` 和 `@SerialName` 标注
- **Entity**（`FeedItemEntity`）：Room 数据库表结构
- **Domain Model**（`FeedCard`）：UI 层使用的密封类，4 种子类型

**代码位置**：
```
app/src/main/java/com/example/toutiao/data/remote/dto/NewsFeedResponse.kt   (DTO)
app/src/main/java/com/example/toutiao/data/local/entity/FeedItemEntity.kt    (Entity)
app/src/main/java/com/example/toutiao/domain/model/FeedCard.kt               (Domain)
app/src/main/java/com/example/toutiao/data/mapper/NewsMapper.kt              (转换器)
```

**转换链**：
```kotlin
// DTO → Entity（写入 Room 前）
NewsItemDto.toEntity(channel: String) : FeedItemEntity

// Entity → Domain（UI 展示前）
FeedItemEntity.toDomain() : FeedCard
```

**FeedCard 密封类**：
```kotlin
sealed class FeedCard {
    data class TextTop(...) : FeedCard()          // 置顶文字卡片
    data class LeftTextRightImage(...) : FeedCard() // 左文右图卡片
    data class LargeImage(...) : FeedCard()       // 大图卡片
    data class Video(...) : FeedCard()            // 视频卡片
}
```

**学习要点**：
- `type` 字段（"text_top"/"left_text_right_image"/"large_image"/"video"）决定 Mapper 生成哪种 `FeedCard`。
- 密封类 + `when` 表达式是类型安全的多卡片渲染核心。

---

### 第 6 层：Repository 实现

**功能解释**：
`NewsRepositoryImpl` 组装 Pager，将 RemoteMediator 和 Room PagingSource 连接起来，对外暴露 `Flow<PagingData<FeedCard>>`。

**代码位置**：
```
app/src/main/java/com/example/toutiao/data/repository/NewsRepositoryImpl.kt
app/src/main/java/com/example/toutiao/domain/repository/NewsRepository.kt
```

**关键代码**：
```kotlin
override fun getFeedPagingData(channel: String): Flow<PagingData<FeedCard>> {
    return Pager(
        config = PagingConfig(pageSize = 8, prefetchDistance = 2),
        remoteMediator = NewsRemoteMediator(channel, remoteDataSource, feedDao, remoteKeyDao),
        pagingSourceFactory = { feedDao.getFeedPagingSource(channel) },
    ).flow.map { pagingData -> pagingData.map { it.toDomain() } }
}
```

---

### 第 7 层：ViewModel（MVI 核心）

**功能解释**：
`HomeViewModel` 管理所有 UI 状态。`currentTab` 变化时，通过 `flatMapLatest` 自动切换不同 channel 的分页数据流。

**代码位置**：
```
app/src/main/java/com/example/toutiao/presentation/home/HomeViewModel.kt
app/src/main/java/com/example/toutiao/presentation/home/HomeUiState.kt
app/src/main/java/com/example/toutiao/presentation/home/HomeUiEvent.kt
```

**关键代码**：
```kotlin
val feedPagingData: Flow<PagingData<FeedCard>> = _currentTab
    .flatMapLatest { tab ->
        newsRepository.getFeedPagingData(tab)   // Tab 切换时自动取消旧流、订阅新流
    }
    .cachedIn(viewModelScope)

fun onEvent(event: HomeUiEvent) {
    when (event) {
        is OnTabSelected -> switchTab(event.tab)
        is OnSearchSubmit -> performSearch(query)
        ...
    }
}
```

---

### 第 8 层：Compose UI 渲染

**功能解释**：
`HomeScreen` 订阅 `feedPagingData`，使用 `LazyPagingItems` 渲染列表。根据 `loadState` 自动展示 Loading / Error / Empty / 内容 四种状态。

**代码位置**：
```
app/src/main/java/com/example/toutiao/presentation/home/HomeScreen.kt
app/src/main/java/com/example/toutiao/presentation/home/components/TextTopCard.kt
app/src/main/java/com/example/toutiao/presentation/home/components/LeftTextRightImageCard.kt
app/src/main/java/com/example/toutiao/presentation/home/components/LargeImageCard.kt
app/src/main/java/com/example/toutiao/presentation/home/components/VideoCard.kt
app/src/main/java/com/example/toutiao/presentation/home/components/BottomInfoRow.kt
```

**列表渲染关键代码**：
```kotlin
LazyColumn {
    items(count = lazyPagingItems.itemCount, key = lazyPagingItems.itemKey { it.id }) { index ->
        val card = lazyPagingItems[index]
        if (card != null) {
            when (card) {
                is FeedCard.TextTop -> TextTopCard(card)
                is FeedCard.LeftTextRightImage -> LeftTextRightImageCard(card)
                is FeedCard.LargeImage -> LargeImageCard(card)
                is FeedCard.Video -> VideoCard(card)
            }
        }
    }
    // 底部加载更多指示器
    if (lazyPagingItems.loadState.append is LoadState.Loading) { ... }
}
```

---

## 二、代码结构总览

```
com.example.toutiao/
│
├── MainActivity.kt                    # 入口，设置 HomeScreen + Theme
├── ToutiaoApplication.kt              # Application，启用 Timber
│
├── domain/                            # 【Domain 层】纯 Kotlin，不依赖 Android
│   ├── model/
│   │   └── FeedCard.kt                # 密封类：4 种卡片 Domain Model
│   └── repository/
│       └── NewsRepository.kt          # 仓库接口
│
├── data/                              # 【Data 层】实现 Domain 接口
│   ├── remote/
│   │   ├── api/NewsApi.kt             # Retrofit 接口（真实 API 时使用）
│   │   ├── dto/NewsFeedResponse.kt    # Kotlinx Serialization DTO
│   │   ├── datasource/
│   │   │   ├── RemoteDataSource.kt    # 远程数据源接口
│   │   │   ├── MockDataSource.kt      # Mock 实现（当前在用）
│   │   │   ├── RealRemoteDataSource.kt# 真实网络实现
│   │   │   └── DebugControls.kt       # 调试开关（延迟/错误）
│   │   └── mediator/
│   │       └── NewsRemoteMediator.kt  # Paging3 远程中介器
│   ├── local/
│   │   ├── entity/FeedItemEntity.kt   # Room 实体
│   │   ├── entity/RemoteKeyEntity.kt  # 分页键实体
│   │   ├── dao/FeedDao.kt             # Feed 数据访问对象
│   │   ├── dao/RemoteKeyDao.kt        # 分页键 DAO
│   │   └── database/AppDatabase.kt    # Room 数据库定义
│   ├── mapper/NewsMapper.kt           # DTO ↔ Entity ↔ Domain 转换
│   └── repository/NewsRepositoryImpl.kt # 仓库实现
│
├── presentation/                      # 【Presentation 层】UI + 状态管理
│   └── home/
│       ├── HomeScreen.kt              # 首页 Compose Screen
│       ├── HomeViewModel.kt           # MVI ViewModel
│       ├── HomeUiState.kt             # UI 状态密封类
│       ├── HomeUiEvent.kt             # UI 事件密封类
│       └── components/                # 卡片组件
│           ├── TextTopCard.kt
│           ├── LeftTextRightImageCard.kt
│           ├── LargeImageCard.kt
│           ├── VideoCard.kt
│           └── BottomInfoRow.kt
│
├── di/                                # 【依赖注入】Hilt Module
│   ├── NetworkModule.kt               # OkHttp / Retrofit
│   ├── DatabaseModule.kt              # Room Database
│   ├── DataSourceModule.kt            # RemoteDataSource 绑定（Mock/真实）
│   └── RepositoryModule.kt            # NewsRepository 绑定
│
└── ui/theme/                          # Material3 主题
    ├── Color.kt
    ├── Theme.kt
    └── Type.kt
```

---

## 三、实战任务（分步完成）

> **目标**：让底部 5 个导航 Tab 都能切换到独立页面，每个页面都有基本 UI 和 Mock 数据。
>
> **当前状态**：底部导航只有 `selectedBottomNav == 4`（我的）有独立页面，其余 0~3 都显示首页信息流。

---

### ✅ 任务 0：先跑通编译，观察现有行为

1. 执行 `./gradlew assembleDebug`，确保当前代码编译通过。
2. 在 Android Studio 中打开 `HomeScreen.kt` 的 Preview 面板，查看 `HomeScreenSuccessPreview`。
3. 真机/模拟器运行 App，操作以下步骤并观察日志（logcat 过滤 `HomeViewModel` 和 `MockDataSource`）：
   - 下拉刷新 → 观察 `NewsRemoteMediator.load — loadType=REFRESH`
   - 滑动到底部 → 观察 `loadType=APPEND`
   - 切换 Tab → 观察 `feedPagingData — switching to tab=hot`
   - 点击调试图标 → 开启「模拟网络错误」→ 下拉刷新 → 观察 Error UI

---

### 📝 任务 1：提取底部导航路由，建立页面切换框架

**目标**：让 `MainActivity` 持有当前页面状态，底部导航切换时显示不同页面，而不是把所有逻辑堆在 `HomeScreen`。

**步骤**：
1. 在 `MainActivity.kt` 中新增 `remember { mutableIntStateOf(0) }` 记录当前选中页面。
2. 修改 `MainActivity` 的 `setContent`：
   ```kotlin
   when (selectedPage) {
       0 -> HomeScreen(viewModel = homeViewModel)
       1 -> VideoScreen()      // 新建
       2 -> SearchScreen()     // 新建
       3 -> TaskScreen()       // 新建
       4 -> ProfileScreen()    // 把现有 ProfileNotLoggedIn 提取成独立 Screen
   }
   ```
3. 将底部导航栏从 `HomeScreen` 中提取出来，放到 `MainActivity` 或一个独立的 `AppScaffold` 中，使其在所有页面底部常驻（或按需显示）。
4. 确保 `HomeScreen` 不再包含底部导航和「我的」页面逻辑。

**提示**：
- 参考 `AGENTS.md` 的「改 UI 状态或交互，要联动这几处」检查清单。
- 不同页面需要不同的 ViewModel，暂时只有 `HomeViewModel` 是完整的，其余页面可以先写死 Mock 数据。

---

### 📝 任务 2：新建「视频」页面（VideoScreen）

**目标**：底部导航第 2 个 Tab（"视频"）切换到独立的视频聚合页。

**步骤**：
1. 新建文件：
   ```
   app/src/main/java/com/example/toutiao/presentation/video/VideoScreen.kt
   app/src/main/java/com/example/toutiao/presentation/video/VideoUiState.kt
   app/src/main/java/com/example/toutiao/presentation/video/VideoViewModel.kt
   ```
2. `VideoScreen` 使用 `LazyColumn` 展示视频列表，数据直接使用 `MockDataSource` 的 `videoItems(0)`（或通过 Repository 拿）。
3. 每行使用现有的 `VideoCard` 组件渲染。
4. 页面顶部加一个标题栏："视频"，白色背景，红色标题。
5. 在 Preview 中写死 3 条视频 Mock 数据，确保可预览。

**Mock 数据建议**（直接复用 `MockDataSource.videoItems(0).take(3)` 的数据结构即可）。

---

### 📝 任务 3：新建「搜索」独立页面（SearchScreen）

**目标**：底部导航第 3 个 Tab（"搜索"）切换到独立的搜索页。

> 注意：首页顶部已有搜索栏交互。本任务是把「搜索」做成一个独立页面，与首页顶栏搜索并存。

**步骤**：
1. 新建文件：
   ```
   app/src/main/java/com/example/toutiao/presentation/search/SearchScreen.kt
   app/src/main/java/com/example/toutiao/presentation/search/SearchViewModel.kt
   app/src/main/java/com/example/toutiao/presentation/search/SearchUiState.kt
   ```
2. 页面设计：
   - 顶部：全宽搜索输入框（带返回按钮）
   - 中部：热门搜索标签（如 "华为", "AI", "高考", "房价"）
   - 下部：搜索历史列表（用 `DataStore` 或内存模拟）
   - 提交搜索后：下方展示搜索结果列表（复用 4 种卡片组件）
3. Mock 数据：点击热门标签或提交搜索时，调用 `NewsRepository.searchNews(query)` 返回 Mock 结果。

---

### 📝 任务 4：新建「任务」页面（TaskScreen）

**目标**：底部导航第 4 个 Tab（"任务"）切换到任务中心页。

**步骤**：
1. 新建文件：
   ```
   app/src/main/java/com/example/toutiao/presentation/task/TaskScreen.kt
   app/src/main/java/com/example/toutiao/presentation/task/TaskViewModel.kt
   ```
2. 页面设计（参考头条任务中心风格）：
   - 顶部：金币/积分数字大字展示
   - 中部：任务列表（每日签到、阅读 10 篇新闻、分享 1 次、观看 3 个视频）
   - 每个任务项：左侧图标 + 中间标题和奖励 + 右侧「去完成」/「已领取」按钮
3. 所有数据为 Mock，用 `remember { mutableStateListOf(...) }` 管理任务完成状态。
4. 提供 Preview。

---

### 📝 任务 5：完善「我的」页面（ProfileScreen）

**目标**：把 `HomeScreen` 中内联的 `ProfileNotLoggedIn` 提取为独立 Screen，并增加更多菜单项。

**步骤**：
1. 新建文件：
   ```
   app/src/main/java/com/example/toutiao/presentation/profile/ProfileScreen.kt
   ```
2. 把现有 `ProfileNotLoggedIn` 的逻辑迁移过来，保持未登录状态 UI。
3. 增加菜单项：
   - 消息通知
   - 我的关注
   - 浏览历史（已有）
   - 我的收藏（已有）
   - 夜间模式开关（纯 UI，不保存状态）
   - 设置（已有）
4. 提供 Preview。

---

### 📝 任务 6：为各页面注入 ViewModel 并统一主题

**目标**：让每个页面都能通过 Hilt 获取 ViewModel（或至少结构正确），且都在 `ToutiaoFeedDemoTheme` 下渲染。

**步骤**：
1. 为 `VideoViewModel`、`SearchViewModel`、`TaskViewModel` 添加 `@HiltViewModel` 注解和必要的构造函数注入。
2. 如果某些页面暂时不需要 Repository，构造函数可为空，但要保留注解。
3. 在 `MainActivity` 中：
   ```kotlin
   val videoViewModel: VideoViewModel by viewModels()
   val searchViewModel: SearchViewModel by viewModels()
   val taskViewModel: TaskViewModel by viewModels()
   ```
4. 确保所有 Screen 的背景色统一使用 `Color(0xFFF5F5F5)`。

---

### 📝 任务 7：验证全链路 Mock 数据

**目标**：确保每个页面的数据都来自 Mock，且能在无网络环境下正常展示。

**验证清单**：
- [ ] 首页：4 个 Tab 切换正常，下拉刷新和加载更多正常，数据来自 `MockDataSource`
- [ ] 视频页：展示视频频道内容，数据来自 `MockDataSource.videoItems()`
- [ ] 搜索页：热门标签点击后展示 Mock 搜索结果，数据来自 `NewsRepository.searchNews()`
- [ ] 任务页：任务列表为本地 Mock 状态，点击「去完成」按钮状态变化
- [ ] 我的页：纯 UI，无网络依赖
- [ ] 断网环境下所有页面都能正常显示（证明数据不依赖真实网络）

---

## 四、修改代码理解流程建议

按以下顺序修改并观察效果，可最大化理解数据流：

1. **改 Mock 数据**：修改 `MockDataSource.recommendItems(0)` 中第一条新闻的标题 → 编译运行 → 观察首页第一条卡片变化。
2. **改 Mapper 映射**：在 `NewsMapper.kt` 中把 `"text_top"` 的映射改成生成 `LeftTextRightImage` → 观察置顶卡片样式变化（理解 Mapper 作用）。
3. **改分页逻辑**：把 `MockDataSource` 中的 `hasMore = page < 2` 改成 `page < 5` → 观察加载更多能加载到第几页。
4. **改 UI 状态**：在 `HomeViewModel` 的 `init` 中手动 `_uiState.value = HomeUiState.Error("测试错误")` → 观察 Error 预览和真机效果。
5. **加日志**：在 `NewsRemoteMediator.load()`、`FeedDao.insertAll()`、`HomeScreen.PagingFeedList()` 中打 `Timber.d()` → 运行时查看 logcat，理解调用顺序。

---

## 五、常见问题速查

| 问题 | 原因 | 解决 |
|------|------|------|
| Tab 切换后数据没变 | `flatMapLatest` 已切换流，但 Room 缓存了旧数据 | 这是正常行为，RemoteMediator 会在 REFRESH 时清空旧数据 |
| 下拉刷新没反应 | `PullToRefreshBox` 的 `onRefresh` 调用了 `lazyPagingItems.refresh()` | 检查是否开启了模拟错误（DebugControls） |
| 图片加载不出来 | picsum.photos 偶尔抽风或网络问题 | 换其他占位图服务，或下载到本地 drawable |
| 底部加载更多不触发 | PagingConfig 的 `prefetchDistance` 不够 | 默认是 2，已足够；检查是否已到 `endOfPaginationReached` |
| 编译报错 `kspDebugKotlin` | Hilt / Room 的 KSP 生成失败 | 检查 `@Entity`、`@Dao`、`@Database` 注解是否正确；检查构造函数注入参数 |

---

> 完成以上 7 个任务后，你将完整掌握：MockDataSource → Repository → Paging3 → ViewModel → Compose UI 的全链路数据流，以及多页面 Compose 导航的组织和实现。
