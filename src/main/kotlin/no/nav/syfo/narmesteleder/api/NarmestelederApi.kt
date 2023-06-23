package no.nav.syfo.narmesteleder.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import java.util.UUID
import no.nav.syfo.application.BrukerPrincipal
import no.nav.syfo.application.metrics.DEAKTIVERT_LEDER_COUNTER
import no.nav.syfo.log
import no.nav.syfo.minesykmeldte.model.HttpMessage
import no.nav.syfo.narmesteleder.NarmestelederService
import no.nav.syfo.util.getBrukerPrincipal
import no.nav.syfo.util.getParam

fun Route.registerNarmestelederApi(narmestelederService: NarmestelederService) {
    post("api/narmesteleder/{narmesteLederId}/avkreft") {
        val principal: BrukerPrincipal = call.getBrukerPrincipal()
        val narmestelederId = call.getParam("narmestelederId")
        val lederFnr = principal.fnr
        val callId = UUID.randomUUID()

        narmestelederService.deaktiverNarmesteLeder(
            fnrLeder = lederFnr,
            narmestelederId = narmestelederId,
            callId = callId,
        )
        log.info(
            "Nærmeste leder har deaktivert NL-kobling for narmestelederId $narmestelederId, $callId"
        )
        DEAKTIVERT_LEDER_COUNTER.inc()
        call.respond(HttpStatusCode.OK, HttpMessage("Kobling deaktivert"))
    }
}
