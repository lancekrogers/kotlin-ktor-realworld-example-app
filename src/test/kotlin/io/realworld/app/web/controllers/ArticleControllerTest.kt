package io.realworld.app.web.controllers

import io.realworld.app.domain.Article
import io.realworld.app.domain.ArticleDTO
import io.realworld.app.domain.ArticlesDTO
import io.realworld.app.domain.ProfileDTO
import io.realworld.app.web.rules.AppRule
import io.realworld.app.web.util.HttpUtil
import io.realworld.app.web.util.assertNoAuthorSecrets
import org.apache.http.HttpStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class ArticleControllerTest {
    @Rule
    @JvmField
    val appRule = AppRule()

    /** `articlesCount` is the total number of matches, not the page size; the default page holds 20. */
    private fun assertPageConsistent(page: ArticlesDTO) {
        assertTrue("page ${page.articles.size} must not exceed total ${page.articlesCount}", page.articles.size <= page.articlesCount)
        assertTrue("default page must hold at most 20", page.articles.size <= 20)
    }

    @Test
    fun `get all articles`() {
        appRule.http.createArticle()
        val http = HttpUtil(appRule.port)
        val response = http.get<ArticlesDTO>("/articles")

        assertEquals(response.status, HttpStatus.SC_OK)
        assertNotNull(response.body.articles)
        assertPageConsistent(response.body)
    }

    @Test
    fun `get all articles with auth`() {
        appRule.http.createArticle()
        val response = appRule.http.get<ArticlesDTO>("/articles")

        assertEquals(response.status, HttpStatus.SC_OK)
        assertNotNull(response.body.articles)
        assertPageConsistent(response.body)
        assertNotNull(response.body.articles.first())
        assertFalse(response.body.articles.first().title.isNullOrBlank())
        assertTrue(response.body.articles.first().tagList.isNotEmpty())
    }

    @Test
    fun `get all articles by author`() {
        val author = "user_name_test"
        val response = appRule.http.get<ArticlesDTO>("/articles?author=$author")

        assertEquals(response.status, HttpStatus.SC_OK)
        assertNotNull(response.body.articles)
        assertPageConsistent(response.body)
        response.body.articles.forEach {
            assertEquals(it.author?.username, author)
            assertFalse(it.title.isNullOrBlank())
            assertTrue(it.tagList.isNotEmpty())
        }
    }

    @Test
    fun `get all articles by author with auth`() {
        appRule.http.createArticle()
        val author = "user_name_test"
        val response = appRule.http.get<ArticlesDTO>("/articles?author=$author")

        assertEquals(response.status, HttpStatus.SC_OK)
        assertNotNull(response.body.articles)
        assertPageConsistent(response.body)
        response.body.articles.forEach {
            assertEquals(it.author?.username, author)
            assertFalse(it.title.isNullOrBlank())
            assertTrue(it.tagList.isNotEmpty())
        }
    }

    @Test
    fun `get all articles favorited by username`() {
        val responseCreate = appRule.http.createArticle()
        appRule.http.post<ArticleDTO>("/articles/${responseCreate.body.article?.slug}/favorite")

        val response = appRule.http.get<ArticlesDTO>("/articles?favorited=user_name_test")

        assertEquals(response.status, HttpStatus.SC_OK)
        assertNotNull(response.body.articles)
        assertPageConsistent(response.body)
        assertNotNull(response.body.articles.first())
        assertFalse(response.body.articles.first().title.isNullOrBlank())
        assertTrue(response.body.articles.first().tagList.isNotEmpty())
        assertTrue(response.body.articles.first().favorited)
        assertTrue(response.body.articles.first().favoritesCount > 0)
    }

    @Test
    fun `get all articles favorited by username with auth`() {
        val responseCreate = appRule.http.createArticle()
        appRule.http.post<ArticleDTO>("/articles/${responseCreate.body.article?.slug}/favorite")

        val response = appRule.http.get<ArticlesDTO>("/articles?favorited=${responseCreate.body.article?.author?.username}")

        assertEquals(response.status, HttpStatus.SC_OK)
        assertNotNull(response.body.articles)
        assertPageConsistent(response.body)
        assertNotNull(response.body.articles.first())
        assertFalse(response.body.articles.first().title.isNullOrBlank())
        assertTrue(response.body.articles.first().tagList.isNotEmpty())
    }

    @Test
    fun `get all articles by tag`() {
        val responseCreate = appRule.http.createArticle()
        val tag = responseCreate.body.article?.tagList?.first()
        val response = appRule.http.get<ArticlesDTO>("/articles?tag=${responseCreate.body.article?.tagList?.first()}")

        assertEquals(response.status, HttpStatus.SC_OK)
        assertNotNull(response.body.articles)
        assertPageConsistent(response.body)
        assertTrue(response.body.articles.first().tagList.contains(tag))
    }

    @Test
    fun `create article`() {
        val article = Article(
            title = "Create How to train your dragon",
            description = "Ever wonder how?",
            body = "Very carefully.",
            tagList = listOf("create_article")
        )
        val response = appRule.http.createArticle(article)
        assertEquals(response.status, HttpStatus.SC_OK)
        assertNotNull(response.body.article)
        assertEquals(response.body.article?.title, article.title)
        assertEquals(response.body.article?.description, article.description)
        assertEquals(response.body.article?.body, article.body)
        assertEquals(response.body.article?.tagList, article.tagList)
    }

    @Test
    fun `get all articles of feed`() {
        appRule.http.createArticle()

        // A second user follows the article's author; that user's feed must contain the article.
        val suffix = UUID.randomUUID().toString().take(8)
        val http = HttpUtil(appRule.port)
        http.createUser("feed-reader-$suffix@valid_email.com", "feed_reader_$suffix")

        http.post<ProfileDTO>("/profiles/user_name_test/follow")

        val response = http.get<ArticlesDTO>("/articles/feed")

        assertEquals(response.status, HttpStatus.SC_OK)
        assertNotNull(response.body.articles)
        assertPageConsistent(response.body)
        assertNotNull(response.body.articles.first())
        assertFalse(response.body.articles.first().title.isNullOrBlank())
        assertTrue(response.body.articles.first().tagList.isNotEmpty())
    }

    @Test
    fun `get single article by slug`() {
        val responseArticle = appRule.http.createArticle()
        val slug = responseArticle.body.article?.slug
        val response = appRule.http.get<ArticleDTO>("/articles/$slug")

        assertEquals(response.status, HttpStatus.SC_OK)
        assertNotNull(response.body.article)
        assertNotNull(response.body.article?.body)
        assertFalse(response.body.article?.title.isNullOrBlank())
        assertNotNull(response.body.article?.description)
        assertTrue(response.body.article?.tagList?.isNotEmpty() ?: false)
    }

    @Test
    fun `update article by slug`() {
        val responseCreated = appRule.http.createArticle()
        val slug = responseCreated.body.article?.slug
        val article = Article(body = "Very carefully.", title = "Teste", description = "Teste Desc")
        val response = appRule.http.put<ArticleDTO>("/articles/$slug", ArticleDTO(article))

        assertEquals(response.status, HttpStatus.SC_OK)
        assertNotNull(response.body.article)
        assertEquals(response.body.article?.body, article.body)
        assertNotNull(response.body.article?.body)
        assertFalse(response.body.article?.title.isNullOrBlank())
        assertNotNull(response.body.article?.description)
        assertTrue(response.body.article?.tagList?.isNotEmpty() ?: false)
    }

    @Test
    fun `favorite article by slug`() {
        val email = "favorite_slug_test@valid_email.com"
        val password = "Test"
        appRule.http.registerUser(email, password, "user_name_test_favorite")
        appRule.http.loginAndSetTokenHeader(email, password)
        val article = Article(
            title = "slug test",
            description = "Ever wonder how?",
            body = "Very carefully.",
            tagList = listOf("favorite")
        )
        appRule.http.post<ArticleDTO>("/articles", ArticleDTO(article))
        val slug = "slug-test"
        val response = appRule.http.post<ArticleDTO>("/articles/$slug/favorite")

        assertEquals(response.status, HttpStatus.SC_OK)
        assertNotNull(response.body.article)
        assertNotNull(response.body.article?.body)
        assertFalse(response.body.article?.title.isNullOrBlank())
        assertNotNull(response.body.article?.description)
        assertTrue(response.body.article?.tagList?.isNotEmpty() ?: false)
    }

    @Test
    fun `unfavorite article by slug`() {
        val email = "unfavorite_article@valid_email.com"
        val password = "Test"
        appRule.http.registerUser(email, password, "user_name_test_unfavorite")
        appRule.http.loginAndSetTokenHeader(email, password)
        val article = Article(
            title = "slug test 2",
            description = "Ever wonder how?",
            body = "Very carefully.",
            tagList = listOf("unfavorite")
        )
        appRule.http.post<ArticleDTO>("/articles", ArticleDTO(article))
        val slug = "slug-test-2"
        val response = appRule.http.deleteWithResponseBody<ArticleDTO>("/articles/$slug/favorite")

        assertEquals(response.status, HttpStatus.SC_OK)
        assertNotNull(response.body.article)
        assertNotNull(response.body.article?.body)
        assertFalse(response.body.article?.title.isNullOrBlank())
        assertNotNull(response.body.article?.description)
        assertTrue(response.body.article?.tagList?.isNotEmpty() ?: false)
    }

    @Test
    fun `favorite response has no author secrets`() {
        val suffix = UUID.randomUUID()
        val http = HttpUtil(appRule.port)
        val email = "fav-secrets-$suffix@valid_email.com"
        http.registerUser(email, "Test", "fav_secrets_$suffix")
        http.loginAndSetTokenHeader(email, "Test")
        val article = Article(
            title = "Favorite secrets $suffix",
            description = "desc",
            body = "body",
            tagList = listOf("t")
        )
        val created = http.post<ArticleDTO>("/articles", ArticleDTO(article))
        val slug = created.body.article!!.slug!!
        val response = http.postRaw("/articles/$slug/favorite", "")
        assertEquals(HttpStatus.SC_OK, response.status)
        assertNoAuthorSecrets(response.body)
    }

    @Test
    fun `unfavorite response has no author secrets`() {
        val suffix = UUID.randomUUID()
        val http = HttpUtil(appRule.port)
        val email = "unfav-secrets-$suffix@valid_email.com"
        http.registerUser(email, "Test", "unfav_secrets_$suffix")
        http.loginAndSetTokenHeader(email, "Test")
        val article = Article(
            title = "Unfavorite secrets $suffix",
            description = "desc",
            body = "body",
            tagList = listOf("t")
        )
        val created = http.post<ArticleDTO>("/articles", ArticleDTO(article))
        val slug = created.body.article!!.slug!!
        http.post<ArticleDTO>("/articles/$slug/favorite")
        val response = http.deleteRaw("/articles/$slug/favorite")
        assertEquals(HttpStatus.SC_OK, response.status)
        assertNoAuthorSecrets(response.body)
    }

    @Test
    fun `favorite unknown slug returns 404`() {
        val suffix = UUID.randomUUID()
        val http = HttpUtil(appRule.port)
        val email = "fav-404-$suffix@valid_email.com"
        http.registerUser(email, "Test", "fav_404_$suffix")
        http.loginAndSetTokenHeader(email, "Test")
        val unknownSlug = "missing-slug-$suffix"
        val response = http.postRaw("/articles/$unknownSlug/favorite", "")
        assertEquals(HttpStatus.SC_NOT_FOUND, response.status)
        assertTrue(response.body.contains("Article not found."))
    }

    @Test
    fun `delete article by slug`() {
        val responseCreate = appRule.http.createArticle()
        val slug = responseCreate.body.article?.slug
        val response = appRule.http.delete("/articles/$slug")

        assertEquals(response.status, HttpStatus.SC_OK)
        assertEquals(HttpStatus.SC_NOT_FOUND, appRule.http.getRaw("/articles/$slug").status)
    }

    @Test
    fun `only the author can update or delete`() {
        val created = appRule.http.createArticle()
        val slug = created.body.article!!.slug!!
        val suffix = UUID.randomUUID().toString().take(8)
        val other = HttpUtil(appRule.port)
        other.createUser("intruder-$suffix@valid_email.com", "intruder_$suffix")

        val update = other.putRaw("/articles/$slug", ArticleDTO(Article(title = "Mine now", description = "d", body = "b")))
        assertEquals(HttpStatus.SC_FORBIDDEN, update.status)
        assertTrue(update.body.contains("Only the author can update this article."))
        assertEquals(HttpStatus.SC_FORBIDDEN, other.delete("/articles/$slug").status)
        assertEquals(HttpStatus.SC_OK, other.getRaw("/articles/$slug").status)
    }

    @Test
    fun `feed requires a token and lists only followed authors`() {
        val anonymous = HttpUtil(appRule.port)
        assertEquals(HttpStatus.SC_UNAUTHORIZED, anonymous.getRaw("/articles/feed").status)

        val suffix = UUID.randomUUID().toString().take(8)
        val reader = HttpUtil(appRule.port)
        reader.createUser("lonely-$suffix@valid_email.com", "lonely_$suffix")
        val empty = reader.get<ArticlesDTO>("/articles/feed")
        assertEquals(HttpStatus.SC_OK, empty.status)
        assertEquals(0, empty.body.articlesCount)
        assertTrue(empty.body.articles.isEmpty())
    }
}
