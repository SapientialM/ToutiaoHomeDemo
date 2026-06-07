package com.example.toutiao.presentation.common

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toutiao.ui.theme.NavSelected
import com.example.toutiao.ui.theme.NavUnselected

data class NavItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

@Composable
fun AppBottomNav(selectedIndex: Int, onSelected: (Int) -> Unit) {
    val items = listOf(
        NavItem("首页", Icons.Filled.Home, Icons.Outlined.Home),
        NavItem("视频", Icons.Filled.PlayArrow, Icons.Outlined.PlayArrow),
        NavItem("赚钱", Icons.Filled.MonetizationOn, Icons.Outlined.MonetizationOn),
        NavItem("商城", Icons.Filled.ShoppingCart, Icons.Outlined.ShoppingCart),
        NavItem("我的", Icons.Filled.Person, Icons.Outlined.Person),
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
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label,
                        modifier = Modifier.size(24.dp),
                    )
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
