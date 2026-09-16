package io.realworld.app.domain.service

import io.realworld.app.domain.ProfileStats
import io.realworld.app.domain.exceptions.NotFoundException
import io.realworld.app.domain.repository.ArticleRepository
import io.realworld.app.domain.repository.CommentRepository
import io.realworld.app.domain.repository.UserRepository

class ProfileStatsService(
    private val users: UserRepository,
    private val articles: ArticleRepository,
    private val comments: CommentRepository
) {
    fun stats(username: String): ProfileStats {
        val user = users.findByUsername(username) ?: throw NotFoundException("Profile not found.")
        val id = requireNotNull(user.id)
        return ProfileStats(articles.countByAuthor(id), comments.countByAuthor(id), articles.countFavoritesBy(id))
    }
}
