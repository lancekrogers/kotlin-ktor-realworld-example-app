package io.realworld.app.domain.repository

import io.realworld.app.domain.Comment
import io.realworld.app.domain.Profile
import io.realworld.app.domain.exceptions.NotFoundException
import java.util.Date
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

internal object Comments : LongIdTable() {
    val body: Column<String> = text("body")
    val article: Column<EntityID<Long>> = reference("article", Articles)
    val author: Column<EntityID<Long>> = reference("author", Users)
    val createdAt: Column<Long> = long("created_at")
    val updatedAt: Column<Long> = long("updated_at")
}

class CommentRepository {
    init {
        transaction {
            // Kodein may construct this before ArticleRepository; create every referenced table here too.
            SchemaUtils.create(Users, Tags, Articles, ArticleTags, ArticleFavorites, Comments)
        }
    }

    fun add(slug: String, authorEmail: String, body: String): Comment = transaction {
        val author = Users.select { Users.email eq authorEmail }.singleOrNull()
            ?: throw NotFoundException("User not found.")
        val articleId = Articles.select { Articles.slug eq slug }.singleOrNull()?.get(Articles.id)
            ?: throw NotFoundException("Article not found.")
        val now = System.currentTimeMillis()
        val id = Comments.insertAndGetId {
            it[Comments.body] = body; it[article] = articleId; it[Comments.author] = author[Users.id]
            it[createdAt] = now; it[updatedAt] = now
        }
        Comment(id = id.value, createdAt = Date(now), updatedAt = Date(now), body = body,
                author = Profile(author[Users.username], author[Users.bio], author[Users.image], following = false))
    }

    fun countByAuthor(userId: Long): Long = transaction { Comments.select { Comments.author eq userId }.count() }
}
