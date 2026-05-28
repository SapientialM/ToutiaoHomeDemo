# Mock 数据到界面显示逻辑解析与实战任务

> 本文档用于帮助初学者快速理解 `ToutiaoFeedDemo` 的 Mock 数据流，并通过动手修改代码来掌握项目结构。阅读完本文档后，请按文末的「实战任务」逐步完成。
>
> **前置要求**：有 Java 基础，了解 Android 基本概念（Activity、RecyclerView 思路即可）。

---

## 一、Mock 数据到界面显示的完整逻辑流程

本项目采用 **MVI + Clean Architecture + Paging3** 架构，数据流向是单向的：

```
assets/news_data.json (1421 条真实新闻)
    ↓
MockDataSource (按频道过滤 + 分页 + 类型推断)
    ↓
NewsRemoteMediator (REFRESH 时清空旧数据 → 写入 Room)
    ↓
Room 数据库 (feed_items 表 + remote_keys 表)
    ↓
FeedDao.getFeedPagingSource() (PagingSource 监听 Room 变化)
    ↓
Pager.flow → Repository → ViewModel → Compose UI
```

以下按调用顺序逐层解析。

---

### 第 1 层：Mock 数据生成（Data 层）

**功能解释**：
`MockDataSource` 不请求真实网络，而是从 `assets/news_data.json` 加载 1421 条真实新闻数据，按 `channel` 过滤、按时间倒序排列，再按 `page` 分页返回。支持模拟网络延迟和模拟错误。

**代码位置**：
```
app/src/main/java/com/example/toutiao/data/remote/datasource/MockDataSource.kt
```

**关键流程**：
```kotlin
class MockDataSource(context: Context) : RemoteDataSource {

    // 首次调用时从 assets 加载 JSON，缓存在内存中
    private val allItems: List<RawNewsItem> by lazy {
        loadFromAssets(context)
    }

    override suspend fun getNewsFeed(channel: String, page: Int, size: Int): NewsFeedResponse {
        // 1. 模拟延迟（DebugControls 控制）
        delay(DebugControls.networkDelayMs)
        // 2. 模拟错误（DebugControls 控制）
        if (DebugControls.shouldSimulateError) throw IOException("模拟网络错误")
        // 3. 按频道过滤（recommend=全量, hot=社会/财经/科技..., video=视频, society=社会/法治...）
        val filtered = filterByChannel(allItems, channel)
        // 4. 按时间倒序排列
        val sorted = filtered.sortedByDescending { parseDatetime(it.datetime) }
        // 5. 分页截取（page=0 取前 8 条，page=1 取第 9~16 条...）
        val offset = page * size
        val pageItems = sorted.drop(offset).take(size)
        val hasMore = (offset + size) < sorted.size
        // 6. 推断卡片类型并转为 DTO
        val dtoList = pageItems.mapIndexed { index, raw ->
            mapToDto(raw, channel, offset + index)
        }
        return NewsFeedResponse(code = 0, data = NewsFeedData(list = dtoList, hasMore = hasMore))
    }
}
```

**学习要点**：
- `recommend` 频道不过滤，返回全部 1421 条；其他频道按 `category` 字段过滤。
- `page=0` 返回前 8 条，`hasMore` 根据实际数据量动态计算，不是写死的。
- `mapToDto()` 中的 `determineType()` 决定每条新闻最终渲染成哪种卡片：
  - `category == "视频"` → `video`
  - `imageUrl` 为空 → `text_top`
  - 有图且 `index % 3 == 0` → `large_image`
  - 有图且 `index % 3 != 0` → `left_text_right_image`
- `DebugControls` 是全局单例，控制延迟和错误开关，可在 UI 调试面板（顶部栏右侧扳手图标）中实时切换。

---

### 第 2 层：远程数据源接口与 DI 绑定

**功能解释**：
`RemoteDataSource` 是接口，`MockDataSource` 是实现。通过 Hilt 模块注入，使 Repository 不直接依赖具体实现，方便后续替换为真实 API。

**代码位置**：
```
app/src/main/java/com/example/toutiao/data/remote/datasource/RemoteDataSource.kt   (接口)
app/src/main/java/com/example/toutiao/data/remote/datasource/MockDataSource.kt       (实现)
app/src/main/java/com/example/toutiao/di/DataSourceModule.kt                         (DI 绑定)
```

