package no.nav.syfo.virksomhet.api

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.syfo.plugins.BrukerPrincipal
import no.nav.syfo.util.getBrukerPrincipal

fun Route.registerVirksomhetApi(virksomhetService: VirksomhetService) {
    get("api/virksomheter") {
        val principal: BrukerPrincipal = call.getBrukerPrincipal()
        val lederFnr = principal.fnr

        call.respond(virksomhetService.getVirksomheter(lederFnr))
    }
}
