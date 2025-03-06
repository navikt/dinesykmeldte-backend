package no.nav.syfo.dinesykmeldte.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authentication
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.syfo.dinesykmeldte.service.DineSykmeldteService
import no.nav.syfo.plugins.BrukerPrincipal
import no.nav.syfo.util.logger
import no.nav.syfo.util.securelog

private val log = logger("no.nav.syfo.dinesykmeldte")

fun Route.registerDineSykmeldteApi(dineSykmeldteService: DineSykmeldteService) {
    get("api/v2/dinesykmeldte") {
        val principal: BrukerPrincipal = call.authentication.principal()!!
        val fnr = principal.fnr
        securelog.info("Mottak kall mot /api/dinesykmeldte for fnr: $fnr")
        val sykmeldte = dineSykmeldteService.getDineSykmeldte(fnr)
        log.info("Hentet ${sykmeldte.size} fra db")
        call.respond(sykmeldte)
    }

    get("api/v2/dinesykmeldte/{narmestelederId}") {
        val narmestelederId = call.parameters["narmestelederId"]!!
        val principal: BrukerPrincipal = call.authentication.principal()!!
        val fnr = principal.fnr
        securelog.info(
            "Mottak kall mot /api/dinesykmeldte/{narmestelederId} for fnr: $fnr " +
                "og narmestelederId: $narmestelederId"
        )
        when (val sykmeldt = dineSykmeldteService.getSykmeldt(narmestelederId, fnr)) {
            null -> call.respond(HttpStatusCode.NotFound)
            else -> {
                call.respond(sykmeldt)
            }
        }
    }
}
