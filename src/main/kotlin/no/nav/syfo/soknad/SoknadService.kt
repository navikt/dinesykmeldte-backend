package no.nav.syfo.soknad

import com.fasterxml.jackson.databind.exc.InvalidFormatException
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.application.metrics.SOKNAD_TOPIC_COUNTER
import no.nav.syfo.soknad.db.SoknadDb
import no.nav.syfo.soknad.kafka.model.FlexSoknad
import no.nav.syfo.soknad.kafka.model.FlexSoknadStatus
import no.nav.syfo.util.logger
import no.nav.syfo.util.objectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import java.time.LocalDate

class SoknadService(
    private val soknadDb: SoknadDb,
    private val cluster: String,
) {
    private val log = logger()

    suspend fun handleSykepengesoknad(record: ConsumerRecord<String, String>) {
        try {
            handleSykepengesoknad(objectMapper.readValue<FlexSoknad>(record.value()))
        } catch (exception: InvalidFormatException) {
            if (cluster != "dev-gcp") {
                log.error("Noe gikk galt ved mottak av sykepengesøknad med id ${record.key()}")
                throw exception
            } else {
                log.info(
                    "Ignoring sykepengesøknad when error InvalidFormatException med id: ${record.key()}",
                )
            }
        } catch (exception: Exception) {
            log.error("Noe gikk galt ved mottak av sykepengesøknad med id ${record.key()}")
            throw exception
        }
    }

    suspend fun handleSykepengesoknad(sykepengesoknad: FlexSoknad) {
        when (sykepengesoknad.status) {
            FlexSoknadStatus.NY, FlexSoknadStatus.FREMTIDIG -> {
                insertIfRelevant(sykepengesoknad)
            }

            FlexSoknadStatus.SENDT, FlexSoknadStatus.KORRIGERT -> {
                handleSendt(sykepengesoknad)
            }

            FlexSoknadStatus.AVBRUTT, FlexSoknadStatus.SLETTET, FlexSoknadStatus.UTGAATT -> {
                soknadDb.deleteSoknad(sykepengesoknad.id)
            }
        }
        SOKNAD_TOPIC_COUNTER.inc()
    }

    private fun insertIfRelevant(sykepengesoknad: FlexSoknad) {
        if (isRelevant(sykepengesoknad)) {
            soknadDb.insertOrUpdate(sykepengesoknad.toSoknadDbModel())
        }
    }

    private fun handleSendt(sykepengesoknad: FlexSoknad) {
        if (sykepengesoknad.sendtArbeidsgiver != null) {
            insertIfRelevant(sykepengesoknad)
        } else {
            soknadDb.deleteSoknad(sykepengesoknad.id)
        }
    }

    private fun hasArbeidsgiver(sykepengesoknad: FlexSoknad): Boolean =
        sykepengesoknad.arbeidsgiver?.orgnummer != null

    private fun isRelevant(sykepengesoknad: FlexSoknad) =
        hasArbeidsgiver(sykepengesoknad) &&
            sykepengesoknad.tom?.isAfter(LocalDate.now().minusMonths(4)) == true
}
