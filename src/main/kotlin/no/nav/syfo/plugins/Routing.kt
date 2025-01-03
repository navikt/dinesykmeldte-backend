package no.nav.syfo.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.auth.authenticate
import io.ktor.server.http.content.staticResources
import io.ktor.server.routing.routing
import no.nav.syfo.Environment
import no.nav.syfo.application.metrics.monitorHttpRequests
import no.nav.syfo.minesykmeldte.MineSykmeldteService
import no.nav.syfo.minesykmeldte.api.registerMineSykmeldteApi
import no.nav.syfo.narmesteleder.NarmestelederService
import no.nav.syfo.narmesteleder.api.registerNarmestelederApi
import no.nav.syfo.virksomhet.api.VirksomhetService
import no.nav.syfo.virksomhet.api.registerVirksomhetApi
import org.koin.ktor.ext.inject

fun Application.configureRouting() {
    val env by inject<Environment>()
    val virksomhetService by inject<VirksomhetService>()
    val mineSykmeldteService by inject<MineSykmeldteService>()
    val narmestelederService by inject<NarmestelederService>()
    routing {
        if (env.cluster == "dev-gcp") {
            staticResources("/api/v1/docs/", "api") { default("api/index.html") }
        }
        authenticate("tokenx") {
            registerMineSykmeldteApi(mineSykmeldteService)
            registerVirksomhetApi(virksomhetService)
            registerNarmestelederApi(narmestelederService)
        }
    }
    intercept(ApplicationCallPipeline.Monitoring, monitorHttpRequests())
}
