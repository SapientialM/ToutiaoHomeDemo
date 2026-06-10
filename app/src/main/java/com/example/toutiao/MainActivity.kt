package com.example.toutiao

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import com.example.toutiao.ui.theme.RedMain
import com.example.toutiao.presentation.common.AppBottomNav
import com.example.toutiao.presentation.detail.NewsDetailScreen
import com.example.toutiao.presentation.earn.EarnScreen
import com.example.toutiao.presentation.home.HomeScreen
import com.example.toutiao.presentation.home.HomeViewModel
import com.example.toutiao.presentation.mall.MallScreen
import com.example.toutiao.presentation.mall.sub.CouponsScreen
import com.example.toutiao.presentation.mall.sub.FollowedShopsScreen
import com.example.toutiao.presentation.mall.sub.OrderListScreen
import com.example.toutiao.presentation.notification.NotificationScreen
import com.example.toutiao.presentation.profile.ProfileScreen
import com.example.toutiao.presentation.task.TaskScreen
import com.example.toutiao.presentation.tools.AllFunctionsScreen
import com.example.toutiao.presentation.tools.BookshelfScreen
import com.example.toutiao.presentation.tools.CreatorCenterScreen
import com.example.toutiao.presentation.tools.HistoryScreen
import com.example.toutiao.presentation.video.VideoScreen
import com.example.toutiao.presentation.wallet.WalletScreen
import com.example.toutiao.ui.theme.ToutiaoFeedDemoTheme
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // MVPTask #1: 让红色 topbar 铺到状态栏
        // 把 status bar / nav bar 的 scrim 都设为 RedMain（不透明），
        // 这样状态栏区域会被 HomeTopBar 的红色 Box 完全覆盖，无灰色留白。
        val brandRedArgb = RedMain.value.toInt()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(brandRedArgb),
            navigationBarStyle = SystemBarStyle.dark(brandRedArgb),
        )
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // 显式把 status bar / nav bar 设为品牌红色（铺满）
        window.statusBarColor = brandRedArgb
        window.navigationBarColor = brandRedArgb
        // 让 decor view 不去插入 insets（顶部 Box 自己处理 statusBar 区域）
        window.decorView.fitsSystemWindows = false
        setContent {
            ToutiaoFeedDemoTheme {
                AppRoot(homeViewModel = viewModel)
            }
        }
    }
}

/**
 * App 根容器：底部导航 + 主页面 + 详情页/子页（顶层覆盖）
 *
 * subPage 当前支持: 消息中心 (从我的页 消息图标进入)
 */
