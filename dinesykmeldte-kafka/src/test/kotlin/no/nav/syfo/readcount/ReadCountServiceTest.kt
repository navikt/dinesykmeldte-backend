package no.nav.syfo.readcount

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearMocks
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.syfo.readcount.db.ReadCountDb
import no.nav.syfo.readcount.kafka.NLReadCountProducer
import no.nav.syfo.readcount.kafka.model.NLReadCount
import no.nav.syfo.readcount.model.HendelseType
import no.nav.syfo.util.TestDb
import no.nav.syfo.util.createHendelseDbModel
import no.nav.syfo.util.createSoknadDbModel
import no.nav.syfo.util.createSykmeldingDbModel
import no.nav.syfo.util.createSykmeldtDbModel
import no.nav.syfo.util.insertHendelse
import no.nav.syfo.util.insertOrUpdate
import no.nav.syfo.util.insertOrUpdateNl
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class ReadCountServiceTest : FunSpec({
    val readCountDb = ReadCountDb(TestDb.database)
    val nlReadCountProducer = mockk<NLReadCountProducer>(relaxed = true)
    val readCountService = ReadCountService(readCountDb, nlReadCountProducer)

    beforeEach {
        TestDb.clearAllData()
        clearMocks(nlReadCountProducer)
    }

    context("ReadCountService") {
        test("Happy case - sender oppdatert lest-status") {
            val narmestelederId = UUID.randomUUID().toString()
            val sykmeldingId = UUID.randomUUID().toString()
            TestDb.database.insertOrUpdateNl(narmestelederId, "888888", "12345678910", "65656565656")
            TestDb.database.insertOrUpdate(
                createSykmeldingDbModel(sykmeldingId = sykmeldingId, pasientFnr = "12345678910", orgnummer = "888888"),
                createSykmeldtDbModel(pasientFnr = "12345678910")
            )
            TestDb.database.insertOrUpdate(createSoknadDbModel(sykmeldingId = sykmeldingId, soknadId = UUID.randomUUID().toString(), pasientFnr = "12345678910", orgnummer = "888888"))
            TestDb.database.insertHendelse(createHendelseDbModel(id = UUID.randomUUID().toString(), pasientFnr = "12345678910", orgnummer = "888888", oppgavetype = HendelseType.OPPFOLGINGSPLAN_TIL_GODKJENNING.name))

            readCountService.updateReadCountKafkaTopic("12345678910", "888888")

            coVerify(exactly = 1) {
                nlReadCountProducer.send(
                    match {
                        it.nlReadCount == NLReadCount(
                            narmestelederId = narmestelederId,
                            unreadSykmeldinger = 1,
                            unreadSoknader = 1,
                            unreadMeldinger = 0,
                            unreadDialogmoter = 0,
                            unreadOppfolgingsplaner = 1
                        )
                    }
                )
            }
        }
        test("Kun ulest sykmelding") {
            val narmestelederId = UUID.randomUUID().toString()
            val sykmeldingId = UUID.randomUUID().toString()
            TestDb.database.insertOrUpdateNl(narmestelederId, "888888", "12345678910", "65656565656")
            TestDb.database.insertOrUpdate(
                createSykmeldingDbModel(sykmeldingId = sykmeldingId, pasientFnr = "12345678910", orgnummer = "888888"),
                createSykmeldtDbModel(pasientFnr = "12345678910")
            )
            TestDb.database.insertOrUpdate(
                createSoknadDbModel(sykmeldingId = sykmeldingId, soknadId = UUID.randomUUID().toString(), pasientFnr = "12345678910", orgnummer = "888888").copy(lest = true)
            )
            TestDb.database.insertHendelse(
                createHendelseDbModel(id = UUID.randomUUID().toString(), pasientFnr = "12345678910", orgnummer = "888888", oppgavetype = HendelseType.OPPFOLGINGSPLAN_TIL_GODKJENNING.name)
                    .copy(ferdigstilt = true, ferdigstiltTimestamp = OffsetDateTime.now(ZoneOffset.UTC))
            )

            readCountService.updateReadCountKafkaTopic("12345678910", "888888")

            coVerify(exactly = 1) {
                nlReadCountProducer.send(
                    match {
                        it.nlReadCount == NLReadCount(
                            narmestelederId = narmestelederId,
                            unreadSykmeldinger = 1,
                            unreadSoknader = 0,
                            unreadMeldinger = 0,
                            unreadDialogmoter = 0,
                            unreadOppfolgingsplaner = 0
                        )
                    }
                )
            }
        }
        test("Kun ulest søknad") {
            val narmestelederId = UUID.randomUUID().toString()
            val sykmeldingId = UUID.randomUUID().toString()
            TestDb.database.insertOrUpdateNl(narmestelederId, "888888", "12345678910", "65656565656")
            TestDb.database.insertOrUpdate(
                createSykmeldingDbModel(sykmeldingId = sykmeldingId, pasientFnr = "12345678910", orgnummer = "888888").copy(lest = true),
                createSykmeldtDbModel(pasientFnr = "12345678910")
            )
            TestDb.database.insertOrUpdate(
                createSoknadDbModel(sykmeldingId = sykmeldingId, soknadId = UUID.randomUUID().toString(), pasientFnr = "12345678910", orgnummer = "888888")
            )
            TestDb.database.insertHendelse(
                createHendelseDbModel(id = UUID.randomUUID().toString(), pasientFnr = "12345678910", orgnummer = "888888", oppgavetype = HendelseType.OPPFOLGINGSPLAN_TIL_GODKJENNING.name)
                    .copy(ferdigstilt = true, ferdigstiltTimestamp = OffsetDateTime.now(ZoneOffset.UTC))
            )

            readCountService.updateReadCountKafkaTopic("12345678910", "888888")

            coVerify(exactly = 1) {
                nlReadCountProducer.send(
                    match {
                        it.nlReadCount == NLReadCount(
                            narmestelederId = narmestelederId,
                            unreadSykmeldinger = 0,
                            unreadSoknader = 1,
                            unreadMeldinger = 0,
                            unreadDialogmoter = 0,
                            unreadOppfolgingsplaner = 0
                        )
                    }
                )
            }
        }
        test("Kun ulest dialogmøte-hendelse") {
            val narmestelederId = UUID.randomUUID().toString()
            val sykmeldingId = UUID.randomUUID().toString()
            TestDb.database.insertOrUpdateNl(narmestelederId, "888888", "12345678910", "65656565656")
            TestDb.database.insertOrUpdate(
                createSykmeldingDbModel(sykmeldingId = sykmeldingId, pasientFnr = "12345678910", orgnummer = "888888").copy(lest = true),
                createSykmeldtDbModel(pasientFnr = "12345678910")
            )
            TestDb.database.insertOrUpdate(
                createSoknadDbModel(sykmeldingId = sykmeldingId, soknadId = UUID.randomUUID().toString(), pasientFnr = "12345678910", orgnummer = "888888").copy(lest = true)
            )
            TestDb.database.insertHendelse(
                createHendelseDbModel(id = UUID.randomUUID().toString(), pasientFnr = "12345678910", orgnummer = "888888", oppgavetype = HendelseType.DIALOGMOTE_INNKALLING.name)
            )

            readCountService.updateReadCountKafkaTopic("12345678910", "888888")

            coVerify(exactly = 1) {
                nlReadCountProducer.send(
                    match {
                        it.nlReadCount == NLReadCount(
                            narmestelederId = narmestelederId,
                            unreadSykmeldinger = 0,
                            unreadSoknader = 0,
                            unreadMeldinger = 0,
                            unreadDialogmoter = 1,
                            unreadOppfolgingsplaner = 0
                        )
                    }
                )
            }
        }
        test("Kun ulest aktivitetskrav-hendelse") {
            val narmestelederId = UUID.randomUUID().toString()
            val sykmeldingId = UUID.randomUUID().toString()
            TestDb.database.insertOrUpdateNl(narmestelederId, "888888", "12345678910", "65656565656")
            TestDb.database.insertOrUpdate(
                createSykmeldingDbModel(sykmeldingId = sykmeldingId, pasientFnr = "12345678910", orgnummer = "888888").copy(lest = true),
                createSykmeldtDbModel(pasientFnr = "12345678910")
            )
            TestDb.database.insertOrUpdate(
                createSoknadDbModel(sykmeldingId = sykmeldingId, soknadId = UUID.randomUUID().toString(), pasientFnr = "12345678910", orgnummer = "888888").copy(lest = true)
            )
            TestDb.database.insertHendelse(
                createHendelseDbModel(id = UUID.randomUUID().toString(), pasientFnr = "12345678910", orgnummer = "888888", oppgavetype = HendelseType.AKTIVITETSKRAV.name)
            )

            readCountService.updateReadCountKafkaTopic("12345678910", "888888")

            coVerify(exactly = 1) {
                nlReadCountProducer.send(
                    match {
                        it.nlReadCount == NLReadCount(
                            narmestelederId = narmestelederId,
                            unreadSykmeldinger = 0,
                            unreadSoknader = 0,
                            unreadMeldinger = 1,
                            unreadDialogmoter = 0,
                            unreadOppfolgingsplaner = 0
                        )
                    }
                )
            }
        }
        test("Sender ikke melding hvis vi ikke finner nærmeste leder") {
            readCountService.updateReadCountKafkaTopic("15151515151", "1234")

            coVerify(exactly = 0) { nlReadCountProducer.send(any()) }
        }
        test("Sender ikke melding hvis vi ikke finner den sykmeldte i listen over leders sykmeldte ansatte") {
            TestDb.database.insertOrUpdateNl(UUID.randomUUID().toString(), "1234", "15151515151", "65656565656")

            readCountService.updateReadCountKafkaTopic("15151515151", "1234")

            coVerify(exactly = 0) { nlReadCountProducer.send(any()) }
        }
    }
})
