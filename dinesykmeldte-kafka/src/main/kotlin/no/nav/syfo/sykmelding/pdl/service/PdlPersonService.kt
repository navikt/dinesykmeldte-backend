package no.nav.syfo.sykmelding.pdl.service

import no.nav.syfo.azuread.AccessTokenClient
import no.nav.syfo.log
import no.nav.syfo.sykmelding.pdl.client.PdlClient
import no.nav.syfo.sykmelding.pdl.client.model.GetPersonResponse
import no.nav.syfo.sykmelding.pdl.exceptions.NameNotFoundInPdlException
import no.nav.syfo.sykmelding.pdl.model.Navn
import no.nav.syfo.sykmelding.pdl.model.PdlPerson
import java.lang.RuntimeException

class PdlPersonService(
    private val pdlClient: PdlClient,
    private val accessTokenClient: AccessTokenClient,
    private val pdlScope: String,
) {
    companion object {
        const val AKTORID_GRUPPE = "AKTORID"
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
            it.extensions?.details?.let { details -> log.error("Type: ${details.type}, cause: ${details.cause}, policy: ${details.policy}, $callId") }
        }

        if (navn == null) {
            throw NameNotFoundInPdlException("Fant ikke navn i PDL $callId")
        }
        if (aktorId == null) {
            throw RuntimeException("Fant ikke aktorId i PDL $callId")
        }

        return PdlPerson(
            navn = Navn(fornavn = navn.fornavn, mellomnavn = navn.mellomnavn, etternavn = navn.etternavn),
            aktorId = aktorId,
        )
    }
}
