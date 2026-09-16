package io.realworld.app.domain.repository

import io.realworld.app.config.DbConfig
import org.jetbrains.exposed.sql.LikePattern
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import java.util.UUID

class LowerOnClobProbeTest {
    companion object {
        @BeforeClass @JvmStatic
        fun db() {
            DbConfig.setup("jdbc:h2:mem:realworld;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false", "sa", "")
            ArticleRepository()
        }
    }

    @Test
    fun `lower on the text body matches case-insensitively with an escaped literal`() {
        val marker = "ZeBrA" + UUID.randomUUID().toString().take(8)
        transaction {
            val user = Users.insertAndGetId {
                it[email] = "$marker@probe.test"; it[username] = marker; it[password] = "x"
            }
            Articles.insert {
                it[slug] = marker.lowercase(); it[title] = "no match here"; it[description] = "d"
                it[body] = "body holds the $marker word"; it[author] = user
                it[createdAt] = 0L; it[updatedAt] = 0L
            }
        }
        val hits = transaction {
            val pattern = LikePattern("%", '\\') + LikePattern.ofLiteral(marker.lowercase()) + "%"
            Articles.select { Articles.body.lowerCase() like pattern }.count()
        }
        assertEquals(1L, hits)
    }
}
