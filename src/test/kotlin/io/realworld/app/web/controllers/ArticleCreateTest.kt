package io.realworld.app.web.controllers

import io.realworld.app.domain.Article
import io.realworld.app.domain.ArticleDTO
import io.realworld.app.domain.repository.Articles
import io.realworld.app.web.rules.AppRule
import io.realworld.app.web.util.HttpUtil
import io.realworld.app.web.util.assertNoAuthorSecrets
import org.apache.http.HttpStatus
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runners.MethodSorters
import java.util.UUID

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ArticleCreateTest {
    companion object {
        var storedSlug: String = ""
    }

    @Rule
    @JvmField
    val appRule = AppRule()

    @Test
    fun a_writes_row() {
        val suffix = UUID.randomUUID()
        val http = HttpUtil(appRule.port)
        val email = "persist-$suffix@valid_email.com"
        http.registerUser(email, "Test", "persist_user_$suffix")
        http.loginAndSetTokenHeader(email, "Test")
        val title = "Persistence probe $suffix"
        val article = Article(title = title, description = "desc", body = "body", tagList = listOf("t"))
        val response = http.post<ArticleDTO>("/articles", ArticleDTO(article))
        assertEquals(HttpStatus.SC_OK, response.status)
        storedSlug = response.body.article!!.slug!!
    }

    @Test
    fun b_row_survives_new_app() {
        transaction {
            val count = Articles.select { Articles.slug eq storedSlug }.count()
            assertEquals(1L, count)
        }
    }

    @Test
    fun `blank title returns 422`() {
        val suffix = UUID.randomUUID()
        val http = HttpUtil(appRule.port)
        val email = "blank-title-$suffix@valid_email.com"
        http.registerUser(email, "Test", "blank_title_$suffix")
        http.loginAndSetTokenHeader(email, "Test")
        val article = Article(
            title = "  ",
            description = "desc",
            body = "body",
            tagList = listOf("t")
        )
        val response = http.postRaw("/articles", ArticleDTO(article))
        assertEquals(HttpStatus.SC_UNPROCESSABLE_ENTITY, response.status)
    }

    @Test
    fun `create without token returns 401`() {
        val suffix = UUID.randomUUID()
        val http = HttpUtil(appRule.port)
        val article = Article(
            title = "No token $suffix",
            description = "desc",
            body = "body",
            tagList = listOf("t")
        )
        val response = http.postRaw("/articles", ArticleDTO(article))
        assertEquals(HttpStatus.SC_UNAUTHORIZED, response.status)
    }

    @Test
    fun `duplicate title slug ends with -2`() {
        val suffix = UUID.randomUUID()
        val http = HttpUtil(appRule.port)
        val email = "dup-$suffix@valid_email.com"
        http.registerUser(email, "Test", "dup_user_$suffix")
        http.loginAndSetTokenHeader(email, "Test")
        val title = "Duplicate $suffix"
        val article = Article(title = title, description = "desc", body = "body", tagList = listOf("t"))
        val first = http.post<ArticleDTO>("/articles", ArticleDTO(article))
        assertEquals(HttpStatus.SC_OK, first.status)
        val second = http.post<ArticleDTO>("/articles", ArticleDTO(article))
        assertEquals(HttpStatus.SC_OK, second.status)
        assertTrue(second.body.article?.slug!!.endsWith("-2"))
    }

    @Test
    fun `missing body returns 422`() {
        val suffix = UUID.randomUUID()
        val http = HttpUtil(appRule.port)
        val email = "missing-body-$suffix@valid_email.com"
        http.registerUser(email, "Test", "missing_body_$suffix")
        http.loginAndSetTokenHeader(email, "Test")
        val response = http.postRaw(
            "/articles",
            """{"article":{"title":"Title $suffix","description":"desc","tagList":[]}}"""
        )
        assertEquals(HttpStatus.SC_UNPROCESSABLE_ENTITY, response.status)
    }

    @Test
    fun `raw response has no author secrets and ISO createdAt`() {
        val suffix = UUID.randomUUID()
        val http = HttpUtil(appRule.port)
        val email = "raw-$suffix@valid_email.com"
        http.registerUser(email, "Test", "raw_user_$suffix")
        http.loginAndSetTokenHeader(email, "Test")
        val article = Article(
            title = "Raw check $suffix",
            description = "desc",
            body = "body",
            tagList = listOf("t")
        )
        val response = http.postRaw("/articles", ArticleDTO(article))
        assertEquals(HttpStatus.SC_OK, response.status)
        val rawJson = response.body
        assertNoAuthorSecrets(rawJson)
        assertTrue("createdAt should be ISO string", rawJson.contains("\"createdAt\":\""))
    }
}
