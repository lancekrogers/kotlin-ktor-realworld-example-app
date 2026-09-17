package io.realworld.app.domain.service

import io.realworld.app.domain.Profile
import io.realworld.app.domain.User
import io.realworld.app.domain.exceptions.NotFoundException
import io.realworld.app.domain.exceptions.UnauthorizedException
import io.realworld.app.domain.repository.UserRepository
import io.realworld.app.utils.JwtProvider
import io.realworld.app.utils.PasswordEncoder

class UserService(private val jwtProvider: JwtProvider, private val userRepository: UserRepository) {

    /**
     * Verified against a throwaway hash when no account matches, so a request for an unknown
     * email costs the same as one for a known email. Without this, response latency alone
     * discloses which addresses are registered.
     */
    private val dummyHash = PasswordEncoder.hash("timing-equalisation-placeholder")

    fun create(user: User): User {
        userRepository.findByEmail(user.email).apply {
            require(this == null) { "Email already registered!" }
        }
        val rawPassword = requireNotNull(user.password) { "Password is required." }
        userRepository.create(user.copy(password = PasswordEncoder.hash(rawPassword)))
        return user.withToken()
    }

    fun authenticate(user: User): User {
        val userFound = userRepository.findByEmail(user.email)
        val storedHash = userFound?.password ?: dummyHash
        val matches = PasswordEncoder.matches(user.password.orEmpty(), storedHash)
        if (userFound == null || !matches) {
            throw UnauthorizedException("email or password invalid!")
        }
        return userFound.withToken()
    }

    fun getByEmail(email: String): User {
        val user = userRepository.findByEmail(email)
        user ?: throw NotFoundException("User not found to get.")
        return user.withToken()
    }

    /** `following` is relative to [viewerEmail]; anonymous viewers always get false. */
    fun getProfileByUsername(viewerEmail: String?, username: String): Profile {
        val user = userRepository.findByUsername(username) ?: throw NotFoundException("Profile not found.")
        val following = viewerEmail?.let { userRepository.findIsFollowUser(it, user.id!!) } ?: false
        return Profile(user.username, user.bio, user.image, following)
    }

    /**
     * Hashing happens here, not in the repository, so both write paths go through it. The
     * update path previously wrote the raw request password straight into the column, which
     * stored it in plaintext and left the account permanently unable to log in, because
     * [authenticate] compares against a hash.
     */
    fun update(email: String, user: User): User? {
        val toPersist = user.password
            ?.let { user.copy(password = PasswordEncoder.hash(it)) }
            ?: user
        return userRepository.update(email, toPersist)?.stripPassword()
    }

    fun follow(email: String, usernameToFollow: String): Profile {
        return userRepository.follow(email, usernameToFollow).let { user ->
            Profile(user.username, user.bio, user.image, true)
        }
    }

    fun unfollow(email: String, usernameToUnfollow: String): Profile {
        return userRepository.unfollow(email, usernameToUnfollow).let { user ->
            Profile(user.username, user.bio, user.image, false)
        }
    }

    /** Never hand a password — raw or hashed — back to a caller. */
    private fun User.stripPassword(): User = copy(password = null)

    private fun User.withToken(): User = copy(token = jwtProvider.createJWT(this)).stripPassword()
}
