package no.nav.syfo.sykmelding.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.syfo.dinesykmeldte.api.tryReceive
import no.nav.syfo.sykmelding.SykmeldingService
import no.nav.syfo.util.securelog

fun Route.registerSykmeldingApi(sykmeldingService: SykmeldingService) {
    post("api/narmesteleder/isActiveSykmelding") {
        val request = call.tryReceive<IsActiveSykmeldingRequestDTO>()
        val sykmeldtFnr = request.sykmeldtFnr
        securelog.info("Mottak kall mot /api/dinesykmeldte for pasientFnr: $sykmeldtFnr")
        val result = sykmeldingService.canSykmeldtGetNarmesteLeder("", sykmeldtFnr)
        call.respond(HttpStatusCode.OK, result)
    }
}

data class IsActiveSykmeldingRequestDTO(val sykmeldtFnr: String, val orgnummer: String)
