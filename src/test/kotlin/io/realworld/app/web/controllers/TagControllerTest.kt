package io.realworld.app.web.controllers

import io.realworld.app.domain.Article
import io.realworld.app.domain.ArticleDTO
import io.realworld.app.domain.TagDTO
import io.realworld.app.web.rules.AppRule
import org.apache.http.HttpStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class TagControllerTest {
    @Rule
    @JvmField
    val appRule = AppRule()

    @Test
    fun `get all tags`() {
        val suffix = UUID.randomUUID()
        val article = Article(
            title = "How to train your dragon $suffix",
            description = "Ever wonder how?",
            body = "Very carefully.",
            tagList = listOf("dragons", "training")
        )
        val email = "tags-$suffix@valid_email.com"
        val password = "Test"
        appRule.http.registerUser(email, password, "tags_user_$suffix")
        appRule.http.loginAndSetTokenHeader(email, password)

        val createResponse = appRule.http.post<ArticleDTO>("/articles", ArticleDTO(article))
        assertEquals(HttpStatus.SC_OK, createResponse.status)

        val response = appRule.http.get<TagDTO>("/tags")

        assertEquals(HttpStatus.SC_OK, response.status)
        assertTrue(response.body.tags.contains("dragons"))
        assertTrue(response.body.tags.contains("training"))
    }
}
