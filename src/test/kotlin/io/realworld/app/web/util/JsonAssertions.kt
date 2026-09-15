package io.realworld.app.web.util

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.Assert.assertFalse

private val FORBIDDEN_AUTHOR_FIELDS = listOf("password", "email", "token")

/** Fails if any object under an "author" key carries user secrets (D008). */
fun assertNoAuthorSecrets(rawJson: String) {
    fun walk(node: JsonNode) {
        if (node.isObject) {
            node.fields().forEach { (key, value) ->
                if (key == "author" && value.isObject) {
                    FORBIDDEN_AUTHOR_FIELDS.forEach { field ->
                        assertFalse("author must not expose '$field': $value", value.has(field))
                    }
                }
                walk(value)
            }
        } else if (node.isArray) {
            node.forEach { walk(it) }
        }
    }
    walk(jacksonObjectMapper().readTree(rawJson))
}
