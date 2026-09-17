package io.realworld.app.domain.service

import io.realworld.app.domain.Comment
import io.realworld.app.domain.repository.CommentRepository

class CommentService(private val commentRepository: CommentRepository) {
    fun add(slug: String, email: String, comment: Comment): Comment {
        require(comment.body.isNotBlank()) { "Comment body can't be blank." }
        return commentRepository.add(slug, email, comment.body.trim())
    }

    fun findBySlug(slug: String, viewerEmail: String?): List<Comment> = commentRepository.findBySlug(slug, viewerEmail)

    fun delete(email: String, slug: String, id: String?) {
        val commentId = requireNotNull(id?.toLongOrNull()) { "Comment id must be an integer." }
        commentRepository.delete(email, slug, commentId)
    }
}
