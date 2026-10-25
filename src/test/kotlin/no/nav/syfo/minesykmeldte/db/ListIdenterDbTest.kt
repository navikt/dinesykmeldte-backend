package no.nav.syfo.minesykmeldte.db

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.syfo.application.metrics.IDENTER_FALLBACK_COUNTER
import no.nav.syfo.application.metrics.IDENTER_LOOKUP_COUNTER
import no.nav.syfo.application.metrics.RELATION_SCOPE_DENIED_COUNTER
import no.nav.syfo.dinesykmeldte.service.DineSykmeldteService
import no.nav.syfo.hendelser.db.HendelseDbModel
import no.nav.syfo.minesykmeldte.MineSykmeldteService
import no.nav.syfo.minesykmeldte.model.PreviewNySoknad
import no.nav.syfo.pdl.service.PdlIdenterService
import no.nav.syfo.soknad.model.SoknadStatus
import no.nav.syfo.util.TestDb
import no.nav.syfo.util.createSoknadDbModel
import no.nav.syfo.util.createSykmeldingDbModel
import no.nav.syfo.util.createSykmeldtDbModel
import no.nav.syfo.util.insertHendelse
import no.nav.syfo.util.insertOrUpdate
import no.nav.syfo.virksomhet.api.VirksomhetService
import no.nav.syfo.virksomhet.db.VirksomhetDb
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ListIdenterDbTest :
    FunSpec({
        val database = TestDb.database
        val identer = mockk<PdlIdenterService>()
        val resolver = RelationScopeResolver(database, identer)
        val db = MineSykmeldteDb(database, resolver)
        val service = MineSykmeldteService(db)
        val v2 = DineSykmeldteService(db)
        val virksomhetDb = VirksomhetDb(database, resolver)
        val virksomhetService = VirksomhetService(virksomhetDb)
        val old = "12345678910"
        val current = "12345678912"
        val leader = "12345678913"
        val former = "12345678914"
        val other = "12345678915"
        val otherOld = "12345678916"
        val org = "synthetic-org"
        val otherOrg = "other-synthetic-org"

        beforeEach {
            clearMocks(identer)
            coEvery { identer.hentIdenter(any()) } coAnswers {
                // Deliberately return the batch in the opposite order.
                firstArg<Collection<String>>().reversed().associateWith {
                    when (it) {
                        current, old -> setOf(current, old)
                        other, otherOld -> setOf(other, otherOld)
                        else -> setOf(it)
                    }
                }
            }
        }
        afterEach { TestDb.clearAllData() }

        fun relation(
            ident: String = current,
            leder: String = leader,
            orgnummer: String = org,
        ): String =
            UUID.randomUUID().toString().also {
                database.insertOrUpdate(it, orgnummer, ident, leder)
            }

        fun event(
            ident: String,
            orgnummer: String,
            id: String = UUID.randomUUID().toString(),
            type: String = "OPPFOLGINGSPLAN_OPPRETTET",
            utlopstidspunkt: OffsetDateTime? = null,
            ferdigstilt: Boolean = false,
        ): UUID {
            val eventId = UUID.randomUUID()
            database.insertHendelse(
                HendelseDbModel(
                    id = id,
                    hendelseId = eventId,
                    pasientFnr = ident,
                    orgnummer = orgnummer,
                    oppgavetype = type,
                    lenke = null,
                    tekst = "Synthetic event",
                    timestamp = OffsetDateTime.now(),
                    utlopstidspunkt = utlopstidspunkt,
                    ferdigstilt = ferdigstilt,
                    ferdigstiltTimestamp = if (ferdigstilt) OffsetDateTime.now() else null,
                ),
            )
            return eventId
        }

        fun documents(
            ident: String,
            orgnummer: String = org,
            latestTom: LocalDate = LocalDate.now(),
            name: String = "Synthetic Name",
            startdato: LocalDate = LocalDate.now().minusMonths(2),
        ): ListDocuments {
            val sm = UUID.randomUUID().toString()
            val sk = UUID.randomUUID().toString()
            database.insertOrUpdate(
                createSykmeldingDbModel(sm, ident, orgnummer),
                createSykmeldtDbModel(ident).copy(
                    pasientNavn = name,
                    latestTom = latestTom,
                    startdatoSykefravaer = startdato,
                ),
            )
            val soknad = createSoknadDbModel(sk, sm, ident, orgnummer)
            database.insertOrUpdate(
                soknad.copy(
                    sykepengesoknad = soknad.sykepengesoknad.copy(status = SoknadStatus.NY),
                ),
            )
            return ListDocuments(
                sm,
                sk,
                event(ident, orgnummer),
                event(ident, orgnummer, type = "DIALOGMOTE_INNKALLING"),
                event(ident, orgnummer, id = sk, type = "IKKE_SENDT_SOKNAD"),
            )
        }

        fun assertMarked(
            docs: ListDocuments,
            marked: Boolean,
        ) {
            assertEquals(marked, TestDb.getSykmelding(docs.sykmelding)!!.lest)
            assertEquals(marked, TestDb.getSoknad(docs.soknad)!!.lest)
        }

        test(
            "one relation combines old and current documents with frontend and v2 identities",
        ) {
            val relationId = relation()
            val currentDocs = documents(current, name = "Current row")
            val latestStartdato = LocalDate.now().minusMonths(3)
            val oldDocs =
                documents(
                    old,
                    latestTom = LocalDate.now().plusMonths(1),
                    name = "Latest row",
                    startdato = latestStartdato,
                )
            val preview = service.getMineSykmeldte(leader).single()
            coVerify(exactly = 1) { identer.hentIdenter(listOf(current)) }
            assertEquals(relationId, preview.narmestelederId)
            assertEquals(current, preview.fnr)
            assertEquals("Latest row", preview.navn)
            assertEquals(latestStartdato, preview.startdatoSykefravar)
            assertEquals(
                setOf(
                    oldDocs.sykmelding,
                    currentDocs.sykmelding,
                ),
                preview.sykmeldinger
                    .map {
                        it.id
                    }.toSet(),
            )
            assertEquals(setOf(old, current), preview.sykmeldinger.map { it.fnr }.toSet())
            assertTrue(preview.sykmeldinger.all { it.startdatoSykefravar == latestStartdato })
            assertEquals(
                setOf(oldDocs.soknad, currentDocs.soknad),
                preview.previewSoknader
                    .map {
                        it.id
                    }.toSet(),
            )
            assertTrue(
                preview.previewSoknader.all { (it as PreviewNySoknad).ikkeSendtSoknadVarsel },
            )
            assertEquals(
                setOf(oldDocs.plan, currentDocs.plan),
                preview.oppfolgingsplaner
                    .map {
                        it.hendelseId
                    }.toSet(),
            )
            assertEquals(
                setOf(oldDocs.dialog, currentDocs.dialog),
                preview.dialogmoter
                    .map {
                        it.hendelseId
                    }.toSet(),
            )

            val allV2 = v2.getDineSykmeldte(leader).single()
            val singleV2 = assertNotNull(v2.getSykmeldt(relationId, leader))
            listOf(allV2, singleV2).forEach {
                assertEquals(current, it.fnr)
                assertEquals("Latest row", it.navn)
                assertEquals(
                    setOf(oldDocs.sykmelding, currentDocs.sykmelding),
                    it.sykmeldinger!!
                        .map { sm ->
                            sm.sykmeldingId
                        }.toSet(),
                )
                assertTrue(it.sykmeldinger.all { sm -> sm.pasient.fnr == current })
            }
            assertEquals(
                listOf(org),
                virksomhetService.getVirksomheter(leader).map { it.orgnummer },
            )
            clearMocks(identer, answers = false)
            val marked = db.markAllSykmeldingAndSoknadAsRead(leader)
            coVerify(exactly = 1) { identer.hentIdenter(listOf(current)) }
            assertEquals(
                setOf(oldDocs.sykmelding, currentDocs.sykmelding),
                marked.sykmeldingIds.toSet(),
            )
            assertEquals(setOf(oldDocs.soknad, currentDocs.soknad), marked.soknadIds.toSet())
            assertMarked(oldDocs, true)
            assertMarked(currentDocs, true)
        }

        test(
            "two persons never share scopes even with reversed batch results and cross-org decoys",
        ) {
            val firstRelation = relation()
            val secondRelation = relation(other, orgnummer = otherOrg)
            val first = documents(old)
            val second = documents(otherOld, otherOrg)
            val decoys = listOf(documents(otherOld), documents(old, otherOrg))
            val previews = service.getMineSykmeldte(leader).associateBy { it.narmestelederId }
            coVerify(exactly = 1) {
                identer.hentIdenter(
                    match {
                        it.toSet() == setOf(current, other) &&
                            it.size == 2
                    },
                )
            }
            listOf(firstRelation to first, secondRelation to second).forEach { (id, docs) ->
                val preview = previews.getValue(id)
                assertEquals(listOf(docs.sykmelding), preview.sykmeldinger.map { it.id })
                assertEquals(listOf(docs.soknad), preview.previewSoknader.map { it.id })
                assertEquals(listOf(docs.plan), preview.oppfolgingsplaner.map { it.hendelseId })
                assertEquals(listOf(docs.dialog), preview.dialogmoter.map { it.hendelseId })
                assertEquals(
                    listOf(
                        docs.sykmelding,
                    ),
                    v2.getSykmeldt(id, leader)!!.sykmeldinger!!.map {
                        it.sykmeldingId
                    },
                )
            }
            assertEquals(2, v2.getDineSykmeldte(leader).size)
            assertEquals(
                setOf(org, otherOrg),
                virksomhetDb
                    .getVirksomheter(leader)
                    .map {
                        it.orgnummer
                    }.toSet(),
            )
            val marked = db.markAllSykmeldingAndSoknadAsRead(leader)
            assertEquals(setOf(first.sykmelding, second.sykmelding), marked.sykmeldingIds.toSet())
            assertEquals(setOf(first.soknad, second.soknad), marked.soknadIds.toSet())
            decoys.forEach { assertMarked(it, false) }
        }

        test("two persons in the same org remain separate in lists and hendelser") {
            val firstRelation = relation()
            val secondRelation = relation(other)
            val first = documents(old)
            val second = documents(otherOld)
            val previews = service.getMineSykmeldte(leader).associateBy { it.narmestelederId }
            listOf(firstRelation to first, secondRelation to second).forEach { (id, docs) ->
                assertEquals(
                    listOf(docs.sykmelding),
                    previews.getValue(id).sykmeldinger.map { it.id },
                )
                assertEquals(
                    listOf(docs.soknad),
                    previews.getValue(id).previewSoknader.map { it.id },
                )
                assertEquals(
                    listOf(docs.plan),
                    previews.getValue(id).oppfolgingsplaner.map { it.hendelseId },
                )
                assertEquals(
                    listOf(docs.dialog),
                    previews.getValue(id).dialogmoter.map { it.hendelseId },
                )
                assertEquals(
                    listOf(
                        docs.sykmelding,
                    ),
                    v2.getSykmeldt(id, leader)!!.sykmeldinger!!.map {
                        it.sykmeldingId
                    },
                )
            }
            assertEquals(2, v2.getDineSykmeldte(leader).size)
            assertEquals(listOf(org), virksomhetDb.getVirksomheter(leader).map { it.orgnummer })
            val marked = db.markAllSykmeldingAndSoknadAsRead(leader)
            assertEquals(setOf(first.sykmelding, second.sykmelding), marked.sykmeldingIds.toSet())
            assertEquals(setOf(first.soknad, second.soknad), marked.soknadIds.toSet())
            assertMarked(first, true)
            assertMarked(second, true)
        }

        test("former and current leaders retain only exact access and denied widening is counted") {
            relation()
            relation(old, former)
            val oldDocs = documents(old, name = "Old exact row")
            val currentDocs =
                documents(
                    current,
                    latestTom = LocalDate.now().plusMonths(1),
                    name = "Current exact row",
                )
            val before = RELATION_SCOPE_DENIED_COUNTER.get()
            listOf(leader to currentDocs, former to oldDocs).forEach { (caller, docs) ->
                val preview = service.getMineSykmeldte(caller).single()
                assertEquals(listOf(docs.sykmelding), preview.sykmeldinger.map { it.id })
                assertEquals(listOf(docs.soknad), preview.previewSoknader.map { it.id })
                assertEquals(listOf(docs.plan), preview.oppfolgingsplaner.map { it.hendelseId })
                assertEquals(
                    listOf(
                        docs.sykmelding,
                    ),
                    v2.getDineSykmeldte(caller).single().sykmeldinger!!.map {
                        it.sykmeldingId
                    },
                )
                assertEquals(listOf(org), virksomhetDb.getVirksomheter(caller).map { it.orgnummer })
            }
            assertEquals("Current exact row", service.getMineSykmeldte(leader).single().navn)
            assertEquals("Old exact row", service.getMineSykmeldte(former).single().navn)
            assertTrue(RELATION_SCOPE_DENIED_COUNTER.get() > before)
            db.markAllSykmeldingAndSoknadAsRead(leader)
            assertMarked(currentDocs, true)
            assertMarked(oldDocs, false)
        }

        test("PDL fallback preserves exact list, v2, hendelse, virksomhet and markAll access") {
            coEvery { identer.hentIdenter(any()) } coAnswers {
                firstArg<Collection<String>>().associateWith { setOf(it) }
            }
            val relationId = relation()
            val oldDocs = documents(old)
            assertTrue(service.getMineSykmeldte(leader).isEmpty())
            assertTrue(db.getHendelser(leader).isEmpty())
            assertTrue(v2.getDineSykmeldte(leader).isEmpty())
            assertNull(v2.getSykmeldt(relationId, leader))
            assertTrue(virksomhetDb.getVirksomheter(leader).isEmpty())
            assertTrue(db.markAllSykmeldingAndSoknadAsRead(leader).sykmeldingIds.isEmpty())
            assertMarked(oldDocs, false)
            val exactDocs = documents(current)
            assertEquals(
                listOf(
                    exactDocs.sykmelding,
                ),
                service.getMineSykmeldte(leader).single().sykmeldinger.map {
                    it.id
                },
            )
            assertEquals(
                listOf(
                    exactDocs.sykmelding,
                ),
                v2.getSykmeldt(relationId, leader)!!.sykmeldinger!!.map {
                    it.sykmeldingId
                },
            )
            assertEquals(listOf(org), virksomhetDb.getVirksomheter(leader).map { it.orgnummer })
            db.markAllSykmeldingAndSoknadAsRead(leader)
            assertMarked(exactDocs, true)
            assertMarked(oldDocs, false)
        }

        test("same person in two orgs has relation-specific plans, dialogmoter and soknad events") {
            val firstRelation = relation()
            val secondRelation = relation(orgnummer = otherOrg)
            val first = documents(old)
            val second = documents(old, otherOrg)
            // Same application id in another org must not attach its event to the first relation.
            database.connection.use { connection ->
                connection.prepareStatement("DELETE FROM hendelser WHERE hendelse_id = ?").use {
                    it.setObject(1, first.soknadEvent)
                    it.executeUpdate()
                }
                connection.commit()
            }
            event(old, otherOrg, first.soknad, "IKKE_SENDT_SOKNAD")
            val previews = service.getMineSykmeldte(leader).associateBy { it.narmestelederId }
            coVerify(exactly = 1) { identer.hentIdenter(listOf(current)) }
            listOf(firstRelation to first, secondRelation to second).forEach { (id, docs) ->
                assertEquals(
                    listOf(docs.plan),
                    previews.getValue(id).oppfolgingsplaner.map { it.hendelseId },
                )
                assertEquals(
                    listOf(docs.dialog),
                    previews.getValue(id).dialogmoter.map { it.hendelseId },
                )
            }
            assertFalse(
                (
                    previews
                        .getValue(
                            firstRelation,
                        ).previewSoknader
                        .single() as PreviewNySoknad
                ).ikkeSendtSoknadVarsel,
            )
            assertTrue(
                (
                    previews
                        .getValue(
                            secondRelation,
                        ).previewSoknader
                        .single() as PreviewNySoknad
                ).ikkeSendtSoknadVarsel,
            )
        }

        test("markAll excludes unrelated orgs and persons") {
            relation()
            val readable = documents(old)
            val excluded =
                listOf(documents(old, otherOrg), documents(current, otherOrg), documents(other))
            val marked = db.markAllSykmeldingAndSoknadAsRead(leader)
            assertEquals(listOf(readable.sykmelding), marked.sykmeldingIds)
            assertEquals(listOf(readable.soknad), marked.soknadIds)
            assertMarked(readable, true)
            excluded.forEach { assertMarked(it, false) }
        }

        test("lists and hendelser require a sykmeldt row within scope") {
            relation()
            documents(old)
            database.connection.use { connection ->
                connection.prepareStatement("DELETE FROM sykmeldt").use { it.executeUpdate() }
                connection.commit()
            }
            assertTrue(service.getMineSykmeldte(leader).isEmpty())
            assertTrue(v2.getDineSykmeldte(leader).isEmpty())
            assertTrue(db.getHendelser(leader).isEmpty())
        }

        test("widened hendelser exclude completed and expired events while keeping future expiry") {
            val relationId = relation()
            val docs = documents(old)
            event(old, org, ferdigstilt = true)
            event(old, org, utlopstidspunkt = OffsetDateTime.now().minusDays(1))
            val future = event(current, org, utlopstidspunkt = OffsetDateTime.now().plusDays(1))
            val events = db.getHendelser(leader)
            assertTrue(events.all { it.narmestelederId == relationId })
            assertEquals(
                setOf(docs.plan, docs.dialog, docs.soknadEvent, future),
                events
                    .map {
                        it.hendelse.hendelseId
                    }.toSet(),
            )
            assertEquals(
                setOf(
                    docs.plan,
                    future,
                ),
                service
                    .getMineSykmeldte(leader)
                    .single()
                    .oppfolgingsplaner
                    .map {
                        it.hendelseId
                    }.toSet(),
            )
        }

        test("filtered v2 request never resolves another leader's or unknown relation") {
            val foreignRelation = relation(leder = former)
            documents(old)
            assertNull(v2.getSykmeldt(foreignRelation, leader))
            assertNull(v2.getSykmeldt(UUID.randomUUID().toString(), leader))
            coVerify(exactly = 0) { identer.hentIdenter(any()) }
        }

        test("no relations makes no PDL request and returns empty lists and updates") {
            documents(old)
            assertTrue(service.getMineSykmeldte(leader).isEmpty())
            assertTrue(v2.getDineSykmeldte(leader).isEmpty())
            assertTrue(virksomhetDb.getVirksomheter(leader).isEmpty())
            assertTrue(db.markAllSykmeldingAndSoknadAsRead(leader).soknadIds.isEmpty())
            coVerify(exactly = 0) { identer.hentIdenter(any()) }
        }

        listOf(
            "deleted",
            "different-leader",
            "different-org",
            "different-person",
            "second-relation",
            "mismatched-caller",
        ).flatMap { change ->
            listOf(true, false).map { widened -> change to widened }
        }.forEach { (change, widened) ->
            test("final list and markAll SQL recheck $change with widened=$widened") {
                val id = relation()
                val docs = documents(if (widened) old else current)
                if (!widened) {
                    coEvery { identer.hentIdenter(any()) } coAnswers {
                        firstArg<Collection<String>>().associateWith { setOf(it) }
                    }
                }
                val scopes = resolver.forLeder(leader)
                val staleResolver = mockk<RelationScopeResolver>()
                coEvery { staleResolver.forLeder(any(), any()) } returns scopes
                val staleDb = MineSykmeldteDb(database, staleResolver)
                database.connection.use { connection ->
                    val sql =
                        when (change) {
                            "deleted" -> "DELETE FROM narmesteleder WHERE narmeste_leder_id = ?"
                            "different-leader" ->
                                "UPDATE narmesteleder SET leder_fnr = '12345678914' " +
                                    "WHERE narmeste_leder_id = ?"
                            "different-org" ->
                                "UPDATE narmesteleder SET orgnummer = 'changed-org' " +
                                    "WHERE narmeste_leder_id = ?"
                            "different-person" ->
                                "UPDATE narmesteleder SET pasient_fnr = '12345678915' " +
                                    "WHERE narmeste_leder_id = ?"
                            else -> null
                        }
                    if (sql != null) {
                        connection.prepareStatement(sql).use {
                            it.setString(1, id)
                            it.executeUpdate()
                        }
                    }
                    connection.commit()
                }
                if (change == "second-relation") relation(old, former)
                val caller = if (change == "mismatched-caller") former else leader
                if (change == "second-relation" && !widened) {
                    // An exact scope remains readable: D2 only constrains widening.
                    assertEquals(
                        listOf(docs.sykmelding),
                        staleDb.getMineSykmeldte(caller).map { it.sykmeldingId },
                    )
                    val marked = staleDb.markAllSykmeldingAndSoknadAsRead(caller)
                    assertEquals(listOf(docs.sykmelding), marked.sykmeldingIds)
                    assertEquals(listOf(docs.soknad), marked.soknadIds)
                    assertMarked(docs, true)
                    return@test
                }
                assertTrue(MineSykmeldteService(staleDb).getMineSykmeldte(caller).isEmpty())
                assertTrue(DineSykmeldteService(staleDb).getDineSykmeldte(caller).isEmpty())
                assertNull(DineSykmeldteService(staleDb).getSykmeldt(id, caller))
                assertTrue(staleDb.getHendelser(caller).isEmpty())
                assertTrue(VirksomhetDb(database, staleResolver).getVirksomheter(caller).isEmpty())
                val marked = staleDb.markAllSykmeldingAndSoknadAsRead(caller)
                assertTrue(marked.sykmeldingIds.isEmpty())
                assertTrue(marked.soknadIds.isEmpty())
                assertMarked(docs, false)
            }
        }

        test("a stale widened relation does not suppress or broaden another person's scope") {
            relation()
            val otherRelation = relation(other)
            val denied = documents(old)
            val allowed = documents(otherOld)
            val scopes = resolver.forLeder(leader)
            val staleResolver = mockk<RelationScopeResolver>()
            coEvery { staleResolver.forLeder(any(), any()) } returns scopes
            val staleDb = MineSykmeldteDb(database, staleResolver)
            relation(old, former)

            assertEquals(
                listOf(
                    otherRelation,
                ),
                MineSykmeldteService(staleDb).getMineSykmeldte(leader).map {
                    it.narmestelederId
                },
            )
            assertEquals(
                listOf(
                    otherRelation,
                ),
                DineSykmeldteService(staleDb).getDineSykmeldte(leader).map {
                    it.narmestelederId
                },
            )
            assertEquals(
                setOf(
                    allowed.plan,
                    allowed.dialog,
                    allowed.soknadEvent,
                ),
                staleDb
                    .getHendelser(leader)
                    .map {
                        it.hendelse.hendelseId
                    }.toSet(),
            )
            assertEquals(
                listOf(
                    org,
                ),
                VirksomhetDb(database, staleResolver).getVirksomheter(leader).map {
                    it.orgnummer
                },
            )
            val marked = staleDb.markAllSykmeldingAndSoknadAsRead(leader)
            assertEquals(listOf(allowed.sykmelding), marked.sykmeldingIds)
            assertEquals(listOf(allowed.soknad), marked.soknadIds)
            assertMarked(allowed, true)
            assertMarked(denied, false)
        }

        test("fallback alert metrics expose exactly one total suffix") {
            IDENTER_LOOKUP_COUNTER.labels("fallback").inc(0.0)
            IDENTER_FALLBACK_COUNTER.labels("timeout").inc(0.0)
            assertTrue(
                IDENTER_LOOKUP_COUNTER.collect().flatMap { it.samples }.any {
                    it.name ==
                        "dinesykmeldte_backend_identer_lookup_total"
                },
            )
            assertTrue(
                IDENTER_FALLBACK_COUNTER.collect().flatMap { it.samples }.any {
                    it.name ==
                        "dinesykmeldte_backend_identer_fallback_total"
                },
            )
        }
    })

private class ListDocuments(
    val sykmelding: String,
    val soknad: String,
    val plan: UUID,
    val dialog: UUID,
    val soknadEvent: UUID,
)
