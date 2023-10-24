package dev.craftstudio.auth

import dev.craftstudio.auth.data.LoginSuccessResponse
import dev.craftstudio.auth.oauth.DiscordUser
import dev.craftstudio.db.account.Account
import dev.craftstudio.db.account.accountsDAO
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
import io.ktor.util.pipeline.*
import kotlin.time.Duration.Companion.days

fun Application.configureAuth() {
    install(Sessions) {
        /**
         * Serialized as a cookie on the client.
         * This should not be a DB thing but the bare minimum.
         */
        cookie<UserSession>("user_session") {
            cookie.maxAge = 7.days
            cookie.httpOnly = false // TODO: research if this is okay. this is so javascript can access the cookie
        }
    }

    authentication {
        /**
         * Allows for either a cookie session OR the api token to be used for authentication
         */
        sessionOrBearer<UserSession>("logged_in") {
            sessionValidate {
                val account = accountsDAO.read(it.accountId)
                if (account?.accessToken == it.apiToken) {
                    authentication.principal(AccountPrinciple(account))
                    it
                } else {
                    null
                }
            }

            sessionChallenge {
                call.respondRedirect("http://localhost:8080/auth/login")
            }

            bearerValidate {
                val account = accountsDAO.readByAccessToken(it.token)
                    ?: return@bearerValidate null
                authentication.principal(AccountPrinciple(account))
                UserSession(account.id, account.accessToken)
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
                    discordId = discordUser.id,
                    username = discordUser.username,
                    email = discordUser.email
                ) ?: return@get call.respond(HttpStatusCode.InternalServerError)

                call.sessions.set(UserSession(account.id, account.accessToken))
                call.respond(LoginSuccessResponse(success = true, account.accessToken))
            }
        }
    }
}

fun Route.authenticateUser(build: Route.(account: PipelineContext<*, ApplicationCall>.() -> Account) -> Unit): Route {
    return authenticate("logged_in") {
        build { call.principal<AccountPrinciple>()!!.account }
    }
}