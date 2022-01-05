package no.nav.syfo.minesykmeldte.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.put
import no.nav.syfo.application.BrukerPrincipal
import no.nav.syfo.minesykmeldte.MineSykmeldteService
import no.nav.syfo.minesykmeldte.model.HttpErrorMessage
import no.nav.syfo.minesykmeldte.model.HttpMessage
import no.nav.syfo.util.getBrukerPrincipal
import no.nav.syfo.util.getParam

fun Route.registerMineSykmeldteApi(mineSykmeldteService: MineSykmeldteService) {
    get("api/minesykmeldte") {
        val principal: BrukerPrincipal = call.getBrukerPrincipal()
        val lederFnr = principal.fnr
        call.respond(mineSykmeldteService.getMineSykmeldte(lederFnr))
    }

    get("api/sykmelding/{sykmeldingId}") {
        val principal: BrukerPrincipal = call.getBrukerPrincipal()
        val lederFnr = principal.fnr
        val sykmeldingId = call.getParam("sykmeldingId")

        val sykmelding = mineSykmeldteService.getSykmelding(sykmeldingId, lederFnr)
        if (sykmelding != null) call.respond(sykmelding) else call.respond(
            HttpStatusCode.NotFound,
            HttpErrorMessage("Sykmeldingen finnes ikke")
        )
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
        if (soknad != null) call.respond(soknad) else call.respond(
            HttpStatusCode.NotFound,
            HttpErrorMessage("Søknaden finnes ikke")
        )
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
}
