package dev.craftstudio.routes

import dev.craftstudio.auth.authenticateUser
import dev.craftstudio.db.account.buyerAccountDAO
import dev.craftstudio.db.account.developerAccountDAO
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.html.*

fun Application.configureSignupRoutes() {
    routing {
        authenticateUser { accountGetter ->
            get("/signup/choose-type") {
                call.respondHtml {
                    head {
                        title { +"CraftStudio" }
                    }

                    body {
                        h1 { +"Signup Wizard" }

                        ul {
                            li {
                                a(href = "http://localhost:8080/signup/buyer") { +"Buyer" }
                            }
                            li {
                                a(href = "http://localhost:8080/signup/developer") { +"Developer" }
                            }
                        }
                    }
                }
            }

            get("/signup/buyer") {
                val account = accountGetter()

                if (!account.isBuyer) {
                    buyerAccountDAO.create(account.id)
                }
                call.respondRedirect("http://localhost:8080/frontend/buyer")
            }

            get("/signup/developer") {
                val account = accountGetter()

                if (!account.isDeveloper) {
                    developerAccountDAO.create(account.id)
                }
                call.respondRedirect("http://localhost:8080/frontend/developer")
            }
        }
    }
}