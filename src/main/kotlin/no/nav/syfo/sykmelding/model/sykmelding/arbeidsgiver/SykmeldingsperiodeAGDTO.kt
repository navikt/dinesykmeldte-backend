package no.nav.syfo.sykmelding.model.sykmelding.arbeidsgiver

import java.time.LocalDate
import no.nav.syfo.sykmelding.model.sykmelding.model.GradertDTO
import no.nav.syfo.sykmelding.model.sykmelding.model.PeriodetypeDTO

data class SykmeldingsperiodeAGDTO(
    val fom: LocalDate,
    val tom: LocalDate,
    val gradert: GradertDTO?,
    val behandlingsdager: Int?,
    val innspillTilArbeidsgiver: String?,
    val type: PeriodetypeDTO,
    val aktivitetIkkeMulig: AktivitetIkkeMuligAGDTO?,
    val reisetilskudd: Boolean
)
