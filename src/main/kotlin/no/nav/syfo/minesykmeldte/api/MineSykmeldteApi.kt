package no.nav.syfo.minesykmeldte.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import no.nav.syfo.minesykmeldte.MineSykmeldteService
import no.nav.syfo.minesykmeldte.model.HttpErrorMessage
import no.nav.syfo.minesykmeldte.model.HttpMessage
import no.nav.syfo.plugins.BrukerPrincipal
import no.nav.syfo.util.getBrukerPrincipal
import no.nav.syfo.util.getParam
import no.nav.syfo.util.logger
import no.nav.syfo.util.teamLogsLogger
import java.util.UUID
import kotlin.time.measureTimedValue

private val log = logger("no.nav.syfo.minesykmeldte.api")

fun Route.registerMineSykmeldteApi(mineSykmeldteService: MineSykmeldteService) {
    get("api/minesykmeldte") {
        val principal: BrukerPrincipal = call.getBrukerPrincipal()
        val xRequestId = call.request.headers["X-Request-Id"]
        val lederFnr = principal.fnr
        val timedValue = measureTimedValue { mineSykmeldteService.getMineSykmeldte(lederFnr) }
        log.info(
            "Calling api path: api/minesykmeldt getting ${timedValue.value.size} sykmeldte, duration: ${timedValue.duration.inWholeMilliseconds} ms",
        )
        teamLogsLogger.info(
            "Getting sykmeldte lederFnr: $lederFnr, requestId: $xRequestId, sykmeldte: ${timedValue.value.map {
                it.narmestelederId
            }}",
        )
        call.respond(timedValue.value)
    }

    get("api/sykmelding/{sykmeldingId}") {
        val principal: BrukerPrincipal = call.getBrukerPrincipal()
        val lederFnr = principal.fnr
        val sykmeldingId = call.getParam("sykmeldingId")
        teamLogsLogger.info("Calling api path: api/sykmelding/$sykmeldingId for lederFnr $lederFnr")
        log.info("Calling api path: api/sykmelding/$sykmeldingId")

        val sykmelding = mineSykmeldteService.getSykmelding(sykmeldingId, lederFnr)
        if (sykmelding != null) {
            call.respond(sykmelding)
        } else {
            call.respond(
                HttpStatusCode.NotFound,
                HttpErrorMessage("Sykmeldingen finnes ikke"),
            )
        }
    }

    put("api/sykmelding/{sykmeldingId}/lest") {
        val principal: BrukerPrincipal = call.getBrukerPrincipal()
        val lederFnr = principal.fnr
        val sykmeldingId = call.getParam("sykmeldingId")

        when (mineSykmeldteService.markSykmeldingRead(sykmeldingId, lederFnr)) {
            true -> call.respond(HttpStatusCode.OK, HttpMessage("Markert som lest"))
            false -> call.respond(HttpStatusCode.NotFound, HttpMessage("Not found"))
        }
    }

    get("api/soknad/{soknadId}") {
        val principal: BrukerPrincipal = call.getBrukerPrincipal()
        val lederFnr = principal.fnr
        val soknadId = call.getParam("soknadId")

        val soknad = mineSykmeldteService.getSoknad(soknadId, lederFnr)
        if (soknad != null) {
            call.respond(soknad)
        } else {
            call.respond(
                HttpStatusCode.NotFound,
                HttpErrorMessage("Søknaden finnes ikke"),
            )
        }
    }

    put("api/soknad/{soknadId}/lest") {
        val principal: BrukerPrincipal = call.getBrukerPrincipal()
        val lederFnr = principal.fnr
        val soknadId = call.getParam("soknadId")

        when (mineSykmeldteService.markSoknadRead(soknadId, lederFnr)) {
            true -> call.respond(HttpStatusCode.OK, HttpMessage("Markert som lest"))
            false -> call.respond(HttpStatusCode.NotFound, HttpMessage("Not found"))
        }
    }

    put("api/hendelse/{hendelseId}/lest") {
        val principal: BrukerPrincipal = call.getBrukerPrincipal()
        val lederFnr = principal.fnr
        val hendelseId = UUID.fromString(call.getParam("hendelseId"))

        teamLogsLogger.info("Calling api path: api/hendelse/$hendelseId/les for lederFnr $lederFnr")

        when (mineSykmeldteService.markHendelseRead(hendelseId, lederFnr)) {
            true -> call.respond(HttpStatusCode.OK, HttpMessage("Markert som lest"))
            false -> call.respond(HttpStatusCode.NotFound, HttpMessage("Not found"))
        }
    }

    put("api/hendelser/read") {
        val fnr = call.getBrukerPrincipal().fnr
        mineSykmeldteService.markAllSykmeldingerAndSoknaderRead(fnr)
        call.respond(HttpStatusCode.OK, HttpMessage("Markert som lest"))
    }
}
