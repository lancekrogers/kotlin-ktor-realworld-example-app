package io.realworld.app.domain.service

import io.realworld.app.domain.Article
import io.realworld.app.domain.ArticleUpdate
import io.realworld.app.domain.ArticlesDTO
import io.realworld.app.domain.Paging
import io.realworld.app.domain.exceptions.NotFoundException
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

    fun findBy(tag: String?, author: String?, favorited: String?, limit: String?, offset: String?, viewerEmail: String?): ArticlesDTO {
        val paging = Paging.parse(limit, offset)
        val page = articleRepository.findBy(tag.clean(), author.clean(), favorited.clean(), paging.limit, paging.offset, viewerEmail)
        return ArticlesDTO(page.articles, Math.toIntExact(page.total))
    }

    fun feed(email: String, limit: String?, offset: String?): ArticlesDTO {
        val paging = Paging.parse(limit, offset)
        val page = articleRepository.feed(email, paging.limit, paging.offset)
        return ArticlesDTO(page.articles, Math.toIntExact(page.total))
    }

    fun findBySlug(slug: String, viewerEmail: String?): Article =
        articleRepository.findBySlug(slug, viewerEmail) ?: throw NotFoundException("Article not found.")

    /** A field that is absent is left alone; a field that is present but blank is rejected. */
    fun update(email: String, slug: String, patch: ArticleUpdate): Article {
        val title = patch.title?.trim()
        val description = patch.description?.trim()
        val body = patch.body?.trim()
        require(title == null || title.isNotEmpty()) { "Article title can't be blank." }
        require(description == null || description.isNotEmpty()) { "Article description can't be blank." }
        require(body == null || body.isNotEmpty()) { "Article body can't be blank." }
        return articleRepository.update(email, slug, title, title?.toSlugBase(), description, body)
    }

    fun delete(email: String, slug: String) = articleRepository.delete(email, slug)

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

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
