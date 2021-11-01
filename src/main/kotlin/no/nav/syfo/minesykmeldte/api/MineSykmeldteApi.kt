package no.nav.syfo.minesykmeldte.api

import io.ktor.application.call
import io.ktor.auth.authentication
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import no.nav.syfo.application.BrukerPrincipal
import no.nav.syfo.minesykmeldte.MineSykmeldteService
import java.util.UUID

fun Route.registerMineSykmeldteApi(mineSykmeldteService: MineSykmeldteService) {
    get("api/minesykmeldte") {
        val principal: BrukerPrincipal = call.authentication.principal()!!
        val lederFnr = principal.fnr
        val callId = UUID.randomUUID()
        call.respond(mineSykmeldteService.getMineSykmeldte(lederFnr, callId))
    }
}
