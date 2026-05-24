# 从传统 Android 到现代开发的映射指南

> 如果你熟悉 2022 年及之前的 Android 开发（XML Layout、Activity 重逻辑、RecyclerView Adapter），但面对本项目感到陌生，这份文档就是为你写的。  
> 它将用你熟悉的概念作为「锚点」，带你快速定位本项目中的对应物，并理解背后的设计逻辑。

---

## 一、一句话总结：本项目到底用了什么？

本项目是一个 **Jetpack Compose + MVI + Clean Architecture** 的现代化 Android 项目。  
你可以把它理解为：

- **没有 XML 布局文件**，所有界面都用 Kotlin 代码「画」出来（声明式 UI）。
- **Activity 不再写业务逻辑**，它只是一个「入口」，负责把应用启动起来。
- **没有 RecyclerView.Adapter**，列表直接用 Compose 的 `LazyColumn` 渲染。
- **逻辑触发是单向的**：用户点击 → 发送事件(UiEvent) → ViewModel 处理 → 更新状态(UiState) → 界面自动刷新。

---

## 二、核心概念快速对照表

| 你熟悉的（传统开发） | 本项目中的对应物 | 文件位置示例 |
|---|---|---|
| `res/layout/xxx.xml` | `@Composable` 函数 | `presentation/home/HomeScreen.kt` |
| `Activity.onCreate()` + `setContentView()` | `ComponentActivity` + `setContent { ... }` | `MainActivity.kt` |
| `findViewById()` / ViewBinding | Compose 函数参数直接接收状态 | `HomeScreen(uiState = uiState)` |
| `RecyclerView` + `Adapter` + `ViewHolder` | `LazyColumn` + `items { ... }` | `HomeScreen.kt` 中的 `FeedList()` |
| `onClickListener` 直接改 UI | 发送 `UiEvent` 给 ViewModel | `HomeUiEvent.OnTabSelected(...)` |
| Activity/Fragment 里的业务逻辑 | `ViewModel` + `Repository` | `HomeViewModel.kt`, `NewsRepositoryImpl.kt` |
| `new Retrofit.Builder()` 单例 | Hilt `@Module` + `@Provides` | `di/NetworkModule.kt` |
| `SharedPreferences` | DataStore / Room | `data/local/` 目录 |

---

## 三、Layout（界面布局）去哪儿了？

### 3.1 传统方式
在传统开发中，你会有一个 `res/layout/activity_main.xml`：

```xml
<!-- 以前的做法 -->
<LinearLayout ...>
    <RecyclerView android:id="@+id/recyclerView" ... />
    <ProgressBar android:id="@+id/progressBar" ... />
</LinearLayout>
```

然后在 Activity 里：
```kotlin
setContentView(R.layout.activity_main)
val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
recyclerView.adapter = MyAdapter(...)
```

### 3.2 本项目的方式：Jetpack Compose
本项目**没有任何 `res/layout/` 目录**。所有 UI 都是 Kotlin 函数。

以首页为例，传统意义上的「布局」被拆分到了这些文件中：

| 组件 | 传统对应 | 本项目文件 |
|---|---|---|
| 整个页面框架（状态栏、搜索、Tab、底部导航） | `activity_main.xml` | `presentation/home/HomeScreen.kt` |
| 大图卡片 Item | `item_large_image.xml` | `presentation/home/components/LargeImageCard.kt` |
| 左文右图卡片 Item | `item_left_text.xml` | `presentation/home/components/LeftTextRightImageCard.kt` |
| 视频卡片 Item | `item_video.xml` | `presentation/home/components/VideoCard.kt` |
| 底部信息栏（来源/评论/时间） | 包含在各 item_xml 中 | `presentation/home/components/BottomInfoRow.kt` |

**关键区别**：Compose 不是「先画好布局，再往里填数据」，而是「数据即界面」。

### 3.3 一个具体例子：大图卡片

