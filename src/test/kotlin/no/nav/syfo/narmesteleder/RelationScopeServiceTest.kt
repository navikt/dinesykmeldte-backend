package no.nav.syfo.narmesteleder

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.syfo.application.metrics.RELATION_SCOPE_DENIED_COUNTER
import no.nav.syfo.narmesteleder.db.NarmestelederDb
import no.nav.syfo.narmesteleder.db.NarmestelederDbModel
import no.nav.syfo.pdl.service.PdlIdenterService
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RelationScopeServiceTest :
    FunSpec({
        val old = "12345678910"
        val current = "12345678912"
        val other = "12345678915"
        val otherOld = "12345678916"
        val identer = setOf(old, current)
        val former = NarmestelederDbModel("former", old, "12345678913", "org")
        val leader = NarmestelederDbModel("leader", current, "12345678914", "org")
        val db = mockk<NarmestelederDb>()
        val pdl = mockk<PdlIdenterService>()
        val service = RelationScopeService(db, pdl)

        beforeEach { clearMocks(db, pdl) }

        context("pure D2 decision") {
            test("only active relation gets its own resolved identer") {
                val decision = resolveRelationScope(leader, identer, listOf(leader))
                assertEquals(leader.narmestelederId, decision.scope.narmestelederId)
                assertEquals(identer, decision.scope.identer)
                assertFalse(decision.denied)
            }

            test("two relations across leaders deny widening for both without side effects") {
                val before = RELATION_SCOPE_DENIED_COUNTER.get()
                listOf(former, leader).forEach { relation ->
                    val decision = resolveRelationScope(relation, identer, listOf(former, leader))
                    assertTrue(decision.denied)
                    assertEquals(setOf(relation.pasientFnr), decision.scope.identer)
                }
                assertEquals(before, RELATION_SCOPE_DENIED_COUNTER.get())
            }

            test("another org and another person do not count") {
                val otherOrg = former.copy(narmestelederId = "other-org", orgnummer = "another-org")
                val otherPerson = leader.copy(narmestelederId = "other-person", pasientFnr = other)
                val decision =
                    resolveRelationScope(leader, identer, listOf(leader, otherOrg, otherPerson))
                assertEquals(identer, decision.scope.identer)
                assertFalse(decision.denied)
            }

            test("two rows for the same leader also deny widening") {
                val second = leader.copy(narmestelederId = "second", pasientFnr = old)
                val decision = resolveRelationScope(leader, identer, listOf(leader, second))
                assertEquals(setOf(current), decision.scope.identer)
                assertTrue(decision.denied)
            }

            test("fallback and missing relations always retain the relation's own ident") {
                assertEquals(
                    setOf(current),
                    resolveRelationScope(leader, setOf(current), listOf(leader)).scope.identer,
                )
                assertEquals(
                    setOf(current),
                    resolveRelationScope(leader, identer, emptyList()).scope.identer,
                )
                assertEquals(
                    setOf(current),
                    resolveRelationScope(leader, setOf(old), listOf(leader)).scope.identer,
                )
            }
        }

        test("no matching relations skips both PDL and the active-relation query") {
            coEvery { db.finnNarmestelederkoblinger(leader.lederFnr, "unknown", "org") } returns
                emptyList()
            assertTrue(service.forLeder(leader.lederFnr, "unknown", "org").isEmpty())
            coVerify(exactly = 0) { pdl.hentIdenter(any()) }
            coVerify(exactly = 0) { db.finnAktiveRelasjoner(any(), any()) }
        }

        test("one lookup deduplicates relation identer and applies the optional filters") {
            val otherOrg = leader.copy(narmestelederId = "other-org", orgnummer = "another-org")
            coEvery { db.finnNarmestelederkoblinger(leader.lederFnr, null, null) } returns
                listOf(leader, otherOrg)
            coEvery { pdl.hentIdenter(listOf(current)) } returns mapOf(current to identer)
            coEvery { db.finnAktiveRelasjoner(identer, setOf("org", "another-org")) } returns
                listOf(leader, otherOrg)
            assertTrue(service.forLeder(leader.lederFnr).all { it.identer == identer })
            coVerify(exactly = 1) { pdl.hentIdenter(listOf(current)) }

            coEvery { db.finnNarmestelederkoblinger(leader.lederFnr, "leader", "org") } returns
                listOf(leader)
            coEvery { db.finnAktiveRelasjoner(identer, setOf("org")) } returns listOf(leader)
            assertEquals(
                "leader",
                service.forLeder(leader.lederFnr, "leader", "org").single().narmestelederId,
            )
        }

        test("reversed answers never pool another relation's resolved identer") {
            val second = leader.copy(narmestelederId = "second", pasientFnr = other)
            coEvery { db.finnNarmestelederkoblinger(leader.lederFnr, null, null) } returns
                listOf(leader, second)
            coEvery { pdl.hentIdenter(listOf(current, other)) } returns
                linkedMapOf(other to setOf(other, otherOld), current to identer)
            coEvery {
                db.finnAktiveRelasjoner(identer + setOf(other, otherOld), setOf("org"))
            } returns listOf(leader, second)
            val scopes = service.forLeder(leader.lederFnr).associateBy { it.narmestelederId }
            assertEquals(identer, scopes.getValue("leader").identer)
            assertEquals(setOf(other, otherOld), scopes.getValue("second").identer)
        }

        test("service records a denial for both leaders when D2 finds multiple active relations") {
            coEvery { pdl.hentIdenter(any()) } returns mapOf(current to identer, old to identer)
            coEvery { db.finnAktiveRelasjoner(identer, setOf("org")) } returns
                listOf(former, leader)
            val before = RELATION_SCOPE_DENIED_COUNTER.get()
            listOf(former, leader).forEach { relation ->
                coEvery { db.finnNarmestelederkoblinger(relation.lederFnr, null, null) } returns
                    listOf(relation)
                assertEquals(
                    setOf(relation.pasientFnr),
                    service.forLeder(relation.lederFnr).single().identer,
                )
            }
            assertEquals(before + 2, RELATION_SCOPE_DENIED_COUNTER.get())
        }
    })