@Composable
private fun AppRoot(homeViewModel: HomeViewModel) {
    var selectedBottomNav by rememberSaveable { mutableIntStateOf(0) }
    // 新闻详情页状态：null = 不显示详情页
    var detailTarget by rememberSaveable(stateSaver = DetailTarget.Saver) {
        mutableStateOf<DetailTarget?>(null)
    }
    // 子页面: null = 不显示
    var subPage by rememberSaveable(stateSaver = SubPage.Saver) {
        mutableStateOf<SubPage?>(null)
    }

    val isOverlayOpen = detailTarget != null || subPage != null

    Scaffold(
        // MVPTask #1: 让 Scaffold 不强行插入 statusBar inset，让 HomeScreen 内的
        // 红色 topbar 自行铺到 statusBar 区域
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (!isOverlayOpen) {
                AppBottomNav(
                    selectedIndex = selectedBottomNav,
                    onSelected = { selectedBottomNav = it },
                )
            }
        },
        containerColor = Color(0xFFF5F5F5),
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            // 主页面层
            AnimatedContent(
                targetState = selectedBottomNav,
                transitionSpec = {
                    fadeIn(animationSpec = tween(200)) togetherWith
                        fadeOut(animationSpec = tween(200))
                },
                label = "main_screen",
            ) { navIndex ->
                when (navIndex) {
                    0 -> HomeScreen(
                        viewModel = homeViewModel,
                        onCardClick = { feedCard ->
                            Timber.d("MainActivity onCardClick — title=${feedCard.title.take(20)}, sourceUrl=${feedCard.sourceUrl}")
                            feedCard.sourceUrl?.let { url ->
                                detailTarget = DetailTarget(
                                    sourceUrl = url,
                                    fallbackTitle = feedCard.title,
                                )
                            } ?: Timber.w("MainActivity onCardClick — no sourceUrl, skip")
                        },
                    )
                    1 -> VideoScreen()
                    2 -> EarnScreen()
                    3 -> MallScreen(
                        onOrderClick = { subPage = SubPage.OrderList },
                        onCouponsClick = { subPage = SubPage.Coupons },
                        onFollowedShopsClick = { subPage = SubPage.FollowedShops },
                    )
                    4 -> ProfileScreen(
                        onNotificationsClick = { subPage = SubPage.Notifications },
                        onWalletClick = { subPage = SubPage.Wallet },
                        onOrderClick = { subPage = SubPage.OrderList },
                        onCreatorClick = { subPage = SubPage.CreatorCenter },
                        onTasksClick = { subPage = SubPage.Tasks },
                        onHistoryClick = { subPage = SubPage.History },
                        onBookshelfClick = { subPage = SubPage.Bookshelf },
                        onAllFunctionsClick = { subPage = SubPage.AllFunctions },
                    )
                }
            }

            // 新闻详情页层（覆盖在主页之上）
            detailTarget?.let { target ->
                NewsDetailScreen(
                    sourceUrl = target.sourceUrl,
                    fallbackTitle = target.fallbackTitle,
                    onBack = { detailTarget = null },
                )
            }

            // 子页面层
            subPage?.let { page ->
                when (page) {
                    SubPage.Notifications -> NotificationScreen(
                        onBack = { subPage = null },
                    )
                    SubPage.Wallet -> WalletScreen(
                        onBack = { subPage = null },
                    )
                    SubPage.OrderList -> OrderListScreen(
                        onBack = { subPage = null },
                    )
                    SubPage.Coupons -> CouponsScreen(
                        onBack = { subPage = null },
                    )
                    SubPage.FollowedShops -> FollowedShopsScreen(
                        onBack = { subPage = null },
                    )
                    SubPage.CreatorCenter -> CreatorCenterScreen(
                        onBack = { subPage = null },
                    )
                    SubPage.Tasks -> TaskScreen()
                    SubPage.History -> HistoryScreen(
                        onBack = { subPage = null },
                    )
                    SubPage.Bookshelf -> BookshelfScreen(
                        onBack = { subPage = null },
                    )
                    SubPage.AllFunctions -> AllFunctionsScreen(
                        onBack = { subPage = null },
                    )
                }
            }
        }
    }
}

/**
 * 新闻详情页跳转参数（用 Saver 持久化以应对 Activity 重建）
 */
data class DetailTarget(
    val sourceUrl: String,
    val fallbackTitle: String?,
) {
    companion object {
        val Saver: Saver<DetailTarget?, Any> = listSaver(
            save = { it?.let { listOf(it.sourceUrl, it.fallbackTitle.orEmpty()) } ?: emptyList() },
            restore = {
                if (it.isEmpty()) null
                else DetailTarget(
                    sourceUrl = it[0] as String,
                    fallbackTitle = (it[1] as String).takeIf { s -> s.isNotEmpty() },
                )
            },
        )
    }
}

/** 子页面类型 */
enum class SubPage {
    Notifications,
    Wallet,
    OrderList,
    Coupons,
    FollowedShops,
    CreatorCenter,
    Tasks,
    History,
    Bookshelf,
    AllFunctions,
    ;

    companion object {
        val Saver: Saver<SubPage?, Any> = listSaver(
            save = { it?.let { listOf(it.name) } ?: emptyList() },
            restore = { if (it.isEmpty()) null else SubPage.valueOf(it[0] as String) },
        )
    }
}
