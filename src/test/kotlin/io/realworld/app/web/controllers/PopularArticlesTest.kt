package io.realworld.app.web.controllers

import io.realworld.app.domain.Article
import io.realworld.app.domain.ArticleDTO
import io.realworld.app.domain.ArticlesDTO
import io.realworld.app.web.rules.AppRule
import io.realworld.app.web.util.HttpUtil
import io.realworld.app.web.util.assertNoAuthorSecrets
import org.apache.http.HttpStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class PopularArticlesTest {
    @Rule
    @JvmField
    val appRule = AppRule()

    private fun registerUser(token: String, suffix: String): HttpUtil {
        val http = HttpUtil(appRule.port)
        val email = "popular-$token-$suffix@valid_email.com"
        val username = "popular_${token}_$suffix"
        http.registerUser(email, "Test", username)
        http.loginAndSetTokenHeader(email, "Test")
        return http
    }

    private fun createArticle(http: HttpUtil, title: String): String {
        val article = Article(title = title, description = "desc", body = "body", tagList = listOf("t"))
        val response = http.post<ArticleDTO>("/articles", ArticleDTO(article))
        assertEquals(HttpStatus.SC_OK, response.status)
        return response.body.article!!.slug!!
    }

    private fun favorite(http: HttpUtil, slug: String) {
        val response = http.post<ArticleDTO>("/articles/$slug/favorite")
        assertEquals(HttpStatus.SC_OK, response.status)
    }

    private fun fullWalk(http: HttpUtil = appRule.http): List<String> {
        val slugs = mutableListOf<String>()
        var offset = 0
        var pages = 0
        while (pages < 50) {
            val response = http.get<ArticlesDTO>(
                "/articles/feed/popular",
                mapOf("limit" to 100, "offset" to offset)
            )
            assertEquals(HttpStatus.SC_OK, response.status)
            if (response.body.articles.isEmpty()) break
            slugs.addAll(response.body.articles.mapNotNull { it.slug })
            offset += response.body.articles.size
            pages++
        }
        assertTrue("full walk exceeded 50 pages", pages < 50)
        return slugs
    }

    private fun assertRelativeOrder(allSlugs: List<String>, expectedSlugs: List<String>) {
        val positions = expectedSlugs.map { allSlugs.indexOf(it) }
        assertTrue("expected slugs missing from feed: $expectedSlugs in $allSlugs", positions.all { it >= 0 })
        assertTrue(
            "relative order wrong: expected $expectedSlugs, positions $positions in feed",
            positions.zipWithNext().all { (a, b) -> a < b }
        )
    }

    @Test
    fun `more favorites ranks first`() {
        val token = UUID.randomUUID().toString().take(8)
        val author = registerUser(token, "author")
        val fav1 = registerUser(token, "fav1")
        val fav2 = registerUser(token, "fav2")
        val fav3 = registerUser(token, "fav3")

        val slugA = createArticle(author, "Popular $token A two favorites")
        val slugB = createArticle(author, "Popular $token B one favorite")
        val slugC = createArticle(author, "Popular $token C zero favorites")

        favorite(fav1, slugA)
        favorite(fav2, slugA)
        favorite(fav3, slugB)

        val slugs = fullWalk()
        assertRelativeOrder(slugs, listOf(slugA, slugB, slugC))
    }

    @Test
    fun `equal counts newest first`() {
        val token = UUID.randomUUID().toString().take(8)
        val author = registerUser(token, "author")
        val fav = registerUser(token, "fav")

        val slugD = createArticle(author, "Popular $token D equal count")
        favorite(fav, slugD)
        Thread.sleep(5)
        val slugE = createArticle(author, "Popular $token E equal count newer")
        favorite(fav, slugE)

        val slugs = fullWalk()
        assertRelativeOrder(slugs, listOf(slugE, slugD))
    }

    @Test
    fun `pages are stable`() {
        val k = 2
        val pageSize = k + 1
        val bulk = appRule.http.get<ArticlesDTO>(
            "/articles/feed/popular",
            mapOf("limit" to pageSize, "offset" to 0)
        )
        assertEquals(HttpStatus.SC_OK, bulk.status)
        val expected = bulk.body.articles.mapNotNull { it.slug }

        val paged = (0..k).map { offset ->
            appRule.http.get<ArticlesDTO>(
                "/articles/feed/popular",
                mapOf("limit" to 1, "offset" to offset)
            ).body.articles.single().slug!!
        }
        assertEquals(expected, paged)
    }

    @Test
    fun `zero favorite included`() {
        val token = UUID.randomUUID().toString().take(8)
        val author = registerUser(token, "author")
        val slug = createArticle(author, "Popular $token zero only")

        val slugs = fullWalk()
        assertTrue("zero-favorite article must appear in feed", slugs.contains(slug))
    }

    @Test
    fun `offset past end`() {
        val first = appRule.http.get<ArticlesDTO>("/articles/feed/popular", mapOf("limit" to 1, "offset" to 0))
        assertEquals(HttpStatus.SC_OK, first.status)
        val total = first.body.articlesCount

        val past = appRule.http.get<ArticlesDTO>(
            "/articles/feed/popular",
            mapOf("limit" to 20, "offset" to total)
        )
        assertEquals(HttpStatus.SC_OK, past.status)
        assertTrue(past.body.articles.isEmpty())
        assertEquals(total, past.body.articlesCount)
    }

    @Test
    fun `bad limit`() {
        val response = appRule.http.getRaw("/articles/feed/popular?limit=0")
        assertEquals(HttpStatus.SC_UNPROCESSABLE_ENTITY, response.status)
        assertTrue(response.body.contains("limit must be between 1 and 100."))
    }

    @Test
    fun `anonymous is public`() {
        val anonymous = HttpUtil(appRule.port)
        val response = anonymous.get<ArticlesDTO>("/articles/feed/popular")
        assertEquals(HttpStatus.SC_OK, response.status)
    }

    @Test
    fun `favorited is viewer specific`() {
        val token = UUID.randomUUID().toString().take(8)
        val viewer = registerUser(token, "viewer")
        val slug = createArticle(viewer, "Popular $token favorited check")
        favorite(viewer, slug)

        val authResponse = viewer.get<ArticlesDTO>("/articles/feed/popular", mapOf("limit" to 100))
        assertEquals(HttpStatus.SC_OK, authResponse.status)
        val authArticle = authResponse.body.articles.first { it.slug == slug }
        assertTrue(authArticle.favorited)

        val anonymous = HttpUtil(appRule.port)
        val anonResponse = anonymous.get<ArticlesDTO>("/articles/feed/popular", mapOf("limit" to 100))
        assertEquals(HttpStatus.SC_OK, anonResponse.status)
        val anonArticle = anonResponse.body.articles.first { it.slug == slug }
        assertFalse(anonArticle.favorited)
    }

    @Test
    fun `double favorite`() {
        val token = UUID.randomUUID().toString().take(8)
        val author = registerUser(token, "author")
        val slug = createArticle(author, "Popular $token double favorite")

        val first = author.post<ArticleDTO>("/articles/$slug/favorite")
        assertEquals(HttpStatus.SC_OK, first.status)
        assertEquals(1L, first.body.article!!.favoritesCount)

        val second = author.post<ArticleDTO>("/articles/$slug/favorite")
        assertEquals(HttpStatus.SC_OK, second.status)
        assertEquals(1L, second.body.article!!.favoritesCount)
    }

    @Test
    fun `no author secrets`() {
        val response = appRule.http.getRaw("/articles/feed/popular?limit=5")
        assertEquals(HttpStatus.SC_OK, response.status)
        assertNoAuthorSecrets(response.body)
    }
}
