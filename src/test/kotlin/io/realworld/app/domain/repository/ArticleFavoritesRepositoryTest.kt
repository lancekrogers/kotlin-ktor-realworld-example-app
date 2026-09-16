package io.realworld.app.domain.repository

import io.realworld.app.config.DbConfig
import io.realworld.app.domain.exceptions.NotFoundException
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.util.UUID

class ArticleFavoritesRepositoryTest {
    companion object {
        private lateinit var repo: ArticleRepository

        @BeforeClass @JvmStatic
        fun db() {
            DbConfig.setup("jdbc:h2:mem:realworld;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false", "sa", "")
            UserRepository()
            repo = ArticleRepository()
        }
    }

    private fun insertUser(marker: String): EntityID<Long> = transaction {
        Users.insertAndGetId {
            it[email] = "$marker@test.com"
            it[username] = marker
            it[password] = "password"
        }
    }

    private fun favoriteRowCount(email: String, slug: String): Long = transaction {
        val userId = Users.select { Users.email eq email }.single()[Users.id]
        val articleId = Articles.select { Articles.slug eq slug }.single()[Articles.id]
        ArticleFavorites.select {
            (ArticleFavorites.user eq userId) and (ArticleFavorites.article eq articleId)
        }.count()
    }

    private fun insertArticle(userId: EntityID<Long>, slug: String): String = transaction {
        Articles.insertAndGetId {
            it[Articles.slug] = slug
            it[Articles.title] = "title"
            it[Articles.description] = "description"
            it[Articles.body] = "body"
            it[Articles.author] = userId
            it[Articles.createdAt] = System.currentTimeMillis()
            it[Articles.updatedAt] = System.currentTimeMillis()
        }
        slug
    }

    @Test
    fun `favoriting twice leaves one row with favoritesCount 1`() {
        val token = UUID.randomUUID().toString()
        val marker = "user-$token"
        val email = "$marker@test.com"
        insertUser(marker)
        val slug = insertArticle(insertUser("author-$token"), "slug-$token")

        val first = repo.favorite(email, slug)
        val second = repo.favorite(email, slug)

        assertEquals(1L, favoriteRowCount(email, slug))
        assertEquals(1L, first.favoritesCount)
        assertEquals(1L, second.favoritesCount)
        assertTrue(first.favorited)
        assertTrue(second.favorited)
    }

    @Test
    fun `unfavoriting twice raises no error and favoritesCount is 0`() {
        val token = UUID.randomUUID().toString()
        val marker = "user-$token"
        val email = "$marker@test.com"
        insertUser(marker)
        val slug = insertArticle(insertUser("author-$token"), "slug-unfav-$token")

        repo.favorite(email, slug)
        val first = repo.unfavorite(email, slug)
        val second = repo.unfavorite(email, slug)

        assertEquals(0L, favoriteRowCount(email, slug))
        assertEquals(0L, first.favoritesCount)
        assertEquals(0L, second.favoritesCount)
        assertFalse(first.favorited)
        assertFalse(second.favorited)
    }

    @Test
    fun `unknown slug throws NotFoundException`() {
        val token = UUID.randomUUID().toString()
        val marker = "user-$token"
        val email = "$marker@test.com"
        insertUser(marker)

        assertThrows(NotFoundException::class.java) {
            repo.favorite(email, "missing-slug-$token")
        }
    }

    @Test
    fun `unknown email throws NotFoundException`() {
        val token = UUID.randomUUID().toString()
        val authorId = insertUser("author-$token")
        val slug = insertArticle(authorId, "slug-email-$token")

        assertThrows(NotFoundException::class.java) {
            repo.favorite("missing-$token@test.com", slug)
        }
    }

    @Test
    fun `favorited depends on viewer`() {
        val token = UUID.randomUUID().toString()
        val emailA = "viewer-a-$token@test.com"
        val emailB = "viewer-b-$token@test.com"
        insertUser("viewer-a-$token")
        insertUser("viewer-b-$token")
        val slug = insertArticle(insertUser("author-$token"), "slug-viewer-$token")

        repo.favorite(emailA, slug)

        val forA = repo.findBySlug(slug, emailA)!!
        val forB = repo.findBySlug(slug, emailB)!!
        val anonymous = repo.findBySlug(slug, null)!!

        assertTrue(forA.favorited)
        assertEquals(1L, forA.favoritesCount)
        assertFalse(forB.favorited)
        assertEquals(1L, forB.favoritesCount)
        assertFalse(anonymous.favorited)
        assertEquals(1L, anonymous.favoritesCount)
    }
}
