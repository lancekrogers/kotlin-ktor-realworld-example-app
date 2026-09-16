package io.realworld.app.domain.repository

import io.realworld.app.config.DbConfig
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.util.UUID

class ArticleFollowingMappingTest {
    companion object {
        private lateinit var repo: ArticleRepository

        @BeforeClass @JvmStatic
        fun db() {
            DbConfig.setup("jdbc:h2:mem:realworld;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false", "sa", "")
            UserRepository()
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

    private fun insertArticle(userId: EntityID<Long>, slug: String): String = transaction {
        Articles.insertAndGetId {
            it[Articles.slug] = slug
            it[Articles.title] = "title"
            it[Articles.description] = "description"
            it[Articles.body] = "body"
            it[Articles.author] = userId
            it[Articles.createdAt] = System.currentTimeMillis()
            it[Articles.updatedAt] = System.currentTimeMillis()
        }
        slug
    }

    @Test
    fun `following depends on viewer`() {
        val token = UUID.randomUUID().toString()
        val authorId = insertUser("author-$token")
        val followerEmail = "follower-$token@test.com"
        val otherEmail = "other-$token@test.com"
        insertUser("follower-$token")
        insertUser("other-$token")
        val slug = insertArticle(authorId, "slug-following-$token")

        transaction {
            Follows.insert {
                it[user] = authorId.value
                it[follower] = Users.select { Users.email eq followerEmail }.single()[Users.id].value
            }
        }

        val forFollower = repo.findBySlug(slug, followerEmail)!!
        val forOther = repo.findBySlug(slug, otherEmail)!!
        val anonymous = repo.findBySlug(slug, null)!!

        assertTrue(forFollower.author!!.following)
        assertFalse(forOther.author!!.following)
        assertFalse(anonymous.author!!.following)
    }
}
