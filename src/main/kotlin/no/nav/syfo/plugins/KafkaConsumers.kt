package no.nav.syfo.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import no.nav.syfo.common.delete.DeleteDataService
import no.nav.syfo.common.kafka.CommonKafkaService
import no.nav.syfo.synchendelse.SyncHendelseConsumer

fun Application.configureRunningTasks(
    commonKafkaService: CommonKafkaService,
    deleteDataService: DeleteDataService,
    syncHendelseConsumer: SyncHendelseConsumer
) {
    val commonKafkaServiceJob = launch(Dispatchers.IO) { commonKafkaService.startConsumer() }
    val deleteServiceJob = launch(Dispatchers.IO) { deleteDataService.start() }
    val syncHendelseJob = launch(Dispatchers.IO) { syncHendelseConsumer.start() }
    monitor.subscribe(ApplicationStopping) {
        commonKafkaServiceJob.cancel()
        deleteServiceJob.cancel()
        syncHendelseJob.cancel()
    }
}
