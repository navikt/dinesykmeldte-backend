package no.nav.syfo.sykmelding.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.syfo.dinesykmeldte.api.tryReceive
import no.nav.syfo.sykmelding.SykmeldingService
import no.nav.syfo.texas.TexasAzureADAuthPlugin
import no.nav.syfo.texas.client.TexasHttpClient
import no.nav.syfo.util.teamLogsLogger

fun Route.registerSykmeldingApi(
    sykmeldingService: SykmeldingService,
    texasHttpClient: TexasHttpClient,
) {
    route("/api/sykmelding/isActiveSykmelding") {
        install(TexasAzureADAuthPlugin) {
            this.client = texasHttpClient
        }
        post {
            val request = call.tryReceive<IsActiveSykmeldingRequestDTO>()
            teamLogsLogger.info(
                "Mottak kall mot /api/dinesykmeldte for pasientFnr: ${request.sykmeldtFnr}",
            )
            val result =
                sykmeldingService.getActiveSendtSykmeldingsperioder(
                    request.sykmeldtFnr,
                    orgnummer = request.orgnummer,
                )
            call.respond(HttpStatusCode.OK, result)
        }
    }
}

data class IsActiveSykmeldingRequestDTO(
    val sykmeldtFnr: String,
    val orgnummer: String,
)
