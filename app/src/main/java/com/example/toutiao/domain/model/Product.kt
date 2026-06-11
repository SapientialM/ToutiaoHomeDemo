package com.example.toutiao.domain.model

import androidx.compose.runtime.Immutable

/**
 * 商城商品 (Domain Model)
 *
 * 抓取来源: 苏宁易购 (mock, 详见 docs/data/mock-data.md)
 */
@Immutable
data class Product(
    val id: String,
    val name: String,
    val category: String,
    val sourceUrl: String,
    val imageUrl: String,
)
