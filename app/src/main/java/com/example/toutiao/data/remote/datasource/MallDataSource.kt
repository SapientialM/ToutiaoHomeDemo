package com.example.toutiao.data.remote.datasource

import android.content.Context
import com.example.toutiao.data.remote.dto.MallProductItem
import com.example.toutiao.data.remote.dto.MallProductsWrapper
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.IOException

// =============================================================================
// MallDataSource — 商城商品数据源
//
// 数据来源: app/src/main/assets/mall_products.json (抓取自苏宁, 详见 NEWS_DATA_README.md)
// Schema: { 商品: [{ 商品ID, 名称, 类别, 源URL, 封面URL }] }
//
// 调用链:
//   assets/mall_products.json
//          ↓ loadFromAssets() (by lazy)
//   List<MallProductItem>
//          ↓ mapToProduct()
//   List<Product>  (domain model)
//          ↓
//   MallRepositoryImpl → ViewModel → UI
// =============================================================================
class MallDataSource(context: Context) {

    private val appContext = context.applicationContext

    // by lazy: 首次访问时才读 JSON, 不阻塞 App 启动
    private val allItems: List<MallProductItem> by lazy {
        loadFromAssets(appContext)
    }

    // 类别 → 商品列表 (按字母顺序分组备用)
    val byCategory: Map<String, List<MallProductItem>> by lazy {
        allItems.groupBy { it.category }
    }

    val allCategories: List<String> by lazy {
        byCategory.keys.sorted()
    }

    private fun loadFromAssets(context: Context): List<MallProductItem> {
        return try {
            val jsonStr = context.assets.open("mall_products.json")
                .bufferedReader().use { it.readText() }
            val json = Json { ignoreUnknownKeys = true; isLenient = true }
            val wrapper = json.decodeFromString<MallProductsWrapper>(jsonStr)
            Timber.d("MallDataSource — loaded ${wrapper.items.size} products from assets/mall_products.json")
            wrapper.items
        } catch (e: Exception) {
            Timber.e(e, "MallDataSource — failed to load mall_products.json")
            emptyList()
        }
    }

    /**
     * 模拟商品分页 (用于商城首页瀑布流)
     */
    suspend fun getProducts(category: String? = null, page: Int, size: Int): List<MallProductItem> {
        val delayMs = DebugControls.networkDelayMs
        if (delayMs > 0) {
            Timber.d("MallDataSource — simulating network delay: ${delayMs}ms")
            delay(delayMs.toLong())
        }
        if (DebugControls.shouldSimulateError) {
            throw IOException(DebugControls.DEFAULT_ERROR_MESSAGE)
        }

        val source = if (category.isNullOrBlank() || category == "all") {
            allItems
        } else {
            allItems.filter { it.category == category }
        }
        val offset = page * size
        return if (offset >= source.size) emptyList()
        else source.drop(offset).take(size)
    }
}
