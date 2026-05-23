package com.example.toutiao.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.toutiao.domain.model.FeedCard
import com.example.toutiao.presentation.home.components.LargeImageCard
import com.example.toutiao.presentation.home.components.LeftTextRightImageCard
import com.example.toutiao.presentation.home.components.TextTopCard
import com.example.toutiao.presentation.home.components.VideoCard

// =============================================================================
// HomeScreen.kt — 首页信息流 UI
//
// 文件结构（自上而下的组合层级）：
//   HomeScreen           → MVI 入口：收集 StateFlow，建立 ViewModel ↔ UI 的连接
//   └─ HomeScreenContent → Scaffold 骨架：组装 TopBar / Content / BottomNav
//        ├─ HomeTopBar   → 顶部红色区域：搜索栏占位 + TabRow 频道切换
//        ├─ FeedList     → 内容区：PullToRefreshBox 包裹 LazyColumn，内嵌滑动加载更多逻辑
//        └─ HomeBottomNav → 底部导航栏：5 个 Tab（纯 UI 状态，暂不切换页面）
//
// 数据流向（MVI 单向数据流）：
//   用户操作 → HomeUiEvent（通过 onEvent 回调）
//            → HomeViewModel.onEvent(event)
//            → Repository 获取数据
//            → HomeUiState 更新（StateFlow）
//            → collectAsStateWithLifecycle() 触发 Compose 重组
//            → UI 响应状态变化
// =============================================================================

// ── 入口：连接 ViewModel 的 StateFlow ──────────────────────────────────────────
// 这是整个首页 Compose 树的根节点。它做两件事：
// 1. collectAsStateWithLifecycle() — 以生命周期感知方式订阅 ViewModel 的 StateFlow
//    当 Activity 在后台时自动暂停收集，避免不必要的重组
// 2. 将 ViewModel 的方法引用（::onEvent）转换为 lambda 传递给子组件，
//    子组件无需知道 ViewModel 的存在，只依赖 UiState 和 (UiEvent) -> Unit
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreenContent(uiState = uiState, onEvent = viewModel::onEvent)

}

// ── 骨架：Scaffold 三区布局 ──────────────────────────────────────────────────
// Scaffold 是 Material3 的页面骨架组件，自动处理：
//   - topBar: 固定在顶部，不受列表滚动影响
//   - bottomBar: 固定在底部
//   - content: 中间可滚动区域，自动扣除 topBar/bottomBar 高度（通过 innerPadding）
//
// when (uiState) 是 MVI 的核心——UI 只根据唯一的 State 决定渲染什么，
// 不存在 "如果 A 则显示 X 但 B 情况下又显示 Y" 的混乱分支。
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreenContent(
    uiState: HomeUiState,
    onEvent: (HomeUiEvent) -> Unit,
) {
    Scaffold(
        topBar = { HomeTopBar(uiState = uiState, onEvent = onEvent) },
        bottomBar = { HomeBottomNav() },
        containerColor = Color(0xFFF5F5F5),
    ) { innerPadding ->
        when (uiState) {
            is HomeUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Color(0xFFD81E06))
                }
            }
            is HomeUiState.Success -> {
                FeedList(
                    items = uiState.feedItems,
                    isRefreshing = uiState.isRefreshing,
                    isLoadingMore = uiState.isLoadingMore,
                    onRefresh = { onEvent(HomeUiEvent.OnRefresh) },
                    onLoadMore = { onEvent(HomeUiEvent.OnLoadMore) },
                    onCardClick = { onEvent(HomeUiEvent.OnCardClick(it)) },
                    modifier = Modifier.padding(innerPadding),
                )
            }
            is HomeUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(uiState.message, color = Color.Gray)
                        Spacer(Modifier.height(8.dp))
                        if (uiState.retryable) {
                            Button(onClick = { onEvent(HomeUiEvent.OnRetry) }) {
                                Text("重试")
                            }
                        }
                    }
                }
            }
            is HomeUiState.Empty -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("暂无内容", color = Color.Gray)
                }
            }
        }
    }
}

