package io.realworld.app.domain.service

import io.realworld.app.domain.Article
import io.realworld.app.domain.ArticlesDTO
import io.realworld.app.domain.Paging
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

    fun search(q: String?, limit: String?, offset: String?, viewerEmail: String?): ArticlesDTO {
        val term = q?.trim()
        require(!term.isNullOrEmpty()) { "q is required." }
        val paging = Paging.parse(limit, offset)
        val page = articleRepository.search(term, paging.limit, paging.offset, viewerEmail)
        return ArticlesDTO(page.articles, Math.toIntExact(page.total))
    }

    fun popular(limit: String?, offset: String?, viewerEmail: String?): ArticlesDTO {
        val paging = Paging.parse(limit, offset)
        val page = articleRepository.popular(paging.limit, paging.offset, viewerEmail)
        return ArticlesDTO(page.articles, Math.toIntExact(page.total))
    }

    fun favorite(email: String, slug: String): Article {
        require(slug.isNotBlank()) { "slug is required." }
        return articleRepository.favorite(email, slug)
    }

    fun unfavorite(email: String, slug: String): Article {
        require(slug.isNotBlank()) { "slug is required." }
        return articleRepository.unfavorite(email, slug)
    }
}
