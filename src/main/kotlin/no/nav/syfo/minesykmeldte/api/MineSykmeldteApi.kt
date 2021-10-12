package no.nav.syfo.minesykmeldte.api

import io.ktor.application.call
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import no.nav.syfo.application.getToken
import no.nav.syfo.minesykmeldte.MineSykmeldteService
import java.util.UUID

fun Route.registerMineSykmeldteApi(mineSykmeldteService: MineSykmeldteService) {
    // api for landingsside (preview sykmelding, komplett søknad)
    // api for å hente sykmelding
    // merke sykmelding som lest
    // merke søknad som lest
    get("api/minesykmeldte") {
        val token = "Bearer ${call.getToken()}"
        val callId = UUID.randomUUID()
        call.respond(mineSykmeldteService.getMineSykmeldte(token, callId))
    }
}
