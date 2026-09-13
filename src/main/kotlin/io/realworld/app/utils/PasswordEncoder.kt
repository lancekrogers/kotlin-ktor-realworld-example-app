package io.realworld.app.utils

import at.favre.lib.crypto.bcrypt.BCrypt
import at.favre.lib.crypto.bcrypt.LongPasswordStrategies

/**
 * Password hashing.
 *
 * This replaces an unsalted HMAC-SHA256 keyed with a secret that was committed to the
 * repository. An HMAC is a message authentication code, not a password hash: it has no salt,
 * no work factor, and here its key was public, so identical passwords collided across accounts
 * and the whole table was crackable at GPU speed.
 *
 * bcrypt gives us a per-hash salt and a tunable cost. The salt is embedded in the output, so
 * the stored string is self-describing and no separate column is needed.
 */
object PasswordEncoder {
    /** ~250ms per hash on current hardware. Raise as hardware improves. */
    private const val COST = 12

    /**
     * bcrypt silently truncates at 72 bytes, so long passphrases would collide on their first
     * 72 bytes. Pre-hashing with SHA-512 removes the limit instead of rejecting the input.
     */
    private val hasher = BCrypt.with(
        BCrypt.Version.VERSION_2A,
        LongPasswordStrategies.hashSha512(BCrypt.Version.VERSION_2A)
    )
    private val verifier = BCrypt.verifyer(
        BCrypt.Version.VERSION_2A,
        LongPasswordStrategies.hashSha512(BCrypt.Version.VERSION_2A)
    )

    fun hash(rawPassword: String): String =
        hasher.hashToString(COST, rawPassword.toCharArray())

    fun matches(rawPassword: String, storedHash: String): Boolean =
        runCatching {
            verifier.verify(rawPassword.toCharArray(), storedHash.toCharArray()).verified
        }.getOrDefault(false)
}
