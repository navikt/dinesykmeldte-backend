package no.nav.syfo.virksomhet.api

import no.nav.syfo.virksomhet.db.VirksomhetDb
import no.nav.syfo.virksomhet.model.Virksomhet

class VirksomhetService(
    private val virksomhetDb: VirksomhetDb
) {
    suspend fun getVirksomheter(lederFnr: String): List<Virksomhet> =
        virksomhetDb.getVirksomheter(lederFnr).map { Virksomhet(it.navn, it.orgnummer) }
}