**关键代码**（`DataSourceModule.kt`）：
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object DataSourceModule {

    @Provides
    @Singleton
    fun provideRemoteDataSource(@ApplicationContext context: Context): RemoteDataSource =
        MockDataSource(context)
}
```

**学习要点**：
- 如需切换真实 API，只需修改 `DataSourceModule` 的最后一行：`= MockDataSource(context)` → `= RealRemoteDataSource(api)`，业务代码零改动。
- Hilt 会自动把 `RemoteDataSource` 实例注入到 `NewsRepositoryImpl` 的构造函数中。

---

### 第 3 层：RemoteMediator 分页调度

**功能解释**：
`NewsRemoteMediator` 是 Paging3 的核心组件，负责决定何时加载下一页数据。它有三种 `LoadType`：
- `REFRESH`：首次加载 / 下拉刷新 / Tab 切换，清空旧数据，从 page=0 开始
- `APPEND`：滑动到底部，加载下一页
- `PREPEND`：向前加载（本项目基本不用）

**代码位置**：
```
app/src/main/java/com/example/toutiao/data/remote/mediator/NewsRemoteMediator.kt
```

**关键流程**（`load` 方法）：
```kotlin
override suspend fun load(loadType: LoadType, state: PagingState<Int, FeedItemEntity>): MediatorResult {
    // 1. 根据 loadType 计算当前 page
    val page = when (loadType) {
        LoadType.REFRESH -> {
            val remoteKey = getRemoteKeyClosestToCurrentPosition(state)
            remoteKey?.nextKey?.minus(1) ?: 0
        }
        LoadType.APPEND -> {
            val remoteKey = getRemoteKeyForLastItem(state)
            val nextKey = remoteKey?.nextKey
                ?: return MediatorResult.Success(endOfPaginationReached = remoteKey != null)
            nextKey
        }
        LoadType.PREPEND -> { ... }
    }

    // 2. 请求数据（Mock 或真实 API）
    val response = remoteDataSource.getNewsFeed(channel, page)

    // 3. REFRESH 时清空该 channel 的旧数据
    if (loadType == LoadType.REFRESH) {
        feedDao.deleteByChannel(channel)
        remoteKeyDao.deleteByChannel(channel)
    }

    // 4. DTO → Entity → 写入 Room
    val entities = items.map { it.toEntity(channel) }
    feedDao.insertAll(entities)
    remoteKeyDao.insertAll(keys)

    // 5. 返回是否还有更多数据
    return MediatorResult.Success(endOfPaginationReached = !response.data.hasMore)
}
```

**学习要点**：
- `remote_keys` 表保存了每条数据的 `prevKey` 和 `nextKey`，用于追踪分页位置。
- `REFRESH` 时会先 `deleteByChannel(channel)` 再 `insertAll`，确保 Tab 切换后旧数据不会残留。
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
@Dao
interface FeedDao {
    @Query("SELECT * FROM feed_items WHERE channel = :channel ORDER BY created_at DESC")
    fun getFeedPagingSource(channel: String): PagingSource<Int, FeedItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<FeedItemEntity>)

    @Query("DELETE FROM feed_items WHERE channel = :channel")
    suspend fun deleteByChannel(channel: String)
}
```

**学习要点**：
- `PagingSource<Int, FeedItemEntity>` 是 Paging3 与 Room 的桥梁，Room 数据变化会自动触发 UI 刷新。
- `channel` 字段带了 `@Index`，不同 Tab 数据互不干扰。
- `created_at` 默认值为 `System.currentTimeMillis()`，用于排序。

---

### 第 5 层：DTO / Entity / Domain Model 转换

**功能解释**：
三层数据模型隔离：
- **DTO**（`NewsItemDto`）：网络层数据结构，用 `@Serializable` 和 `@SerialName` 标注
- **Entity**（`FeedItemEntity`）：Room 数据库表结构，用 `@Entity` 和 `@ColumnInfo` 标注
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
fun NewsItemDto.toEntity(channel: String): FeedItemEntity = FeedItemEntity(
    id = id,
    type = type,
    title = title,
    source = source,
    commentCount = commentCount,
    imageUrl = imageUrl,
    videoUrl = videoUrl,
    duration = duration,
    publishTime = publishTime,
    isTop = isTop,
    channel = channel,
)

// Entity → Domain（UI 展示前）
fun FeedItemEntity.toDomain(): FeedCard = when (type) {
    "text_top" -> FeedCard.TextTop(...)
    "left_text_right_image" -> FeedCard.LeftTextRightImage(...)
    "large_image" -> FeedCard.LargeImage(...)
    "video" -> FeedCard.Video(...)
    else -> FeedCard.LeftTextRightImage(...) // 兜底
}
```

**FeedCard 密封类**：
```kotlin
@Immutable
sealed class FeedCard {
    abstract val id: String
    abstract val title: String
    abstract val source: String
    abstract val commentCount: Int
    abstract val publishTime: String

