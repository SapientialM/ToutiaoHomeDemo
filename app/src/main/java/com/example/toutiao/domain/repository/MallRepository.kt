package com.example.toutiao.domain.repository

import com.example.toutiao.domain.model.Product

interface MallRepository {
    /** 拉取所有商品分类 */
    suspend fun getCategories(): List<String>

    /**
     * 拉取商品列表
     * @param category 类别名, null/"all" = 全量
     */
    suspend fun getProducts(category: String? = null, page: Int = 0, size: Int = 20): List<Product>
}
