package com.example.toutiao.presentation.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// =============================================================================
// ProfileScreen — "我的"未登录页
//
// 当前为纯视觉占位，展示未登录状态的个人信息中心 UI。
// 所有点击事件预留，不做实际页面跳转。
// =============================================================================
@Composable
fun ProfileScreen() {
    Scaffold(
        topBar = { ProfileTopBar() },
        containerColor = Color(0xFFF5F5F5),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // 用户信息区
            ProfileHeaderCard()

            Spacer(Modifier.height(12.dp))

            // 功能入口列表
            ProfileMenuList()
        }
    }
}

@Composable
private fun ProfileTopBar() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFD81E06))
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = "我的",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ProfileHeaderCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 默认头像占位
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Color(0xFFE0E0E0)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = "头像",
                tint = Color(0xFFAAAAAA),
                modifier = Modifier.size(40.dp),
            )
        }

        Spacer(Modifier.height(12.dp))

        // 登录/注册按钮
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFFD81E06))
                .clickable { /* TODO: 跳转登录页 */ }
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            Text(
                text = "登录 / 注册",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = "登录后可同步阅读记录和收藏内容",
            color = Color.Gray,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun ProfileMenuList() {
    val menus = listOf(
        ProfileMenuItem("我的收藏", Icons.Filled.FavoriteBorder),
        ProfileMenuItem("阅读历史", Icons.Filled.History),
        ProfileMenuItem("消息通知", Icons.Filled.NotificationsNone),
        ProfileMenuItem("设置", Icons.Filled.Settings),
    )

    Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
        menus.forEachIndexed { index, item ->
            ProfileMenuRow(item = item)
            if (index < menus.lastIndex) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 48.dp)
                        .height(1.dp)
                        .background(Color(0xFFEEEEEE)),
                )
            }
        }
    }
}

private data class ProfileMenuItem(
    val title: String,
    val icon: ImageVector,
)

@Composable
private fun ProfileMenuRow(item: ProfileMenuItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* TODO: 预留点击事件 */ }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.title,
            tint = Color(0xFF666666),
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = item.title,
            fontSize = 15.sp,
            color = Color(0xFF333333),
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Color(0xFFCCCCCC),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileScreenPreview() {
    com.example.toutiao.ui.theme.ToutiaoFeedDemoTheme {
        ProfileScreen()
    }
}
