package io.realworld.app.domain.repository

import io.realworld.app.domain.Article
import io.realworld.app.domain.Profile
import io.realworld.app.domain.exceptions.NotFoundException
import io.realworld.app.ext.uniqueSlug
import java.util.Date
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.LikePattern
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.lowerCase
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

internal object ArticleFavorites : Table() {
    val user: Column<EntityID<Long>> = reference("user", Users)
    val article: Column<EntityID<Long>> = reference("article", Articles)
    override val primaryKey = PrimaryKey(user, article)
}

data class ArticlePage(val articles: List<Article>, val total: Long)

class ArticleRepository {
    init {
        transaction {
            // One call: SchemaUtils sorts by foreign-key references and skips existing tables,
            // so this is safe no matter which repository Kodein constructs first.
            SchemaUtils.create(Users, Tags, Articles, ArticleTags, ArticleFavorites)
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

    fun favorite(email: String, slug: String): Article = transaction {
        val (userId, articleId) = userAndArticle(email, slug)
        val already = !ArticleFavorites.select {
            (ArticleFavorites.user eq userId) and (ArticleFavorites.article eq articleId)
        }.empty()
        if (!already) ArticleFavorites.insert { it[user] = userId; it[article] = articleId }
        requireNotNull(loadBySlug(slug, email))
    }

    fun unfavorite(email: String, slug: String): Article = transaction {
        val (userId, articleId) = userAndArticle(email, slug)
        ArticleFavorites.deleteWhere { (ArticleFavorites.user eq userId) and (ArticleFavorites.article eq articleId) }
        requireNotNull(loadBySlug(slug, email))
    }

    fun search(term: String, limit: Int, offset: Long, viewerEmail: String?): ArticlePage = transaction {
        val pattern = LikePattern("%", '\\') + LikePattern.ofLiteral(term.lowercase()) + "%"
        val matches = (Articles.title.lowerCase() like pattern) or (Articles.body.lowerCase() like pattern)
        val total = Articles.select { matches }.count()
        val rows = (Articles innerJoin Users).select { matches }
            .orderBy(Articles.createdAt to SortOrder.DESC, Articles.id to SortOrder.DESC)
            .limit(limit, offset)
            .toList()
        ArticlePage(toArticles(rows, viewerEmail), total)
    }

    /** Call inside a transaction. */
    internal fun loadBySlug(slug: String, viewerEmail: String?): Article? =
        (Articles innerJoin Users).select { Articles.slug eq slug }.singleOrNull()
            ?.let { toArticles(listOf(it), viewerEmail).single() }

    /** Call inside a transaction. */
    private fun userAndArticle(email: String, slug: String): Pair<EntityID<Long>, EntityID<Long>> {
        val userId = Users.select { Users.email eq email }.singleOrNull()?.get(Users.id)
            ?: throw NotFoundException("User not found.")
        val articleId = Articles.select { Articles.slug eq slug }.singleOrNull()?.get(Articles.id)
            ?: throw NotFoundException("Article not found.")
        return userId to articleId
    }

    /** Maps article+author rows to domain articles, loading all tags in one query. Call inside a transaction. */
    internal fun toArticles(rows: List<ResultRow>, viewerEmail: String?): List<Article> {
        if (rows.isEmpty()) return emptyList()
        val ids = rows.map { it[Articles.id] }
        val tagsByArticle = (ArticleTags innerJoin Tags).select { ArticleTags.article inList ids }
            .groupBy({ it[ArticleTags.article] }, { it[Tags.name] })
        val viewerId = viewerEmail?.let { e -> Users.select { Users.email eq e }.singleOrNull()?.get(Users.id) }
        val favCount = ArticleFavorites.user.count()
        val counts = ArticleFavorites.slice(ArticleFavorites.article, favCount)
            .select { ArticleFavorites.article inList ids }
            .groupBy(ArticleFavorites.article)
            .associate { it[ArticleFavorites.article] to it[favCount] }
        val favoritedByViewer = if (viewerId == null) emptySet() else
            ArticleFavorites.select { (ArticleFavorites.user eq viewerId) and (ArticleFavorites.article inList ids) }
                .map { it[ArticleFavorites.article] }.toSet()
        return rows.map { row ->
            val authorId = row[Articles.author].value
            val following = viewerId != null &&
                !Follows.select { (Follows.user eq authorId) and (Follows.follower eq viewerId.value) }.empty()
            Article(
                slug = row[Articles.slug],
                title = row[Articles.title],
                description = row[Articles.description],
                body = row[Articles.body],
                tagList = tagsByArticle[row[Articles.id]].orEmpty().sorted(),
                createdAt = Date(row[Articles.createdAt]),
                updatedAt = Date(row[Articles.updatedAt]),
                favorited = row[Articles.id] in favoritedByViewer,
                favoritesCount = counts[row[Articles.id]] ?: 0L,
                author = Profile(row[Users.username], row[Users.bio], row[Users.image], following)
            )
        }
    }
}
