package com.example.toutiao.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.example.toutiao.data.remote.datasource.DebugControls
import com.example.toutiao.domain.model.FeedCard
import com.example.toutiao.presentation.common.FeedCardItem
import com.example.toutiao.presentation.home.components.AudioCategoryChips
import com.example.toutiao.presentation.home.components.AudioHotCard
import com.example.toutiao.presentation.home.components.AudioRecommendItem
import com.example.toutiao.presentation.home.components.HotAuthorsRow
import com.example.toutiao.presentation.home.components.AudioSectionHeader
import com.example.toutiao.presentation.home.components.AudioSubTabs
import com.example.toutiao.presentation.home.components.FinanceRiskNotice
import com.example.toutiao.presentation.home.components.FinanceStockIndexCard
import com.example.toutiao.presentation.home.components.FloatingHintCardWithState
import com.example.toutiao.presentation.home.components.FollowAuthor
import com.example.toutiao.presentation.home.components.FollowAuthorRecommendRow
import com.example.toutiao.presentation.home.components.FollowAuthorSection
import com.example.toutiao.presentation.home.components.FollowInterestCarousel
import com.example.toutiao.presentation.home.components.HotListView
import com.example.toutiao.presentation.home.components.LastSeenHint
import com.example.toutiao.presentation.home.components.XhsGridList
import com.example.toutiao.presentation.home.components.feedCardToXhsCard
import com.example.toutiao.presentation.home.components.MilitaryRankDivider
import com.example.toutiao.presentation.home.components.MilitaryRankHeader
import com.example.toutiao.presentation.home.components.MilitaryRankItem
import com.example.toutiao.presentation.home.components.NovelBook
import com.example.toutiao.presentation.home.components.NovelBookshelfRow
import com.example.toutiao.presentation.home.components.NovelRankItem
import com.example.toutiao.presentation.home.components.NovelRankingTabs
import com.example.toutiao.presentation.home.components.NovelRecommendBook
import com.example.toutiao.presentation.home.components.NovelRecommendItem
import com.example.toutiao.presentation.home.components.NovelSectionHeader
import com.example.toutiao.presentation.home.components.RecommendFlashCard
import com.example.toutiao.presentation.home.components.ShenzhenLocalHotBanner
import com.example.toutiao.presentation.home.components.TextTopCard
import com.example.toutiao.presentation.home.components.ShenzhenVideoCarousel
import com.example.toutiao.presentation.home.components.ShenzhenWeatherStrip
import com.example.toutiao.presentation.home.components.SportsBanner
import com.example.toutiao.presentation.home.components.SportsCategoryChips
import com.example.toutiao.presentation.home.components.SportsMatch
import com.example.toutiao.presentation.home.components.SportsMatchRow
import com.example.toutiao.presentation.home.components.StockIndex
import com.example.toutiao.ui.theme.RedMain
import timber.log.Timber
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// =============================================================================
// HomeScreen — MVI 入口 + Tab 切换数据隔离
//
// 关键设计：
//  1. key(currentTab) 包裹 Paging3 数据收集和列表渲染，
//     Tab 切换时 Compose 丢弃旧 key 内的所有状态（包括 LazyPagingItems 和
//     LazyListState），新 key 内从 Loading 态全新开始，消除旧数据闪现。
//  2. snapshotFlow + derivedStateOf 监听 firstVisibleItemIndex / firstVisibleItemScrollOffset，
//     首条可见项变化时上报 HomeUiEvent.OnFirstVisibleCardChanged，
//     触发 HomeViewModel 把该 card id 持久化到当前 tab。
//  3. 「上次看到这里」提示展示条件：
//     - UiState.lastSeenCardId != null
//     - 当前 LazyPagingItems 中能定位到该 id（lastSeenIndex >= 0）
//     - 用户尚未滚过该位置（firstVisibleItemIndex <= lastSeenIndex）
//     满足以上 3 条时，在 lastSeenIndex 之前插入一个 LastSeenHint item；
//     用户点击 hint → scrollToItem(lastSeenIndex) 跳转到原位置。
// =============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onCardClick: (FeedCard) -> Unit = {},
    onQuickActionClick: (com.example.toutiao.domain.model.HotQuickAction) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    var showDebugDialog by remember { mutableStateOf(false) }

    // 包装 onCardClick 加 Timber 日志，方便排查跳转不生效的 bug
    val onCardClickLogged: (FeedCard) -> Unit = { card ->
        Timber.d("HomeScreen onCardClick — id=${card.id}, sourceUrl=${card.sourceUrl}")
        onCardClick(card)
    }

    HomeScreenContent(
        uiState = uiState,
        currentTab = currentTab,
        searchQuery = searchQuery,
        searchResults = searchResults,
        feedPagingData = viewModel.feedPagingData,
        onEvent = viewModel::onEvent,
        onCardClick = onCardClickLogged,
        onQuickActionClick = onQuickActionClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreenContent(
    uiState: HomeUiState,
    currentTab: String,
    searchQuery: String,
    searchResults: List<FeedCard>,
    feedPagingData: Flow<PagingData<FeedCard>>,
    onEvent: (HomeUiEvent) -> Unit,
    onCardClick: (FeedCard) -> Unit,
    onQuickActionClick: (com.example.toutiao.domain.model.HotQuickAction) -> Unit,
) {
    val successState = uiState as? HomeUiState.Success
    val isSearching = successState?.isSearching ?: false
    val searchError = successState?.searchError

    // PM ISSUE-001 修复：浮卡「高考作文题来了」原本一进入就显示，会压在首张置顶卡的图片上。
    // 改为只展示「用户已滚过 2 张卡片」时才显示浮卡。状态由 PagingFeedList 通过回调更新。
    var scrolledPastFirstCard by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        HomeTopBar(
            uiState = uiState,
            currentTab = currentTab,
            searchQuery = searchQuery,
            onEvent = onEvent,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            when {
                isSearching && searchQuery.isNotEmpty() && searchResults.isNotEmpty() -> {
                    SearchResultList(
                        results = searchResults,
                        onCardClick = { card -> onCardClick(card) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                isSearching && searchQuery.isNotEmpty() && searchResults.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (searchError != null) {
                                Text(searchError, color = RedMain)
                                Spacer(Modifier.height(4.dp))
                                Text("点击搜索按钮重试", color = Color.Gray, fontSize = 13.sp)
                            } else {
                                Text("点击搜索按钮查看结果", color = Color.Gray)
                            }
                        }
                    }
                }
                else -> {
                    // 频道差异化：热榜频道使用专门的 HotListView（带快捷入口+带徽标列表）
                    if (currentTab == "hot" && successState != null) {
                        HotListView(
                            items = successState.hotListItems,
                            quickActions = successState.hotQuickActions,
                            onItemClick = { onEvent(HomeUiEvent.OnCardClick(it.id)) },
                            onQuickActionClick = { action ->
                                // 顶部快捷入口是子分页按钮 → 委托 MainActivity 打开 HotTopicScreen
                                onQuickActionClick(action)
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        // key(currentTab): Tab 切换时销毁整个子树（LazyPagingItems + LazyListState），
                        // 新子树从 Loading 态开始，避免：
                        //  1. 旧 Tab 数据闪现（LazyPagingItems 是全新创建的，初始 0 条）
                        //  2. 滚动位置残留（LazyListState 是全新创建的，位置为 0）
                        key(currentTab) {
                            val lazyPagingItems = feedPagingData.collectAsLazyPagingItems()
                            val listState = remember { LazyListState() }

                            // MVPTask #8: 发现 tab 用小红书双列网格
                            if (currentTab == "discover") {
                                val xhsCards = lazyPagingItems.itemSnapshotList.items.map { feedCardToXhsCard(it) }
                                XhsGridList(
                                    items = xhsCards,
                                    onItemClick = { card ->
                                        val orig = lazyPagingItems.itemSnapshotList.items.firstOrNull { it.id == card.id }
                                        if (orig != null) onCardClick(orig)
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {

                            // Tab 切换时 key 变化，LazyListState 是全新的，但 Paging3 的
                            // differ 过程可能在数据到达前就把列表滚离顶部。这里显式保证回顶。
                            LaunchedEffect(Unit) {
                                listState.scrollToItem(0)
                            }

                            PagingFeedList(
                                listState = listState,
                                lazyPagingItems = lazyPagingItems,
                                lastSeenCardId = successState?.lastSeenCardId,
                                lastSeenAt = successState?.lastSeenAt ?: 0L,
                                onEvent = onEvent,
                                onCardClick = onCardClick,
                                channelKey = currentTab,
                                onScrollProgressChange = { visibleCount ->
                                    scrolledPastFirstCard = visibleCount >= 2
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                            }
                        }
                    }
                }
            }

            // 悬浮提示卡（设计稿：右下角浮于内容之上）— 仅在「推荐/热榜」频道显示
            // PM 审查 ISSUE-001 修复：仅在用户滚过 2 张卡片后才显示，避免遮挡首屏内容
            if ((currentTab == "recommend" || currentTab == "hot") && scrolledPastFirstCard) {
                FloatingHintCardWithState(
                    title = "高考作文题来了",
                    subtitle = "去热榜看详情  ›",
                    onClick = {
                        onEvent(HomeUiEvent.OnTabSelected("hot"))
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = 80.dp),
                )
            }
        }
    }
}

// ── Paging3 列表渲染 ─────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PagingFeedList(
    listState: LazyListState,
    lazyPagingItems: LazyPagingItems<FeedCard>,
    lastSeenCardId: String?,
    lastSeenAt: Long,
    onEvent: (HomeUiEvent) -> Unit,
    onCardClick: (FeedCard) -> Unit,
    channelKey: String,
    onScrollProgressChange: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val refreshLoadState = lazyPagingItems.loadState.refresh
    val isRefreshing = refreshLoadState is LoadState.Loading && lazyPagingItems.itemCount > 0
    val isInitialLoading = refreshLoadState is LoadState.Loading && lazyPagingItems.itemCount == 0
    val isEmpty = refreshLoadState is LoadState.NotLoading && lazyPagingItems.itemCount == 0
    val isError = refreshLoadState is LoadState.Error && lazyPagingItems.itemCount == 0
    val errorMessage = (refreshLoadState as? LoadState.Error)?.error?.message ?: "加载失败"

    val coroutineScope = rememberCoroutineScope()

    // 解析 lastSeenCardId 在当前 LazyPagingItems 中的 index（-1 表示未找到或不需要展示）
    val lastSeenIndex by remember(lazyPagingItems, lastSeenCardId) {
        derivedStateOf {
            if (lastSeenCardId.isNullOrBlank() || lazyPagingItems.itemCount == 0) {
                -1
            } else {
                // 在 itemSnapshotList 中查找 id 匹配的 index
                val snapshot = lazyPagingItems.itemSnapshotList
                snapshot.items.indexOfFirst { it.id == lastSeenCardId }
            }
        }
    }

    // 当前 firstVisibleItemIndex
    val firstVisibleIndex by remember {
        derivedStateOf { listState.firstVisibleItemIndex }
    }

    // PM ISSUE-001: 把 firstVisibleIndex 透传给父组件，父组件据此控制浮卡可见性
    LaunchedEffect(firstVisibleIndex) {
        onScrollProgressChange(firstVisibleIndex)
    }

    // 当 firstVisibleIndex > 0 且首条 card 变化时，触发持久化。
    // 注意：0 不持久化（避免刚进入 tab 立即把顶部条目当成"上次位置"覆盖）。
    // MVPTask #5 修复：idx 等于 lastSeenIndex 也不持久化（避免 hint 显示位置被覆盖回自己）
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .map { idx ->
                val card = lazyPagingItems.itemSnapshotList.getOrNull(idx)
                idx to card?.id
            }
            .distinctUntilChanged { (oldIdx, _), (newIdx, _) -> oldIdx == newIdx }
            .collect { (idx, id) ->
                Timber.d("PagingFeedList scroll: idx=$idx, id=$id, lastSeenCardId=$lastSeenCardId, lastSeenIndex=$lastSeenIndex")
                if (idx > 0 && id != null && id != lastSeenCardId && idx > lastSeenIndex) {
                    Timber.d("PagingFeedList persist: id=$id")
                    onEvent(HomeUiEvent.OnFirstVisibleCardChanged(id))
                }
            }
    }

    // Tab 切换 / 下拉刷新：key(currentTab) 重建整个子树，LazyListState 初始位置为 0，
    // 天然回到顶部。
    // 注意：不要在数据第一次到达时强制 scrollToItem(0)，会触发 firstVisibleIndex=0 → OnFirstVisibleCardChanged
    // → 覆盖掉持久化的 lastSeenCardId，让「上次看到这里」hint 永远不显示。
    // 实际语义：初始化时 LazyListState 本身就是 0，differ 后保持 0，不需要 scroll。
    // 这里只在用户**主动下拉刷新**（loadState.append 进入 loading 后再 NotLoading）才回顶，
    // 用 TrackPreviousKey 记录 append 的前一个状态来判断"是否刚 append 完成"。
    val prevAppendState = remember { mutableStateOf<LoadState?>(null) }
    LaunchedEffect(lazyPagingItems.loadState.append) {
        val current = lazyPagingItems.loadState.append
        if (prevAppendState.value is LoadState.Loading && current is LoadState.NotLoading) {
            // 用户滚到底，触发了 append loading，加载完成后不要回顶（用户期待的是看到更多）
            // 这里刻意不 scrollToItem(0)
        }
        prevAppendState.value = current
    }

    // 决定 hint 是否展示：仅当 lastSeenIndex > 0 且用户尚未滚过该位置时
    val showLastSeenHint by remember {
        derivedStateOf {
            lastSeenIndex > 0 && firstVisibleIndex <= lastSeenIndex
        }
    }

    val relativeMinutes: Long = remember(lastSeenAt) {
        if (lastSeenAt <= 0L) 0L else (System.currentTimeMillis() - lastSeenAt) / 60_000L
    }

    when {
        isInitialLoading -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = RedMain)
            }
        }
        isError -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(errorMessage, color = Color.Gray)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { lazyPagingItems.retry() }) {
                        Text("重试")
                    }
                }
            }
        }
        isEmpty -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text("暂无内容", color = Color.Gray)
            }
        }
        else -> {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { lazyPagingItems.refresh() },
                modifier = modifier,
            ) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(top = 0.dp, bottom = 8.dp),
                ) {
                    // ── 频道专属 Header ──
                    if (channelKey == "discover") {
                        // MVPTask #8: 小红书双列无限下拉
                        // Paging 列表本身是 LazyColumn，但需要把每行 item 改成双列
                        // 这里把 Paging items 改用 grid 方式渲染：每行 2 个 card
                        // 由 SpecialGridPagingList 包装
                    }
                    if (channelKey == "follow") {
                        // MVPTask #7: 关注频道 Header
                        item(key = "follow_author_section") {
                            FollowAuthorSection(
                                authors = listOf(
                                    FollowAuthor("财", "财经观察家", "1245万粉丝", Color(0xFFFF9F43), 3),
                                    FollowAuthor("深", "深圳土著 1 号", "8.6万粉丝", Color(0xFF26C281), 2),
                                    FollowAuthor("王", "老王爱数码", "21.8万粉丝", Color(0xFF45B7D1), 1),
                                ),
                            )
                        }
                        // MVPTask #7: 轮播"你可能感兴趣的人"
                        item(key = "follow_interest_carousel") {
                            FollowInterestCarousel()
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(Color(0xFFEDEDED)),
                            )
                        }
                        // MVPTask #7: 推荐账号列表
                        item(key = "follow_recommend_header") {
                            Text(
                                text = "推荐关注",
                                color = Color(0xFF1A1A1A),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                        val recommendAuthors = listOf(
                            FollowAuthor("🔥", "头条热榜", "实时更新", Color(0xFFFF4757), 0),
                            FollowAuthor("💰", "理财早班车", "投资理财", Color(0xFFFFA502), 0),
                            FollowAuthor("🏥", "健康日报", "养生健康", Color(0xFF2ED573), 0),
                        )
                        items(
                            count = recommendAuthors.size,
                            key = { idx -> "follow_recommend_$idx" },
                        ) { idx ->
                            val author = recommendAuthors[idx]
                            FollowAuthorRecommendRow(author = author)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(Color(0xFFF0F0F0)),
                            )
                        }
                        // MVPTask #7: 朋友圈列表 Paging（每行带关注按钮）
                        item(key = "follow_friends_divider") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .background(Color(0xFFF5F5F5)),
                            )
                        }
                    }
                    if (channelKey == "shenzhen") {
                        item(key = "shenzhen_weather") {
                            ShenzhenWeatherStrip()
                        }
                        item(key = "shenzhen_local_hot") {
                            ShenzhenLocalHotBanner(title = "一起家庭悲剧背后的三重追问")
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(Color(0xFFEDEDED)),
                            )
                        }
                        // MVPTask #6: 视频轮播（第一栏位置）
                        item(key = "shenzhen_video_carousel") {
                            ShenzhenVideoCarousel()
                        }
                    }
                    if (channelKey == "finance") {
                        item(key = "finance_risk") {
                            FinanceRiskNotice()
                        }
                        item(key = "finance_stock_card") {
                            FinanceStockIndexCard(
                                indices = listOf(
                                    StockIndex("上证指数", "4027.74", "-30.04", "-0.74%"),
                                    StockIndex("深证成指", "15314.70", "-346.87", "-2.21%"),
                                    StockIndex("创业板指", "3957.94", "-130.95", "-3.20%"),
                                ),
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .background(Color(0xFFF5F5F5)),
                            )
                        }
                    }
                    if (channelKey == "military") {
                        item(key = "military_header") {
                            MilitaryRankHeader()
                        }
                        val militaryRanks = listOf(
                            "普京喊话俄将士兵：兄弟们继续战斗吧" to false,
                            "有理儿有面：日本不只想改南京大屠杀" to false,
                            "乌方证实袭击俄境内军火库及油库" to true,
                        )
                        items(
                            count = militaryRanks.size,
                            key = { idx -> "military_rank_$idx" },
                        ) { idx ->
                            val (title, isNew) = militaryRanks[idx]
                            MilitaryRankItem(
                                title = title,
                                isNew = isNew,
                                onClick = { onEvent(HomeUiEvent.OnCardClick("military_$idx")) },
                            )
                            if (idx < militaryRanks.size - 1) {
                                MilitaryRankDivider()
                            }
                        }
                        item(key = "military_divider") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .background(Color(0xFFF5F5F5)),
                            )
                        }
                    }
                    if (channelKey == "audio") {
                        item(key = "audio_sub_tabs") {
                            var selectedSubTab by remember { mutableIntStateOf(1) }
                            AudioSubTabs(
                                tabs = listOf("听头条", "听书", "听音乐"),
                                selectedIndex = selectedSubTab,
                                onTabSelected = { selectedSubTab = it },
                            )
                        }
                        item(key = "audio_hot_header") {
                            AudioSectionHeader(
                                title = "热门榜",
                                rightTabs = listOf("完结榜", "高分榜"),
                            )
                        }
                        // 双列热门榜（6 项，按设计稿 2 列 × 3 行）
                        val hotItems = listOf(
                            AudioHot(1, "9.4", "《万历十五年》精读", "名著解读", "14.2万人在听"),
                            AudioHot(2, "8.7", "曾仕强讲易经", "励志成长", "1.8万人在听"),
                            AudioHot(3, "7.5", "内在清醒：人际关系的简化", "情感故事", "975人在听"),
                            AudioHot(4, "9.1", "回到明朝当王爷", "历史古装", "5.2万人在听"),
                            AudioHot(5, "8.7", "被退婚后，我诗仙的身份瞒不住了", "情节流脑", "3.6万人在听"),
                            AudioHot(6, "8.8", "人性的弱点", "励志成长", "2.1万人在听"),
                        )
                        items(
                            count = (hotItems.size + 1) / 2,
                            key = { idx -> "audio_hot_row_$idx" },
                        ) { rowIdx ->
                            val left = hotItems[rowIdx * 2]
                            val right = hotItems.getOrNull(rowIdx * 2 + 1)
                            Row(modifier = Modifier.fillMaxWidth()) {
                                AudioHotCard(
                                    rank = left.rank,
                                    coverUrl = "",
                                    rating = left.rating,
                                    title = left.title,
                                    tag = left.tag,
                                    listeners = left.listeners,
                                    onClick = { onEvent(HomeUiEvent.OnCardClick("audio_hot_${left.rank}")) },
                                    modifier = Modifier.weight(1f),
                                )
                                if (right != null) {
                                    AudioHotCard(
                                        rank = right.rank,
                                        coverUrl = "",
                                        rating = right.rating,
                                        title = right.title,
                                        tag = right.tag,
                                        listeners = right.listeners,
                                        onClick = { onEvent(HomeUiEvent.OnCardClick("audio_hot_${right.rank}")) },
                                        modifier = Modifier.weight(1f),
                                    )
                                } else {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(Color(0xFFF0F0F0)),
                            )
                        }
                        item(key = "audio_recommend_header") {
                            AudioSectionHeader(title = "相关推荐")
                        }
                        item(key = "audio_categories") {
                            var selectedCat by remember { mutableIntStateOf(0) }
                            AudioCategoryChips(
                                categories = listOf("全部", "总裁", "玄幻", "神医", "评书", "战神赘婿"),
                                selectedIndex = selectedCat,
                                onCategorySelected = { selectedCat = it },
                            )
                        }
                        val recommendItems = listOf(
                            AudioRecommend("坠机前夜的林彪", "1971年9月8日，林彪下达反革命武装政变手令...", "名人传  |  连载中  |  11.6万人在听", "8.7"),
                            AudioRecommend("抗美援朝解密全史", "1945年8月8日，根据雅尔塔协定，苏联对日宣战...", "战争史  |  已完结  |  3.8万人在听", "9.0"),
                            AudioRecommend("对越自卫还击战", "1979年2月17日，《人民日报》发表声明...", "军事历史  |  完结  |  2.1万人在听", "8.6"),
                        )
                        items(
                            count = recommendItems.size,
                            key = { idx -> "audio_rec_$idx" },
                        ) { idx ->
                            val item = recommendItems[idx]
                            AudioRecommendItem(
                                title = item.title,
                                description = item.description,
                                tag = item.tag,
                                rating = item.rating,
                                onClick = { onEvent(HomeUiEvent.OnCardClick("audio_rec_$idx")) },
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(Color(0xFFF0F0F0)),
                            )
                        }
                    }
                    if (channelKey == "novel") {
                        item(key = "novel_bookshelf_header") {
                            NovelSectionHeader(title = "为你推荐", rightText = "换一批")
                        }
                        item(key = "novel_bookshelf") {
                            NovelBookshelfRow(
                                books = listOf(
                                    NovelBook("n1", "靠着模拟器,我把宗门带飞了", "玄幻", coverUrl = "https://picsum.photos/seed/n1/240/320"),
                                    NovelBook("n2", "家族修仙,我是老祖", "修仙", coverUrl = "https://picsum.photos/seed/n2/240/320"),
                                    NovelBook("n3", "全属性武道", "都市", coverUrl = "https://picsum.photos/seed/n3/240/320"),
                                ),
                                bookshelfCount = 12,
                                onBookClick = { onEvent(HomeUiEvent.OnCardClick(it)) },
                                onBookshelfClick = { /* TODO: 跳书架页 */ },
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(Color(0xFFEDEDED)),
                            )
                        }
                        item(key = "novel_rank_header") {
                            NovelSectionHeader(title = "排行榜", rightText = "更多")
                        }
                        item(key = "novel_rank_tabs") {
                            var selectedRankTab by remember { mutableIntStateOf(0) }
                            NovelRankingTabs(
                                tabs = listOf("推荐榜", "完结榜", "阅读榜", "高分榜", "热搜榜"),
                                selectedIndex = selectedRankTab,
                                onTabSelected = { selectedRankTab = it },
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(Color(0xFFEDEDED)),
                            )
                        }
                        val rankItems = listOf(
                            NovelBook("r1", "都在等起义,我杀穿河工营先反了", tag = "", author = "历史系之狼", category = "历史脑洞", heat = "2273万热度", coverUrl = "https://picsum.photos/seed/rank1/120/160"),
                            NovelBook("r2", "开局无限分身,我一人包围全...", tag = "", author = "中原五百", category = "玄幻脑洞", heat = "1813万热度", coverUrl = "https://picsum.photos/seed/rank2/120/160"),
                            NovelBook("r3", "名义:家父赵德汉,我冒充成...", tag = "", author = "萌俊", category = "男频", heat = "1659万热度", coverUrl = "https://picsum.photos/seed/rank3/120/160"),
                            NovelBook("r4", "开局召唤策...灾,横推修...", tag = "", author = "天云空", category = "玄幻脑洞", heat = "1200万热度", coverUrl = "https://picsum.photos/seed/rank4/120/160"),
                            NovelBook("r5", "万倍返还...圣母,逆袭...", tag = "", author = "锦鲤先生", category = "玄幻脑洞", heat = "980万热度", coverUrl = "https://picsum.photos/seed/rank5/120/160"),
                            NovelBook("r6", "苟到成仙,报把修仙界...", tag = "", author = "南风知我意", category = "玄幻脑洞", heat = "750万热度", coverUrl = "https://picsum.photos/seed/rank6/120/160"),
                        )
                        items(
                            count = rankItems.size,
                            key = { idx -> "novel_rank_$idx" },
                        ) { idx ->
                            val book = rankItems[idx]
                            NovelRankItem(
                                rank = idx + 1,
                                book = book,
                                onClick = { onEvent(HomeUiEvent.OnCardClick("novel_rank_$idx")) },
                            )
                            if (idx < rankItems.size - 1) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(Color(0xFFF0F0F0)),
                                )
                            }
                        }
                        item(key = "novel_recommend_header") {
                            NovelSectionHeader(title = "猜你喜欢", rightText = "分类")
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(Color(0xFFEDEDED)),
                            )
                        }
                        val recommendBooks = listOf(
                            NovelRecommendBook(
                                "港综:古惑仔天天做善事这正经吗",
                                "有人说他是香港教父 有人说他是第一慈善家",
                                "男频衍生", "完结", "5.4万人在读", "7.6",
                            ),
                            NovelRecommendBook(
                                "都在等起义,我杀穿河工营先反了",
                                "(评分早就出了,后面不一定会涨的,狗头保命) 胡族入主中原二十年...",
                                "历史脑洞", "完读", "6万+", "6.3",
                            ),
                        )
                        items(
                            count = recommendBooks.size,
                            key = { idx -> "novel_rec_$idx" },
                        ) { idx ->
                            val book = recommendBooks[idx]
                            NovelRecommendItem(
                                book = book,
                                onClick = { onEvent(HomeUiEvent.OnCardClick("novel_rec_$idx")) },
                            )
                            if (idx < recommendBooks.size - 1) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(Color(0xFFF0F0F0)),
                                )
                            }
                        }
                    }
                    if (channelKey == "sports") {
                        item(key = "sports_banner") {
                            SportsBanner()
                        }
                        item(key = "sports_categories") {
                            var selectedCat by remember { mutableIntStateOf(0) }
                            SportsCategoryChips(
                                categories = listOf("直播", "NBA", "CBA", "世界杯"),
                                selectedIndex = selectedCat,
                                onCategorySelected = { selectedCat = it },
                            )
                        }
                        item(key = "sports_match_row_1") {
                            SportsMatchRow(
                                matches = listOf(
                                    SportsMatch(
                                        time = "今日 08:00",
                                        status = "已结束",
                                        league = "国际友谊赛",
                                        homeTeam = "阿根廷",
                                        homeScore = 2,
                                        awayTeam = "洪都拉斯",
                                        awayScore = 0,
                                        homeColor = Color(0xFF74ACDF),
                                        awayColor = Color(0xFF0073CF),
                                    ),
                                    SportsMatch(
                                        time = "今日 06:00",
                                        status = "已结束",
                                        league = "国际友谊赛",
                                        homeTeam = "巴西",
                                        homeScore = 2,
                                        awayTeam = "埃及",
                                        awayScore = 1,
                                        homeColor = Color(0xFFFFC83D),
                                        awayColor = Color(0xFFCE1126),
                                    ),
                                ),
                                onItemClick = { onEvent(HomeUiEvent.OnCardClick("sports_match_$it")) },
                            )
                        }
                    }
                    // 热门作者横向轮播: 仅推荐 tab
                    if (channelKey == "recommend") {
                        // MVPTask #3: 推荐频道 - 5 条置顶新闻（紧凑列表）+ 资讯速递卡片 + 多种图文动态混合
                        // 取真实 Paging 数据前 5 条作为置顶
                        val pinnedNews = lazyPagingItems.itemSnapshotList.items.take(5)
                        items(
                            count = pinnedNews.size,
                            key = { idx -> "recommend_top_${pinnedNews[idx].id}" },
                        ) { idx ->
                            val card = pinnedNews[idx]
                            // 把 FeedCard 转为 FeedCard.TextTop 显示（数据层已确保前 5 条是 text_top）
                            val topCard = card as? FeedCard.TextTop
                                ?: FeedCard.TextTop(
                                    id = card.id,
                                    title = card.title,
                                    source = card.source,
                                    commentCount = card.commentCount,
                                    publishTime = card.publishTime,
                                    sourceUrl = card.sourceUrl,
                                    isTop = true,
                                )
                            // 必须包一层 Box.clickable，否则置顶卡无法跳详情页
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onCardClick(card) },
                            ) {
                                TextTopCard(card = topCard)
                            }
                        }
                        // MVPTask #3: 资讯速递卡片（蓝色水滴 + 标题 + 大图）
                        item(key = "recommend_flash") {
                            RecommendFlashCard()
                        }
                    }
                    // 顶部 header 区域：MVPTask #5 「上次看到这里」断点续读
                    // 显示条件：lastSeenCardId 存在 且 (没找到 index OR 还没滚到 index 位置)
                    item(key = "header_separator") {
                        val canShowHint = !lastSeenCardId.isNullOrBlank() &&
                            (lastSeenIndex < 0 || firstVisibleIndex <= lastSeenIndex)
                        if (canShowHint) {
                            LastSeenHint(
                                relativeMinutes = relativeMinutes,
                                onClick = {
                                    onEvent(HomeUiEvent.OnLastSeenHintClicked)
                                    coroutineScope.launch {
                                        val target = if (lastSeenIndex > 0) lastSeenIndex else 0
                                        listState.animateScrollToItem(target)
                                    }
                                },
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White)
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "以下为新内容",
                                    color = Color(0xFF999999),
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }

                    // Paging 渲染：过滤掉已在置顶部分展示的 item
                    val allItems = lazyPagingItems.itemSnapshotList.items
                    // 推荐频道：跳过前 5 条置顶
                    val skipCount = if (channelKey == "recommend") 5 else 0
                    val visibleItems = if (skipCount > 0) allItems.drop(skipCount) else allItems
                    items(
                        count = visibleItems.size,
                        key = { idx -> "paging_${visibleItems[idx].id}" },
                    ) { idx ->
                        val card = visibleItems[idx]
                        // 注意：lastSeenCardId 的全局 index 也要重新映射（基于 allItems）
                        val globalIndex = allItems.indexOfFirst { it.id == card.id }
                        Column {
                            // 当 index == lastSeenIndex 时，在该卡片前再插一个 hint
                            if (globalIndex >= 0 && globalIndex == lastSeenIndex &&
                                showLastSeenHint && firstVisibleIndex == 0
                            ) {
                                LastSeenHint(
                                    relativeMinutes = relativeMinutes,
                                    onClick = {
                                        onEvent(HomeUiEvent.OnLastSeenHintClicked)
                                        coroutineScope.launch {
                                            listState.animateScrollToItem(globalIndex)
                                        }
                                    },
                                )
                            }
                            FeedCardItem(
                                card = card,
                                onClick = remember(card.id) { { onCardClick(card) } },
                            )
                        }
                    }

                    lazyPagingItems.apply {
                        if (loadState.append is LoadState.Loading) {
                            item(key = "footer_loading") {
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
        }
    }
}

@Composable
private fun SearchResultList(
    results: List<FeedCard>,
    onCardClick: (FeedCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = 8.dp),
    ) {
        items(results, key = { it.id }) { card ->
            FeedCardItem(
                card = card,
                onClick = { onCardClick(card) },
            )
        }
    }
}

