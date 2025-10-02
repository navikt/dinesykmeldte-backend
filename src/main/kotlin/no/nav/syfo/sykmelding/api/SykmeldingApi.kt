package no.nav.syfo.sykmelding.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.syfo.sykmelding.SykmeldingService
import no.nav.syfo.util.securelog

fun Route.registerSykmeldingApi(sykmeldingService: SykmeldingService) {
    post ("api/narmesteleder/isSykefravarstilefelle/") {
        val request = call.receive<IsActiveSykmeldingRequestDTO>()
        val sykmeldtFnr = request.sykmeldtFnr
        securelog.info("Mottak kall mot /api/dinesykmeldte for pasientFnr: $sykmeldtFnr")
        val result = sykmeldingService.canSykmeldtGetNarmesteLeder("",sykmeldtFnr)
        if(result == false) {
            securelog.info("could not find sykemeldt for fnr: $sykmeldtFnr")
            call.respond(HttpStatusCode.NotFound)
        }
        else {
            call.respond(HttpStatusCode.OK, true)
        }
    }
}
data class IsActiveSykmeldingRequestDTO(val sykmeldtFnr: String, val orgnummer: String)
