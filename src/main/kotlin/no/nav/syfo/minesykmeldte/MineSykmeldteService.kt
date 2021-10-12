package no.nav.syfo.minesykmeldte

import no.nav.syfo.minesykmeldte.model.Sykmeldt
import java.util.UUID

class MineSykmeldteService {
    fun getMineSykmeldte(token: String, callId: UUID): List<Sykmeldt> {
        // hent token for sykmeldigner-arb
        // hent sykmeldinger: sykmeldinger-arbeidsgiver/api/dinesykmeldte
        // hent token for søknad-arb
        // hent søknader
        // hent refusjon? Oppfølgingsplan, dialogmøte?
        // kun hente siste fire måneder
        return emptyList()
    }
}
