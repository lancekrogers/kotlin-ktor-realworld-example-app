package io.realworld.app.web

import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.features.StatusPages
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.util.pipeline.PipelineContext
import io.realworld.app.domain.exceptions.NotFoundException
import io.realworld.app.domain.exceptions.UnauthorizedException
import org.slf4j.LoggerFactory

internal data class ErrorResponse(val errors: Map<String, List<String?>>)

/**
 * Exception-to-status mapping.
 *
 * The previous handler caught [Exception] alone and answered every failure with a 500 whose
 * body contained `exception.toString()`. That did two bad things: it handed unauthenticated
 * callers internal detail (JDBC and Exposed failures carry SQL fragments, table names, and
 * constraint text), and it collapsed 401/404/422 into 500, which also fails the RealWorld
 * spec tests.
 *
 * Now each known failure maps to its documented status, and anything unrecognised is logged
 * server-side but answered with a fixed generic message.
 */
object ErrorExceptionMapping {
    private val log = LoggerFactory.getLogger(ErrorExceptionMapping::class.java)

    fun register(config: StatusPages.Configuration) = with(config) {
        exception<UnauthorizedException> { cause ->
            respondError(HttpStatusCode.Unauthorized, cause.message)
        }
        exception<NotFoundException> { cause ->
            respondError(HttpStatusCode.NotFound, cause.message)
        }
        // require(...) in the DTO validators throws this; the spec asks for 422 on validation.
        exception<IllegalArgumentException> { cause ->
            respondError(HttpStatusCode.UnprocessableEntity, cause.message)
        }
        exception<Exception> { cause ->
            log.error("Unhandled exception while serving request", cause)
            respondError(HttpStatusCode.InternalServerError, "Internal server error.")
        }
    }

    private suspend fun PipelineContext<Unit, ApplicationCall>.respondError(
        status: HttpStatusCode,
        message: String?
    ) = call.respond(status, ErrorResponse(mapOf("body" to listOf(message))))
}
