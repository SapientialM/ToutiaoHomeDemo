package com.example.toutiao.data.repository

import com.example.toutiao.data.remote.datasource.MallDataSource
import com.example.toutiao.domain.model.Product
import com.example.toutiao.domain.repository.MallRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MallRepositoryImpl @Inject constructor(
    private val dataSource: MallDataSource,
) : MallRepository {

    override suspend fun getCategories(): List<String> = dataSource.allCategories

    override suspend fun getProducts(category: String?, page: Int, size: Int): List<Product> {
        return dataSource.getProducts(category, page, size).map { item ->
            Product(
                id = item.productId.ifBlank { item.sourceUrl.substringAfterLast('/') },
                name = item.name,
                category = item.category,
                sourceUrl = item.sourceUrl,
                imageUrl = item.imageUrl,
            )
        }
    }
}
