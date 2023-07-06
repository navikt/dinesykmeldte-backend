package no.nav.syfo.util

import com.fasterxml.jackson.module.kotlin.readValue
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import no.nav.helse.flex.sykepengesoknad.kafka.ArbeidsgiverDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.AktivitetIkkeMuligAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.ArbeidsgiverAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.ArbeidsgiverSykmelding
import no.nav.syfo.model.sykmelding.arbeidsgiver.BehandlerAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.KontaktMedPasientAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.UtenlandskSykmeldingAGDTO
import no.nav.syfo.model.sykmelding.model.AdresseDTO
import no.nav.syfo.model.sykmelding.model.GradertDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.objectMapper
import no.nav.syfo.soknad.db.SoknadDbModel
import no.nav.syfo.sykmelding.db.SykmeldingDbModel
import no.nav.syfo.sykmelding.db.SykmeldtDbModel
import no.nav.syfo.testutils.getFileAsString

fun createSykmeldingDbModel(
    sykmeldingId: String,
    pasientFnr: String = "12345678910",
    orgnummer: String = "orgnummer",
    orgnavn: String = "Navn AS",
    sendtTilArbeidsgiverDato: OffsetDateTime? = OffsetDateTime.now(ZoneOffset.UTC),
    land: String? = null,
): SykmeldingDbModel {
    return SykmeldingDbModel(
        sykmeldingId = sykmeldingId,
        pasientFnr = pasientFnr,
        orgnummer = orgnummer,
        orgnavn = orgnavn,
        sykmelding = createArbeidsgiverSykmelding(sykmeldingId = sykmeldingId, land = land),
        lest = false,
        timestamp = OffsetDateTime.now(ZoneOffset.UTC),
        latestTom = LocalDate.now().minusWeeks(2),
        sendtTilArbeidsgiverDato = sendtTilArbeidsgiverDato,
        egenmeldingsdager = null,
    )
}

fun createSykmeldtDbModel(pasientFnr: String = "12345678910"): SykmeldtDbModel {
    return SykmeldtDbModel(
        pasientFnr = pasientFnr,
        pasientNavn = "Navn Navnesen",
        startdatoSykefravaer = LocalDate.now().minusMonths(2),
        latestTom = LocalDate.now().minusWeeks(2),
    )
}

fun createSoknadDbModel(
    soknadId: String,
    sykmeldingId: String = "76483e9f-eb16-464c-9bed-a9b258794bc4",
    pasientFnr: String = "123456789",
    arbeidsgivernavn: String = "Kebabbiten",
    orgnummer: String = "123454543",
): SoknadDbModel {
    val sykepengesoknadDTO: SykepengesoknadDTO =
        objectMapper
            .readValue<SykepengesoknadDTO>(
                getFileAsString("src/test/resources/soknad.json"),
            )
            .copy(
                id = soknadId,
                sykmeldingId = sykmeldingId,
                fnr = pasientFnr,
                arbeidsgiver =
                    ArbeidsgiverDTO(
                        navn = arbeidsgivernavn,
                        orgnummer = orgnummer,
                    ),
            )
    return sykepengesoknadDTO.toSoknadDbModel()
}

fun createSykepengesoknadDto(
    soknadId: String,
    sykmeldingId: String,
) =
    objectMapper
        .readValue<SykepengesoknadDTO>(
            getFileAsString("src/test/resources/soknad.json"),
        )
        .copy(
            id = soknadId,
            fom = LocalDate.now().minusMonths(1),
            tom = LocalDate.now().minusWeeks(2),
            sendtArbeidsgiver = LocalDateTime.now().minusWeeks(1),
            sykmeldingId = sykmeldingId,
        )

fun createArbeidsgiverSykmelding(
    sykmeldingId: String,
    perioder: List<SykmeldingsperiodeAGDTO> = listOf(createSykmeldingsperiode()),
    land: String? = null,
) =
    ArbeidsgiverSykmelding(
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
        behandler =
            if (land == null) {
                BehandlerAGDTO(
                    "Fornavn",
                    null,
                    "Etternavn",
                    null,
                    AdresseDTO(null, null, null, null, null),
                    null
                )
            } else {
                null
            },
        egenmeldt = false,
        papirsykmelding = false,
        harRedusertArbeidsgiverperiode = false,
        merknader = null,
        utenlandskSykmelding = land?.let { UtenlandskSykmeldingAGDTO(it) },
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
) =
    SykmeldingsperiodeAGDTO(
        fom = fom,
        tom = tom,
        gradert = gradert,
        behandlingsdager = behandlingsdager,
        innspillTilArbeidsgiver = innspillTilArbeidsgiver,
        type = type,
        aktivitetIkkeMulig = aktivitetIkkeMulig,
        reisetilskudd = reisetilskudd,
    )


fun SykepengesoknadDTO.toSoknadDbModel(): SoknadDbModel {
    return SoknadDbModel(
        soknadId = id,
        sykmeldingId = sykmeldingId,
        pasientFnr = fnr,
        orgnummer = arbeidsgiver?.orgnummer
            ?: throw IllegalStateException("Har mottatt sendt søknad uten orgnummer: $id"),
        soknad = this,
        sendtDato = sendtArbeidsgiver?.toLocalDate(),
        lest = false, // oppdateres fra strangler
        timestamp = OffsetDateTime.now(ZoneOffset.UTC),
        tom = tom!!,
    )
}
