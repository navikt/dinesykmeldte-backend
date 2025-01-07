package no.nav.syfo.pdl.service

import no.nav.syfo.azuread.AccessTokenClient
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.client.model.GetPersonResponse
import no.nav.syfo.pdl.exceptions.NameNotFoundInPdlException
import no.nav.syfo.pdl.model.Navn
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.util.logger

class PdlPersonService(
    private val pdlClient: PdlClient,
    private val accessTokenClient: AccessTokenClient,
    private val pdlScope: String,
) {
    companion object {
        const val AKTORID_GRUPPE = "AKTORID"
        private val log = logger()
    }

    suspend fun getPerson(fnr: String, callId: String): PdlPerson {
        val accessToken = accessTokenClient.getAccessToken(pdlScope)
        try {
            val pdlResponse = pdlClient.getPerson(fnr = fnr, token = accessToken)
            return pdlResponse.toPerson(callId)
        } catch (e: Exception) {
            log.error("Feil ved henting av person fra PDL for $callId", e)
            throw e
        }
    }

    private fun GetPersonResponse.toPerson(callId: String): PdlPerson {
        val navn = data.person?.navn?.firstOrNull()
        val aktorId = data.identer?.identer?.firstOrNull { it.gruppe == AKTORID_GRUPPE }?.ident

        errors?.forEach {
            log.error("PDL returnerte feilmelding: ${it.message}, ${it.extensions?.code}, $callId")
            it.extensions?.details?.let { details ->
                log.error(
                    "Type: ${details.type}, cause: ${details.cause}, policy: ${details.policy}, $callId"
                )
            }
        }

        if (navn == null) {
            throw NameNotFoundInPdlException("Fant ikke navn i PDL $callId")
        }
        if (aktorId == null) {
            throw RuntimeException("Fant ikke aktorId i PDL $callId")
        }

        return PdlPerson(
            navn =
                Navn(
                    fornavn = navn.fornavn,
                    mellomnavn = navn.mellomnavn,
                    etternavn = navn.etternavn
                ),
        )
    }
}
