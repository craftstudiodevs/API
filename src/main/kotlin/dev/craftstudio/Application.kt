package dev.craftstudio

import dev.craftstudio.auth.configureAuth
import dev.craftstudio.db.DatabaseFactory
import dev.craftstudio.payment.configurePaymentRoutes
import dev.craftstudio.plugins.*
import dev.craftstudio.routes.*
import dev.craftstudio.testfrontend.configureTestFrontend
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
    configureStatusPages()
    configureAccountRoutes()
    configurePaymentRoutes()
    configureTestFrontend()
    configureSignupRoutes()
}
