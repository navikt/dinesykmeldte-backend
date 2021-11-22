package no.nav.syfo.common.delete

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.util.Unbounded
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime

class DeleteDataService(
    private val database: DeleteDataDb,
    private val applicationState: ApplicationState
) {

    companion object {
        val log: Logger = LoggerFactory.getLogger(DeleteDataService::class.java)
        private const val DELAY_HOURS = 24
        private const val MONTHS_FOR_SYKMELDING = 4L
    }

    @DelicateCoroutinesApi
    @ExperimentalTime
    fun start() {
        GlobalScope.launch(Dispatchers.Unbounded) {
            while (applicationState.ready) {
                try {
                    val result = database.deleteOldData(getDateForDeletion())
                    log.info("Deleted ${result.deletedSykmelding} sykmeldinger, ${result.deletedSykmeldt} sykmeldte, ${result.deletedSoknader} soknader and ${result.deletedHendelser} hendelser")
                } catch (ex: Exception) {
                    log.info("Could not delete data", ex)
                }
                delay(hours(DELAY_HOURS))
            }
        }
    }

    private fun getDateForDeletion() = LocalDate.now().minusMonths(MONTHS_FOR_SYKMELDING)
}
