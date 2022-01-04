package no.nav.syfo.util

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.install
import io.ktor.auth.authenticate
import io.ktor.features.ContentNegotiation
import io.ktor.jackson.jackson
import io.ktor.routing.Route
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.TestApplicationRequest
import no.nav.syfo.Environment
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.setupAuth
import no.nav.syfo.objectMapper
import no.nav.syfo.testutils.generateJWTLoginservice
import java.nio.file.Paths
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@ExperimentalContracts
inline fun <reified T> Any?.shouldBeInstance() {
    contract {
        returns() implies (this@shouldBeInstance is T)
    }

    this is T
}

fun withKtor(env: Environment, build: Route.() -> Unit, block: TestApplicationEngine.() -> Unit) {
    with(TestApplicationEngine()) {
        start()
        val applicationState = ApplicationState()
        applicationState.ready = true
        applicationState.alive = true
        application.install(ContentNegotiation) {
            jackson {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        application.setupAuth(
            jwkProviderTokenX = JwkProviderBuilder(Paths.get("src/test/resources/jwkset.json").toUri().toURL()).build(),
            tokenXIssuer = "https://sts.issuer.net/myid",
            env = env
        )
        application.routing {
            authenticate("tokenx", build = build)
        }

        block(this)
    }
}

fun TestApplicationRequest.addAuthorizationHeader(
    audience: String = "dummy-client-id",
    subject: String = "08086912345",
    issuer: String = "https://sts.issuer.net/myid",
    level: String = "Level4",
) {
    addHeader(
        "Authorization",
        "Bearer ${
        generateJWTLoginservice(
            audience = audience,
            subject = subject,
            issuer = issuer,
            level = level
        )
        }"
    )
}

fun String.minifyApiResponse(): String =
    objectMapper.writeValueAsString(
        objectMapper.readValue(this)
    )
