package io.realworld.app.domain.repository

import io.realworld.app.domain.Article
import io.realworld.app.domain.Profile
import io.realworld.app.domain.exceptions.NotFoundException
import io.realworld.app.ext.uniqueSlug
import java.util.Date
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

internal object Articles : LongIdTable() {
    val slug: Column<String> = varchar("slug", 255).uniqueIndex()
    val title: Column<String> = varchar("title", 255)
    val description: Column<String> = varchar("description", 1000)
    val body: Column<String> = text("body")
    val author: Column<EntityID<Long>> = reference("author", Users)
    val createdAt: Column<Long> = long("created_at")
    val updatedAt: Column<Long> = long("updated_at")
}

internal object ArticleTags : Table() {
    val article: Column<EntityID<Long>> = reference("article", Articles)
    val tag: Column<EntityID<Long>> = reference("tag", Tags)
    override val primaryKey = PrimaryKey(article, tag)
}

class ArticleRepository {
    init {
        transaction {
            // One call: SchemaUtils sorts by foreign-key references and skips existing tables,
            // so this is safe no matter which repository Kodein constructs first.
            SchemaUtils.create(Users, Tags, Articles, ArticleTags)
        }
    }

    fun create(authorEmail: String, article: Article, slugBase: String): Article = transaction {
        val authorRow = Users.select { Users.email eq authorEmail }.singleOrNull()
            ?: throw NotFoundException("Author not found.")
        val slug = uniqueSlug(slugBase) { candidate -> !Articles.select { Articles.slug eq candidate }.empty() }
        val now = System.currentTimeMillis()
        val articleId = Articles.insertAndGetId {
            it[Articles.slug] = slug
            it[title] = requireNotNull(article.title)
            it[description] = requireNotNull(article.description)
            it[body] = article.body
            it[author] = authorRow[Users.id]
            it[createdAt] = now
            it[updatedAt] = now
        }
        Tags.idsFor(article.tagList).forEach { tagId ->
            ArticleTags.insert { row -> row[ArticleTags.article] = articleId; row[ArticleTags.tag] = tagId }
        }
        requireNotNull(loadBySlug(slug, viewerEmail = authorEmail))
    }

    fun findBySlug(slug: String, viewerEmail: String?): Article? = transaction { loadBySlug(slug, viewerEmail) }

    /** Call inside a transaction. */
    internal fun loadBySlug(slug: String, viewerEmail: String?): Article? =
        (Articles innerJoin Users).select { Articles.slug eq slug }.singleOrNull()
            ?.let { toArticles(listOf(it), viewerEmail).single() }

    /** Maps article+author rows to domain articles, loading all tags in one query. Call inside a transaction. */
    internal fun toArticles(rows: List<ResultRow>, viewerEmail: String?): List<Article> {
        if (rows.isEmpty()) return emptyList()
        val ids = rows.map { it[Articles.id] }
        val tagsByArticle = (ArticleTags innerJoin Tags).select { ArticleTags.article inList ids }
            .groupBy({ it[ArticleTags.article] }, { it[Tags.name] })
        val viewerId = viewerEmail?.let { email -> Users.select { Users.email eq email }.singleOrNull()?.get(Users.id)?.value }
        return rows.map { row ->
            val authorId = row[Articles.author].value
            val following = viewerId != null &&
                !Follows.select { (Follows.user eq authorId) and (Follows.follower eq viewerId) }.empty()
            Article(
                slug = row[Articles.slug],
                title = row[Articles.title],
                description = row[Articles.description],
                body = row[Articles.body],
                tagList = tagsByArticle[row[Articles.id]].orEmpty().sorted(),
                createdAt = Date(row[Articles.createdAt]),
                updatedAt = Date(row[Articles.updatedAt]),
                author = Profile(row[Users.username], row[Users.bio], row[Users.image], following)
            )
        }
    }
}
