package io.realworld.app.web.controllers

import io.realworld.app.domain.ProfileDTO
import io.realworld.app.web.rules.AppRule
import io.realworld.app.web.util.HttpUtil
import org.apache.http.HttpStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class ProfileControllerTest {
    @Rule
    @JvmField
    val appRule = AppRule()

    private data class Pair(val celeb: String, val viewer: HttpUtil)

    /** Registers a celebrity and a logged-in viewer with unique names; the database persists across tests. */
    private fun celebAndViewer(): Pair {
        val suffix = UUID.randomUUID().toString().take(8)
        val celeb = "celeb_$suffix"
        appRule.http.registerUser("celeb-$suffix@valid_email.com", "Test", celeb)
        val viewer = HttpUtil(appRule.port)
        viewer.createUser("viewer-$suffix@valid_email.com", "viewer_$suffix")
        return Pair(celeb, viewer)
    }

    @Test
    fun `get profile by username`() {
        val (celeb, viewer) = celebAndViewer()
        val response = viewer.get<ProfileDTO>("/profiles/$celeb")

        assertEquals(HttpStatus.SC_OK, response.status)
        assertEquals(celeb, response.body.profile?.username)
        assertFalse(response.body.profile?.following ?: true)
    }

    @Test
    fun `get profile anonymously`() {
        val (celeb, _) = celebAndViewer()
        val response = HttpUtil(appRule.port).get<ProfileDTO>("/profiles/$celeb")

        assertEquals(HttpStatus.SC_OK, response.status)
        assertEquals(celeb, response.body.profile?.username)
        assertFalse(response.body.profile?.following ?: true)
    }

    @Test
    fun `unknown profile returns 404`() {
        val response = HttpUtil(appRule.port).getRaw("/profiles/nobody-${UUID.randomUUID()}")
        assertEquals(HttpStatus.SC_NOT_FOUND, response.status)
        assertTrue(response.body.contains("Profile not found."))
    }

    @Test
    fun `follow profile by username`() {
        val (celeb, viewer) = celebAndViewer()
        val response = viewer.post<ProfileDTO>("/profiles/$celeb/follow")

        assertEquals(HttpStatus.SC_OK, response.status)
        assertEquals(celeb, response.body.profile?.username)
        assertTrue(response.body.profile?.following ?: false)

        // Following again is idempotent, and the profile now reads as followed.
        assertEquals(HttpStatus.SC_OK, viewer.post<ProfileDTO>("/profiles/$celeb/follow").status)
        assertTrue(viewer.get<ProfileDTO>("/profiles/$celeb").body.profile?.following ?: false)
    }

    @Test
    fun `unfollow profile by username`() {
        val (celeb, viewer) = celebAndViewer()
        viewer.post<ProfileDTO>("/profiles/$celeb/follow")
        val response = viewer.deleteWithResponseBody<ProfileDTO>("/profiles/$celeb/follow")

        assertEquals(HttpStatus.SC_OK, response.status)
        assertEquals(celeb, response.body.profile?.username)
        assertFalse(response.body.profile?.following ?: true)
        assertFalse(viewer.get<ProfileDTO>("/profiles/$celeb").body.profile?.following ?: true)
    }

    @Test
    fun `follow requires a token`() {
        val (celeb, _) = celebAndViewer()
        assertEquals(HttpStatus.SC_UNAUTHORIZED, HttpUtil(appRule.port).postRaw("/profiles/$celeb/follow", "").status)
    }
}
