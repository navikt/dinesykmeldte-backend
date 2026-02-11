package no.nav.syfo.application.metrics

import io.ktor.server.application.PipelineCall
import io.ktor.server.request.path
import io.ktor.util.pipeline.PipelineInterceptor

val REGEX =
    """[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}""".toRegex()

fun monitorHttpRequests(): PipelineInterceptor<Unit, PipelineCall> =
    {
        val path = context.request.path()
        val label = REGEX.replace(path, ":id")
        val timer = HTTP_HISTOGRAM.labels(label).startTimer()
        proceed()
        timer.observeDuration()
    }
