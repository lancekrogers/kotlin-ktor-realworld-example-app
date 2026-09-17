package io.realworld.app.web.controllers

import io.realworld.app.domain.Article
import io.realworld.app.domain.ArticleDTO
import io.realworld.app.domain.Comment
import io.realworld.app.domain.CommentDTO
import io.realworld.app.domain.CommentsDTO
import io.realworld.app.web.rules.AppRule
import io.realworld.app.web.util.HttpUtil
import org.apache.http.HttpStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class CommentControllerTest {
    @Rule
    @JvmField
    val appRule = AppRule()

    @Test
    fun `add comment for article by slug`() {
        val token = UUID.randomUUID().toString().take(8)
        val http = HttpUtil(appRule.port)
        val email = "comment-ctrl-$token@valid_email.com"
        http.registerUser(email, "Test", "comment_ctrl_$token")
        http.loginAndSetTokenHeader(email, "Test")
        val article = Article(
            title = "Comment ctrl $token",
            description = "desc",
            body = "body",
            tagList = listOf("t")
        )
        val responseArticle = http.post<ArticleDTO>("/articles", ArticleDTO(article))
        assertEquals(HttpStatus.SC_OK, responseArticle.status)

        val comment = Comment(body = "Very carefully.")
        val response = http.post<CommentDTO>(
            "/articles/${responseArticle.body.article?.slug}/comments",
            CommentDTO(comment)
        )

        assertEquals(response.status, HttpStatus.SC_OK)
        assertEquals(response.body.comment?.body, comment.body)
    }

    @Test
    fun `get all comments for article by slug`() {
        val responseArticle = appRule.http.createArticle()

        val slug = responseArticle.body.article?.slug

        val comment = Comment(body = "Very carefully.")
        appRule.http.post<CommentDTO>(
            "/articles/$slug/comments",
            CommentDTO(comment)
        )

        val response = appRule.http.get<CommentsDTO>("/articles/$slug/comments")

        assertEquals(response.status, HttpStatus.SC_OK)
        assertTrue(response.body.comments.isNotEmpty())
        assertEquals(response.body.comments.first().body, comment.body)
    }

    @Test
    fun `delete comment for article by slug`() {
        val responseArticle = appRule.http.createArticle()

        val slug = responseArticle.body.article?.slug

        val comment = Comment(body = "Very carefully.")
        val responseAddComment = appRule.http.post<CommentDTO>(
            "/articles/$slug/comments",
            CommentDTO(comment)
        )

        val response = appRule.http.delete("/articles/$slug/comments/${responseAddComment.body.comment?.id}")

        assertEquals(response.status, HttpStatus.SC_OK)
    }
}