    data class TextTop(..., val isTop: Boolean = true) : FeedCard()
    data class LeftTextRightImage(..., val imageUrl: String) : FeedCard()
    data class LargeImage(..., val imageUrl: String) : FeedCard()
    data class Video(..., val imageUrl: String, val videoUrl: String, val duration: String) : FeedCard()
}
```

**学习要点**：
- `type` 字段（"text_top"/"left_text_right_image"/"large_image"/"video"）决定 Mapper 生成哪种 `FeedCard`。
- 密封类 + `when` 表达式是类型安全的多卡片渲染核心。
- 为什么分三层？DTO 和 Entity 都带框架注解（Serialization/Room），不宜直接传到 UI；Domain 是纯 Kotlin 类，UI 层只依赖它。

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
        config = PagingConfig(
            pageSize = 8,               // 每页 8 条
            prefetchDistance = 2,       // 距离底部 2 条时触发下一页
            enablePlaceholders = false, // 不显示占位骨架屏
        ),
        remoteMediator = NewsRemoteMediator(
            channel = channel,
            remoteDataSource = remoteDataSource,
            feedDao = feedDao,
            remoteKeyDao = remoteKeyDao,
        ),
        pagingSourceFactory = { feedDao.getFeedPagingSource(channel) },
    ).flow.map { pagingData ->
        pagingData.map { it.toDomain() } // Entity → Domain 映射
    }
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
// 当前 Tab，默认"推荐"
private val _currentTab = MutableStateFlow("recommend")

// 核心数据管道：Tab 变化 → 取消旧流 → 创建新 Pager → 新数据
val feedPagingData: Flow<PagingData<FeedCard>> = _currentTab
    .flatMapLatest { tab ->
        newsRepository.getFeedPagingData(tab)
    }
    .cachedIn(viewModelScope)   // 在 ViewModel 存活期间缓存数据

fun onEvent(event: HomeUiEvent) {
    when (event) {
        is HomeUiEvent.OnTabSelected -> switchTab(event.tab)
        is HomeUiEvent.OnRefresh -> { /* UI 层调用 lazyPagingItems.refresh() */ }
        is HomeUiEvent.OnCardClick -> Timber.d("Card clicked: ${event.cardId}")
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
```

