package dev.craftstudio.routes

import dev.craftstudio.auth.AccountPrinciple
import dev.craftstudio.auth.authenticateUser
import dev.craftstudio.data.account.*
import dev.craftstudio.data.respondError
import dev.craftstudio.db.account.buyerAccountDAO
import dev.craftstudio.db.account.developerAccountDAO
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureAccountRoutes() {
    routing {
        configureBuyerRoutes()
        configureDeveloperRoutes()

        authenticateUser { accountGetter ->
            get("/account/me") {
                val account = accountGetter()

                val response = PrivateAccountDetailsResponse(
                    id = account.id,
                    discordId = account.discordId,
                    username = account.username,
                    email = account.email,
                    buyerAccount = account.buyerAccount?.resolve()?.let {
                        BuyerAccountDetails(
                            subscriptionType = it.subscriptionType.name,
                            remainingCommissions = it.remainingCommissions,
                            totalCommissions = it.totalCommissions,
                        )
                    },
                    developerAccount = account.developerAccount?.resolve()?.let {
                        DeveloperAccountDetails(
                            subscriptionType = it.subscriptionType.name,
                            remainingBids = it.remainingBids,
                            totalBids = it.totalBids,
                        )
                    },
                )

                call.respond(response)
            }

            get("/account/select-type") {
                val account = accountGetter()

                if (account.isBuyer || account.isDeveloper) {
                    return@get call.respondError(HttpStatusCode.BadRequest, "You already have an account type")
                }

                val type = call.parameters["type"]
                    ?: return@get call.respondError(HttpStatusCode.BadRequest, "Missing type")
                when (type.lowercase()) {
                    "buyer" -> {
                        buyerAccountDAO.create(account.id)
                        call.respond(HttpStatusCode.OK)
                    }
                    "developer" -> {
                        developerAccountDAO.create(account.id)
                        call.respond(HttpStatusCode.OK)
                    }
                    else -> call.respondError(HttpStatusCode.BadRequest, "Invalid type")
                }
            }
        }
    }
}