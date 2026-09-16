package io.realworld.app.domain.repository

import io.realworld.app.config.DbConfig
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.UUID

class ArticleSchemaTest {
    @Test
    fun `schema creation is idempotent and tags and slug constraints hold`() {
        val suffix = UUID.randomUUID()
        val tagName = "tag-$suffix"
        val slug = "slug-$suffix"
        val email = "user-$suffix@test.com"
        val username = "user-$suffix"

        DbConfig.setup("jdbc:h2:mem:realworld;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false", "sa", "")
        ArticleRepository()
        ArticleRepository()

        transaction {
            val userId = Users.insertAndGetId {
                it[Users.email] = email
                it[Users.username] = username
                it[Users.password] = "password"
            }

            val now = System.currentTimeMillis()
            Articles.insertAndGetId {
                it[Articles.slug] = slug
                it[Articles.title] = "Title $suffix"
                it[Articles.description] = "Description $suffix"
                it[Articles.body] = "Body $suffix"
                it[Articles.author] = userId
                it[Articles.createdAt] = now
                it[Articles.updatedAt] = now
            }

            val ids1 = Tags.idsFor(listOf(tagName))
            val ids2 = Tags.idsFor(listOf(tagName))
            assertEquals(ids1.single(), ids2.single())

            assertThrows(ExposedSQLException::class.java) {
                Articles.insert {
                    it[Articles.slug] = slug
                    it[Articles.title] = "Other title"
                    it[Articles.description] = "Other description"
                    it[Articles.body] = "Other body"
                    it[Articles.author] = userId
                    it[Articles.createdAt] = now
                    it[Articles.updatedAt] = now
                }
            }
        }
    }
}
