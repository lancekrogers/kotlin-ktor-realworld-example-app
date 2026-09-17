package io.realworld.app.web.controllers

import io.ktor.application.ApplicationCall
import io.ktor.auth.authentication
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.realworld.app.domain.ArticleDTO
import io.realworld.app.domain.ArticleUpdateDTO
import io.realworld.app.domain.User
import io.realworld.app.domain.exceptions.UnauthorizedException
import io.realworld.app.domain.service.ArticleService

class ArticleController(private val articleService: ArticleService) {

    suspend fun findBy(ctx: ApplicationCall) {
        val viewer = ctx.authentication.principal<User>()?.email
        ctx.respond(
            articleService.findBy(
                ctx.parameters["tag"], ctx.parameters["author"], ctx.parameters["favorited"],
                ctx.parameters["limit"], ctx.parameters["offset"], viewer
            )
        )
    }

    /** Registered under optional auth so "feed" is matched before "{slug}"; the token is required here. */
    suspend fun feed(ctx: ApplicationCall) {
        val email = ctx.authentication.principal<User>()?.email ?: throw UnauthorizedException("User not logged.")
        ctx.respond(articleService.feed(email, ctx.parameters["limit"], ctx.parameters["offset"]))
    }

    suspend fun get(ctx: ApplicationCall) {
        val viewer = ctx.authentication.principal<User>()?.email
        val slug = requireNotNull(ctx.parameters["slug"]) { "slug is required." }
        ctx.respond(ArticleDTO(articleService.findBySlug(slug, viewer)))
    }

    suspend fun create(ctx: ApplicationCall) {
        val email = ctx.authentication.principal<User>()?.email
        require(!email.isNullOrBlank()) { "User not logged." }
        val article = runCatching { ctx.receive<ArticleDTO>().article }
            .getOrElse { throw IllegalArgumentException("Article is invalid.") }
        requireNotNull(article) { "Article is invalid." }
        ctx.respond(ArticleDTO(articleService.create(email, article)))
    }

    suspend fun update(ctx: ApplicationCall) {
        val email = ctx.authentication.principal<User>()?.email
        require(!email.isNullOrBlank()) { "User not logged." }
        val slug = requireNotNull(ctx.parameters["slug"]) { "slug is required." }
        val patch = runCatching { ctx.receive<ArticleUpdateDTO>().article }
            .getOrElse { throw IllegalArgumentException("Article is invalid.") }
        requireNotNull(patch) { "Article is invalid." }
        ctx.respond(ArticleDTO(articleService.update(email, slug, patch)))
    }

    suspend fun delete(ctx: ApplicationCall) {
        val email = ctx.authentication.principal<User>()?.email
        require(!email.isNullOrBlank()) { "User not logged." }
        val slug = requireNotNull(ctx.parameters["slug"]) { "slug is required." }
        articleService.delete(email, slug)
        ctx.respond(HttpStatusCode.OK)
    }

    suspend fun favorite(ctx: ApplicationCall) {
        val email = ctx.authentication.principal<User>()?.email
        require(!email.isNullOrBlank()) { "User not logged." }
        val slug = requireNotNull(ctx.parameters["slug"]) { "slug is required." }
        ctx.respond(ArticleDTO(articleService.favorite(email, slug)))
    }

    suspend fun unfavorite(ctx: ApplicationCall) {
        val email = ctx.authentication.principal<User>()?.email
        require(!email.isNullOrBlank()) { "User not logged." }
        val slug = requireNotNull(ctx.parameters["slug"]) { "slug is required." }
        ctx.respond(ArticleDTO(articleService.unfavorite(email, slug)))
    }

    suspend fun search(ctx: ApplicationCall) {
        val viewer = ctx.authentication.principal<User>()?.email
        ctx.respond(articleService.search(ctx.parameters["q"], ctx.parameters["limit"], ctx.parameters["offset"], viewer))
    }

    suspend fun popular(ctx: ApplicationCall) {
        val viewer = ctx.authentication.principal<User>()?.email
        ctx.respond(articleService.popular(ctx.parameters["limit"], ctx.parameters["offset"], viewer))
    }
}
