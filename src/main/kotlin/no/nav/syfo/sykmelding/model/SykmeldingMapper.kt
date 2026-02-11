package no.nav.syfo.sykmelding.model

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.sykmelding.db.SykmeldingDbModel
import no.nav.syfo.sykmelding.kafka.model.SendtSykmeldingKafkaMessage
import no.nav.syfo.sykmelding.model.sykmeldingstatus.ShortNameDTO
import no.nav.syfo.util.objectMapper
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

class SykmeldingMapper private constructor() {
    companion object {
        fun toSykmeldingDbModel(
            sykmelding: SendtSykmeldingKafkaMessage,
            sisteTom: LocalDate,
        ) = SykmeldingDbModel(
            sykmeldingId = sykmelding.kafkaMetadata.sykmeldingId,
            pasientFnr = sykmelding.kafkaMetadata.fnr,
            orgnummer = sykmelding.event.arbeidsgiver!!.orgnummer,
            orgnavn = sykmelding.event.arbeidsgiver.orgNavn,
            sykmelding = sykmelding.sykmelding,
            lest = false, // fra strangler
            timestamp = OffsetDateTime.now(ZoneOffset.UTC),
            latestTom = sisteTom,
            sendtTilArbeidsgiverDato = sykmelding.event.timestamp,
            egenmeldingsdager =
                sykmelding.event.sporsmals
                    ?.find { it.shortName == ShortNameDTO.EGENMELDINGSDAGER }
                    ?.svar
                    ?.let { objectMapper.readValue(it) },
        )
    }
}
