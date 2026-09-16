package io.realworld.app.web.controllers

import io.ktor.application.ApplicationCall
import io.ktor.auth.authentication
import io.ktor.request.receive
import io.ktor.response.respond
import io.realworld.app.domain.CommentDTO
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

    fun findBySlug(ctx: ApplicationCall) {
        ctx.parameters["slug"]
//            commentService.findBySlug(this).also { comments ->
//                ctx.json(CommentsDTO(comments))
//            }

    }

    fun delete(ctx: ApplicationCall) {
        val slug = ctx.parameters["slug"]
        val id = ctx.parameters["id"]
//        commentService.delete(id, slug)
    }

}