**传统写法（你熟悉的）**：
```xml
<!-- res/layout/item_large_image.xml -->
<CardView>
    <LinearLayout android:orientation="vertical">
        <TextView android:id="@+id/tvTitle" ... />
        <ImageView android:id="@+id/ivImage" ... />
        <TextView android:id="@+id/tvSource" ... />
    </LinearLayout>
</CardView>
```

```kotlin
// Adapter 里的 ViewHolder
class LargeImageHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val tvTitle = itemView.findViewById<TextView>(R.id.tvTitle)
    fun bind(card: LargeImageItem) {
        tvTitle.text = card.title
        // ...
    }
}
```

**本项目的写法**：
```kotlin
// presentation/home/components/LargeImageCard.kt
@Composable
fun LargeImageCard(card: FeedCard.LargeImage, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(0.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = card.title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            AsyncImage(model = card.imageUrl, contentDescription = null, ...)
            Spacer(Modifier.height(8.dp))
            BottomInfoRow(card.source, card.commentCount, card.publishTime)
        }
    }
}
```

**对比解读**：
- `@Composable` 标记的函数就是「布局单元」，相当于 XML + ViewHolder 的合体。
- `card.title` 直接在函数参数里传入，没有 `findViewById`。
- `AsyncImage` 是 Coil 库提供的 Compose 图片组件，相当于 `ImageView` + Glide/Picasso。

---

## 四、Activity（主进程入口）变瘦了

### 4.1 传统方式
在传统开发中，`MainActivity` 是「胖子」：初始化网络、请求权限、设置 Adapter、处理点击、管理生命周期……

```kotlin
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        val api = Retrofit.Builder()...build().create(NewsApi::class.java)
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        val adapter = NewsAdapter()
        recyclerView.adapter = adapter
        
        api.getNewsFeed(...).enqueue(object : Callback<...> {
            override fun onResponse(...) {
                adapter.setData(...)
            }
            ...
        })
    }
}
```

### 4.2 本项目的方式
本项目的 `MainActivity.kt` 只有 **27 行**：

```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ToutiaoFeedDemoTheme {
                HomeScreen(viewModel = viewModel)
            }
        }
    }
}
```

**关键变化解读**：

| 传统写法 | 本项目的写法 | 含义 |
|---|---|---|
| `AppCompatActivity` | `ComponentActivity` | Compose 不需要 ActionBar，所以用更基础的 ComponentActivity |
| `setContentView(R.layout...)` | `setContent { ... }` | 把 Compose 的界面「装」到 Activity 里 |
| 在 Activity 里 `new Retrofit()` | `by viewModels()` | ViewModel 由 Hilt 自动注入，Activity 不创建它 |
| Activity 里直接调用 API | 传给 `HomeScreen(viewModel = viewModel)` | 所有业务逻辑都交给了 ViewModel 和 Screen |

**结论**：`MainActivity` 现在只是一个**壳**。它做三件事：声明自己是 Hilt 入口 (`@AndroidEntryPoint`)、启用 EdgeToEdge、把 `HomeScreen` 挂到窗口上。

---

## 五、逻辑触发代码：从「直接调用」到「单向数据流」

这是变化最大的部分，也是现代 Android 开发最核心的思维转变。

### 5.1 传统方式：UI 直接操作一切
```kotlin
// 传统：点击按钮，直接在这里发请求、改 UI
buttonRefresh.setOnClickListener {
    buttonRefresh.isEnabled = false
    progressBar.visibility = View.VISIBLE
    
    api.getNewsFeed(...).enqueue(object : Callback<...> {
        override fun onResponse(...) {
            adapter.setData(response.body()!!)
            progressBar.visibility = View.GONE
            buttonRefresh.isEnabled = true
        }
        override fun onFailure(...) {
            Toast.makeText(this@MainActivity, "加载失败", ...).show()
            buttonRefresh.isEnabled = true
        }
    })
}
```

**问题**：Activity 越来越肥；状态分散在各处；横竖屏旋转后数据丢失；难以测试。

### 5.2 本项目的方式：MVI 单向数据流

本项目采用 **MVI (Model-View-Intent)** 模式，核心是三个文件：

