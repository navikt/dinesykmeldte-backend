package no.nav.syfo.application.metrics

import io.prometheus.client.Counter
import io.prometheus.client.Histogram

const val METRICS_NS = "dinesykmeldte_backend"

val HTTP_HISTOGRAM: Histogram = Histogram.Builder()
    .labelNames("path")
    .name("requests_duration_seconds")
    .help("http requests durations for incoming requests in seconds")
    .register()

val NL_TOPIC_COUNTER: Counter = Counter.build()
    .labelNames("status")
    .name("nl_topic_counter")
    .namespace(METRICS_NS)
    .help("Counts NL-messages from kafka (new or deleted)")
    .register()

val SYKMELDING_TOPIC_COUNTER: Counter = Counter.build()
    .name("sykmelding_topic_counter")
    .namespace(METRICS_NS)
    .help("Counts sendte sykmeldinger from kafka (new or deleted)")
    .register()

val SOKNAD_TOPIC_COUNTER: Counter = Counter.build()
    .name("soknad_topic_counter")
    .namespace(METRICS_NS)
    .help("Counts sendte soknader from kafka")
    .register()

val ERROR_COUNTER: Counter = Counter.build()
    .labelNames("error")
    .name("error")
    .namespace(METRICS_NS)
    .help("Error counters")
    .register()

val DEAKTIVERT_LEDER_COUNTER: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name("deaktivert_leder_counter")
    .help("Antall NL-koblinger deaktivert av leder")
    .register()
