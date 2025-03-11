package no.nav.syfo.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import no.nav.syfo.common.delete.DeleteDataService
import no.nav.syfo.common.kafka.CommonKafkaService

fun Application.configureRunningTasks(
    commonKafkaService: CommonKafkaService,
    deleteDataService: DeleteDataService,
) {
    val commonKafkaServiceJob = launch(Dispatchers.IO) { commonKafkaService.startConsumer() }
    val deleteServiceJob = launch(Dispatchers.IO) { deleteDataService.start() }
    monitor.subscribe(ApplicationStopping) {
        commonKafkaServiceJob.cancel()
        deleteServiceJob.cancel()
    }
}
