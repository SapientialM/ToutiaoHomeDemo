package com.example.toutiao.presentation.mall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.toutiao.domain.model.Product
import com.example.toutiao.domain.repository.MallRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * MallViewModel — 商城页状态机
 *
 * 一次性拉全部商品到内存, UI 按需按 category 切片.
 * 商品数据走 MallDataSource (assets/mall_products.json, 650 条合成 mock).
 */
@HiltViewModel
class MallViewModel @Inject constructor(
    private val mallRepository: MallRepository,
) : ViewModel() {

    data class UiState(
        val categories: List<String> = emptyList(),
        val officialStoreProducts: List<Product> = emptyList(), // 官方商城 4 张
        val recommendProducts: List<Product> = emptyList(),     // 你可能喜欢 8 张
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val cats = mallRepository.getCategories()
                val official = mallRepository.getProducts(category = "手机", size = 4)
                val recommend = mallRepository.getProducts(category = null, page = 0, size = 8)
                _uiState.update {
                    it.copy(
                        categories = cats,
                        officialStoreProducts = official,
                        recommendProducts = recommend,
                        isLoading = false,
                    )
                }
                Timber.d("MallViewModel — loaded ${cats.size} categories, ${official.size} official, ${recommend.size} recommend")
            } catch (e: Exception) {
                Timber.e(e, "MallViewModel load failed")
                _uiState.update { it.copy(isLoading = false, errorMessage = "加载失败: ${e.message}") }
            }
        }
    }
}
