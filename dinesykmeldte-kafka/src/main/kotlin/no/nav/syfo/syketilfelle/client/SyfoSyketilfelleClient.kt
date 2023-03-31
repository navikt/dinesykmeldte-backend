package no.nav.syfo.syketilfelle.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.ContentType
import no.nav.syfo.azuread.AccessTokenClient
import no.nav.syfo.log
import java.time.LocalDate

class SyfoSyketilfelleClient(
    private val syketilfelleEndpointURL: String,
    private val accessTokenClient: AccessTokenClient,
    private val syketilfelleScope: String,
    private val httpClient: HttpClient,
) {

    suspend fun finnStartdato(fnr: String, sykmeldingId: String): LocalDate {
        val sykeforloep = hentSykeforloep(fnr)
        val aktueltSykeforloep = sykeforloep.firstOrNull {
            it.sykmeldinger.any { simpleSykmelding -> simpleSykmelding.id == sykmeldingId }
        }

        if (aktueltSykeforloep == null) {
            log.error("Fant ikke sykeforløp for sykmelding med id $sykmeldingId")
            throw SyketilfelleNotFoundException("Fant ikke sykeforløp for sykmelding med id $sykmeldingId")
        } else {
            return aktueltSykeforloep.oppfolgingsdato
        }
    }

    private suspend fun hentSykeforloep(fnr: String): List<Sykeforloep> =
        httpClient.get("$syketilfelleEndpointURL/api/v1/sykeforloep?inkluderPapirsykmelding=true") {
            accept(ContentType.Application.Json)
            val token = accessTokenClient.getAccessToken(syketilfelleScope)
            headers {
                append("Authorization", "Bearer $token")
                append("fnr", fnr)
            }
        }.body()
}

data class Sykeforloep(
    var oppfolgingsdato: LocalDate,
    val sykmeldinger: List<SimpleSykmelding>,
)

data class SimpleSykmelding(
    val id: String,
    val fom: LocalDate,
    val tom: LocalDate,
)

class SyketilfelleNotFoundException(override val message: String?) : Exception(message)