// ── 顶部栏：搜索 + Tab 频道切换 ──────────────────────────────────────────────
// 分为上下两部分：
//   1. 搜索栏占位（下拉刷新页面的搜索框的视觉还原，暂不可交互）
//   2. Material3 TabRow：推荐 / 热榜 / 视频 / 社会
//
// currentTab 默认取 "recommend"，在 Loading/Error/Empty 状态下也能保证 UI 不崩溃。
// statusBarsPadding() 自动为状态栏留出空间，避免内容被刘海/挖孔遮挡。
@Composable
private fun HomeTopBar(
    uiState: HomeUiState,
    onEvent: (HomeUiEvent) -> Unit,
) {
    val tabs = listOf("recommend" to "推荐", "hot" to "热榜", "video" to "视频", "society" to "社会")
    val currentTab = (uiState as? HomeUiState.Success)?.currentTab ?: "recommend"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFD81E06))
            .statusBarsPadding(),
    ) {
        // 搜索栏占位 — 半透明白色圆角矩形 + 搜索图标 + "搜索" 文字
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = 0.3f)),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 12.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "搜索",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                )
            }
        }

        // TabRow — 选中的 Tab 字号更大（18sp）、加粗、纯白；未选中略小（15sp）、半透明
        TabRow(
            selectedTabIndex = tabs.indexOfFirst { it.first == currentTab }.coerceAtLeast(0),
            containerColor = Color(0xFFD81E06),
            contentColor = Color.White,
            divider = {},
        ) {
            tabs.forEach { (key, label) ->
                val selected = key == currentTab
                Tab(
                    selected = selected,
                    onClick = { onEvent(HomeUiEvent.OnTabSelected(key)) },
                    text = {
                        Text(
                            text = label,
                            fontSize = if (selected) 18.sp else 15.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) Color.White else Color.White.copy(alpha = 0.7f),
                        )
                    },
                )
            }
        }
    }
}

// ── 信息流列表：下拉刷新 + 滑动加载更多 ──────────────────────────────────────
// 这个组件组合了三层逻辑：
//
// 1. PullToRefreshBox — Material3 的下拉刷新容器
//    下拉时触发 onRefresh → ViewModel → 网络请求 → isRefreshing 状态变化
//
// 2. LazyColumn — 高性能的懒加载列表
//    - items(key = { it.id }) 确保 Compose 重组时能精确定位变化项
//    - when (card) 根据密封类子类型分发到不同卡片组件
//
// 3. shouldLoadMore — 加载更多检测
//    derivedStateOf 监听列表滚动位置，当最后可见项抵达倒数第 3 项时，
//    通过 LaunchedEffect 触发 onLoadMore（避免在重组中直接发起副作用）
//    isLoadingMore 防重入，确保不会重复请求
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedList(
    items: List<FeedCard>,
    isRefreshing: Boolean,
    isLoadingMore: Boolean,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onCardClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisibleItem != null && lastVisibleItem.index >= listState.layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && !isLoadingMore) {
            onLoadMore()
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 异构列表：根据 FeedCard 子类型渲染不同卡片
            items(items = items, key = { it.id }) { card ->
                Box(modifier = Modifier.clickable { onCardClick(card.id) }) {
                    when (card) {
                        is FeedCard.TextTop -> TextTopCard(card)
                        is FeedCard.LeftTextRightImage -> LeftTextRightImageCard(card)
                        is FeedCard.LargeImage -> LargeImageCard(card)
                        is FeedCard.Video -> VideoCard(card)
                    }
                }
            }
            // Footer：加载更多时的底部 Loading 指示器
            if (isLoadingMore) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}

// ── 辅助：导航项数据 ─────────────────────────────────────────────────────────
// 封装每个导航按钮需要的图标对（选中/未选中）和文字
private data class NavItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

// ── 底部导航栏：5 个 Tab ──────────────────────────────────────────────────────
// 对应需求文档中的 5 个底部入口：首页 / 视频 / 搜索 / 任务 / 我的
// selectedIndex 是纯粹的本地 UI 状态（用 remember 保存），不经过 ViewModel，
// 因为这 5 个 Tab 在当前版本仅做视觉展示，不触发实际页面切换
@Composable
private fun HomeBottomNav() {
    val items = listOf(
        NavItem("首页", Icons.Filled.Home, Icons.Outlined.Home),
        NavItem("视频", Icons.Filled.PlayArrow, Icons.Outlined.PlayArrow),
        NavItem("搜索", Icons.Filled.Search, Icons.Outlined.Search),
        NavItem("任务", Icons.Filled.Star, Icons.Outlined.Star),
        NavItem("我的", Icons.Filled.Person, Icons.Outlined.Person),
    )
    var selectedIndex by remember { mutableIntStateOf(0) }

    NavigationBar {
        items.forEachIndexed { index, item ->
            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = if (index == selectedIndex) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label,
                    )
                },
                label = { Text(item.label) },
                selected = index == selectedIndex,
                onClick = { selectedIndex = index },
            )
        }
    }
}

// =============================================================================
// Preview — Mock 数据与多状态预览
// =============================================================================

