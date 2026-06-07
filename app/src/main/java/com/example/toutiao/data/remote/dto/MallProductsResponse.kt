package com.example.toutiao.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 商城商品数据源 (assets/mall_products.json) Schema:
 * {
 *   "商品": [
 *     { "商品ID": "...", "名称": "...", "类别": "...", "源URL": "...", "封面URL": "..." },
 *     ...
 *   ]
 * }
 */
@Serializable
data class MallProductsWrapper(
    @SerialName("商品") val items: List<MallProductItem> = emptyList(),
)

@Serializable
data class MallProductItem(
    @SerialName("商品ID") val productId: String = "",
    @SerialName("名称") val name: String = "",
    @SerialName("类别") val category: String = "",
    @SerialName("源URL") val sourceUrl: String = "",
    @SerialName("封面URL") val imageUrl: String = "",
)
