package io.realworld.app.domain.service

import io.realworld.app.config.DbConfig
import io.realworld.app.domain.Article
import io.realworld.app.domain.User
import io.realworld.app.domain.repository.ArticleRepository
import io.realworld.app.domain.repository.UserRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class ArticleServiceTest {
    private lateinit var articleService: ArticleService
    private lateinit var authorEmail: String

    @Before
    fun setUp() {
        val suffix = UUID.randomUUID()
        authorEmail = "author-$suffix@test.com"
        DbConfig.setup("jdbc:h2:mem:realworld;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false", "sa", "")
        UserRepository().create(User(email = authorEmail, username = "user-$suffix", password = "x"))
        articleService = ArticleService(ArticleRepository())
    }

    @Test
    fun `create rejects blank title`() {
        assertThrows(IllegalArgumentException::class.java) {
            articleService.create(authorEmail, Article(title = "  ", description = "desc", body = "body"))
        }
    }

    @Test
    fun `create rejects blank description`() {
        assertThrows(IllegalArgumentException::class.java) {
            articleService.create(authorEmail, Article(title = "title", description = "  ", body = "body"))
        }
    }

    @Test
    fun `create rejects blank body`() {
        assertThrows(IllegalArgumentException::class.java) {
            articleService.create(authorEmail, Article(title = "title", description = "desc", body = "  "))
        }
    }

    @Test
    fun `create normalizes tags`() {
        val suffix = UUID.randomUUID()
        val created = articleService.create(
            authorEmail,
            Article(
                title = "Tags test $suffix",
                description = "desc",
                body = "body",
                tagList = listOf(" x ", "x", "", "y")
            )
        )
        assertEquals(listOf("x", "y"), created.tagList)
    }

    @Test
    fun `create appends -2 slug on duplicate title`() {
        val suffix = UUID.randomUUID()
        val title = "Duplicate title $suffix"
        val first = articleService.create(authorEmail, Article(title = title, description = "desc", body = "body"))
        val second = articleService.create(authorEmail, Article(title = title, description = "desc", body = "body"))
        assertTrue(second.slug!!.endsWith("-2"))
        assertTrue(first.slug != second.slug)
    }
}
