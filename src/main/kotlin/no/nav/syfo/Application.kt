package no.nav.syfo

import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import no.nav.syfo.plugins.configureContentNegotiation
import no.nav.syfo.plugins.configureDependencies
import no.nav.syfo.plugins.configureLifecycleHooks
import no.nav.syfo.plugins.configureNaisResources
import no.nav.syfo.plugins.configurePrometheus
import no.nav.syfo.plugins.configureRouting
import no.nav.syfo.plugins.configureRunningTasks
import no.nav.syfo.plugins.setupAuth
import org.koin.ktor.ext.get

fun main() {
    embeddedServer(Netty, port = 8080, module = Application::module).start(true)
}

fun Application.module() {
    configureDependencies()
    configurePrometheus()
    configureContentNegotiation()
    setupAuth(get())
    configureNaisResources(get())
    configureLifecycleHooks(get())
    configureRouting()
    configureRunningTasks(get(), get())
}
