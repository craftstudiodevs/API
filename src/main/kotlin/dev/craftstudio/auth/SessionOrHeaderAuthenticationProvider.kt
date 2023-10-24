package dev.craftstudio.auth

import io.ktor.http.auth.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*
import io.ktor.util.logging.*
import kotlin.reflect.KClass

private val LOGGER = KtorSimpleLogger("SessionOrBearerAuthenticationProvider")

class SessionOrBearerAuthenticationProvider<T : Any> @PublishedApi internal constructor(config: Config<T>) : AuthenticationProvider(config) {
    private val type: KClass<T> = config.type

    private val sessionValidator: AuthenticationFunction<T> = config.sessionValidator
    private val sessionChallengeFunction: SessionAuthChallengeFunction<T> = config.sessionChallengeFunction

    private val bearerAuthenticator: AuthenticationFunction<BearerTokenCredential> = config.bearerValidator

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        context.call.sessions.get(type)
            ?.let { authenticateSession(context, it) }
            ?: authenticateHeader(context)
    }

    private suspend fun authenticateSession(context: AuthenticationContext, session: T) {
        LOGGER.trace("Authenticating session")

        val call = context.call
        val principle = sessionValidator(call, session)

        if (principle != null) {
            context.principal(name, principle)
        } else {
            val cause = AuthenticationFailedCause.InvalidCredentials

            @Suppress("NAME_SHADOWING")
            context.challenge(SessionOrHeaderAuthChallengeKey, cause) { challenge, call ->
                sessionChallengeFunction(SessionChallengeContext(call), principle)
                if (!challenge.completed && call.response.status() != null) {
                    challenge.complete()
                }
            }
        }
    }

    private suspend fun authenticateHeader(context: AuthenticationContext) {
        LOGGER.trace("Authenticating header")

        val call = context.call

        val authHeader = call.request.parseAuthorizationHeader() ?: let {
            context.challenge(SessionOrHeaderAuthChallengeKey, AuthenticationFailedCause.NoCredentials) { challenge, call ->
                call.respond(UnauthorizedResponse(HttpAuthHeader.bearerAuthChallenge(AuthScheme.Bearer,null)))
                challenge.complete()
            }
            return
        }

        val principal = (authHeader as? HttpAuthHeader.Single)
            ?.takeIf { it.authScheme == AuthScheme.Bearer }
            ?.let { bearerAuthenticator(call, BearerTokenCredential(it.blob)) }
            ?: let {
                context.challenge(SessionOrHeaderAuthChallengeKey, AuthenticationFailedCause.InvalidCredentials) { challenge, call ->
                    call.respond(UnauthorizedResponse(HttpAuthHeader.bearerAuthChallenge(AuthScheme.Bearer,null)))
                    challenge.complete()
                }
                return
            }

        context.principal(principal)
    }

    class Config<T : Any> @PublishedApi internal constructor(
        name: String?,
        internal val type: KClass<T>
    ) : AuthenticationProvider.Config(name) {
        internal var sessionValidator: AuthenticationFunction<T> = UninitializedValidator
        internal var sessionChallengeFunction: SessionAuthChallengeFunction<T> = { call.respond(UnauthorizedResponse()) }

        internal var bearerValidator: AuthenticationFunction<BearerTokenCredential> = UninitializedValidator

        fun sessionChallenge(block: SessionAuthChallengeFunction<T>) {
            sessionChallengeFunction = block
        }

        fun sessionChallenge(redirectUrl: String) {
            sessionChallenge {
                call.respondRedirect(redirectUrl)
            }
        }

        fun sessionValidate(block: AuthenticationFunction<T>) {
            check(sessionValidator === UninitializedValidator)
            sessionValidator = block
        }

        fun bearerValidate(block: AuthenticationFunction<BearerTokenCredential>) {
            check(bearerValidator === UninitializedValidator)
            bearerValidator = block
        }

        private fun verifyConfiguration() {
            check(sessionValidator !== UninitializedValidator) { "Session validator should be specified" }
            check(bearerValidator !== UninitializedValidator) { "Bearer validator should be specified" }
        }

        fun buildProvider(): SessionOrBearerAuthenticationProvider<T> {
            verifyConfiguration()
            return SessionOrBearerAuthenticationProvider(this)
        }
    }

    companion object {
        private val UninitializedValidator: suspend ApplicationCall.(Any) -> Principal? = {
            error("It should be a validator supplied to a session auth provider")
        }
    }
}

inline fun <reified T : Any> AuthenticationConfig.sessionOrBearer(
    name: String? = null,
    configure: SessionOrBearerAuthenticationProvider.Config<T>.() -> Unit
) {
    val provider = SessionOrBearerAuthenticationProvider.Config(name, T::class).apply(configure).buildProvider()
    register(provider)
}

const val SessionOrHeaderAuthChallengeKey: String = "SessionOrBearerAuth"