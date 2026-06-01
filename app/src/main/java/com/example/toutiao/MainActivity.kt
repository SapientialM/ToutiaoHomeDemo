package com.example.toutiao

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.toutiao.presentation.common.AppBottomNav
import com.example.toutiao.presentation.home.HomeScreen
import com.example.toutiao.presentation.home.HomeViewModel
import com.example.toutiao.presentation.profile.ProfileScreen
import com.example.toutiao.ui.theme.ToutiaoFeedDemoTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ToutiaoFeedDemoTheme {
                var selectedBottomNav by remember { mutableIntStateOf(0) }

                Scaffold(
                    bottomBar = {
                        AppBottomNav(
                            selectedIndex = selectedBottomNav,
                            onSelected = { selectedBottomNav = it },
                        )
                    },
                    containerColor = Color(0xFFF5F5F5)
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        when (selectedBottomNav) {
                            0 -> HomeScreen(viewModel = viewModel)
                            1 -> VideoPlaceholderScreen()
                            2 -> SearchPlaceholderScreen()
                            3 -> TaskPlaceholderScreen()
                            4 -> ProfileScreen()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VideoPlaceholderScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("视频页 - 待实现", color = Color.Gray)
    }
}

@Composable
fun SearchPlaceholderScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("搜索页 - 待实现", color = Color.Gray)
    }
}

@Composable
fun TaskPlaceholderScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("任务页 - 待实现", color = Color.Gray)
    }
}