@Composable
private fun HomeTopBar(
    uiState: HomeUiState,
    currentTab: String,
    searchQuery: String,
    onEvent: (HomeUiEvent) -> Unit,
) {
    val tabs = remember {
        listOf(
            "follow" to "关注",
            "recommend" to "推荐",
            "hot" to "热榜",
            "shenzhen" to "深圳",
            "novel" to "小说",
            "discover" to "发现",
            "video" to "视频",
            "finance" to "财经",
        )
    }
    val isSearching = (uiState as? HomeUiState.Success)?.isSearching ?: false

    Column(
        modifier = Modifier
            .fillMaxWidth(),
    ) {
        // 顶部红色品牌栏（Logo + 搜索 + AI 入口）
        // MVPTask #1: 用 statusBarsPadding() 把红色 Box 延伸覆盖到状态栏区域，
        // 让状态栏完全红色，无灰色空白
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(RedMain)
                .statusBarsPadding(),
        ) {
            if (!isSearching) {
                BrandTopRow()
            } else {
                SearchInputBar(
                    query = searchQuery,
                    onQueryChange = { onEvent(HomeUiEvent.OnSearchQueryChanged(it)) },
                    onSubmit = { onEvent(HomeUiEvent.OnSearchSubmit) },
                    onDismiss = { onEvent(HomeUiEvent.OnSearchDismiss) },
                )
            }
        }

        // 白色 Tab 行：选中=红字+红色下划线，未选中=黑字
        val selectedTabIndex = tabs.indexOfFirst { it.first == currentTab }.coerceAtLeast(0)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .shadow(2.dp),
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = Color.White,
                contentColor = Color(0xFF1A1A1A),
                // MVPTask #2: Tab 行边缘更紧凑，缩短 padding 让按钮之间更近
                edgePadding = 4.dp,
                divider = {},
                indicator = { tabPositions ->
                    if (selectedTabIndex < tabPositions.size) {
                        // MVPTask #2: 红线比 2 字符文本宽度短一些
                        // 16sp Bold "推荐" ≈ 36px ≈ 9dp 宽，所以红线总宽应 < 24dp（含 padding）
                        // horizontal padding = 16dp 让红线仅 2 字符宽度一半左右
                        SecondaryIndicator(
                            modifier = Modifier
                                .tabIndicatorOffset(tabPositions[selectedTabIndex])
                                .padding(horizontal = 16.dp),
                            height = 3.dp,
                            color = RedMain,
                        )
                    }
                },
            ) {
                tabs.forEach { (key, label) ->
                    val selected = key == currentTab
                    Tab(
                        selected = selected,
                        onClick = { onEvent(HomeUiEvent.OnTabSelected(key)) },
                        // MVPTask #2: 减小 Tab 之间水平 padding，让按钮更紧凑
                        modifier = Modifier.padding(horizontal = 0.dp),
                        text = {
                            // 设计稿：选中 16sp Bold / 未选中 15sp Regular
                            // 文字 padding 收紧让红线距离更近
                            Box(modifier = Modifier.padding(horizontal = 8.dp)) {
                                Text(
                                    text = label,
                                    fontSize = if (selected) 16.sp else 15.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) RedMain else Color(0xFF1A1A1A),
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun WeatherHeader() {
    BrandTopRow()
}

@Composable
private fun BrandTopRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            // 设计稿：Logo 文字 17sp Medium
            Text("头", color = RedMain, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White)
                .clickable { /* TODO: 触发搜索 */ }
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = Color(0xFFCCCCCC),
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                // 设计稿：searchHint 13sp Regular
                // 热搜词用单条完整短语，# 号包裹 + 前缀"热搜"小标签，避免被截断粘在一起
                Text(
                    text = "热搜  高考首日现场直击",
                    color = Color(0xFF999999),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        // AI 入口：去重 — 之前同时显示红色"AI"徽标和"豆包 AI"文字
        // 现在只用一个圆形图标 + tooltip，避免与底部"豆包 AI"标签重复
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color(0xFFFFCDB2))
                .clickable { /* TODO: 豆包 AI */ },
            contentAlignment = Alignment.Center,
        ) {
            // 用一个 16sp 的 "AI" 文字作图标（无独立 drawable 时用 emoji/文字占位）
            Text("AI", color = Color(0xFFD81E06), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SearchPlaceholderBar(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = Color(0xFFCCCCCC),
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "今天发生了什么",
                color = Color(0xFFCCCCCC),
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun SearchInputBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .height(36.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = Color.White,
            )
        }
        Spacer(Modifier.width(4.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            singleLine = true,
            cursorBrush = SolidColor(RedMain),
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.Black),
            decorationBox = { innerTextField ->
                if (query.isEmpty()) {
                    Text(
                        text = "输入关键词搜索",
                        color = Color.Gray,
                        fontSize = 14.sp,
                    )
                }
                innerTextField()
            },
        )
        Spacer(Modifier.width(4.dp))
        TextButton(onClick = onSubmit) {
            Text("搜索", color = Color.White, fontSize = 14.sp)
        }
    }
}

// ── 频道内辅助数据类 ──────────────────────────────────────────────────────────
private data class AudioHot(
    val rank: Int,
    val rating: String,
    val title: String,
    val tag: String,
    val listeners: String,
)

private data class AudioRecommend(
    val title: String,
    val description: String,
    val tag: String,
    val rating: String,
)
@Composable
private fun DebugDialog(showDialog: Boolean, onDismiss: () -> Unit) {
    if (!showDialog) return
    var selectedDelay by remember { mutableStateOf(DebugControls.networkDelayMs) }
    var simulateError by remember { mutableStateOf(DebugControls.shouldSimulateError) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("调试控制") },
        text = {
            Column {
                Text("网络延迟模拟", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                DebugControls.delayOptions.forEach { delay ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedDelay = delay
                                DebugControls.networkDelayMs = delay
                            }
                            .padding(vertical = 2.dp),
                    ) {
                        RadioButton(
                            selected = selectedDelay == delay,
                            onClick = {
                                selectedDelay = delay
                                DebugControls.networkDelayMs = delay
                            },
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(DebugControls.delayLabel(delay), fontSize = 13.sp)
                    }
                }

                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0xFFEEEEEE)),
                )
                Spacer(Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            simulateError = !simulateError
                            DebugControls.shouldSimulateError = simulateError
                        }
                        .padding(vertical = 4.dp),
                ) {
                    Checkbox(
                        checked = simulateError,
                        onCheckedChange = {
                            simulateError = it
                            DebugControls.shouldSimulateError = it
                        },
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("模拟网络错误", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Text(
                    text = "开启后，下次请求将返回错误状态",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(start = 48.dp),
                )

                Spacer(Modifier.height(8.dp))

                TextButton(
                    onClick = {
                        DebugControls.reset()
                        selectedDelay = 0L
                        simulateError = false
                    },
                ) {
                    Text("重置所有调试选项", fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

// ── Preview ──────────────────────────────────────────────────────────────────
private val mockFeedItems = listOf(
    FeedCard.TextTop(id = "1", title = "Title 1", source = "Source 1", commentCount = 12876, publishTime = "3 hours ago"),
    FeedCard.LeftTextRightImage(id = "2", title = "Title 2", source = "Source 2", commentCount = 5432, publishTime = "5 hours ago", imageUrl = "https://picsum.photos/seed/news2/400/300"),
    FeedCard.LargeImage(id = "3", title = "Title 3", source = "Source 3", commentCount = 9876, publishTime = "1 hour ago", imageUrl = "https://picsum.photos/seed/news3/800/450"),
    FeedCard.LeftTextRightImage(id = "5", title = "Title 5", source = "Source 5", commentCount = 3456, publishTime = "6 hours ago", imageUrl = "https://picsum.photos/seed/news5/400/300"),
    FeedCard.TextTop(id = "6", title = "Title 6", source = "Source 6", commentCount = 5678, publishTime = "4 hours ago"),
    FeedCard.LargeImage(id = "7", title = "Title 7", source = "Source 7", commentCount = 7890, publishTime = "2 hours ago", imageUrl = "https://picsum.photos/seed/news7/800/450"),
)

@Preview(name = "Success", showBackground = true, showSystemUi = true)
@Composable
private fun HomeScreenSuccessPreview() {
    com.example.toutiao.ui.theme.ToutiaoFeedDemoTheme {
        val flow = flowOf(PagingData.from(mockFeedItems))
        key("preview_success") {
            val lazyItems = flow.collectAsLazyPagingItems()
            HomeScreenContent(
                uiState = HomeUiState.Success(currentTab = "recommend"),
                currentTab = "recommend",
                searchQuery = "",
                searchResults = emptyList(),
                feedPagingData = flow,
                onEvent = {},
                onCardClick = {},
                onQuickActionClick = {},
            )
        }
    }
}

@Preview(name = "Loading", showBackground = true)
@Composable
private fun HomeScreenLoadingPreview() {
    com.example.toutiao.ui.theme.ToutiaoFeedDemoTheme {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = RedMain)
        }
    }
}

@Preview(name = "Error", showBackground = true)
@Composable
private fun HomeScreenErrorPreview() {
    com.example.toutiao.ui.theme.ToutiaoFeedDemoTheme {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("网络连接失败", color = Color.Gray)
                Spacer(Modifier.height(8.dp))
                Button(onClick = {}) {
                    Text("重试")
                }
            }
        }
    }
}

@Preview(name = "Empty", showBackground = true)
@Composable
private fun HomeScreenEmptyPreview() {
    com.example.toutiao.ui.theme.ToutiaoFeedDemoTheme {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text("暂无内容", color = Color.Gray)
        }
    }
}
