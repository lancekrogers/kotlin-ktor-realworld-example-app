package io.realworld.app.web.controllers

import io.realworld.app.domain.Article
import io.realworld.app.domain.ArticleDTO
import io.realworld.app.domain.Comment
import io.realworld.app.domain.CommentDTO
import io.realworld.app.domain.ProfileStatsDTO
import io.realworld.app.web.rules.AppRule
import io.realworld.app.web.util.HttpUtil
import org.apache.http.HttpStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class ProfileStatsTest {
    @Rule
    @JvmField
    val appRule = AppRule()

    private fun registerUser(token: String, suffix: String): HttpUtil {
        val http = HttpUtil(appRule.port)
        val email = "stats-$token-$suffix@valid_email.com"
        val username = "stats_${token}_$suffix"
        http.registerUser(email, "Test", username)
        http.loginAndSetTokenHeader(email, "Test")
        return http
    }

    @Test
    fun `new user has zero activity`() {
        val token = UUID.randomUUID().toString().take(8)
        val http = registerUser(token, "zero")
        val username = "stats_${token}_zero"

        val response = http.get<ProfileStatsDTO>("/profiles/$username/stats")
        assertEquals(HttpStatus.SC_OK, response.status)
        assertEquals(0L, response.body.stats.articlesCount)
        assertEquals(0L, response.body.stats.commentsCount)
        assertEquals(0L, response.body.stats.favoritesCount)
    }

    @Test
    fun `article comment and favorite each count once`() {
        val token = UUID.randomUUID().toString().take(8)
        val u = registerUser(token, "u")
        val other1 = registerUser(token, "other1")
        val other2 = registerUser(token, "other2")
        val username = "stats_${token}_u"

        val ownArticle = Article(
            title = "Stats $token U article",
            description = "desc",
            body = "body",
            tagList = listOf("t")
        )
        u.post<ArticleDTO>("/articles", ArticleDTO(ownArticle))

        val otherArticle1 = Article(
            title = "Stats $token other1 article",
            description = "desc",
            body = "body",
            tagList = listOf("t")
        )
        val commentTarget = other1.post<ArticleDTO>("/articles", ArticleDTO(otherArticle1))
        val commentSlug = commentTarget.body.article!!.slug!!

        val otherArticle2 = Article(
            title = "Stats $token other2 article",
            description = "desc",
            body = "body",
            tagList = listOf("t")
        )
        val favoriteTarget = other2.post<ArticleDTO>("/articles", ArticleDTO(otherArticle2))
        val favoriteSlug = favoriteTarget.body.article!!.slug!!

        u.post<CommentDTO>("/articles/$commentSlug/comments", CommentDTO(Comment(body = "Nice $token")))
        u.post<ArticleDTO>("/articles/$favoriteSlug/favorite")

        val response = u.get<ProfileStatsDTO>("/profiles/$username/stats")
        assertEquals(HttpStatus.SC_OK, response.status)
        assertEquals(1L, response.body.stats.articlesCount)
        assertEquals(1L, response.body.stats.commentsCount)
        assertEquals(1L, response.body.stats.favoritesCount)
    }

    @Test
    fun `favorites count what the user gave`() {
        val token = UUID.randomUUID().toString().take(8)
        val v = registerUser(token, "v")
        val w = registerUser(token, "w")
        val vUsername = "stats_${token}_v"
        val wUsername = "stats_${token}_w"

        val article = Article(
            title = "Stats $token W article",
            description = "desc",
            body = "body",
            tagList = listOf("t")
        )
        val created = w.post<ArticleDTO>("/articles", ArticleDTO(article))
        val slug = created.body.article!!.slug!!

        v.post<ArticleDTO>("/articles/$slug/favorite")

        val vStats = v.get<ProfileStatsDTO>("/profiles/$vUsername/stats")
        assertEquals(HttpStatus.SC_OK, vStats.status)
        assertEquals(1L, vStats.body.stats.favoritesCount)

        val wStats = w.get<ProfileStatsDTO>("/profiles/$wUsername/stats")
        assertEquals(HttpStatus.SC_OK, wStats.status)
        assertEquals(0L, wStats.body.stats.favoritesCount)
    }

    @Test
    fun `unknown username is 404`() {
        val token = UUID.randomUUID()
        val response = appRule.http.getRaw("/profiles/nobody-$token/stats")
        assertEquals(HttpStatus.SC_NOT_FOUND, response.status)
    }

    @Test
    fun `anonymous request is public`() {
        val token = UUID.randomUUID().toString().take(8)
        registerUser(token, "anon")
        val username = "stats_${token}_anon"

        val anonymous = HttpUtil(appRule.port)
        val response = anonymous.get<ProfileStatsDTO>("/profiles/$username/stats")
        assertEquals(HttpStatus.SC_OK, response.status)
    }
}
