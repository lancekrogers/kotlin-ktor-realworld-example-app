package io.realworld.app.domain.service

import io.realworld.app.domain.Article
import io.realworld.app.domain.repository.ArticleRepository
import io.realworld.app.ext.toSlugBase

class ArticleService(private val articleRepository: ArticleRepository) {
    fun create(email: String, article: Article): Article {
        val title = article.title?.trim()
        val description = article.description?.trim()
        require(!title.isNullOrEmpty()) { "Article title can't be blank." }
        require(!description.isNullOrEmpty()) { "Article description can't be blank." }
        require(article.body.isNotBlank()) { "Article body can't be blank." }
        val tags = article.tagList.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val clean = article.copy(title = title, description = description, tagList = tags)
        return articleRepository.create(email, clean, title.toSlugBase())
    }
}
