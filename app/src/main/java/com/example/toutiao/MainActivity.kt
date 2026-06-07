package com.example.toutiao

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.toutiao.presentation.common.AppBottomNav
import com.example.toutiao.presentation.earn.EarnScreen
import com.example.toutiao.presentation.home.HomeScreen
import com.example.toutiao.presentation.home.HomeViewModel
import com.example.toutiao.presentation.mall.MallScreen
import com.example.toutiao.presentation.profile.ProfileScreen
import com.example.toutiao.presentation.video.VideoScreen
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
                            1 -> VideoScreen()
                            2 -> EarnScreen()
                            3 -> MallScreen()
                            4 -> ProfileScreen()
                        }
                    }
                }
            }
        }
    }
}
