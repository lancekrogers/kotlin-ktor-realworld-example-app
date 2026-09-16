package io.realworld.app.ext

import org.junit.Assert.assertEquals
import org.junit.Test

class SlugTest {

    @Test
    fun toSlugBase_kebabCase() {
        assertEquals("how-to-train-your-dragon", "How to train your dragon".toSlugBase())
    }

    @Test
    fun toSlugBase_authorExpectations() {
        assertEquals("slug-test", "slug test".toSlugBase())
        assertEquals("slug-test-2", "slug test 2".toSlugBase())
    }

    @Test
    fun toSlugBase_punctuationRuns() {
        assertEquals("hello-world", "Hello,   World!!  ".toSlugBase())
    }

    @Test
    fun toSlugBase_diacritics() {
        assertEquals("cafe-creme", "Café Crème".toSlugBase())
    }

    @Test
    fun toSlugBase_allSymbolsFallsBackToArticle() {
        assertEquals("article", "!!!".toSlugBase())
    }

    @Test
    fun uniqueSlug_reservedWords() {
        assertEquals("search-2", uniqueSlug("search") { false })
        assertEquals("feed-2", uniqueSlug("feed") { false })
    }

    @Test
    fun uniqueSlug_successiveCollisions() {
        val taken = setOf("a", "a-2")
        assertEquals("a-3", uniqueSlug("a") { it in taken })
    }
}