**列表渲染关键代码**：
```kotlin
val refreshLoadState = lazyPagingItems.loadState.refresh
val isInitialLoading = refreshLoadState is LoadState.Loading && lazyPagingItems.itemCount == 0
val isEmpty = refreshLoadState is LoadState.NotLoading && lazyPagingItems.itemCount == 0
val isError = refreshLoadState is LoadState.Error && lazyPagingItems.itemCount == 0

when {
    isInitialLoading -> { /* 显示转圈 */ }
    isError -> { /* 显示错误 + 重试按钮 */ }
    isEmpty -> { /* 显示"暂无内容" */ }
    else -> {
        PullToRefreshBox(...) {
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
            }
        }
    }
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
│           └── VideoCard.kt
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

> **目标**：通过 7 个由浅入深的动手任务，让你从"改一条数据"到"新建一个页面"，完整掌握 MockDataSource → Repository → Paging3 → ViewModel → Compose UI 的全链路。
>
> **建议节奏**：每完成一个任务，编译运行一次（`./gradlew assembleDebug`），确认效果后再进入下一个。

---

### ✅ 任务 0：先跑通编译，观察现有行为

**目标**：熟悉当前代码的运行效果，建立基线。

**步骤**：

1. **编译验证**：在 Android Studio 的 Terminal 中执行：
   ```bash
   ./gradlew assembleDebug
   ```
   确保出现 `BUILD SUCCESSFUL`。

2. **查看 Preview**：在 Android Studio 中打开 `HomeScreen.kt`，找到文件末尾的 `@Preview` 函数，点击 Split/Design 按钮查看预览效果：
   - `HomeScreenSuccessPreview` —— 正常列表
   - `HomeScreenLoadingPreview` —— 加载状态
   - `HomeScreenErrorPreview` —— 错误状态
   - `HomeScreenEmptyPreview` —— 空数据状态

3. **真机/模拟器运行**，打开 logcat，过滤关键字 `Toutiao`，依次操作并观察日志：
   - **首次进入首页** → 观察 `NewsRemoteMediator.load — loadType=REFRESH, channel=recommend, page=0`
   - **滑动到底部** → 观察 `loadType=APPEND, page=1`
   - **切换到"热榜"Tab** → 观察 `feedPagingData — switching to tab=hot`
   - **点击顶部搜索栏右侧的「扳手」图标** → 勾选「模拟网络错误」→ 下拉刷新 → 观察是否显示 Error 界面

4. **打开调试面板（扳手图标）**，把延迟调到 2 秒，重新进入首页，感受 RemoteMediator 加载数据前的空白状态（这是我们之前讨论过的 Paging3 状态判断问题）。

---

### 📝 任务 1：改 Mock 数据，观察首页变化

**目标**：理解**数据源头**在哪里改，以及改动如何一路传递到 UI。

**涉及文件**：
```
app/src/main/java/com/example/toutiao/data/remote/datasource/MockDataSource.kt
```

**步骤**：

1. 打开 `MockDataSource.kt`，找到 `generateCommentCount` 函数（约第 181 行）：
   ```kotlin
   private fun generateCommentCount(category: String, index: Int): Int {
       val base = when (category) {
           "娱乐" -> 30000; "体育" -> 25000; "科技" -> 15000; "社会" -> 12000
           "财经" -> 10000; "国际" -> 9000; "国内" -> 8000; "NBA" -> 20000
           "教育" -> 5000; "健康" -> 4000
           else -> 3000
       }
       return base + (index * 137 % 9000)
   }
   ```

2. **修改评论数算法**：把最后一行改成：
   ```kotlin
   return base + (index * 137 % 9000) + 100000
   ```
   这会让每条新闻的评论数都增加 10 万。

3. **重新编译运行**：
   ```bash
   ./gradlew assembleDebug
   ```

4. **验证**：进入首页，观察每条卡片底部的评论数字是否都变得很大（比如 13 万、15 万）。

**进阶尝试（可选）**：
- 把 `"娱乐" -> 30000` 改成 `"娱乐" -> 999999`，观察娱乐类新闻的评论数是否突出显示。
- 把 `formatRelativeTime` 中的 `val now = LocalDateTime.of(2026, 5, 25, 23, 59)` 改为你今天的日期，观察时间显示变化。

**思考**：为什么改 `MockDataSource` 就能改变 UI 显示？数据经过了哪些层？

---

### 📝 任务 2：修改 Mapper，改变卡片渲染类型

**目标**：理解**类型推断层**（`determineType`）如何决定一条新闻最终长什么样。

**涉及文件**：
```
app/src/main/java/com/example/toutiao/data/remote/datasource/MockDataSource.kt
```

**步骤**：

1. 打开 `MockDataSource.kt`，找到 `determineType` 函数（约第 145 行）：
   ```kotlin
   private fun determineType(category: String, imageUrl: String?, index: Int): String {
       if (category == "视频") return "video"
       if (imageUrl.isNullOrBlank()) return "text_top"
       return if (index % 3 == 0) "large_image" else "left_text_right_image"
   }
   ```

2. **修改分配规则**：把 `index % 3 == 0` 改成 `index % 2 == 0`：
   ```kotlin
   return if (index % 2 == 0) "large_image" else "left_text_right_image"
   ```

3. **重新编译运行**。

4. **验证**：进入首页，观察大图卡片（`LargeImageCard`）的数量是否变多了（原来每 3 条出现 1 张，现在每 2 条就出现 1 张）。

**进阶尝试（可选）**：
- 把 `if (imageUrl.isNullOrBlank()) return "text_top"` 注释掉，让所有有图的新闻都走后面的分支，观察原来纯文字的卡片是否也变成带图的了。
- 打开 `NewsMapper.kt`，在 `toDomain()` 的 `when(type)` 中，把 `"text_top"` 分支的返回改成 `FeedCard.LeftTextRightImage(...)`，观察置顶卡片是否变成了左文右图样式。

**思考**：`MockDataSource` 中的 `type` 和 `NewsMapper` 中的 `when(type)` 是什么关系？如果两边不一致会发生什么？

---

### 📝 任务 3：新增一个 Tab 频道

**目标**：理解 **channel 过滤** 和 **UI 联动**，掌握从数据源到 Tab 的全链路修改。

**涉及文件**：
```
app/src/main/java/com/example/toutiao/presentation/home/HomeScreen.kt
app/src/main/java/com/example/toutiao/data/remote/datasource/MockDataSource.kt
```

**步骤**：

1. **打开 `HomeScreen.kt`**，找到 `HomeTopBar` 中的 tabs 定义（约第 328 行）：
   ```kotlin
   val tabs = listOf("recommend" to "推荐", "hot" to "热榜", "video" to "视频", "society" to "社会")
   ```

2. **新增一个 Tab**：在 list 末尾添加 `"tech" to "科技"`：
   ```kotlin
   val tabs = listOf(
       "recommend" to "推荐",
       "hot" to "热榜",
       "video" to "视频",
       "society" to "社会",
       "tech" to "科技"   // ← 新增
   )
   ```

3. **打开 `MockDataSource.kt`**，找到 `filterByChannel` 函数（约第 104 行）：
   ```kotlin
   private fun filterByChannel(items: List<RawNewsItem>, channel: String): List<RawNewsItem> {
       val categories = when (channel) {
           "recommend" -> null
           "hot" -> setOf("社会", "财经", "科技", "娱乐", "体育", "国际", "国内", "军事", "NBA", "中超", "英超")
           "video" -> setOf("视频")
           "society" -> setOf("社会", "法治", "法律", "时政", "国内", "中国", "地方", "教育", "健康", "环境", "环保")
           else -> null
       }
       return if (categories == null) items else items.filter { it.category in categories }
   }
   ```

4. **为新 Tab 添加过滤规则**：在 `when` 分支中添加：
   ```kotlin
   "tech" -> setOf("科技", "互联网", "数码", "AI", "人工智能")
   ```

5. **重新编译运行**。

6. **验证**：
   - 首页顶部 TabRow 是否出现了"科技"Tab
   - 点击"科技"Tab，观察 logcat 是否有 `switching to tab=tech`
   - 列表中展示的新闻是否都是科技相关（标题里通常有"科技""AI"等字样）

**思考**：为什么 `MockDataSource.filterByChannel` 和 `HomeScreen` 的 tabs 需要同步修改？如果只改 UI 不改数据源会怎样？

---

### 📝 任务 4：模拟网络延迟和错误，观察 UI 状态

**目标**：理解 Paging3 的 **Loading / Error / Empty / Success** 四种状态，以及 `DebugControls` 的工作原理。

**涉及文件**：
```
app/src/main/java/com/example/toutiao/data/remote/datasource/DebugControls.kt
app/src/main/java/com/example/toutiao/presentation/home/HomeScreen.kt
```

**步骤**：

1. **不修改代码，先用 UI 调试面板体验**：
   - 运行 App，点击顶部栏右侧的「扳手」图标
   - 选择「2s」延迟 → 下拉刷新 → 感受 2 秒加载时间
   - 勾选「模拟网络错误」→ 下拉刷新 → 观察是否出现错误界面（带"重试"按钮）
   - 点击「重置所有调试选项」恢复

2. **代码层面修改延迟默认值**：打开 `DebugControls.kt`，把默认值从 0 改为 1500：
   ```kotlin
   @Volatile var networkDelayMs: Long = 1500L   // ← 原来是 0L
   ```

3. **重新编译运行**，不打开调试面板，直接观察首页首次进入时是否有 1.5 秒的加载延迟。

4. **改回 0L**，恢复默认。

5. **（可选）观察 Paging3 的 Mediator 加载状态**：
   打开 `HomeScreen.kt`，找到 `PagingFeedList` 函数（约第 208 行），在 `when` 分支的 `else ->` 之前，添加一个临时状态显示：
   ```kotlin
   // 临时添加：观察 Mediator 是否在加载
   val mediatorLoading = lazyPagingItems.loadState.mediator?.refresh is LoadState.Loading
   if (mediatorLoading && lazyPagingItems.itemCount == 0) {
       Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
           Column(horizontalAlignment = Alignment.CenterHorizontally) {
               CircularProgressIndicator(color = Color(0xFFD81E06))
               Spacer(Modifier.height(8.dp))
               Text("正在加载新闻...", color = Color.Gray)
           }
       }
   }
   ```
   编译运行，观察首次进入时是否显示"正在加载新闻..."而不是"暂无内容"。

**思考**：`loadState.refresh` 和 `loadState.mediator?.refresh` 有什么区别？为什么我们之前讨论过"首次打开可能显示空白"的问题？

---

### 📝 任务 5：把底部导航从 HomeScreen 提取到 MainActivity

**目标**：理解**页面导航架构**，让底部导航独立于首页，为后续新建页面打基础。

**涉及文件**：
```
app/src/main/java/com/example/toutiao/MainActivity.kt
app/src/main/java/com/example/toutiao/presentation/home/HomeScreen.kt
```

**步骤**：

1. **打开 `HomeScreen.kt`**，找到 `HomeScreenContent` 的 `Scaffold` 调用（约第 146 行）。当前 `Scaffold` 的 `bottomBar` 参数里包了一个 `HomeBottomNav`。我们要把底部导航从 `HomeScreen` 中删掉。

2. **修改 `HomeScreenContent` 的签名**：去掉 `selectedBottomNav` 和 `onBottomNavSelected` 参数：
   ```kotlin
   @Composable
   private fun HomeScreenContent(
       uiState: HomeUiState,
       currentTab: String,
       searchQuery: String,
       searchResults: List<FeedCard>,
       lazyPagingItems: LazyPagingItems<FeedCard>,
       showDebugDialog: Boolean,
       // selectedBottomNav: Int,           // ← 删除
       // onBottomNavSelected: (Int) -> Unit, // ← 删除
       onToggleDebug: () -> Unit,
       onEvent: (HomeUiEvent) -> Unit,
   ) {
   ```

3. **修改 `HomeScreenContent` 中的 `Scaffold`**：把 `bottomBar` 参数删掉（或者赋值为 `{}`）：
   ```kotlin
   Scaffold(
       topBar = { ... },
       // bottomBar = { ... },  // ← 删除这行
       containerColor = Color(0xFFF5F5F5),
   ) { innerPadding ->
       // 把原来的 when(selectedBottomNav) 逻辑删掉
       // 直接显示 PagingFeedList 或搜索列表
   }
   ```

4. **简化 `HomeScreenContent` 的内容体**：原来 `Scaffold` 的 `innerPadding` 里有一个 `when(selectedBottomNav)` 判断，现在底部导航已经移走了，`selectedBottomNav` 永远不可能等于 4（"我的"页面会另外处理）。所以把 `when` 简化掉：
   ```kotlin
   { innerPadding ->
       val isSearching = (uiState as? HomeUiState.Success)?.isSearching ?: false
       DebugDialog(showDialog = showDebugDialog, onDismiss = onToggleDebug)
   
       if (isSearching && searchQuery.isNotEmpty() && searchResults.isNotEmpty()) {
           SearchResultList(...)
       } else if (isSearching && searchQuery.isNotEmpty() && searchResults.isEmpty()) {
           Box(...) { Text("点击搜索按钮查看结果") }
       } else {
           PagingFeedList(
               lazyPagingItems = lazyPagingItems,
               onCardClick = { onEvent(HomeUiEvent.OnCardClick(it)) },
               modifier = Modifier.padding(innerPadding),
           )
       }
   }
   ```

5. **修改 `HomeScreen` 函数的调用**：去掉传给 `HomeScreenContent` 的 `selectedBottomNav` 和 `onBottomNavSelected`：
   ```kotlin
   HomeScreenContent(
       uiState = uiState,
       currentTab = currentTab,
       searchQuery = searchQuery,
       searchResults = searchResults,
       lazyPagingItems = lazyPagingItems,
       showDebugDialog = showDebugDialog,
       // selectedBottomNav = selectedBottomNav,      // ← 删除
       // onBottomNavSelected = onBottomNavSelected,  // ← 删除
       onToggleDebug = { showDebugDialog = !showDebugDialog },
       // onBottomNavSelected = { selectedBottomNav = it }, // ← 删除
       onEvent = viewModel::onEvent,
   )
   ```

6. **修改 `HomeScreen` 函数本身**：去掉 `selectedBottomNav` 相关的 state：
   ```kotlin
   fun HomeScreen(viewModel: HomeViewModel) {
       val uiState by viewModel.uiState.collectAsStateWithLifecycle()
       val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
       val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
       val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
       val lazyPagingItems = viewModel.feedPagingData.collectAsLazyPagingItems()
       var showDebugDialog by remember { mutableStateOf(false) }
       // var selectedBottomNav by remember { mutableIntStateOf(0) } // ← 删除
       ...
   }
   ```

7. **打开 `MainActivity.kt`**，把底部导航和页面切换逻辑搬过来：
   ```kotlin
   @AndroidEntryPoint
   class MainActivity : ComponentActivity() {
   
       private val viewModel: HomeViewModel by viewModels()
   
       override fun onCreate(savedInstanceState: Bundle?) {
           super.onCreate(savedInstanceState)
           enableEdgeToEdge()
           setContent {
               ToutiaoFeedDemoTheme {
                   var selectedBottomNav by remember { mutableIntStateOf(0) }
   
                   Scaffold(
                       bottomBar = {
                           HomeBottomNav(
                               selectedIndex = selectedBottomNav,
                               onSelected = { selectedBottomNav = it },
                           )
                       },
                       containerColor = Color(0xFFF5F5F5),
                   ) { innerPadding ->
                       Box(modifier = Modifier.padding(innerPadding)) {
                           when (selectedBottomNav) {
                               0 -> HomeScreen(viewModel = viewModel)
                               1 -> VideoPlaceholderScreen()   // 占位页面，下一步再实现
                               2 -> SearchPlaceholderScreen()  // 占位页面
                               3 -> TaskPlaceholderScreen()    // 占位页面
                               4 -> ProfilePlaceholderScreen() // 占位页面
                           }
                       }
                   }
               }
           }
       }
   }
   ```
   注意：需要 import 相关的 Composable 和 state。

8. **创建占位页面**（在同一个 `MainActivity.kt` 文件的底部，或者新建一个文件）：
   ```kotlin
   @Composable
   fun VideoPlaceholderScreen() {
       Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
           Text("视频页 - 待实现", color = Color.Gray)
       }
   }
   
   @Composable
   fun SearchPlaceholderScreen() {
       Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
           Text("搜索页 - 待实现", color = Color.Gray)
       }
   }
   
   @Composable
   fun TaskPlaceholderScreen() {
       Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
           Text("任务页 - 待实现", color = Color.Gray)
       }
   }
   
   @Composable
   fun ProfilePlaceholderScreen() {
       Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
           Text("我的页 - 待实现", color = Color.Gray)
       }
   }
   ```

9. **把 `HomeBottomNav` 从 `HomeScreen.kt` 中移出**：
   - 在 `HomeScreen.kt` 中，把 `HomeBottomNav` 函数及其依赖的 `NavItem` data class 全部剪切。
   - 粘贴到 `MainActivity.kt` 的底部（或者新建一个文件 `AppBottomNav.kt`）。
   - 需要把 `HomeBottomNav` 和 `NavItem` 的 `private` 修饰符去掉，因为要在别的文件中调用。

10. **重新编译运行**。

11. **验证**：
    
    - 首页是否正常显示信息流？
    - 底部导航是否还在？
    - 点击底部导航的 1/2/3/4，是否显示对应的占位文字？
    - 首页的 Tab 切换、下拉刷新、搜索是否还能正常使用？

**思考**：为什么要把底部导航提取到 `MainActivity`？如果留在 `HomeScreen` 内部，新建其他页面时会出现什么问题？

---

### 📝 任务 6：新建「视频」独立页面并接入导航

**目标**：理解**从零新建一个 Compose 页面**的完整流程。

**涉及文件**：
```
app/src/main/java/com/example/toutiao/presentation/video/VideoScreen.kt   (新建)
app/src/main/java/com/example/toutiao/MainActivity.kt
```

**步骤**：

1. **新建目录**：在 `app/src/main/java/com/example/toutiao/presentation/` 下新建文件夹 `video/`。

2. **新建文件** `VideoScreen.kt`：
   ```kotlin
   package com.example.toutiao.presentation.video
   
   import androidx.compose.foundation.layout.*
   import androidx.compose.foundation.lazy.LazyColumn
   import androidx.compose.foundation.lazy.items
   import androidx.compose.material3.Scaffold
   import androidx.compose.material3.Text
   import androidx.compose.material3.TopAppBar
   import androidx.compose.runtime.Composable
   import androidx.compose.ui.Alignment
   import androidx.compose.ui.Modifier
   import androidx.compose.ui.graphics.Color
   import androidx.compose.ui.text.font.FontWeight
   import androidx.compose.ui.unit.dp
   import androidx.compose.ui.unit.sp
   import com.example.toutiao.domain.model.FeedCard
   import com.example.toutiao.presentation.home.components.VideoCard
   
   @Composable
   fun VideoScreen() {
       // 先写死 3 条 Mock 视频数据
       val videoList = listOf(
           FeedCard.Video(
               id = "v1",
               title = "猫咪玩水的 100 种姿势",
               source = "萌宠频道",
               commentCount = 3421,
               publishTime = "2小时前",
               imageUrl = "https://picsum.photos/seed/video1/800/450",
               videoUrl = "",
               duration = "03:45",
           ),
           FeedCard.Video(
               id = "v2",
               title = "深度解读：2026 年科技趋势",
               source = "科技日报",
               commentCount = 8902,
               publishTime = "5小时前",
               imageUrl = "https://picsum.photos/seed/video2/800/450",
               videoUrl = "",
               duration = "12:30",
           ),
           FeedCard.Video(
               id = "v3",
               title = "家常菜教学：红烧肉正宗做法",
               source = "美食台",
               commentCount = 1205,
               publishTime = "昨天",
               imageUrl = "https://picsum.photos/seed/video3/800/450",
               videoUrl = "",
               duration = "08:15",
           ),
       )
   
       Scaffold(
           topBar = {
               // 简单顶部栏：白色背景，红色标题
               Box(
                   modifier = Modifier
                       .fillMaxWidth()
                       .height(56.dp)
                       .padding(horizontal = 16.dp),
                   contentAlignment = Alignment.CenterStart,
               ) {
                   Text(
                       text = "视频",
                       fontSize = 20.sp,
                       fontWeight = FontWeight.Bold,
                       color = Color(0xFFD81E06),
                   )
               }
           },
           containerColor = Color(0xFFF5F5F5),
       ) { innerPadding ->
           LazyColumn(
               modifier = Modifier.padding(innerPadding),
               contentPadding = PaddingValues(vertical = 8.dp),
               verticalArrangement = Arrangement.spacedBy(8.dp),
           ) {
               items(videoList, key = { it.id }) { card ->
                   VideoCard(card = card)
               }
           }
       }
   }
   ```

3. **修改 `MainActivity.kt`**：把 `VideoPlaceholderScreen()` 替换为 `VideoScreen()`：
   ```kotlin
   import com.example.toutiao.presentation.video.VideoScreen
   
   // ...
   when (selectedBottomNav) {
       0 -> HomeScreen(viewModel = viewModel)
       1 -> VideoScreen()      // ← 替换
       2 -> SearchPlaceholderScreen()
       3 -> TaskPlaceholderScreen()
       4 -> ProfilePlaceholderScreen()
   }
   ```

4. **重新编译运行**。

5. **验证**：
   - 点击底部导航第 2 个 Tab（视频）
   - 是否显示红色标题"视频"？
   - 列表中是否有 3 条视频卡片？
   - 每条卡片是否有封面图、播放按钮、时长标签？

**进阶挑战（可选）**：
- 给 `VideoScreen` 添加 `@Preview`，在 Android Studio 预览面板中直接查看效果。
- 让 `VideoScreen` 从 `HomeViewModel` 获取真实的 `video` 频道数据（提示：可以调用 `viewModel.feedPagingData`，但需要换个 channel，或者新写一个获取视频列表的方法）。

---

### 📝 任务 7：全链路验证与总结

**目标**：回顾所有修改，确认每个环节的数据流转正常。

**验证清单**（逐条检查）：

- [x] **编译通过**：执行 `./gradlew assembleDebug`，`BUILD SUCCESSFUL`
- [x] **首页正常**：默认显示"推荐"Tab，有新闻列表，4 种卡片都能渲染出来
- [x] **Tab 切换**：推荐 → 热榜 → 视频 → 社会 → 科技（任务 3 新增），每个 Tab 内容不同
- [x] **分页加载**：滑到底部自动加载更多，logcat 出现 `loadType=APPEND`
- [x] **下拉刷新**：下拉时触发刷新，列表回到顶部
- [x] **Mock 数据生效**：任务 1 修改的评论数算法仍然生效（数字很大）
- [x] **Mapper 生效**：任务 2 修改的卡片分配规则仍然生效（大图卡片变多）
- [x] **错误模拟**：调试面板开启"模拟网络错误"后下拉刷新，显示错误界面和重试按钮
- [x] **底部导航切换**：0=首页、1=视频页、2=搜索占位、3=任务占位、4=我的占位，切换正常
- [x] **视频页正常**：底部导航"视频"Tab 显示独立的 VideoScreen，有 3 条写死的数据
- [x] **主题一致**：所有页面背景色都是 `Color(0xFFF5F5F5)`（浅灰色）

**思考与总结**（建议写下来）：
1. 数据从 `assets/news_data.json` 到屏幕，经过了几层转换？每层做了什么？
2. 如果要把 `MockDataSource` 换成真实网络 API，最少需要改几个文件？
3. Paging3 的 `RemoteMediator` 和 `PagingSource` 分别负责什么？
4. 为什么 UI 层（`HomeScreen`）不能直接调用 `MockDataSource`，必须通过 `ViewModel` → `Repository`？

---

## 四、修改代码理解流程建议

按以下顺序修改并观察效果，可最大化理解数据流：

1. **改 Mock 数据**：修改 `MockDataSource.generateCommentCount()` 中某个 `base` 值 → 编译运行 → 观察首页对应 category 卡片的评论数变化。
2. **改 Mapper 映射**：在 `MockDataSource.determineType()` 中修改 `index % 3` 为 `index % 5` → 观察大图卡片出现频率变化。
3. **改分页逻辑**：在 `NewsRepositoryImpl.getFeedPagingData()` 中把 `pageSize = 8` 改成 `pageSize = 4` → 观察每次加载的条数变少，触发加载更频繁。
4. **改 UI 状态**：在 `HomeViewModel.init` 中临时加一行 `Timber.d("ViewModel 创建了，当前 Tab: ${_currentTab.value}")` → 运行时查看 logcat，确认 ViewModel 生命周期。
5. **加日志**：在 `NewsRemoteMediator.load()` 开头和结尾分别打 `Timber.d()` → 运行时查看 logcat，理解 `REFRESH` 和 `APPEND` 的调用时机和顺序。
6. **改频道过滤**：在 `MockDataSource.filterByChannel()` 中给 `"hot"` 频道添加 `"科技"` → 观察"热榜"Tab 是否出现了科技新闻。

---

## 五、常见问题速查

| 问题 | 原因 | 解决 |
|------|------|------|
| Tab 切换后数据没变 | `flatMapLatest` 已切换流，但 Room 可能缓存了旧数据；RemoteMediator 会在 REFRESH 时清空旧数据并重新写入 | 这是正常行为，观察 logcat 确认 `loadType=REFRESH` 已触发 |
| 下拉刷新没反应 | `PullToRefreshBox` 的 `onRefresh` 调用了 `lazyPagingItems.refresh()`，但如果开启了模拟错误会失败 | 检查调试面板是否勾选了"模拟网络错误" |
| 图片加载不出来 | 网络问题或占位图服务不可用 | 换其他占位图 URL，或下载图片到本地 drawable |
| 底部加载更多不触发 | PagingConfig 的 `prefetchDistance` 不够，或已到最后一页 | 检查 logcat 的 `endOfPaginationReached` 是否为 true |
| 编译报错 `kspDebugKotlin` | Hilt / Room 的 KSP 生成失败 | 检查 `@Entity`、`@Dao`、`@Database` 注解是否正确；检查 Hilt 构造函数注入参数是否匹配 |
| 新增 Tab 后点击没反应 | `HomeScreen` 的 tabs list 和 `MockDataSource.filterByChannel` 的 channel 不匹配 | 确保两边同步添加 |
| 底部导航提取后首页空白 | `HomeScreenContent` 中的 `when(selectedBottomNav)` 逻辑没删掉，导致 `selectedBottomNav` 不再传入 | 按任务 5 步骤仔细检查是否删干净了 |

---

> 完成以上 7 个任务后，你将完整掌握：
> - MockDataSource → Repository → Paging3 → ViewModel → Compose UI 的全链路数据流
> - Paging3 的 RemoteMediator 分页机制
> - Room 缓存与离线展示原理
> - Compose 多页面导航的组织方式
> - MVI 单向数据流的事件传递方式
