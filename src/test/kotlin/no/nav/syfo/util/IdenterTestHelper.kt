package no.nav.syfo.util

import io.mockk.coEvery
import io.mockk.mockk
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.minesykmeldte.db.RelationScopeResolver
import no.nav.syfo.pdl.service.PdlIdenterService

fun exactRelationScopeResolver(database: DatabaseInterface): RelationScopeResolver {
    val identerService = mockk<PdlIdenterService>()
    coEvery { identerService.hentIdenter(any()) } coAnswers {
        firstArg<Collection<String>>().associateWith { setOf(it) }
    }
    return RelationScopeResolver(database, identerService)
}
