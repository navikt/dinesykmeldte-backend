package no.nav.syfo.minesykmeldte.api

import io.ktor.application.call
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import no.nav.syfo.application.getToken
import no.nav.syfo.minesykmeldte.MineSykmeldteService
import java.util.UUID

fun Route.registerMineSykmeldteApi(mineSykmeldteService: MineSykmeldteService) {
    get("api/minesykmeldte") {
        val token = "Bearer ${call.getToken()}"
        val callId = UUID.randomUUID()
        call.respond(mineSykmeldteService.getMineSykmeldte(token, callId))
    }
}
