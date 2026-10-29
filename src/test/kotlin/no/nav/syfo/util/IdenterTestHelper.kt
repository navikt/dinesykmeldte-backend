package no.nav.syfo.util

import io.mockk.coEvery
import io.mockk.mockk
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.narmesteleder.RelationScopeService
import no.nav.syfo.narmesteleder.db.NarmestelederDb
import no.nav.syfo.pdl.service.PdlIdenterService

fun exactRelationScopeService(database: DatabaseInterface): RelationScopeService {
    val identerService = mockk<PdlIdenterService>()
    coEvery { identerService.hentIdenter(any()) } coAnswers {
        firstArg<Collection<String>>().associateWith { setOf(it) }
    }
    return RelationScopeService(NarmestelederDb(database), identerService)
}
