package dev.craftstudio.data.requests

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(
    val error: String
)
