package no.nav.syfo.pdl.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.util.UUID
import no.nav.syfo.dinesykmeldte.service.DineSykmeldteService
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.plugins.BrukerPrincipal
import no.nav.syfo.util.getBrukerPrincipal
import no.nav.syfo.util.getParam
import no.nav.syfo.util.logger
import no.nav.syfo.util.securelog

private val log = logger("no.nav.syfo.pdl.api")

fun Route.registerPdlPersonApi(
    pdlPersonService: PdlPersonService,
    dineSykmeldteService: DineSykmeldteService,
) {
    get("api/isPilotUser/{narmestelederId}") {
        val principal: BrukerPrincipal = call.getBrukerPrincipal()
        val lederFnr = principal.fnr
        val narmestelederId = call.getParam("narmestelederId")
        val callId = call.request.headers["X-Request-Id"] ?: UUID.randomUUID().toString()

        securelog.info(
            "Henter sykmeldt for lederFnr: $lederFnr og narmestelederId: $narmestelederId, callId: $callId",
        )

        val sykmeldt = dineSykmeldteService.getSykmeldt(narmestelederId, lederFnr)
        if (sykmeldt == null) {
            log.info("Fant ikke sykmeldt for narmestelederId: $narmestelederId, $callId")
            call.respond(HttpStatusCode.NotFound, "Fant ikke sykmeldt")
            return@get
        }

        val sykmeldtFnr = sykmeldt.fnr
        securelog.info(
            "Henter person fra PDL for sykmeldtFnr: $sykmeldtFnr, narmestelederId: $narmestelederId, callId: $callId",
        )
        try {
            val person = pdlPersonService.getPerson(fnr = sykmeldtFnr, callId = callId)
            val pilotBydelerListe = listOf<String>("300102", "300103", "300104", "300105", "300106")
            val pilotKommuneListe = listOf<String>("3001", "3002", "3331", "3332", "3333")
            if (pilotBydelerListe.contains(person.gtBydel) || pilotKommuneListe.contains(person.gtKommune)) {
                call.respond(true)
            } else {
                log.info("Person fra PDL er ikke i pilotbydel, $callId")
                call.respond(false)
            }
        } catch (e: Exception) {
            log.error("Feil ved henting av person fra PDL, $callId", e)
            call.respond(HttpStatusCode.InternalServerError, "Feil ved henting av person")
        }
    }
}
