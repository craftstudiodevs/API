package dev.craftstudio.data

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(
    val success: Boolean = false,
    val error: String
)

suspend inline fun ApplicationCall.respondError(code: HttpStatusCode = HttpStatusCode.Forbidden, reason: String = "forbidden") =
    respond(code, ErrorResponse(error = reason))
