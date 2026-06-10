package com.example.toutiao.presentation.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toutiao.ui.theme.RedMain
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// =============================================================================
// AppToast — 自定义应用内 Toast（替代系统 Toast）
//
// 设计：
//  - 4 种类型：Success（绿）/ Error（红）/ Warning（黄）/ Info（灰）
//  - 位置：顶部状态栏下方（不挡底部导航/键盘）
//  - 动画：上滑淡入 + 下滑淡出
//  - 自动消失：短消息 2s / 长消息 3s
//
// 接入：
//  1. 在 MainActivity 根 Composable 用 CompositionLocalProvider 提供 AppToastHost
//  2. 调 LocalAppToastHost.current.showError("...") 显示
// =============================================================================

/** Toast 类型 */
enum class ToastType(val bg: Color, val icon: ImageVector) {
    Success(Color(0xFF1AAD19), Icons.Filled.CheckCircle),
    Error(Color(0xFFEC4040), Icons.Filled.Error),
    Warning(Color(0xFFFFB400), Icons.Filled.Warning),
    Info(Color(0xFF333333), Icons.Filled.Info),
}

/** Toast 数据 */
data class ToastState(
    val type: ToastType = ToastType.Info,
    val message: String = "",
    val durationMs: Long = if (message.length < 12) 2000L else 3000L,
)

/** 全局 Toast 总线：单一 MutableStateFlow（最新值覆盖） */
class AppToastHost {
    private val _state = MutableStateFlow<ToastState?>(null)
    val state: StateFlow<ToastState?> = _state.asStateFlow()

    fun show(toast: ToastState) {
        _state.value = toast
        timber.log.Timber.d("AppToast — show ${toast.type.name}: ${toast.message}")
    }

    fun showSuccess(message: String) = show(ToastState(ToastType.Success, message))
    fun showError(message: String) = show(ToastState(ToastType.Error, message))
    fun showWarning(message: String) = show(ToastState(ToastType.Warning, message))
    fun showInfo(message: String) = show(ToastState(ToastType.Info, message))

    /** 主动关闭当前 toast */
    fun dismiss() {
        _state.value = null
    }
}

/** CompositionLocal：在 Composable 树内通过 LocalAppToastHost.current 获取 */
val LocalAppToastHost = staticCompositionLocalOf<AppToastHost> {
    error("AppToastHost not provided. Wrap with CompositionLocalProvider { LocalAppToastHost provides host }")
}

/**
 * ToastHost — 在屏幕顶部渲染当前 toast
 * 放在 AppRoot 最外层 Compose 树中：
 *   CompositionLocalProvider(LocalAppToastHost provides appToastHost) {
 *       ToastHost()
 *       // ... 其他 UI
 *   }
 */
@Composable
fun ToastHost(modifier: Modifier = Modifier) {
    val host = LocalAppToastHost.current
    val state by host.state.collectAsState()
    val current = state
    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(top = 12.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        // 直接条件渲染（不用 AnimatedVisibility，避免 Box 组合顺序问题）
        if (current != null) {
            AppToastView(toast = current)
            // 自动消失
            LaunchedEffect(current) {
                delay(current.durationMs)
                host.dismiss()
            }
        }
    }
}

/** 单个 Toast 视图（顶部彩色条幅） */
@Composable
private fun AppToastView(toast: ToastState) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(0.92f)
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(10.dp),
                clip = false,
            )
            .clip(RoundedCornerShape(10.dp))
            .background(toast.type.bg)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = toast.type.icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = toast.message,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}