| 文件 | 作用 | 传统对应 |
|---|---|---|
| `HomeUiEvent.kt` | 定义「用户做了什么」 | `onClickListener` 里的动作描述 |
| `HomeViewModel.kt` | 处理事件、请求数据、更新状态 | Activity 里的网络请求 + 状态管理 |
| `HomeUiState.kt` | 定义「页面长什么样」的所有状态 | Activity 里分散的 `visibility`、`text` 等 |

**事件流向图**：

```
用户点击 Tab "热榜"
    │
    ▼
HomeScreen 发送：onEvent(HomeUiEvent.OnTabSelected("hot"))
    │
    ▼
HomeViewModel.onEvent() 收到事件
    │
    ▼
调用 newsRepository.getNewsFeed("hot", 0) 请求数据
    │
    ▼
请求成功 → _uiState.update { HomeUiState.Success(feedItems = ..., currentTab = "hot") }
    │
    ▼
Compose 观察到 uiState 变化 → 自动重组（刷新界面）
    │
    ▼
HomeScreen 重新执行，显示 "热榜" 的内容
```

### 5.3 代码对应关系（以 Tab 切换为例）

**步骤 1：用户点击 Tab（在 Compose 中）**
```kotlin
// HomeScreen.kt 中的 TabRow
Tab(
    selected = selected,
    onClick = { onEvent(HomeUiEvent.OnTabSelected(key)) },  // ← 不直接做事，只发事件
    text = { Text(label) }
)
```

**步骤 2：ViewModel 接收并处理**
```kotlin
// HomeViewModel.kt
fun onEvent(event: HomeUiEvent) {
    when (event) {
        is HomeUiEvent.OnTabSelected -> switchTab(event.tab)
        ...
    }
}

private fun switchTab(tab: String) {
    currentPage = 0
    _uiState.update { HomeUiState.Loading }   // ← 先通知 UI：我在加载了
    loadFeed(tab, 0)                           // ← 去请求数据
}
```

**步骤 3：Compose 根据状态自动渲染**
```kotlin
// HomeScreen.kt
when (uiState) {
    is HomeUiState.Loading -> { CircularProgressIndicator(...) }   // 显示 loading
    is HomeUiState.Success -> { FeedList(items = uiState.feedItems) } // 显示列表
    is HomeUiState.Error   -> { Text("加载失败"); Button("重试") }    // 显示错误
    is HomeUiState.Empty   -> { Text("暂无内容") }                     // 显示空态
}
```

**关键洞察**：在传统开发中，你是「命令式」地告诉每个 View 该显示什么（`textView.text = ...`、`progressBar.visibility = ...`）。  
在 Compose + MVI 中，你是「声明式」地描述：「当状态是 Loading 时，界面长这样；当状态是 Success 时，界面长那样」。状态一变，界面自动跟着变。

---

## 六、列表渲染：RecyclerView.Adapter 去哪了？

### 6.1 传统方式
```kotlin
class NewsAdapter(private val items: List<NewsItem>) : 
    RecyclerView.Adapter<NewsAdapter.VH>() {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_news, parent, false)
        return VH(view)
    }
    
    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.title.text = item.title
        holder.image.load(item.imageUrl)
    }
    
    override fun getItemCount() = items.size
}
```

### 6.2 本项目的方式：LazyColumn
```kotlin
// HomeScreen.kt 中的 FeedList()
LazyColumn(
    state = listState,
    contentPadding = PaddingValues(vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
) {
    items(items = items, key = { it.id }) { card ->
        Box(modifier = Modifier.clickable { onCardClick(card.id) }) {
            when (card) {                              // ← 相当于根据 viewType 分发
                is FeedCard.TextTop -> TextTopCard(card)
                is FeedCard.LeftTextRightImage -> LeftTextRightImageCard(card)
                is FeedCard.LargeImage -> LargeImageCard(card)
                is FeedCard.Video -> VideoCard(card)
            }
        }
    }
    if (isLoadingMore) {
        item { LoadingFooter() }                       // ← 相当于 Adapter 的 footer
    }
}
```

