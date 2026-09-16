package io.realworld.app.domain.repository

import io.realworld.app.config.DbConfig
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.util.UUID

class ArticleSearchRepositoryTest {
    companion object {
        private lateinit var repo: ArticleRepository

        @BeforeClass @JvmStatic
        fun db() {
            DbConfig.setup("jdbc:h2:mem:realworld;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false", "sa", "")
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

    private fun insertArticle(
        userId: EntityID<Long>,
        slug: String,
        title: String,
        body: String,
        createdAt: Long,
    ): Long = transaction {
        Articles.insertAndGetId {
            it[Articles.slug] = slug
            it[Articles.title] = title
            it[Articles.description] = "description"
            it[Articles.body] = body
            it[Articles.author] = userId
            it[Articles.createdAt] = createdAt
            it[Articles.updatedAt] = createdAt
        }.value
    }

    @Test
    fun `title only match is found`() {
        val token = UUID.randomUUID().toString()
        val userId = insertUser("user-$token")
        insertArticle(userId, "slug-$token-title", "Article about $token", "body without match", 1000L)

        val page = repo.search(token, limit = 20, offset = 0, viewerEmail = null)
        assertEquals(1, page.articles.size)
        assertEquals(1L, page.total)
        assertTrue(page.articles.single().title!!.contains(token))
    }

    @Test
    fun `body only match is found`() {
        val token = UUID.randomUUID().toString()
        val userId = insertUser("user-$token")
        insertArticle(userId, "slug-$token-body", "no match in title", "body holds $token here", 1000L)

        val page = repo.search(token, limit = 20, offset = 0, viewerEmail = null)
        assertEquals(1, page.articles.size)
        assertEquals(1L, page.total)
    }

    @Test
    fun `search is case insensitive`() {
        val token = "CaSe" + UUID.randomUUID().toString().take(8)
        val userId = insertUser("user-$token")
        insertArticle(userId, "slug-$token-case", "Title with $token", "body", 1000L)

        val lower = repo.search(token.lowercase(), limit = 20, offset = 0, viewerEmail = null)
        assertEquals(1, lower.articles.size)
        assertEquals(1L, lower.total)

        val upper = repo.search(token.uppercase(), limit = 20, offset = 0, viewerEmail = null)
        assertEquals(1, upper.articles.size)
        assertEquals(1L, upper.total)
    }

    @Test
    fun `percent sign in search term is literal`() {
        val token = UUID.randomUUID().toString()
        val userId = insertUser("user-$token")
        val withPercent = "${token}100% pure_x"
        // Decoy. The pattern for an escaped term is %...100\%%, which needs a literal '%' and must not
        // match this. An unescaped term gives %...100%%, where %% collapses to % and matches it too.
        // Without the decoy both readings return exactly the one row and the test cannot fail.
        val withoutPercent = "${token}100XX plain"
        insertArticle(userId, "slug-$token-percent", withPercent, "body", 1000L)
        insertArticle(userId, "slug-$token-decoy", withoutPercent, "body", 900L)

        val page = repo.search("${token}100%", limit = 20, offset = 0, viewerEmail = null)
        assertEquals(1, page.articles.size)
        assertEquals(1L, page.total)
        assertEquals(withPercent, page.articles.single().title)
    }

    @Test
    fun `underscore in search term is literal`() {
        val token = UUID.randomUUID().toString()
        val userId = insertUser("user-$token")
        insertArticle(userId, "slug-$token-wildcard", "${token}1000 purex", "body", 1000L)

        val page = repo.search("${token}100_", limit = 20, offset = 0, viewerEmail = null)
        assertEquals(0, page.articles.size)
        assertEquals(0L, page.total)
    }

    @Test
    fun `results ordered by createdAt descending`() {
        val token = UUID.randomUUID().toString()
        val userId = insertUser("user-$token")
        insertArticle(userId, "slug-$token-1", "$token first", "body", 1000L)
        insertArticle(userId, "slug-$token-2", "$token second", "body", 2000L)
        insertArticle(userId, "slug-$token-3", "$token third", "body", 3000L)

        val page = repo.search(token, limit = 20, offset = 0, viewerEmail = null)
        assertEquals(3, page.articles.size)
        assertEquals(
            listOf("slug-$token-3", "slug-$token-2", "slug-$token-1"),
            page.articles.map { it.slug },
        )
    }

    @Test
    fun `results with equal createdAt ordered by id descending`() {
        val token = UUID.randomUUID().toString()
        val userId = insertUser("user-$token")
        val createdAt = 4000L
        val highId = insertArticle(userId, "slug-$token-high", "$token high id", "body", createdAt)
        val lowId = insertArticle(userId, "slug-$token-low", "$token low id", "body", createdAt)

        val page = repo.search(token, limit = 20, offset = 0, viewerEmail = null)
        assertEquals(2, page.articles.size)
        val expectedFirst = if (highId > lowId) "slug-$token-high" else "slug-$token-low"
        val expectedSecond = if (highId > lowId) "slug-$token-low" else "slug-$token-high"
        assertEquals(listOf(expectedFirst, expectedSecond), page.articles.map { it.slug })
    }

    @Test
    fun `paging returns page size and total match count`() {
        val token = UUID.randomUUID().toString()
        val userId = insertUser("user-$token")
        insertArticle(userId, "slug-$token-p1", "$token page1", "body", 1000L)
        insertArticle(userId, "slug-$token-p2", "$token page2", "body", 2000L)
        insertArticle(userId, "slug-$token-p3", "$token page3", "body", 3000L)

        val firstPage = repo.search(token, limit = 2, offset = 0, viewerEmail = null)
        assertEquals(2, firstPage.articles.size)
        assertEquals(3L, firstPage.total)
        assertEquals(listOf("slug-$token-p3", "slug-$token-p2"), firstPage.articles.map { it.slug })

        val lastPage = repo.search(token, limit = 2, offset = 2, viewerEmail = null)
        assertEquals(1, lastPage.articles.size)
        assertEquals(3L, lastPage.total)
        assertEquals("slug-$token-p1", lastPage.articles.single().slug)
    }
}
