package io.realworld.app.domain

import io.ktor.auth.Principal
import io.realworld.app.ext.isEmailValid

data class UserDTO(val user: User? = null) {
    fun validRegister(): User {
        require(
            user != null &&
                user.email.isEmailValid() &&
                !user.password.isNullOrBlank() &&
                !user.username.isNullOrBlank()
        ) { "User is invalid." }
        return user
    }

    fun validLogin(): User {
        require(
            user != null &&
                user.email.isEmailValid() &&
                !user.password.isNullOrBlank()
        ) { "Email or password is invalid." }
        return user
    }

    /**
     * Update accepts a subset of fields, so absent ones are fine; a field that is *present*
     * must carry a real value. The previous version required every field to be blank, which
     * rejected every well-formed update.
     */
    fun validToUpdate(): User {
        require(user != null && user.email.isEmailValid()) { "User is invalid." }
        require(user.password?.isNotBlank() ?: true) { "Password can't be blank." }
        require(user.username?.isNotBlank() ?: true) { "Username can't be blank." }
        return user
    }
}

data class User(
    val id: Long? = null,
    val email: String,
    val token: String? = null,
    val username: String? = null,
    val password: String? = null,
    val bio: String? = null,
    val image: String? = null
) : Principal

/**
 * Response shape for a user, per the RealWorld spec: {email, token, username, bio, image}.
 *
 * This exists so that password material cannot reach a client by accident. Registration used
 * to echo the submitted plaintext back, and login and `GET /user` returned the stored hash.
 *
 * A separate type rather than a Jackson annotation on [User]: `@JsonProperty(WRITE_ONLY)` also
 * suppresses the field when a *client* serializes [User] to build a request body, which
 * silently breaks the API's own test client. Splitting request and response types keeps each
 * direction honest and cannot be defeated by a new endpoint forgetting an annotation.
 */
data class UserResponseDTO(val user: UserResponse?)

data class UserResponse(
    val email: String,
    val token: String?,
    val username: String?,
    val bio: String?,
    val image: String?
)

fun User.toResponse(): UserResponse = UserResponse(
    email = email,
    token = token,
    username = username,
    bio = bio,
    image = image
)
