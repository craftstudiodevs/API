package dev.craftstudio

import dev.craftstudio.auth.configureAuth
import dev.craftstudio.db.DatabaseFactory
import dev.craftstudio.plugins.*
import dev.craftstudio.utils.Environment
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

fun main() {
    embeddedServer(Netty, port = Environment.PORT, host = Environment.HOST, module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    DatabaseFactory.init()
    configureAuth()
    configureSerialization()
    configureMonitoring()
    configureRateLimiting()
    configureRouting()
}
