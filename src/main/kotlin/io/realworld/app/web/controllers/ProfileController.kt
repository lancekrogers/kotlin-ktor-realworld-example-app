package io.realworld.app.web.controllers

import io.ktor.application.ApplicationCall
import io.ktor.auth.authentication
import io.ktor.response.respond
import io.realworld.app.domain.ProfileDTO
import io.realworld.app.domain.ProfileStatsDTO
import io.realworld.app.domain.User
import io.realworld.app.domain.service.ProfileStatsService
import io.realworld.app.domain.service.UserService

class ProfileController(private val userService: UserService, private val profileStatsService: ProfileStatsService) {
    suspend fun get(ctx: ApplicationCall) {
        val viewer = ctx.authentication.principal<User>()?.email
        ctx.respond(ProfileDTO(userService.getProfileByUsername(viewer, username(ctx))))
    }

    suspend fun follow(ctx: ApplicationCall) {
        val email = ctx.authentication.principal<User>()?.email
        require(!email.isNullOrBlank()) { "User not logged." }
        ctx.respond(ProfileDTO(userService.follow(email, username(ctx))))
    }

    suspend fun unfollow(ctx: ApplicationCall) {
        val email = ctx.authentication.principal<User>()?.email
        require(!email.isNullOrBlank()) { "User not logged." }
        ctx.respond(ProfileDTO(userService.unfollow(email, username(ctx))))
    }

    suspend fun stats(ctx: ApplicationCall) {
        ctx.respond(ProfileStatsDTO(profileStatsService.stats(username(ctx))))
    }

    private fun username(ctx: ApplicationCall): String =
        requireNotNull(ctx.parameters["username"]) { "username is required." }
}
