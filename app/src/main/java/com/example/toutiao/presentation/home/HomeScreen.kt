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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
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
import com.example.toutiao.presentation.home.components.LargeImageCard
import com.example.toutiao.presentation.home.components.LeftTextRightImageCard
import com.example.toutiao.presentation.home.components.TextTopCard
import com.example.toutiao.presentation.home.components.VideoCard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

// =============================================================================
// HomeScreen — MVI 入口 + Tab 切换数据隔离
//
// 关键设计：用 key(currentTab) 包裹 Paging3 数据收集和列表渲染，
// Tab 切换时 Compose 丢弃旧 key 内的所有状态（包括 LazyPagingItems 和
// LazyListState），新 key 内从 Loading 态全新开始，消除旧数据闪现。
// =============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    var showDebugDialog by remember { mutableStateOf(false) }

    HomeScreenContent(
        uiState = uiState,
        currentTab = currentTab,
        searchQuery = searchQuery,
        searchResults = searchResults,
        feedPagingData = viewModel.feedPagingData,
        showDebugDialog = showDebugDialog,
        onToggleDebug = { showDebugDialog = !showDebugDialog },
        onEvent = viewModel::onEvent,
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
    showDebugDialog: Boolean,
    onToggleDebug: () -> Unit,
    onEvent: (HomeUiEvent) -> Unit,
) {
    val isSearching = (uiState as? HomeUiState.Success)?.isSearching ?: false

    DebugDialog(showDialog = showDebugDialog, onDismiss = onToggleDebug)

    Scaffold(
        topBar = {
            HomeTopBar(
                uiState = uiState,
                currentTab = currentTab,
                searchQuery = searchQuery,
                onToggleDebug = onToggleDebug,
                onEvent = onEvent,
            )
        },
        containerColor = Color(0xFFF5F5F5),
    ) { innerPadding ->
        when {
            isSearching && searchQuery.isNotEmpty() && searchResults.isNotEmpty() -> {
                SearchResultList(
                    results = searchResults,
                    onCardClick = { onEvent(HomeUiEvent.OnCardClick(it)) },
                    modifier = Modifier.padding(innerPadding),
                )
            }
            isSearching && searchQuery.isNotEmpty() && searchResults.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("点击搜索按钮查看结果", color = Color.Gray)
                }
            }
            else -> {
                // key(currentTab): Tab 切换时销毁整个子树（LazyPagingItems + LazyListState），
                // 新子树从 Loading 态开始，避免：
                //  1. 旧 Tab 数据闪现（LazyPagingItems 是全新创建的，初始 0 条）
                //  2. 滚动位置残留（LazyListState 是全新创建的，位置为 0）
                key(currentTab) {
                    val lazyPagingItems = feedPagingData.collectAsLazyPagingItems()
                    val listState = remember { LazyListState() }

                    // Tab 切换时 key 变化，LazyListState 是全新的，但 Paging3 的
                    // differ 过程可能在数据到达前就把列表滚离顶部。这里显式保证回顶。
                    LaunchedEffect(Unit) {
                        listState.scrollToItem(0)
                    }

                    PagingFeedList(
                        listState = listState,
                        lazyPagingItems = lazyPagingItems,
                        onCardClick = { onEvent(HomeUiEvent.OnCardClick(it)) },
                        modifier = Modifier.padding(innerPadding),
                    )
                }
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
    onCardClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val refreshLoadState = lazyPagingItems.loadState.refresh
    val isRefreshing = refreshLoadState is LoadState.Loading && lazyPagingItems.itemCount > 0
    val isInitialLoading = refreshLoadState is LoadState.Loading && lazyPagingItems.itemCount == 0
    val isEmpty = refreshLoadState is LoadState.NotLoading && lazyPagingItems.itemCount == 0
    val isError = refreshLoadState is LoadState.Error && lazyPagingItems.itemCount == 0
    val errorMessage = (refreshLoadState as? LoadState.Error)?.error?.message ?: "加载失败"

    // Tab 切换：key(currentTab) 重建整个子树，LazyListState 初始位置为 0，
    // 天然回到顶部。下拉刷新：LaunchedEffect 监听 loadState.refresh 变化，
    // 当 LoadState 变为 NotLoading 且有数据时，scrollToItem(0) 回顶。
    LaunchedEffect(lazyPagingItems.loadState.refresh) {
        if (lazyPagingItems.loadState.refresh is LoadState.NotLoading
            && lazyPagingItems.itemCount > 0
        ) {
            listState.scrollToItem(0)
        }
    }

    when {
        isInitialLoading -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = Color(0xFFD81E06))
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
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        count = lazyPagingItems.itemCount,
                        key = lazyPagingItems.itemKey { it.id },
                    ) { index ->
                        val card = lazyPagingItems[index]
                        if (card != null) {
                            Box(modifier = Modifier.clickable { onCardClick(card.id) }) {
                                when (card) {
                                    is FeedCard.TextTop -> TextTopCard(card)
                                    is FeedCard.LeftTextRightImage -> LeftTextRightImageCard(card)
                                    is FeedCard.LargeImage -> LargeImageCard(card)
                                    is FeedCard.Video -> VideoCard(card)
                                }
                            }
                        }
                    }

                    lazyPagingItems.apply {
                        if (loadState.append is LoadState.Loading) {
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
        }
    }
}

