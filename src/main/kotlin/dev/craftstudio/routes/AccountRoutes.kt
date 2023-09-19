package dev.craftstudio.routes

import dev.craftstudio.auth.AccountPrinciple
import dev.craftstudio.auth.requireToken
import dev.craftstudio.data.account.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureAccountRoutes() {
    routing {
        configureBuyerRoutes()
        configureDeveloperRoutes()

        requireToken {
            get("/account/me") {
                val account = call.principal<AccountPrinciple>()?.account
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)

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
        }
    }
}