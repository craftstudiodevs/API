package dev.craftstudio.auth.data

import kotlinx.serialization.Serializable

@Serializable
data class LoginSuccessResponse(val success: Boolean, val token: String? = null)
