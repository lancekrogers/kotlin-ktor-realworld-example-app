package io.realworld.app.domain.repository

import io.realworld.app.domain.Comment
import io.realworld.app.domain.Profile
import io.realworld.app.domain.exceptions.ForbiddenException
import io.realworld.app.domain.exceptions.NotFoundException
import java.util.Date
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
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

    /** Oldest first, with `author.following` relative to the viewer. */
    fun findBySlug(slug: String, viewerEmail: String?): List<Comment> = transaction {
        val articleId = Articles.select { Articles.slug eq slug }.singleOrNull()?.get(Articles.id)
            ?: throw NotFoundException("Article not found.")
        val rows = (Comments innerJoin Users).select { Comments.article eq articleId }
            .orderBy(Comments.createdAt to SortOrder.ASC, Comments.id to SortOrder.ASC)
            .toList()
        val viewerId = viewerEmail?.let { e -> Users.select { Users.email eq e }.singleOrNull()?.get(Users.id)?.value }
        val followed: Set<Long> = if (viewerId == null || rows.isEmpty()) emptySet() else
            Follows.select { (Follows.follower eq viewerId) and (Follows.user inList rows.map { it[Comments.author].value }) }
                .map { it[Follows.user] }.toSet()
        rows.map { r ->
            Comment(id = r[Comments.id].value, createdAt = Date(r[Comments.createdAt]), updatedAt = Date(r[Comments.updatedAt]),
                    body = r[Comments.body],
                    author = Profile(r[Users.username], r[Users.bio], r[Users.image], r[Comments.author].value in followed))
        }
    }

    /** Only the comment's author may delete it. The comment must belong to the given article. */
    fun delete(email: String, slug: String, id: Long): Unit = transaction {
        val user = Users.select { Users.email eq email }.singleOrNull() ?: throw NotFoundException("User not found.")
        val articleId = Articles.select { Articles.slug eq slug }.singleOrNull()?.get(Articles.id)
            ?: throw NotFoundException("Article not found.")
        val comment = Comments.select { (Comments.id eq id) and (Comments.article eq articleId) }.singleOrNull()
            ?: throw NotFoundException("Comment not found.")
        if (comment[Comments.author].value != user[Users.id].value) throw ForbiddenException("Only the author can delete this comment.")
        Comments.deleteWhere { Comments.id eq id }
    }

    fun countByAuthor(userId: Long): Long = transaction { Comments.select { Comments.author eq userId }.count() }
}