| 传统概念 | Compose 对应 | 说明 |
|---|---|---|
| `RecyclerView` | `LazyColumn` | 垂直列表，只渲染可见项 |
| `Adapter` | `LazyColumn` 的 lambda 块 | 没有 Adapter 类，直接在 `items { }` 里描述每一项 |
| `onCreateViewHolder` | `when(card) { ... }` | 根据数据类型渲染不同 Compose 函数 |
| `onBindViewHolder` | Composable 函数参数 | 数据直接作为参数传入 `@Composable fun XxxCard(card: ...)` |
| `getItemCount()` | `items = items`（集合大小自动决定） | 不需要手动返回数量 |
| `notifyDataSetChanged()` | **不需要** | 数据变了，Compose 自动重组对应的项 |

---

## 七、项目目录结构对照

### 传统项目（2022 年左右）
```
app/src/main/
├── java/com/example/app/
│   ├── MainActivity.kt          ← 所有逻辑都在这里
│   ├── NewsAdapter.kt
│   ├── ApiService.kt            ← 可能也塞在 Activity 同级
│   └── model/NewsItem.kt
├── res/
│   ├── layout/
│   │   ├── activity_main.xml
│   │   ├── item_large_image.xml
│   │   └── item_video.xml
│   └── values/
└── AndroidManifest.xml
```

### 本项目（Clean Architecture）
```
app/src/main/java/com/example/toutiao/
├── MainActivity.kt              ← 只剩壳，27 行
├── ToutiaoApplication.kt        ← 应用入口，初始化 Hilt/Timber
├── di/                          ← 依赖注入配置（以前可能写在单例类里）
│   ├── NetworkModule.kt         ← Retrofit/OkHttp 的「工厂」
│   ├── DatabaseModule.kt        ← Room 数据库的「工厂」
│   └── RepositoryModule.kt      ← 接口与实现的绑定
├── presentation/                ← **表示层：UI + 状态管理**
│   ├── home/
│   │   ├── HomeScreen.kt        ← 首页「布局」+「结构」（Composable）
│   │   ├── HomeViewModel.kt     ← 业务逻辑（以前写在 Activity 里）
│   │   ├── HomeUiState.kt       ← 页面状态定义
│   │   ├── HomeUiEvent.kt       ← 用户事件定义
│   │   └── components/          ← 可复用的小组件（相当于 item xml）
│   │       ├── LargeImageCard.kt
│   │       ├── VideoCard.kt
│   │       └── BottomInfoRow.kt
│   └── theme/                   ← 颜色/字体/主题（替代 styles.xml）
├── domain/                      ← **领域层：纯业务，不依赖 Android SDK**
│   ├── model/FeedCard.kt        ← 业务实体（Sealed Class 定义卡片类型）
│   └── repository/NewsRepository.kt  ← 仓库接口
└── data/                        ← **数据层：网络 + 本地**
    ├── remote/                  ← 网络相关
    │   ├── api/NewsApi.kt       ← Retrofit 接口
    │   └── dto/NewsFeedResponse.kt  ← 网络 JSON 对应的数据类
    ├── local/                   ← 本地数据库
    │   ├── dao/FeedDao.kt       ← Room 数据访问对象
    │   ├── entity/FeedItemEntity.kt  ← 数据库表结构
    │   └── database/AppDatabase.kt
    ├── repository/NewsRepositoryImpl.kt  ← 仓库实现
    └── mapper/NewsMapper.kt     ← DTO ↔ Entity ↔ Domain Model 转换
```

**分层逻辑**：
- `presentation`：只关心「怎么展示」和「用户操作了什么」。
- `domain`：只关心「业务规则是什么」，不依赖 Android 框架，方便单元测试。
- `data`：只关心「数据从哪来、怎么存」，对外隐藏了网络和数据库的细节。

---

## 八、常见操作的「新家」速查