// 模拟 4 种卡片类型的样本数据，覆盖全部 FeedCard 子类型
private val mockFeedItems = listOf(
    FeedCard.TextTop(
        id = "1",
        title = "习近平出席上海合作组织成员国元首理事会并发表重要讲话",
        source = "新华网",
        commentCount = 12876,
        publishTime = "3小时前",
    ),
    FeedCard.LeftTextRightImage(
        id = "2",
        title = "华为发布2026年第一季度财报：营收同比增长18%，汽车业务成新增长极",
        source = "36氪",
        commentCount = 5432,
        publishTime = "5小时前",
        imageUrl = "https://picsum.photos/seed/news2/400/300",
    ),
    FeedCard.LargeImage(
        id = "3",
        title = "SpaceX 星舰完成第六次轨道试飞，首次实现上面级在轨推进剂转移",
        source = "环球时报",
        commentCount = 9876,
        publishTime = "1小时前",
        imageUrl = "https://picsum.photos/seed/news3/800/450",
    ),
    FeedCard.Video(
        id = "4",
        title = "【独家】专访《黑神话：悟空》主创：DLC 开发进度过半，将引入全新战斗系统",
        source = "游研社",
        commentCount = 23456,
        publishTime = "2小时前",
        imageUrl = "https://picsum.photos/seed/news4/800/450",
        videoUrl = "",
        duration = "08:25",
    ),
    FeedCard.LeftTextRightImage(
        id = "5",
        title = "北京二手房成交量连续3个月破万套，住建委或将出台新调控政策",
        source = "财经网",
        commentCount = 3456,
        publishTime = "6小时前",
        imageUrl = "https://picsum.photos/seed/news5/400/300",
    ),
    FeedCard.TextTop(
        id = "6",
        title = "2026年高考报名人数突破1400万，教育部部署考试安全工作",
        source = "教育部",
        commentCount = 5678,
        publishTime = "4小时前",
    ),
    FeedCard.LargeImage(
        id = "7",
        title = "苹果WWDC 2026前瞻：iOS 20或引入AI原生交互，Vision Pro 2有望亮相",
        source = "虎嗅",
        commentCount = 7890,
        publishTime = "2小时前",
        imageUrl = "https://picsum.photos/seed/news7/800/450",
    ),
    FeedCard.Video(
        id = "8",
        title = "世界女排联赛：中国队苦战五局逆转巴西队，龚翔宇末局独得8分，李盈莹带伤出战",
        source = "央视体育",
        commentCount = 15678,
        publishTime = "1小时前",
        imageUrl = "https://picsum.photos/seed/news8/800/450",
        videoUrl = "",
        duration = "12:40",
    ),
)

// ── 预览 1：Success 状态，展示全部 4 种卡片类型 ─────────────────────────────
@Preview(name = "Success — 信息流列表", showBackground = true, showSystemUi = true)
@Composable
private fun HomeScreenSuccessPreview() {
    com.example.toutiao.ui.theme.ToutiaoFeedDemoTheme {
        HomeScreenContent(
            uiState = HomeUiState.Success(
                feedItems = mockFeedItems,
                currentTab = "recommend",
            ),
            onEvent = {},
        )
    }
}

// ── 预览 2：Loading 状态 ─────────────────────────────────────────────────────
@Preview(name = "Loading — 首次加载", showBackground = true, showSystemUi = true)
@Composable
private fun HomeScreenLoadingPreview() {
    com.example.toutiao.ui.theme.ToutiaoFeedDemoTheme {
        HomeScreenContent(uiState = HomeUiState.Loading, onEvent = {})
    }
}

// ── 预览 3：Error 状态 ───────────────────────────────────────────────────────
@Preview(name = "Error — 加载失败", showBackground = true, showSystemUi = true)
@Composable
private fun HomeScreenErrorPreview() {
    com.example.toutiao.ui.theme.ToutiaoFeedDemoTheme {
        HomeScreenContent(
            uiState = HomeUiState.Error(message = "网络连接失败，请检查网络设置"),
            onEvent = {},
        )
    }
}

// ── 预览 4：Empty 状态 ───────────────────────────────────────────────────────
@Preview(name = "Empty — 暂无内容", showBackground = true, showSystemUi = true)
@Composable
private fun HomeScreenEmptyPreview() {
    com.example.toutiao.ui.theme.ToutiaoFeedDemoTheme {
        HomeScreenContent(uiState = HomeUiState.Empty, onEvent = {})
    }
}

// ── 预览 5：刷新中状态 — Success + isRefreshing = true ─────────────────────
@Preview(name = "Success — 下拉刷新中", showBackground = true, showSystemUi = true)
@Composable
private fun HomeScreenRefreshingPreview() {
    com.example.toutiao.ui.theme.ToutiaoFeedDemoTheme {
        HomeScreenContent(
            uiState = HomeUiState.Success(
                feedItems = mockFeedItems,
                isRefreshing = true,
                currentTab = "hot",
            ),
            onEvent = {},
        )
    }
}

