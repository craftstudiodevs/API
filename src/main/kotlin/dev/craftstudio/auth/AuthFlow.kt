package dev.craftstudio.auth

import dev.craftstudio.auth.data.LoginSuccessResponse
import dev.craftstudio.auth.oauth.DiscordUser
import dev.craftstudio.db.AccountType
import dev.craftstudio.db.accountsDAO
import dev.craftstudio.utils.Environment
import dev.craftstudio.utils.httpClient
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*

fun Application.configureAuth() {
    install(Sessions) {
        cookie<UserSession>("user_session")
    }

    authentication {
        bearer("session-token") {
            authenticate { credential ->
                accountsDAO.readByAccessToken(credential.token)
                    ?.let { AccountPrinciple(it) }
            }
        }

        oauth("auth-oauth-discord") {
            urlProvider = { "http://localhost:8080/auth/callback" }
            providerLookup = {
                OAuthServerSettings.OAuth2ServerSettings(
                    name = "discord",
                    authorizeUrl = "https://discord.com/api/oauth2/authorize",
                    accessTokenUrl = "https://discord.com/api/oauth2/token",
                    requestMethod = HttpMethod.Post,
                    clientId = Environment.DISCORD_CLIENT_ID,
                    clientSecret = Environment.DISCORD_CLIENT_SECRET,
                    defaultScopes = listOf("identify", "email")
                )
            }
            client = httpClient
        }
    }

    routing {
        authenticate("session-token") {
            get("/protected/test") {
                call.respondText("this is some very secure data")
            }
        }

        authenticate("auth-oauth-discord") {
            get("/auth/login") {

            }

            get("/auth/callback") {
                val principal = call.authentication.principal<OAuthAccessTokenResponse.OAuth2>()
                    ?: return@get call.respond(HttpStatusCode.BadRequest)

                val discordUser = httpClient.get("https://discord.com/api/users/@me") {
                    bearerAuth(principal.accessToken)
                }.body<DiscordUser>()

                if (discordUser.email == null || !discordUser.verified || discordUser.bot)
                    return@get call.respond(HttpStatusCode.BadRequest)

                val account = accountsDAO.readByDiscordId(discordUser.id) ?: accountsDAO.create(
                    type = AccountType.Developer,
                    discordId = discordUser.id,
                    username = discordUser.username,
                    email = discordUser.email
                )
                println(account)

                call.sessions.set("user_session", UserSession(account!!.accountId))

                call.respond(LoginSuccessResponse(success = true, account.accessToken))
            }
        }
    }
}

fun Routing.requireToken(build: Route.() -> Unit): Route {
    return authenticate("session-token") {
        build()
    }
}

data class UserSession(val accountId: Int)