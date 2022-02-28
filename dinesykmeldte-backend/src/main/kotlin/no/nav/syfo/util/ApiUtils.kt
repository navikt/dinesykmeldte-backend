package no.nav.syfo.util

import io.ktor.application.ApplicationCall
import io.ktor.auth.authentication
import no.nav.syfo.application.BrukerPrincipal

fun ApplicationCall.getBrukerPrincipal(): BrukerPrincipal {
    val brukerPrincipal: BrukerPrincipal? = this.authentication.principal()

    requireNotNull(brukerPrincipal) {
        "Mottok HTTP kall uten principal. Er serveren konfigurert riktig?"
    }

    return brukerPrincipal
}

fun ApplicationCall.getParam(paramName: String): String {
    val param = this.parameters[paramName]

    requireNotNull(param) {
        "Tried to get param $paramName. You need to match the param name with the name defined in the route."
    }

    return param
}