| 你想做的事 | 以前的做法 | 在本项目中怎么做 |
|---|---|---|
| 修改文字颜色/大小 | `res/values/styles.xml` + `android:textColor` | `presentation/theme/Type.kt` / 直接在 Composable 里 `color = Color.Red` |
| 修改主题色 | `res/values/colors.xml` + `themes.xml` | `presentation/theme/Color.kt` + `Theme.kt` |
| 添加一个新的列表卡片 | 新建 `res/layout/item_xxx.xml` + ViewHolder | 新建 `@Composable fun XxxCard(...)` |
| 点击列表项跳转详情 | Adapter 里 `holder.itemView.setOnClickListener` | `LazyColumn` 的 `items { Box(Modifier.clickable { onCardClick(id) }) }` |
| 下拉刷新 | `SwipeRefreshLayout` 包裹 RecyclerView | `PullToRefreshBox(isRefreshing = ..., onRefresh = { ... })` |
| 加载更多 | `RecyclerView.OnScrollListener` + `adapter.addFooter()` | Paging3 `LazyPagingItems` 自动触发 `RemoteMediator.APPEND` |
| 保存配置变更（旋转屏幕）| `onSaveInstanceState` 手动存取 | **自动的**。ViewModel 在配置变更时存活，状态不丢失。 |
| 网络请求 | `Retrofit.create().enqueue()` 回调 | ViewModel 里 `viewModelScope.launch { newsApi.getNewsFeed(...) }`（挂起函数） |
| 图片加载 | Glide / Picasso.into(imageView) | Coil 的 `AsyncImage(model = url, ...)` |
| 数据库操作 | `SQLiteOpenHelper` 手写 SQL | Room 的 `@Dao` 接口 + `@Entity` 注解 |
| 单例对象（网络客户端）| 手写 `object Singleton { ... }` | Hilt `@Module` + `@Provides @Singleton` |

---

## 九、总结：思维转变清单

从传统开发过渡到本项目，你需要在脑子里切换以下几个开关：

1. **从「命令式」到「声明式」**
   - 以前：拿到 View 的引用，命令它「变红」「隐藏」「设文字」。
   - 现在：定义好状态（`HomeUiState`），描述不同状态下 UI 应该长什么样，数据驱动界面自动更新。

2. **从「分散状态」到「集中状态」**
   - 以前：`textView.text`、 `progressBar.visibility`、 `adapter.data` 各自为政。
   - 现在：所有 UI 相关的状态都收敛到一个 `HomeUiState` 对象里。

3. **从「View 持有数据」到「数据与 View 分离」**
   - 以前：Adapter 里直接持有 `List<Item>`，Activity 里持有各种变量。
   - 现在：数据在 ViewModel 里管理，Compose 只负责「读状态、发事件」。

4. **从「类膨胀」到「函数即组件」**
   - 以前：每个 Item 都要写一个 XML + ViewHolder + Adapter 绑定。
   - 现在：一个 `@Composable fun Card(...)` 就是一个独立、可复用的 UI 组件。

5. **从「生命周期地狱」到「协程自动管理」**
   - 以前：网络请求要在 `onDestroy` 里取消，不然会内存泄漏。
   - 现在：`viewModelScope.launch { ... }` 自动在 ViewModel 清除时取消。

---

## 十、推荐阅读顺序

如果你想在这个项目里真正动手改代码，建议按这个顺序读文件：

1. `MainActivity.kt` —— 看入口有多简单。
2. `presentation/home/HomeUiState.kt` —— 理解页面有哪些状态。
3. `presentation/home/HomeUiEvent.kt` —— 理解用户能触发哪些操作。
4. `presentation/home/HomeViewModel.kt` —— 理解事件如何被处理、状态如何被更新。
5. `presentation/home/HomeScreen.kt` —— 理解状态如何被渲染成界面。
6. `presentation/home/components/LargeImageCard.kt` —— 理解一个具体卡片怎么写。
7. `di/NetworkModule.kt` —— 理解依赖注入怎么配置网络。
8. `data/repository/NewsRepositoryImpl.kt` —— 理解数据层怎么工作。

读完这 8 个文件，你就完整走通了「用户点击 → 事件发送 → 网络请求 → 状态更新 → 界面刷新」的全链路。
