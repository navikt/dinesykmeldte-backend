package no.nav.syfo.application.metrics

import io.prometheus.client.Counter
import io.prometheus.client.Histogram

const val METRICS_NS = "dinesykmeldte_backend"

val HTTP_HISTOGRAM: Histogram = Histogram.Builder()
    .labelNames("path")
    .name("requests_duration_seconds")
    .help("http requests durations for incoming requests in seconds")
    .register()

val DEAKTIVERT_LEDER_COUNTER: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name("deaktivert_leder_counter")
    .help("Antall NL-koblinger deaktivert av leder")
    .register()
