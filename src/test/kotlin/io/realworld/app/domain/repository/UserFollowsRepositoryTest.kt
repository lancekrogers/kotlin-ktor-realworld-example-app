package io.realworld.app.domain.repository

import io.realworld.app.config.DbConfig
import io.realworld.app.domain.User
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.util.UUID

/**
 * `follow` writes the row as (user = followed, follower = caller). `unfollow` must delete
 * that same orientation. The earlier implementation deleted (user = caller, follower =
 * followed), which matched nothing, so the follow survived and the test below fails on it.
 */
class UserFollowsRepositoryTest {
    companion object {
        private lateinit var repo: UserRepository

        @BeforeClass @JvmStatic
        fun db() {
            DbConfig.setup("jdbc:h2:mem:realworld;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false", "sa", "")
            repo = UserRepository()
        }
    }

    private fun createUser(marker: String): User {
        val id = repo.create(User(email = "$marker@test.com", username = marker, password = "password"))
        return repo.findByEmail("$marker@test.com")!!.copy(id = id)
    }

    @Test
    fun `unfollow removes the follow it was given`() {
        val token = UUID.randomUUID().toString()
        val follower = createUser("follower-$token")
        val followed = createUser("followed-$token")

        repo.follow(follower.email, followed.username!!)
        assertTrue("follow should be visible before unfollow", repo.findIsFollowUser(follower.email, followed.id!!))

        repo.unfollow(follower.email, followed.username!!)
        assertFalse("follow should be gone after unfollow", repo.findIsFollowUser(follower.email, followed.id!!))
    }

    @Test
    fun `unfollow leaves the reverse relation alone`() {
        val token = UUID.randomUUID().toString()
        val a = createUser("a-$token")
        val b = createUser("b-$token")

        repo.follow(a.email, b.username!!)
        repo.follow(b.email, a.username!!)

        repo.unfollow(a.email, b.username!!)

        assertFalse("a no longer follows b", repo.findIsFollowUser(a.email, b.id!!))
        assertTrue("b still follows a", repo.findIsFollowUser(b.email, a.id!!))
    }
}
