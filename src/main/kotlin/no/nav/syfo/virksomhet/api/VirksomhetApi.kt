package no.nav.syfo.virksomhet.api

import io.ktor.application.call
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import no.nav.syfo.application.BrukerPrincipal
import no.nav.syfo.util.getBrukerPrincipal

fun Route.registerVirksomhetApi(virksomhetService: VirksomhetService) {
    get("api/virksomheter") {
        val principal: BrukerPrincipal = call.getBrukerPrincipal()
        val lederFnr = principal.fnr

        call.respond(virksomhetService.getVirksomheter(lederFnr))
    }
}
