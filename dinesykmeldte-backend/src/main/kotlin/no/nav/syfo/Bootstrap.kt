package no.nav.syfo

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.apache.Apache
import io.ktor.client.engine.apache.ApacheEngineConfig
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.network.sockets.SocketTimeoutException
import io.ktor.serialization.jackson.jackson
import io.prometheus.client.hotspot.DefaultExports
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.ApplicationServer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.createApplicationEngine
import no.nav.syfo.application.database.Database
import no.nav.syfo.common.exception.ServiceUnavailableException
import no.nav.syfo.kafka.aiven.KafkaUtils
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.minesykmeldte.MineSykmeldteService
import no.nav.syfo.minesykmeldte.db.MineSykmeldteDb
import no.nav.syfo.narmesteleder.NarmestelederService
import no.nav.syfo.narmesteleder.db.NarmestelederDb
import no.nav.syfo.narmesteleder.kafka.NLReadCountProducer
import no.nav.syfo.narmesteleder.kafka.NLResponseProducer
import no.nav.syfo.narmesteleder.kafka.model.NLReadCountKafkaMessage
import no.nav.syfo.narmesteleder.kafka.model.NlResponseKafkaMessage
import no.nav.syfo.util.JacksonKafkaSerializer
import no.nav.syfo.virksomhet.api.VirksomhetService
import no.nav.syfo.virksomhet.db.VirksomhetDb
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlin.time.ExperimentalTime

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.dinesykmeldte-backend")
val sikkerlogg = LoggerFactory.getLogger("securelog")

val objectMapper: ObjectMapper = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
}

@ExperimentalTime
@DelicateCoroutinesApi
fun main() {

    val env = Environment()
    DefaultExports.initialize()
    val applicationState = ApplicationState()
    val database = Database(env)

    val config: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
        install(ContentNegotiation) {
            jackson {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        HttpResponseValidator {
            handleResponseExceptionWithRequest { exception, _ ->
                when (exception) {
                    is SocketTimeoutException -> throw ServiceUnavailableException(exception.message)
                }
            }
        }
        install(HttpRequestRetry) {
            constantDelay(100, 0, false)
            retryOnExceptionIf(3) { request, throwable ->
                log.warn("Caught exception ${throwable.message}, for url ${request.url}")
                true
            }
            retryIf(maxRetries) { request, response ->
                if (response.status.value.let { it in 500..599 }) {
                    log.warn("Retrying for statuscode ${response.status.value}, for url ${request.url}")
                    true
                } else {
                    false
                }
            }
        }
    }
    val httpClient = HttpClient(Apache, config)

    val wellKnownTokenX = getWellKnownTokenX(httpClient, env.tokenXWellKnownUrl)
    val jwkProviderTokenX = JwkProviderBuilder(URL(wellKnownTokenX.jwks_uri))
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    val nlResponseProducer = NLResponseProducer(createKafkaProducer<NlResponseKafkaMessage>(env), env.nlResponseTopic)
    val narmestelederService = NarmestelederService(NarmestelederDb(database), nlResponseProducer)

    val nlReadCountProducer = NLReadCountProducer(createKafkaProducer<NLReadCountKafkaMessage>(env), env.nlReadCountTopic)
    val mineSykmeldteService = MineSykmeldteService(MineSykmeldteDb(database), nlReadCountProducer)

    val applicationEngine = createApplicationEngine(
        env,
        jwkProviderTokenX,
        wellKnownTokenX.issuer,
        applicationState,
        mineSykmeldteService,
        VirksomhetService(VirksomhetDb(database)),
        narmestelederService
    )
    ApplicationServer(applicationEngine, applicationState).start()
}

fun getWellKnownTokenX(httpClient: HttpClient, wellKnownUrl: String) =
    runBlocking { httpClient.get(wellKnownUrl).body<WellKnownTokenX>() }

fun <T> createKafkaProducer(env: Environment): KafkaProducer<String, T> =
    KafkaProducer(
        KafkaUtils
            .getAivenKafkaConfig()
            .toProducerConfig(
                "${env.applicationName}-producer",
                JacksonKafkaSerializer::class,
                StringSerializer::class
            )
    )

@JsonIgnoreProperties(ignoreUnknown = true)
data class WellKnownTokenX(
    val token_endpoint: String,
    val jwks_uri: String,
    val issuer: String,
)
