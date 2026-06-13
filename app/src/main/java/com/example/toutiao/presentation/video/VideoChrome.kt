package com.example.toutiao.presentation.video

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

// =============================================================================
// VideoChrome — 视频页面共享工具
//  - SystemBarsController: 隐藏 / 显示 statusBar + navBar（沉浸式）
//  - HideSystemBars: 进入时自动隐藏,离开时(DisposableEffect.onDispose)恢复
//
// 被 VideoScreen / VideoDetailScreen 共用
// =============================================================================

/**
 * 进入 Composable 作用域时隐藏系统栏,退出作用域时恢复。
 *
 * 注意:必须放在顶层 Composable(例如 Screen 的根 Composable 内部),
 * 不能放在 Box/Scaffold 等容器 Composable 内,否则容器重组会让效果反复触发。
 */
@Composable
fun HideSystemBarsOnEnter() {
    val controller = rememberSystemBarsController()
    LaunchedEffect(Unit) { controller.hide() }
    DisposableEffect(Unit) {
        onDispose { controller.show() }
    }
}

@Composable
fun rememberSystemBarsController(): SystemBarsController {
    val context = LocalContext.current
    return remember(context) { SystemBarsController(context) }
}

class SystemBarsController(private val context: Context) {
    fun hide() {
        val activity = context.findActivity() ?: return
        val window = activity.window
        val controller = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior =
            androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
    }

    fun show() {
        val activity = context.findActivity() ?: return
        val window = activity.window
        val controller = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
        controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
