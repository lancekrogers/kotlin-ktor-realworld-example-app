package io.realworld.app.web.controllers

import io.ktor.application.ApplicationCall
import io.ktor.auth.authentication
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.realworld.app.domain.CommentDTO
import io.realworld.app.domain.CommentsDTO
import io.realworld.app.domain.User
import io.realworld.app.domain.service.CommentService

class CommentController(private val commentService: CommentService) {
    suspend fun add(ctx: ApplicationCall) {
        val email = ctx.authentication.principal<User>()?.email
        require(!email.isNullOrBlank()) { "User not logged." }
        val slug = requireNotNull(ctx.parameters["slug"]) { "slug is required." }
        val comment = runCatching { ctx.receive<CommentDTO>().comment }
            .getOrElse { throw IllegalArgumentException("Comment is invalid.") }
        requireNotNull(comment) { "Comment is invalid." }
        ctx.respond(CommentDTO(commentService.add(slug, email, comment)))
    }

    suspend fun findBySlug(ctx: ApplicationCall) {
        val viewer = ctx.authentication.principal<User>()?.email
        val slug = requireNotNull(ctx.parameters["slug"]) { "slug is required." }
        ctx.respond(CommentsDTO(commentService.findBySlug(slug, viewer)))
    }

    suspend fun delete(ctx: ApplicationCall) {
        val email = ctx.authentication.principal<User>()?.email
        require(!email.isNullOrBlank()) { "User not logged." }
        val slug = requireNotNull(ctx.parameters["slug"]) { "slug is required." }
        commentService.delete(email, slug, ctx.parameters["id"])
        ctx.respond(HttpStatusCode.OK)
    }
}
