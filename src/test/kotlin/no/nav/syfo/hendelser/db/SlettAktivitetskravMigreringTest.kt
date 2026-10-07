package no.nav.syfo.hendelser.db

import io.kotest.core.spec.style.FunSpec
import no.nav.syfo.application.database.toList
import no.nav.syfo.util.TestDb
import org.amshove.kluent.shouldBeEqualTo

class SlettAktivitetskravMigreringTest :
    FunSpec({
        test("V27 sletter AKTIVITETSKRAV-hendelser og beholder andre hendelser") {
            val migreringsDatabase = TestDb.opprettTomDatabase("migrering_v27")
            migreringsDatabase.migrerTil("26")

            migreringsDatabase.connection().use { connection ->
                connection
                    .prepareStatement(
                        """
                        INSERT INTO hendelser(id, pasient_fnr, orgnummer, oppgavetype, timestamp, ferdigstilt)
                        VALUES ('1', '12345678910', 'orgnummer', 'AKTIVITETSKRAV', now(), false),
                               ('2', '12345678910', 'orgnummer', 'AKTIVITETSKRAV', now(), true),
                               ('3', '12345678910', 'orgnummer', 'DIALOGMOTE_INNKALLING', now(), false),
                               ('4', '12345678910', 'orgnummer', 'IKKE_SENDT_SOKNAD', now(), false),
                               ('5', '12345678910', 'orgnummer', 'OPPFOLGINGSPLAN_OPPRETTET', now(), true);
                        """,
                    ).use { it.executeUpdate() }
            }

            migreringsDatabase.migrerTil("27")

            val gjenvaerende =
                migreringsDatabase.connection().use { connection ->
                    connection
                        .prepareStatement("SELECT id, oppgavetype FROM hendelser ORDER BY id")
                        .use { ps ->
                            ps.executeQuery().toList {
                                getString("id") to getString("oppgavetype")
                            }
                        }
                }

            gjenvaerende shouldBeEqualTo
                listOf(
                    "3" to "DIALOGMOTE_INNKALLING",
                    "4" to "IKKE_SENDT_SOKNAD",
                    "5" to "OPPFOLGINGSPLAN_OPPRETTET",
                )
        }
    })
