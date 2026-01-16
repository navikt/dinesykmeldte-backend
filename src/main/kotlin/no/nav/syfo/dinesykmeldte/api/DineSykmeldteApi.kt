package no.nav.syfo.dinesykmeldte.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authentication
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.syfo.dinesykmeldte.service.DineSykmeldteService
import no.nav.syfo.plugins.BrukerPrincipal
import no.nav.syfo.util.logger
import no.nav.syfo.util.teamLogsLogger

private val log = logger("no.nav.syfo.dinesykmeldte")


fun Route.registerDineSykmeldteApi(dineSykmeldteService: DineSykmeldteService) {
    get("api/v2/dinesykmeldte") {
        val principal: BrukerPrincipal = call.authentication.principal()!!
        val narmesteLederfnr = principal.fnr
        teamLogsLogger.info("Mottak kall mot /api/dinesykmeldte for narmesteLederfnr: $narmesteLederfnr")
        val sykmeldte = dineSykmeldteService.getDineSykmeldte(narmesteLederfnr)
        log.info("Hentet ${sykmeldte.size} fra db")
        call.respond(sykmeldte)
    }

    get("api/v2/dinesykmeldte/{narmestelederId}") {
        val narmestelederId = call.parameters["narmestelederId"]!!
        val principal: BrukerPrincipal = call.authentication.principal()!!
        val narmesteLederfnr = principal.fnr
        teamLogsLogger.info(
            "Mottak kall mot /api/dinesykmeldte/{narmestelederId} for narmesteLederFnr: $narmesteLederfnr " +
                "og narmestelederId: $narmestelederId"
        )
        when (val sykmeldt = dineSykmeldteService.getSykmeldt(narmestelederId, narmesteLederfnr)) {
            null -> {
                log.info("could not find sykemeldt for narmestelederId: $narmestelederId")
                call.respond(HttpStatusCode.NotFound)
            }
            else -> {
                teamLogsLogger.info(
                    "found sykmeldt for narmestelederId: $narmestelederId, sykmeldt: $sykmeldt"
                )
                call.respond(sykmeldt)
            }
        }
    }
}
