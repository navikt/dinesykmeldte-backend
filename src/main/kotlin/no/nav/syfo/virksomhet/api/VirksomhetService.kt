package no.nav.syfo.virksomhet.api

import no.nav.syfo.narmesteleder.RelationScopeService
import no.nav.syfo.virksomhet.db.VirksomhetDb
import no.nav.syfo.virksomhet.model.Virksomhet

class VirksomhetService(
    private val virksomhetDb: VirksomhetDb,
    private val relationScopeService: RelationScopeService,
) {
    suspend fun getVirksomheter(lederFnr: String): List<Virksomhet> {
        val scopes = relationScopeService.forLeder(lederFnr)
        return virksomhetDb
            .getVirksomheter(
                lederFnr,
                scopes,
            ).map { Virksomhet(it.navn, it.orgnummer) }
    }
}
