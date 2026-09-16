package io.realworld.app.ext

import java.text.Normalizer

/** Slugs an article may not use: constant route segments shadow them (D003). */
val RESERVED_SLUGS = setOf("search", "feed")

private val COMBINING_MARKS = Regex("\\p{M}+")
private val NON_SLUG_CHARS = Regex("[^a-z0-9]+")

fun String.toSlugBase(): String {
    val stripped = Normalizer.normalize(this, Normalizer.Form.NFD).replace(COMBINING_MARKS, "")
    val slug = stripped.lowercase().replace(NON_SLUG_CHARS, "-").trim('-')
    return slug.ifEmpty { "article" }
}

/** The first free slug: [base], then base-2, base-3, and so on, never a reserved word. */
fun uniqueSlug(base: String, isTaken: (String) -> Boolean): String {
    fun taken(candidate: String) = candidate in RESERVED_SLUGS || isTaken(candidate)
    if (!taken(base)) return base
    return generateSequence(2) { it + 1 }.map { "$base-$it" }.first { !taken(it) }
}
