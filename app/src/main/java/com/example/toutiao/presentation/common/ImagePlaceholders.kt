package com.example.toutiao.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter

/**
 * 共享的 placeholder / error painter（提到顶层避免重组时重复分配）
 *
 * ColorPainter 是稳定的 Painter 引用，@Composable remember 后
 * 整个进程复用同一个实例，LazyColumn 滚动时不会触发额外的 painter 创建。
 */
@Composable
fun rememberImagePlaceholder(): Painter = remember {
    ColorPainter(Color(0xFFF0F0F0))
}

@Composable
fun rememberImageError(): Painter = remember {
    ColorPainter(Color(0xFFE0E0E0))
}
