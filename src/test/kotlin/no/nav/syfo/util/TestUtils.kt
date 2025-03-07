package no.nav.syfo.util

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.request.*
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.Route
import io.ktor.server.routing.routing
import io.ktor.server.testing.*
import java.nio.file.Paths
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.plugins.setupAuth
import no.nav.syfo.testutils.generateJWTLoginservice
import org.amshove.kluent.shouldBeInstanceOf

@ExperimentalContracts
inline fun <reified T> Any?.shouldBeInstance() {
    contract { returns() implies (this@shouldBeInstance is T) }

    this.shouldBeInstanceOf(T::class)
}

fun withKtor(build: Route.() -> Unit, block: ApplicationTestBuilder.() -> Unit) {
    testApplication {
        application {
            setupAuth(
                AuthConfiguration(
                    jwkProviderTokenX =
                        JwkProviderBuilder(
                                Paths.get("src/test/resources/jwkset.json").toUri().toURL()
                            )
                            .build(),
                    tokenXIssuer = "https://sts.issuer.net/myid",
                    clientIdTokenX = "dummy-client-id"
                )
            )
            val applicationState = ApplicationState()
            applicationState.ready = true
            applicationState.alive = true
            install(ContentNegotiation) {
                jackson {
                    registerKotlinModule()
                    registerModule(JavaTimeModule())
                    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                }
            }
            routing { authenticate("tokenx", build = build) }
        }
        block(this)
    }
}

fun HttpRequestBuilder.addAuthorizationHeader(
    audience: String = "dummy-client-id",
    subject: String = "08086912345",
    issuer: String = "https://sts.issuer.net/myid",
    level: String = "Level4",
) {
    header(
        "Authorization",
        "Bearer ${
            generateJWTLoginservice(
                audience = audience,
                subject = subject,
                issuer = issuer,
                level = level,
            )
        }",
    )
}

fun String.minifyApiResponse(): String =
    objectMapper.writeValueAsString(
        objectMapper.readValue(this),
    )
