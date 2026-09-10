package io.realworld.app.utils

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import io.realworld.app.domain.User
import java.security.SecureRandom
import java.util.Base64
import java.util.Date

object JwtProvider {
    private const val VALIDITY_IN_MS = 36_000_00 * 10 // 10 hours
    private const val MIN_SECRET_LENGTH = 32

    const val issuer = "ktor-realworld"
    const val audience = "ktor-audience"

    private val algorithm: Algorithm = Algorithm.HMAC256(loadSecret())

    /**
     * Issuer *and* audience are asserted here rather than at the call site, so every code path
     * that verifies a token gets both checks. Previously the audience was only checked inside
     * the Ktor auth block, and [decodeJWT] built a verifier that skipped the issuer entirely.
     */
    val verifier: JWTVerifier = JWT.require(algorithm)
        .withIssuer(issuer)
        .withAudience(audience)
        .build()

    fun decodeJWT(token: String): DecodedJWT = verifier.verify(token)

    fun createJWT(user: User): String? =
        JWT.create()
            .withIssuedAt(Date())
            .withSubject("Authentication")
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("email", user.email)
            .withExpiresAt(Date(System.currentTimeMillis() + VALIDITY_IN_MS))
            .sign(algorithm)

    /**
     * The signing key used to be the string literal "something-very-secret-here", committed to
     * a public repository. Anyone holding it could mint a token for any account, because the
     * issuer, audience, and email claim are all that authentication checks.
     *
     * Now: read JWT_SECRET from the environment. If it is absent we generate a random key for
     * this process rather than falling back to a known constant, so the app is never signing
     * with a guessable value. Tokens do not survive a restart in that mode, which is the
     * intended nudge to set the variable before deploying.
     */
    private fun loadSecret(): String {
        val configured = System.getenv("JWT_SECRET")?.trim()
        if (!configured.isNullOrBlank()) {
            require(configured.length >= MIN_SECRET_LENGTH) {
                "JWT_SECRET must be at least $MIN_SECRET_LENGTH characters; got ${configured.length}."
            }
            return configured
        }
        val generated = ByteArray(32)
            .also { SecureRandom().nextBytes(it) }
            .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
        System.err.println(
            "WARNING: JWT_SECRET is not set. Generated an ephemeral signing key for this process; " +
                "every issued token becomes invalid when it exits. Set JWT_SECRET before deploying."
        )
        return generated
    }
}
