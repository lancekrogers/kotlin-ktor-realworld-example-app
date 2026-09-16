package io.realworld.app.web.controllers

import io.realworld.app.domain.Article
import io.realworld.app.domain.ArticleDTO
import io.realworld.app.domain.ArticlesDTO
import io.realworld.app.web.rules.AppRule
import io.realworld.app.web.util.HttpUtil
import io.realworld.app.web.util.assertNoAuthorSecrets
import org.apache.http.HttpStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.net.URLEncoder
import java.util.UUID

class ArticleSearchTest {
    @Rule
    @JvmField
    val appRule = AppRule()

    private fun loginAs(token: String) {
        val email = "$token@valid_email.com"
        val username = "user_$token"
        appRule.http.registerUser(email, "Test", username)
        appRule.http.loginAndSetTokenHeader(email, "Test")
    }

    private fun createArticle(title: String, body: String) {
        val article = Article(
            title = title,
            description = "desc",
            body = body,
            tagList = listOf("t")
        )
        val response = appRule.http.post<ArticleDTO>("/articles", ArticleDTO(article))
        assertEquals(HttpStatus.SC_OK, response.status)
    }

    private fun createWith(token: String, title: String, body: String) {
        loginAs(token)
        createArticle(title, body)
    }

    private fun search(term: String, extra: Map<String, Any> = emptyMap()) =
        appRule.http.get<ArticlesDTO>("/articles/search", mapOf("q" to term) + extra)

    @Test
    fun `title only match`() {
        val token = UUID.randomUUID().toString().take(8)
        createWith(token, "Article $token title", "plain body without marker")
        val response = search(token)
        assertEquals(HttpStatus.SC_OK, response.status)
        assertEquals(1, response.body.articlesCount)
        assertEquals(1, response.body.articles.size)
        assertTrue(response.body.articles.all { it.title!!.contains(token) })
    }

    @Test
    fun `body only match`() {
        val token = UUID.randomUUID().toString().take(8)
        createWith(token, "Plain title", "body contains $token marker")
        val response = search(token)
        assertEquals(HttpStatus.SC_OK, response.status)
        assertEquals(1, response.body.articlesCount)
        assertEquals(1, response.body.articles.size)
        assertTrue(response.body.articles.all { it.body.contains(token) })
    }

    @Test
    fun `case insensitive`() {
        val token = UUID.randomUUID().toString().take(8).uppercase()
        createWith(token, "Title $token", "body")
        val response = search(token.lowercase())
        assertEquals(HttpStatus.SC_OK, response.status)
        assertEquals(1, response.body.articlesCount)
        assertEquals(1, response.body.articles.size)
        assertTrue(response.body.articles.all { it.title!!.contains(token, ignoreCase = true) })
    }

    @Test
    fun `no match`() {
        val token = UUID.randomUUID().toString().take(8)
        createWith(token, "Title $token", "body $token")
        val response = search("nomatch-$token")
        assertEquals(HttpStatus.SC_OK, response.status)
        assertTrue(response.body.articles.isEmpty())
        assertEquals(0, response.body.articlesCount)
    }

    @Test
    fun `blank q`() {
        val encoded = URLEncoder.encode("   ", "UTF-8")
        val response = appRule.http.getRaw("/articles/search?q=$encoded")
        assertEquals(HttpStatus.SC_UNPROCESSABLE_ENTITY, response.status)
    }

    @Test
    fun `missing q`() {
        val response = appRule.http.getRaw("/articles/search")
        assertEquals(HttpStatus.SC_UNPROCESSABLE_ENTITY, response.status)
    }

    @Test
    fun `percent and underscore are literal`() {
        val token = UUID.randomUUID().toString().take(8)
        loginAs(token)
        createArticle("${token}100% pure_x", "body")
        val percentResponse = search("${token}100%")
        assertEquals(HttpStatus.SC_OK, percentResponse.status)
        assertEquals(1, percentResponse.body.articlesCount)
        assertEquals(1, percentResponse.body.articles.size)

        val secondTitle = "${token}1000 purex"
        createArticle(secondTitle, "body")
        val underscoreResponse = search("${token}100_")
        assertEquals(HttpStatus.SC_OK, underscoreResponse.status)
        assertEquals(0, underscoreResponse.body.articlesCount)
        assertTrue(underscoreResponse.body.articles.none { it.title == secondTitle })
    }

    @Test
    fun `limit below total`() {
        val token = UUID.randomUUID().toString().take(8)
        loginAs(token)
        createArticle("First $token", "body")
        createArticle("Second $token", "body")
        createArticle("Third $token", "body")
        val response = search(token, mapOf("limit" to 2))
        assertEquals(HttpStatus.SC_OK, response.status)
        assertEquals(2, response.body.articles.size)
        assertEquals(3, response.body.articlesCount)
    }

    @Test
    fun `bad paging`() {
        val token = UUID.randomUUID().toString().take(8)
        val cases = listOf(
            "limit=0" to "limit must be between 1 and 100.",
            "limit=101" to "limit must be between 1 and 100.",
            "limit=abc" to "limit must be an integer.",
            "offset=-1" to "offset must not be negative.",
            "offset=abc" to "offset must be an integer."
        )
        cases.forEach { (params, expectedMessage) ->
            val response = appRule.http.getRaw("/articles/search?q=$token&$params")
            assertEquals(HttpStatus.SC_UNPROCESSABLE_ENTITY, response.status)
            assertTrue(response.body.contains(expectedMessage))
        }
    }

    @Test
    fun `anonymous request is public`() {
        val token = UUID.randomUUID().toString().take(8)
        createWith(token, "Public $token", "body")
        val anonymous = HttpUtil(appRule.port)
        val response = anonymous.get<ArticlesDTO>("/articles/search", mapOf("q" to token))
        assertEquals(HttpStatus.SC_OK, response.status)
        assertEquals(1, response.body.articlesCount)
        assertNotNull(response.body.articles)
    }

    @Test
    fun `no author secrets`() {
        val token = UUID.randomUUID().toString().take(8)
        createWith(token, "Secrets check $token", "body")
        val response = appRule.http.getRaw("/articles/search?q=$token")
        assertEquals(HttpStatus.SC_OK, response.status)
        assertNoAuthorSecrets(response.body)
    }
}
