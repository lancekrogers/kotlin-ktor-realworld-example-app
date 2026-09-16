package io.realworld.app.web.controllers

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.realworld.app.domain.Article
import io.realworld.app.domain.ArticleDTO
import io.realworld.app.domain.Comment
import io.realworld.app.domain.CommentDTO
import io.realworld.app.web.rules.AppRule
import io.realworld.app.web.util.HttpUtil
import io.realworld.app.web.util.assertNoAuthorSecrets
import org.apache.http.HttpStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class CommentCreateTest {
    @Rule
    @JvmField
    val appRule = AppRule()

    private fun registerAndCreateArticle(token: String): Pair<HttpUtil, String> {
        val http = HttpUtil(appRule.port)
        val email = "comment-$token@valid_email.com"
        val username = "comment_user_$token"
        http.registerUser(email, "Test", username)
        http.loginAndSetTokenHeader(email, "Test")
        val article = Article(
            title = "Comment $token article",
            description = "desc",
            body = "body",
            tagList = listOf("t")
        )
        val response = http.post<ArticleDTO>("/articles", ArticleDTO(article))
        assertEquals(HttpStatus.SC_OK, response.status)
        return http to response.body.article!!.slug!!
    }

    @Test
    fun `blank body returns 422`() {
        val token = UUID.randomUUID().toString().take(8)
        val (http, slug) = registerAndCreateArticle(token)
        val response = http.postRaw("/articles/$slug/comments", CommentDTO(Comment(body = "  ")))
        assertEquals(HttpStatus.SC_UNPROCESSABLE_ENTITY, response.status)
    }

    @Test
    fun `missing body returns 422`() {
        val token = UUID.randomUUID().toString().take(8)
        val (http, slug) = registerAndCreateArticle(token)
        // postRawJson, not postRaw: postRaw's Any parameter binds Unirest's body(Object) overload,
        // which would JSON-encode this string and send "{\"comment\":{}}" — a type mismatch, not a
        // payload with an absent body field. Both return 422, so the status cannot tell them apart.
        val response = http.postRawJson(
            "/articles/$slug/comments",
            """{"comment":{}}"""
        )
        assertEquals(HttpStatus.SC_UNPROCESSABLE_ENTITY, response.status)
    }

    @Test
    fun `unknown slug returns 404`() {
        val token = UUID.randomUUID().toString().take(8)
        val http = HttpUtil(appRule.port)
        val email = "comment-$token@valid_email.com"
        http.registerUser(email, "Test", "comment_user_$token")
        http.loginAndSetTokenHeader(email, "Test")
        val response = http.postRaw(
            "/articles/unknown-slug-$token/comments",
            CommentDTO(Comment(body = "Hello $token"))
        )
        assertEquals(HttpStatus.SC_NOT_FOUND, response.status)
    }

    @Test
    fun `no token returns 401`() {
        val token = UUID.randomUUID().toString().take(8)
        val (_, slug) = registerAndCreateArticle(token)
        val http = HttpUtil(appRule.port)
        val response = http.postRaw("/articles/$slug/comments", CommentDTO(Comment(body = "Hello $token")))
        assertEquals(HttpStatus.SC_UNAUTHORIZED, response.status)
    }

    @Test
    fun `postRawJson sends the object rather than a quoted string`() {
        val token = UUID.randomUUID().toString().take(8)
        val (http, slug) = registerAndCreateArticle(token)

        // Guards the helper itself. A valid payload is the only way to observe which Unirest overload
        // was chosen: sent as a raw object this is accepted, but JSON-encoded into "{\"comment\":...}"
        // it is a type mismatch and comes back 422. An invalid payload returns 422 either way, which is
        // exactly how the missing-field test above went unnoticed.
        val response = http.postRawJson(
            "/articles/$slug/comments",
            """{"comment":{"body":"Raw JSON $token"}}"""
        )

        assertEquals(HttpStatus.SC_OK, response.status)
        val echoed = jacksonObjectMapper().readTree(response.body).path("comment").path("body").asText()
        assertEquals("Raw JSON $token", echoed)
    }

    @Test
    fun `raw response has no author secrets`() {
        val token = UUID.randomUUID().toString().take(8)
        val (http, slug) = registerAndCreateArticle(token)
        val response = http.postRaw("/articles/$slug/comments", CommentDTO(Comment(body = "Hello $token")))
        assertEquals(HttpStatus.SC_OK, response.status)
        assertNoAuthorSecrets(response.body)
    }
}
