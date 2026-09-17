package io.realworld.app.domain

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.util.*

data class ArticleDTO(val article: Article?)

data class ArticlesDTO(val articles: List<Article>, val articlesCount: Int)

/**
 * PUT /articles/{slug} body. Every field is optional; absent fields are left unchanged. Clients
 * commonly send the whole article back (slug, tagList, author, ...), so unknown fields are ignored.
 */
data class ArticleUpdateDTO(val article: ArticleUpdate?)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ArticleUpdate(val title: String? = null,
                         val description: String? = null,
                         val body: String? = null)

data class Article(val slug: String? = null,
                   val title: String?,
                   val description: String?,
                   val body: String,
                   val tagList: List<String> = listOf(),
                   val createdAt: Date? = null,
                   val updatedAt: Date? = null,
                   val favorited: Boolean = false,
                   val favoritesCount: Long = 0,
                   val author: Profile? = null)