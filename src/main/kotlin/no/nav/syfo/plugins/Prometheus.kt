package no.nav.syfo.plugins

import io.prometheus.client.hotspot.DefaultExports

fun configurePrometheus() {
    DefaultExports.initialize()
}
