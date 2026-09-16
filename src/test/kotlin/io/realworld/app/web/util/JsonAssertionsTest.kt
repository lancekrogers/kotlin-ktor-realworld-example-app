package io.realworld.app.web.util

import org.junit.Assert.assertThrows
import org.junit.Test

class JsonAssertionsTest {

    @Test
    fun passesForCleanAuthorProfile() {
        assertNoAuthorSecrets(
            """{"article":{"author":{"username":"a","bio":null,"image":null,"following":false}}}"""
        )
    }

    @Test
    fun throwsWhenAuthorHasPassword() {
        assertThrows(AssertionError::class.java) {
            assertNoAuthorSecrets(
                """{"article":{"author":{"username":"a","password":"x"}}}"""
            )
        }
    }

    @Test
    fun throwsWhenAuthorHasEmail() {
        assertThrows(AssertionError::class.java) {
            assertNoAuthorSecrets(
                """{"article":{"author":{"username":"a","email":"leak@example.com"}}}"""
            )
        }
    }

    @Test
    fun throwsWhenAuthorHasToken() {
        assertThrows(AssertionError::class.java) {
            assertNoAuthorSecrets(
                """{"article":{"author":{"username":"a","token":"secret-token"}}}"""
            )
        }
    }

    @Test
    fun throwsWhenAuthorInArticlesArrayHasForbiddenField() {
        assertThrows(AssertionError::class.java) {
            assertNoAuthorSecrets(
                """{"articles":[{"author":{"username":"a","password":"x"}}]}"""
            )
        }
    }
}
