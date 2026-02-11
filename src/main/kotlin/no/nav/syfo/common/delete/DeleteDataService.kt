package no.nav.syfo.common.delete

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import no.nav.syfo.application.leaderelection.LeaderElection
import no.nav.syfo.application.metrics.SLETTET_COUNTER
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class DeleteDataService(
    private val database: DeleteDataDb,
    private val leaderElection: LeaderElection,
) {
    companion object {
        val log: Logger = LoggerFactory.getLogger(DeleteDataService::class.java)
        private const val DELAY_HOURS = 12
        private const val MONTHS_FOR_SYKMELDING = 4L
    }

    suspend fun start() =
        coroutineScope {
            try {
                while (isActive) {
                    delay(5.minutes)
                    if (leaderElection.isLeader()) {
                        try {
                            val result = database.deleteOldData(getDateForDeletion())
                            log.info(
                                "Deleted ${result.deletedSykmelding} sykmeldinger, ${result.deletedSykmeldt} sykmeldte, ${result.deletedSoknader} soknader and ${result.deletedHendelser} hendelser",
                            )
                            SLETTET_COUNTER
                                .labels("sykmelding")
                                .inc(result.deletedSykmelding.toDouble())
                            SLETTET_COUNTER
                                .labels(
                                    "sykmeldt",
                                ).inc(result.deletedSykmeldt.toDouble())
                            SLETTET_COUNTER.labels("soknad").inc(result.deletedSoknader.toDouble())
                            SLETTET_COUNTER
                                .labels(
                                    "hendelse",
                                ).inc(result.deletedHendelser.toDouble())
                        } catch (ex: Exception) {
                            log.error("Could not delete data", ex)
                        }
                    }
                    delay(DELAY_HOURS.hours)
                }
            } catch (ex: CancellationException) {
                log.info("cancelled delete data job")
            }
        }

    private fun getDateForDeletion() = LocalDate.now().minusMonths(MONTHS_FOR_SYKMELDING)
}
