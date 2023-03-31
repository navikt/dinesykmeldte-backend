package no.nav.syfo.util

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.sykepengesoknad.kafka.ArbeidsgiverDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.syfo.getFileAsString
import no.nav.syfo.model.sykmelding.arbeidsgiver.AktivitetIkkeMuligAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.ArbeidsgiverAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.ArbeidsgiverSykmelding
import no.nav.syfo.model.sykmelding.arbeidsgiver.BehandlerAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.KontaktMedPasientAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.model.AdresseDTO
import no.nav.syfo.model.sykmelding.model.GradertDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.objectMapper
import no.nav.syfo.soknad.db.SoknadDbModel
import no.nav.syfo.soknad.toSoknadDbModel
import no.nav.syfo.sykmelding.db.SykmeldingDbModel
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

fun createSykmeldingDbModel(
    sykmeldingId: String,
    pasientFnr: String = "12345678910",
    orgnummer: String = "orgnummer",
): SykmeldingDbModel {
    return SykmeldingDbModel(
        sykmeldingId = sykmeldingId,
        pasientFnr = pasientFnr,
        orgnummer = orgnummer,
        orgnavn = "Navn AS",
        sykmelding = createArbeidsgiverSykmelding(sykmeldingId = sykmeldingId),
        lest = false,
        timestamp = OffsetDateTime.now(ZoneOffset.UTC),
        latestTom = LocalDate.now().minusWeeks(2),
        sendtTilArbeidsgiverDato = OffsetDateTime.now(ZoneOffset.UTC),
        egenmeldingsdager = emptyList(),
    )
}

fun createSoknadDbModel(
    soknadId: String,
    sykmeldingId: String = "76483e9f-eb16-464c-9bed-a9b258794bc4",
    pasientFnr: String = "123456789",
    arbeidsgivernavn: String = "Kebabbiten",
    orgnummer: String = "123454543",
): SoknadDbModel {
    val sykepengesoknadDTO: SykepengesoknadDTO = objectMapper.readValue<SykepengesoknadDTO>(
        getFileAsString("src/test/resources/soknad.json"),
    ).copy(
        id = soknadId,
        sykmeldingId = sykmeldingId,
        fnr = pasientFnr,
        arbeidsgiver = ArbeidsgiverDTO(
            navn = arbeidsgivernavn,
            orgnummer = orgnummer,
        ),
    )
    return sykepengesoknadDTO.toSoknadDbModel()
}

fun createArbeidsgiverSykmelding(
    sykmeldingId: String,
    perioder: List<SykmeldingsperiodeAGDTO> = listOf(createSykmeldingsperiode()),
) = ArbeidsgiverSykmelding(
    id = sykmeldingId,
    mottattTidspunkt = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1),
    syketilfelleStartDato = null,
    behandletTidspunkt = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1),
    arbeidsgiver = ArbeidsgiverAGDTO(null, null),
    sykmeldingsperioder = perioder,
    prognose = null,
    tiltakArbeidsplassen = null,
    meldingTilArbeidsgiver = null,
    kontaktMedPasient = KontaktMedPasientAGDTO(null),
    behandler = BehandlerAGDTO("Fornavn", null, "Etternavn", null, AdresseDTO(null, null, null, null, null), null),
    egenmeldt = false,
    papirsykmelding = false,
    harRedusertArbeidsgiverperiode = false,
    merknader = null,
    utenlandskSykmelding = null,
)

fun createSykmeldingsperiode(
    fom: LocalDate = LocalDate.now().minusDays(2),
    tom: LocalDate = LocalDate.now().plusDays(10),
    gradert: GradertDTO? = null,
    behandlingsdager: Int? = null,
    innspillTilArbeidsgiver: String? = null,
    type: PeriodetypeDTO = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
    aktivitetIkkeMulig: AktivitetIkkeMuligAGDTO? = AktivitetIkkeMuligAGDTO(null),
    reisetilskudd: Boolean = false,
) = SykmeldingsperiodeAGDTO(
    fom = fom,
    tom = tom,
    gradert = gradert,
    behandlingsdager = behandlingsdager,
    innspillTilArbeidsgiver = innspillTilArbeidsgiver,
    type = type,
    aktivitetIkkeMulig = aktivitetIkkeMulig,
    reisetilskudd = reisetilskudd,
)
