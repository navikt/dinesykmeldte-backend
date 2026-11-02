package no.nav.syfo.narmesteleder

import no.nav.syfo.application.metrics.RELATION_SCOPE_DENIED_COUNTER
import no.nav.syfo.narmesteleder.db.NarmestelederDb
import no.nav.syfo.narmesteleder.db.NarmestelederDbModel
import no.nav.syfo.narmesteleder.model.RelationScope
import no.nav.syfo.pdl.service.PdlIdenterService

class RelationScopeService(
    private val narmestelederDb: NarmestelederDb,
    private val identerService: PdlIdenterService,
) {
    suspend fun forLeder(
        lederFnr: String,
        narmestelederId: String? = null,
        orgnummer: String? = null,
    ): List<RelationScope> {
        val relations =
            narmestelederDb.finnNarmestelederkoblinger(lederFnr, narmestelederId, orgnummer)
        if (relations.isEmpty()) return emptyList()

        val resolved = identerService.hentIdenter(relations.map { it.pasientFnr }.distinct())
        val allIdenter =
            relations.flatMap { resolved[it.pasientFnr] ?: setOf(it.pasientFnr) }.toSet()
        val active =
            narmestelederDb.finnAktiveRelasjoner(allIdenter, relations.map { it.orgnummer }.toSet())
        return relations.map { relation ->
            val decision =
                resolveRelationScope(
                    relation,
                    resolved[relation.pasientFnr] ?: setOf(relation.pasientFnr),
                    active,
                )
            if (decision.denied) RELATION_SCOPE_DENIED_COUNTER.inc()
            decision.scope
        }
    }
}

// Keep the D2 decision pure; the service records denials separately.
internal class RelationScopeDecision(
    val scope: RelationScope,
    val denied: Boolean,
)

internal fun resolveRelationScope(
    relation: NarmestelederDbModel,
    identer: Set<String>,
    activeRelations: List<NarmestelederDbModel>,
): RelationScopeDecision {
    val matching =
        activeRelations.filter {
            it.orgnummer == relation.orgnummer && it.pasientFnr in identer
        }
    val onlyRelation =
        relation.pasientFnr in identer &&
            matching.singleOrNull()?.narmestelederId == relation.narmestelederId
    return RelationScopeDecision(
        scope =
            RelationScope(
                relation.narmestelederId,
                if (onlyRelation) identer else setOf(relation.pasientFnr),
            ),
        denied = matching.size > 1,
    )
}
