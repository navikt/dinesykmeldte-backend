package no.nav.syfo.minesykmeldte.db

import io.kotest.core.spec.style.FunSpec
import io.mockk.mockk
import no.nav.syfo.application.metrics.RELATION_SCOPE_DENIED_COUNTER
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RelationScopeResolverTest :
    FunSpec({
        val resolver = RelationScopeResolver(mockk(), mockk())
        val old = "12345678910"
        val current = "12345678912"
        val identer = setOf(old, current)
        val former = ActiveRelation("former", old, "org", "12345678913")
        val leader = ActiveRelation("leader", current, "org", "12345678914")

        test("only active relation gets widened scope") {
            val scope = resolver.forRelation(leader, identer, listOf(leader))
            assertTrue(scope.utvidet)
            assertEquals(identer, scope.identer)
        }

        test("two relations count all leaders and deny widening for both") {
            val before = RELATION_SCOPE_DENIED_COUNTER.get()
            listOf(former, leader).forEach { relation ->
                val scope = resolver.forRelation(relation, identer, listOf(former, leader))
                assertFalse(scope.utvidet)
                assertEquals(setOf(relation.pasientFnr), scope.identer)
            }
            assertEquals(before + 2, RELATION_SCOPE_DENIED_COUNTER.get())
        }

        test("another org and another person do not count") {
            val otherOrg = ActiveRelation("other-org", old, "another-org", former.lederFnr)
            val otherPerson = ActiveRelation("other-person", "12345678915", "org", leader.lederFnr)
            assertTrue(
                resolver
                    .forRelation(
                        leader,
                        identer,
                        listOf(leader, otherOrg, otherPerson),
                    ).utvidet,
            )
        }

        test("two rows for the same leader also deny widening") {
            val second = ActiveRelation("second", old, leader.orgnummer, leader.lederFnr)
            assertFalse(resolver.forRelation(leader, identer, listOf(leader, second)).utvidet)
        }

        test("singleton fallback never widens and a missing relation cannot widen") {
            assertFalse(resolver.forRelation(leader, setOf(current), listOf(leader)).utvidet)
            assertFalse(resolver.forRelation(leader, identer, emptyList()).utvidet)
            assertEquals(
                setOf(current),
                resolver.forRelation(leader, setOf(old), listOf(leader)).identer,
            )
        }
    })
