package no.nav.syfo.sykmelding.model.sykmeldingstatus

import no.nav.syfo.sykmelding.model.sykmelding.model.TidligereArbeidsgiverDTO
import java.time.OffsetDateTime

data class SykmeldingStatusKafkaEventDTO(
    val sykmeldingId: String,
    val timestamp: OffsetDateTime,
    val statusEvent: String,
    val arbeidsgiver: ArbeidsgiverStatusDTO? = null,
    val sporsmals: List<SporsmalOgSvarDTO>? = null,
    val erSvarOppdatering: Boolean? = null,
    val tidligereArbeidsgiver: TidligereArbeidsgiverDTO? = null,
)
