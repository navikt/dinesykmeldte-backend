package no.nav.syfo.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopped
import no.nav.syfo.application.ApplicationState

fun Application.configureLifecycleHooks(applicationState: ApplicationState = ApplicationState()) {
    monitor.subscribe(ApplicationStarted) { applicationState.ready = true }
    monitor.subscribe(ApplicationStopped) { applicationState.ready = false }
}
