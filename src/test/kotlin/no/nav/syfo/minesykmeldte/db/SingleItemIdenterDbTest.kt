package no.nav.syfo.minesykmeldte.db

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.syfo.hendelser.db.HendelseDbModel
import no.nav.syfo.minesykmeldte.MineSykmeldteService
import no.nav.syfo.narmesteleder.RelationScopeService
import no.nav.syfo.narmesteleder.db.NarmestelederDb
import no.nav.syfo.narmesteleder.model.RelationScope
import no.nav.syfo.pdl.service.PdlIdenterService
import no.nav.syfo.util.TestDb
import no.nav.syfo.util.createSoknadDbModel
import no.nav.syfo.util.createSykmeldingDbModel
import no.nav.syfo.util.createSykmeldtDbModel
import no.nav.syfo.util.insertHendelse
import no.nav.syfo.util.insertOrUpdate
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SingleItemIdenterDbTest :
    FunSpec({
        val database = TestDb.database
        val identer = mockk<PdlIdenterService>()
        val scopeService = RelationScopeService(NarmestelederDb(database), identer)
        val db = MineSykmeldteDb(database)
        val service = MineSykmeldteService(db, scopeService)
        val old = "12345678910"
        val current = "12345678912"
        val leader = "12345678913"
        val former = "12345678914"
        val otherPerson = "12345678915"
        val otherOld = "12345678916"
        val org = "orgnummer"

        beforeEach {
            clearMocks(identer)
            coEvery { identer.hentIdenter(any()) } coAnswers {
                firstArg<Collection<String>>().reversed().associateWith {
                    when (it) {
                        old, current -> setOf(old, current)
                        otherPerson, otherOld -> setOf(otherPerson, otherOld)
                        else -> setOf(it)
                    }
                }
            }
        }
        afterEach { TestDb.clearAllData() }

        fun relation(
            ident: String,
            leder: String,
            orgnummer: String = org,
            id: String = UUID.randomUUID().toString(),
        ): String {
            database.insertOrUpdate(id, orgnummer, ident, leder)
            return id
        }

        fun documents(
            ident: String,
            orgnummer: String = org,
        ): Documents {
            val ids = Documents()
            database.insertOrUpdate(
                createSykmeldingDbModel(ids.sykmelding, pasientFnr = ident, orgnummer = orgnummer),
                createSykmeldtDbModel(ident),
            )
            database.insertOrUpdate(
                createSoknadDbModel(ids.soknad, ids.sykmelding, ident, orgnummer),
            )
            database.insertHendelse(
                HendelseDbModel(
                    id = ids.hendelse.toString(),
                    hendelseId = ids.hendelse,
                    pasientFnr = ident,
                    orgnummer = orgnummer,
                    oppgavetype = "OPPFOLGINGSPLAN_OPPRETTET",
                    lenke = null,
                    tekst = "Synthetic event",
                    timestamp = OffsetDateTime.now(),
                    utlopstidspunkt = null,
                    ferdigstilt = false,
                    ferdigstiltTimestamp = null,
                ),
            )
            return ids
        }

        suspend fun assertAccess(
            ids: Documents,
            leder: String,
            allowed: Boolean,
        ) {
            assertEquals(allowed, service.getSykmelding(ids.sykmelding, leder) != null)
            assertEquals(allowed, service.getSoknad(ids.soknad, leder) != null)
            assertEquals(allowed, service.markSykmeldingRead(ids.sykmelding, leder))
            assertEquals(allowed, service.markSoknadRead(ids.soknad, leder))
            assertEquals(allowed, service.markHendelseRead(ids.hendelse, leder))
        }

        test(
            "single current relation reads and marks all old-ident document types; fnr remains document ident",
        ) {
            relation(current, leader)
            val ids = documents(old)
            val markedAt = Instant.now()
            assertAccess(ids, leader, true)
            assertEquals(old, service.getSykmelding(ids.sykmelding, leader)?.fnr)
            assertEquals(old, service.getSoknad(ids.soknad, leader)?.fnr)
            assertTrue(TestDb.getSykmelding(ids.sykmelding)!!.lest)
            assertTrue(TestDb.getSoknad(ids.soknad)!!.lest)
            val hendelse = assertNotNull(TestDb.getHendelse(ids.hendelse.toString()))
            assertTrue(hendelse.ferdigstilt)
            val ferdigstiltTimestamp = assertNotNull(hendelse.ferdigstiltTimestamp).toInstant()
            assertTrue(ferdigstiltTimestamp in markedAt.minusSeconds(5)..markedAt.plusSeconds(5))
        }

        test("former and new leader retain only exact access while both relations exist") {
            relation(old, former)
            relation(current, leader)
            val oldDocuments = documents(old)
            val currentDocuments = documents(current)
            assertAccess(oldDocuments, leader, false)
            assertAccess(currentDocuments, former, false)
            assertFalse(TestDb.getSykmelding(oldDocuments.sykmelding)!!.lest)
            assertFalse(TestDb.getSoknad(currentDocuments.soknad)!!.lest)
            assertFalse(TestDb.getHendelse(oldDocuments.hendelse.toString())!!.ferdigstilt)
            assertAccess(oldDocuments, former, true)
            assertAccess(currentDocuments, leader, true)
        }

        test("a relation in a different org does not prevent widening") {
            relation(current, leader)
            relation(old, former, "other-org")
            assertAccess(documents(old), leader, true)
        }

        test("a relation in a different org cannot grant access") {
            relation(current, leader, "other-org")
            assertAccess(documents(old), leader, false)
        }

        test(
            "leader without a relation in the document org never resolves identer for reads or updates",
        ) {
            relation(current, leader, "other-org")
            relation(current, former)
            val ids = documents(old)
            assertAccess(ids, leader, false)
            coVerify(exactly = 0) { identer.hentIdenter(any()) }
            assertFalse(TestDb.getSykmelding(ids.sykmelding)!!.lest)
            assertFalse(TestDb.getSoknad(ids.soknad)!!.lest)
            assertFalse(TestDb.getHendelse(ids.hendelse.toString())!!.ferdigstilt)
        }

        test("two persons are not pooled even when the leader has relations to both") {
            relation(current, leader)
            relation(otherPerson, leader)
            val firstPersonDocuments = documents(old)
            val secondPersonDocuments = documents(otherOld)
            assertAccess(firstPersonDocuments, leader, true)
            assertAccess(secondPersonDocuments, leader, true)
            val firstRelationOnlyLeader = former
            relation(current, firstRelationOnlyLeader, "another-org")
            assertAccess(secondPersonDocuments, firstRelationOnlyLeader, false)
            // Remove B's relation: A's remaining relation must not grant access to B.
            database.connection.use { connection ->
                connection.prepareStatement("DELETE FROM narmesteleder WHERE pasient_fnr = ?").use {
                    it.setString(1, otherPerson)
                    it.executeUpdate()
                }
                connection.commit()
            }
            assertAccess(secondPersonDocuments, leader, false)
        }

        test("PDL fallback keeps exact access and cannot grant cross-ident access") {
            coEvery { identer.hentIdenter(any()) } coAnswers {
                firstArg<Collection<String>>().associateWith { setOf(it) }
            }
            relation(current, leader)
            assertAccess(documents(old), leader, false)
            assertAccess(documents(current), leader, true)
        }

        test(
            "widened reads select sykmeldt with greatest latest_tom, exact reads use relation row",
        ) {
            relation(current, leader)
            val oldDocuments = documents(old)
            val currentDocuments = documents(current)
            database.insertOrUpdate(
                createSykmeldingDbModel(UUID.randomUUID().toString(), pasientFnr = current),
                createSykmeldtDbModel(
                    current,
                ).copy(
                    pasientNavn = "Newest synthetic name",
                    latestTom = LocalDate.now().plusMonths(1),
                ),
            )
            assertEquals(
                "Newest synthetic name",
                service.getSykmelding(oldDocuments.sykmelding, leader)?.navn,
            )
            assertEquals(
                "Newest synthetic name",
                service.getSoknad(oldDocuments.soknad, leader)?.navn,
            )
            relation(old, former)
            assertEquals(
                "Navn Navnesen",
                service.getSykmelding(oldDocuments.sykmelding, former)?.navn,
            )
            assertEquals(
                "Newest synthetic name",
                service.getSoknad(currentDocuments.soknad, leader)?.navn,
            )
        }

        test("read without a sykmeldt row in scope is not found") {
            relation(current, leader)
            val ids = documents(old)
            database.connection.use { connection ->
                connection.prepareStatement("DELETE FROM sykmeldt").use { it.executeUpdate() }
                connection.commit()
            }
            assertNull(service.getSykmelding(ids.sykmelding, leader))
            assertNull(service.getSoknad(ids.soknad, leader))
        }

        test("unknown ids and unrelated leader keep not-found results") {
            relation(current, leader)
            assertAccess(Documents(), leader, false)
            assertAccess(documents(old), former, false)
            coVerify(exactly = 0) { identer.hentIdenter(any()) }
        }

        test("single-document SQL with empty scopes reads and marks nothing") {
            val relationId = relation(current, leader)
            val ids = documents(old)
            listOf(emptyList(), listOf(RelationScope(relationId, emptySet()))).forEach { scopes ->
                assertNull(db.getSykmelding(ids.sykmelding, leader, scopes))
                assertNull(db.getSoknad(ids.soknad, leader, scopes))
                assertTrue(db.markSykmeldingRead(ids.sykmelding, leader, scopes).isEmpty())
                assertTrue(db.markSoknadRead(ids.soknad, leader, scopes).isEmpty())
                assertTrue(db.markHendelseRead(ids.hendelse, leader, scopes).isEmpty())
            }
            assertFalse(TestDb.getSykmelding(ids.sykmelding)!!.lest)
            assertFalse(TestDb.getSoknad(ids.soknad)!!.lest)
            assertFalse(TestDb.getHendelse(ids.hendelse.toString())!!.ferdigstilt)
            coVerify(exactly = 0) { identer.hentIdenter(any()) }
        }

        listOf(
            "deleted",
            "different-leader",
            "different-org",
            "different-person",
            "mismatched-caller",
        ).forEach { change ->
            test("final SQL denies stale scope after relation becomes $change") {
                val relationId = relation(current, leader)
                val ids = documents(old)
                val scopes = scopeService.forLeder(leader, orgnummer = org)
                assertNotNull(scopes.singleOrNull())
                database.connection.use { connection ->
                    val sql =
                        when (change) {
                            "deleted" -> "DELETE FROM narmesteleder WHERE narmeste_leder_id = ?"
                            "different-leader" ->
                                "UPDATE narmesteleder SET leder_fnr = '12345678914' " +
                                    "WHERE narmeste_leder_id = ?"
                            "different-org" ->
                                "UPDATE narmesteleder SET orgnummer = 'other-org' " +
                                    "WHERE narmeste_leder_id = ?"
                            "different-person" ->
                                "UPDATE narmesteleder SET pasient_fnr = '12345678915' " +
                                    "WHERE narmeste_leder_id = ?"
                            else -> null
                        }
                    if (sql != null) {
                        connection.prepareStatement(sql).use {
                            it.setString(1, relationId)
                            it.executeUpdate()
                        }
                    }
                    connection.commit()
                }
                val caller = if (change == "mismatched-caller") former else leader
                assertNull(db.getSykmelding(ids.sykmelding, caller, scopes))
                assertNull(db.getSoknad(ids.soknad, caller, scopes))
                assertTrue(db.markSykmeldingRead(ids.sykmelding, caller, scopes).isEmpty())
                assertTrue(db.markSoknadRead(ids.soknad, caller, scopes).isEmpty())
                assertTrue(db.markHendelseRead(ids.hendelse, caller, scopes).isEmpty())
            }
        }
    })

private class Documents(
    val sykmelding: String = UUID.randomUUID().toString(),
    val soknad: String = UUID.randomUUID().toString(),
    val hendelse: UUID = UUID.randomUUID(),
)
