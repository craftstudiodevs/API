package dev.craftstudio.auth.oauth

import kotlinx.serialization.Serializable

@Serializable
data class DiscordUser(
    val id: String,
    val username: String,
    val verified: Boolean = false,
    val email: String? = null,
    val bot: Boolean = false,
)
