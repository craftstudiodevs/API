package dev.craftstudio.auth

import io.ktor.server.auth.*

data class UserSession(val accountId: Int, val apiToken: String) : Principal
