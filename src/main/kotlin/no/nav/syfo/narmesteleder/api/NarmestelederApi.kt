package no.nav.syfo.narmesteleder.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.syfo.application.BrukerPrincipal
import no.nav.syfo.application.metrics.DEAKTIVERT_LEDER_COUNTER
import no.nav.syfo.log
import no.nav.syfo.narmesteleder.NarmestelederService
import no.nav.syfo.util.getBrukerPrincipal
import java.util.UUID

fun Route.registerNarmestelederApi(narmestelederService: NarmestelederService) {
    post("api/narmesteleder/{orgnummer}/avkreft") {
        val principal: BrukerPrincipal = call.getBrukerPrincipal()
        val lederFnr = principal.fnr
        val orgnummer = call.parameters["orgnummer"]?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("orgnummer mangler")
        val fnrSykmeldt: String = call.request.headers["Sykmeldt-Fnr"]?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("Sykmeldt-Fnr mangler")

        val callId = UUID.randomUUID()

        narmestelederService.deaktiverNarmesteLeder(
            fnrLeder = lederFnr,
            orgnummer = orgnummer,
            fnrSykmeldt = fnrSykmeldt,
            callId = callId
        )
        log.info("Nærmeste leder har deaktivert NL-kobling for orgnummer $orgnummer, $callId")
        DEAKTIVERT_LEDER_COUNTER.inc()
        call.respond(HttpStatusCode.OK)
    }
}
