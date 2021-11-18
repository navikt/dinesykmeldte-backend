package no.nav.syfo.minesykmeldte.api

import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.auth.authentication
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import no.nav.syfo.application.BrukerPrincipal
import no.nav.syfo.minesykmeldte.MineSykmeldteService
import no.nav.syfo.minesykmeldte.model.HttpErrorMessage
import java.util.UUID

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

        when {
            sykmeldingId.isInvalidUuid() -> call.respond(
                HttpStatusCode.BadRequest,
                HttpErrorMessage("Sykmelding ID is not a valid UUID")
            )
            else -> {
                val sykmelding = mineSykmeldteService.getSykmelding(UUID.fromString(sykmeldingId), lederFnr)
                if (sykmelding != null) call.respond(sykmelding) else call.respond(
                    HttpStatusCode.NotFound,
                    HttpErrorMessage("Sykmeldingen finnes ikke")
                )
            }
        }
    }

    get("api/soknad/{soknadId}") {
        val principal: BrukerPrincipal = call.getBrukerPrincipal()
        val lederFnr = principal.fnr
        val soknadId = call.getParam("soknadId")

        when {
            soknadId.isInvalidUuid() -> call.respond(
                HttpStatusCode.BadRequest,
                HttpErrorMessage("Soknad ID is not a valid UUID")
            )
            else -> {
                val soknad = mineSykmeldteService.getSoknad(UUID.fromString(soknadId), lederFnr)
                if (soknad != null) call.respond(soknad) else call.respond(
                    HttpStatusCode.NotFound,
                    HttpErrorMessage("Søknaden finnes ikke")
                )
            }
        }
    }
}

private fun ApplicationCall.getBrukerPrincipal(): BrukerPrincipal {
    val brukerPrincipal: BrukerPrincipal? = this.authentication.principal()

    requireNotNull(brukerPrincipal) {
        "Mottok HTTP kall uten principal. Er serveren konfigurert riktig?"
    }

    return brukerPrincipal
}

private fun ApplicationCall.getParam(paramName: String): String {
    val param = this.parameters[paramName]

    requireNotNull(param) {
        "Tried to get param $paramName. You need to match the param name with the name defined in the route."
    }

    return param
}

private fun String.isInvalidUuid(): Boolean = try {
    UUID.fromString(this)
    false
} catch (e: Throwable) {
    true
}
