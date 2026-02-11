package no.nav.syfo.plugins

import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTCredential
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.request.header
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.util.AuthConfiguration
import no.nav.syfo.util.logger

private val log = logger("Authentication")

fun Application.setupAuth(config: AuthConfiguration) {
    install(Authentication) {
        jwt(name = "tokenx") {
            authHeader {
                when (val token: String? = it.getToken()) {
                    null -> return@authHeader null
                    else -> return@authHeader HttpAuthHeader.Single("Bearer", token)
                }
            }
            verifier(config.jwkProviderTokenX, config.tokenXIssuer)
            validate { credentials ->
                when {
                    harDineSykmeldteBackendAudience(
                        credentials,
                        config.clientIdTokenX,
                    ) &&
                        erNiva4(credentials) -> {
                        val principal = JWTPrincipal(credentials.payload)
                        BrukerPrincipal(
                            fnr = finnFnrFraToken(principal),
                            principal = principal,
                        )
                    }
                    else -> unauthorized(credentials)
                }
            }
        }
    }
}

fun ApplicationCall.getToken(): String? = request.header("Authorization")?.removePrefix("Bearer ")

fun finnFnrFraToken(principal: JWTPrincipal): String =
    if (
        principal.payload.getClaim("pid") != null &&
        !principal.payload
            .getClaim("pid")
            .asString()
            .isNullOrEmpty()
    ) {
        log.debug("Bruker fnr fra pid-claim")
        principal.payload.getClaim("pid").asString()
    } else {
        log.debug("Bruker fnr fra subject")
        principal.payload.subject
    }

fun unauthorized(credentials: JWTCredential): Unit? {
    log.warn(
        "Auth: Unexpected audience for jwt {}, {}",
        StructuredArguments.keyValue("issuer", credentials.payload.issuer),
        StructuredArguments.keyValue("audience", credentials.payload.audience),
    )
    return null
}

fun harDineSykmeldteBackendAudience(
    credentials: JWTCredential,
    clientId: String,
): Boolean = credentials.payload.audience.contains(clientId)

fun erNiva4(credentials: JWTCredential): Boolean =
    "Level4" == credentials.payload.getClaim("acr").asString()

data class BrukerPrincipal(
    val fnr: String,
    val principal: JWTPrincipal,
)
