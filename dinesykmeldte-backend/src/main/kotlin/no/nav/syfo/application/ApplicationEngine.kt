package no.nav.syfo.application

import com.auth0.jwk.JwkProvider
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import no.nav.syfo.Environment
import no.nav.syfo.application.api.registerNaisApi
import no.nav.syfo.application.api.setupSwaggerDocApi
import no.nav.syfo.application.metrics.monitorHttpRequests
import no.nav.syfo.log
import no.nav.syfo.minesykmeldte.MineSykmeldteService
import no.nav.syfo.minesykmeldte.api.registerMineSykmeldteApi
import no.nav.syfo.narmesteleder.NarmestelederService
import no.nav.syfo.narmesteleder.api.registerNarmestelederApi
import no.nav.syfo.virksomhet.api.VirksomhetService
import no.nav.syfo.virksomhet.api.registerVirksomhetApi
import java.util.UUID
import kotlin.time.ExperimentalTime

@ExperimentalTime
fun createApplicationEngine(
    env: Environment,
    jwkProviderTokenX: JwkProvider,
    tokenXIssuer: String,
    applicationState: ApplicationState,
    mineSykmeldteService: MineSykmeldteService,
    virksomhetService: VirksomhetService,
    narmestelederService: NarmestelederService
): ApplicationEngine =
    embeddedServer(Netty, env.applicationPort) {
        install(ContentNegotiation) {
            jackson {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        setupAuth(
            jwkProviderTokenX = jwkProviderTokenX,
            tokenXIssuer = tokenXIssuer,
            env = env
        )
        install(CallId) {
            generate { UUID.randomUUID().toString() }
            verify { callId: String -> callId.isNotEmpty() }
            header(HttpHeaders.XCorrelationId)
        }
        install(StatusPages) {
            exception<Throwable> { call, cause ->
                log.error("Caught exception", cause)
                call.respond(HttpStatusCode.InternalServerError, cause.message ?: "Unknown error")
            }
        }
        install(CORS) {
            allowMethod(HttpMethod.Get)
            env.allowedOrigin.forEach {
                hosts.add("https://$it")
            }
            allowHeader("nav_csrf_protection")
            allowHeader("Sykmeldt-Fnr")
            allowCredentials = true
            allowNonSimpleContentTypes = true
        }

        routing {
            registerNaisApi(applicationState)
            if (env.cluster == "dev-gcp") {
                setupSwaggerDocApi()
            }
            authenticate("tokenx") {
                registerMineSykmeldteApi(mineSykmeldteService)
                registerVirksomhetApi(virksomhetService)
                registerNarmestelederApi(narmestelederService)
            }
        }
        intercept(ApplicationCallPipeline.Monitoring, monitorHttpRequests())
    }
