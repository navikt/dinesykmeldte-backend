package no.nav.syfo.application.metrics

import io.prometheus.client.Counter
import io.prometheus.client.Histogram

const val METRICS_NS = "teamsykmelding_kafka"

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

val HENDELSE_TOPIC_COUNTER: Counter = Counter.build()
    .labelNames("status")
    .name("hendelse_topic_counter")
    .namespace(METRICS_NS)
    .help("Counts hendelser from kafka")
    .register()

val SLETTET_COUNTER: Counter = Counter.build()
    .labelNames("ressurs")
    .name("slettet_counter")
    .namespace(METRICS_NS)
    .help("Antall slettede ressurser")
    .register()
