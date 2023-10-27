package dev.craftstudio.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*

fun Application.configureCORS() {
    install(CORS) {
        HttpMethod.DefaultMethods.forEach(::allowMethod)
        listOf(
            HttpHeaders.AccessControlAllowOrigin,
            HttpHeaders.AccessControlAllowHeaders,
            HttpHeaders.AccessControlAllowMethods,
            HttpHeaders.AccessControlAllowCredentials,
            HttpHeaders.AccessControlMaxAge,
            HttpHeaders.AccessControlRequestHeaders,
            HttpHeaders.AccessControlRequestMethod,
            HttpHeaders.AccessControlExposeHeaders,
            HttpHeaders.Authorization,
        ).forEach {
            exposeHeader(it)
            allowHeader(it)
        }
        allowHost("localhost:3000")
        anyHost()
        allowCredentials = true
        allowSameOrigin = true
        allowOrigins { true }
    }
}