@Composable
private fun SearchResultList(
    results: List<FeedCard>,
    onCardClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(results, key = { it.id }) { card ->
            Box(modifier = Modifier.clickable { onCardClick(card.id) }) {
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

@Composable
private fun HomeTopBar(
    uiState: HomeUiState,
    currentTab: String,
    searchQuery: String,
    onToggleDebug: () -> Unit,
    onEvent: (HomeUiEvent) -> Unit,
) {
    val tabs = listOf("recommend" to "推荐", "hot" to "热榜", "video" to "视频", "society" to "社会", "tech" to "科技")
    val isSearching = (uiState as? HomeUiState.Success)?.isSearching ?: false

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFD81E06))
            .statusBarsPadding(),
    ) {
        if (isSearching) {
            SearchInputBar(
                query = searchQuery,
                onQueryChange = { onEvent(HomeUiEvent.OnSearchQueryChanged(it)) },
                onSubmit = { onEvent(HomeUiEvent.OnSearchSubmit) },
                onDismiss = { onEvent(HomeUiEvent.OnSearchDismiss) },
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) {
                    SearchPlaceholderBar(onClick = { onEvent(HomeUiEvent.OnSearchClicked) })
                }
                IconButton(onClick = onToggleDebug, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Build,
                        contentDescription = "调试",
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

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

@Composable
private fun SearchPlaceholderBar(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.3f))
            .clickable(onClick = onClick),
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
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            singleLine = true,
            cursorBrush = SolidColor(Color(0xFFD81E06)),
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

// ── 调试面板 ─────────────────────────────────────────────────────────────────
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
    FeedCard.Video(id = "4", title = "Title 4", source = "Source 4", commentCount = 23456, publishTime = "2 hours ago", imageUrl = "https://picsum.photos/seed/news4/800/450", videoUrl = "", duration = "08:25"),
    FeedCard.LeftTextRightImage(id = "5", title = "Title 5", source = "Source 5", commentCount = 3456, publishTime = "6 hours ago", imageUrl = "https://picsum.photos/seed/news5/400/300"),
    FeedCard.TextTop(id = "6", title = "Title 6", source = "Source 6", commentCount = 5678, publishTime = "4 hours ago"),
    FeedCard.LargeImage(id = "7", title = "Title 7", source = "Source 7", commentCount = 7890, publishTime = "2 hours ago", imageUrl = "https://picsum.photos/seed/news7/800/450"),
    FeedCard.Video(id = "8", title = "Title 8", source = "Source 8", commentCount = 15678, publishTime = "1 hour ago", imageUrl = "https://picsum.photos/seed/news8/800/450", videoUrl = "", duration = "12:40"),
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
                showDebugDialog = false,
                onToggleDebug = {},
                onEvent = {},
            )
        }
    }
}
