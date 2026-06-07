package com.example.toutiao.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MonetizationOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toutiao.ui.theme.NavSelected
import com.example.toutiao.ui.theme.NavUnselected
import com.example.toutiao.ui.theme.RedMain

data class NavItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val badgeCount: Int? = null,
)

@Composable
fun AppBottomNav(selectedIndex: Int, onSelected: (Int) -> Unit) {
    val items = listOf(
        NavItem("首页", Icons.Filled.Home, Icons.Outlined.Home),
        NavItem("视频", Icons.Filled.PlayArrow, Icons.Outlined.PlayArrow),
        NavItem("赚钱", Icons.Filled.MonetizationOn, Icons.Outlined.MonetizationOn),
        NavItem("商城", Icons.Filled.ShoppingCart, Icons.Outlined.ShoppingCart),
        NavItem("我的", Icons.Filled.Person, Icons.Outlined.Person, badgeCount = 5),
    )

    NavigationBar(
        containerColor = Color.White,
        tonalElevation = 0.dp,
        windowInsets = WindowInsets.navigationBars,
        modifier = Modifier.navigationBarsPadding(),
    ) {
        items.forEachIndexed { index, item ->
            val selected = index == selectedIndex
            NavigationBarItem(
                icon = {
                    Box(modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = item.label,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(24.dp),
                        )
                        if (item.badgeCount != null && item.badgeCount > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(if (item.badgeCount > 9) 16.dp else 14.dp)
                                    .clip(CircleShape)
                                    .background(RedMain)
                                    .border(1.5.dp, Color.White, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (item.badgeCount <= 99) {
                                    Text(
                                        text = if (item.badgeCount > 9) "9+" else item.badgeCount.toString(),
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                },
                label = {
                    Text(
                        item.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    )
                },
                selected = selected,
                onClick = { onSelected(index) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = NavSelected,
                    selectedTextColor = NavSelected,
                    unselectedIconColor = NavUnselected,
                    unselectedTextColor = NavUnselected,
                    indicatorColor = Color.Transparent,
                ),
                alwaysShowLabel = true,
            )
        }
    }
}
