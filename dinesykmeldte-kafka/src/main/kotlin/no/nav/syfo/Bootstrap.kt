package no.nav.syfo

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.apache.Apache
import io.ktor.client.engine.apache.ApacheEngineConfig
import io.ktor.client.features.HttpResponseValidator
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.get
import io.ktor.network.sockets.SocketTimeoutException
import io.prometheus.client.hotspot.DefaultExports
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.ApplicationServer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.createApplicationEngine
import no.nav.syfo.azuread.AccessTokenClient
import no.nav.syfo.common.exception.ServiceUnavailableException
import no.nav.syfo.database.GcpDatabase
import no.nav.syfo.database.GcpDatabaseCredentials
import no.nav.syfo.hendelser.HendelserService
import no.nav.syfo.hendelser.db.HendelserDb
import no.nav.syfo.kafka.aiven.KafkaUtils
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.narmesteleder.NarmestelederService
import no.nav.syfo.narmesteleder.db.NarmestelederDb
import no.nav.syfo.soknad.SoknadService
import no.nav.syfo.soknad.db.SoknadDb
import no.nav.syfo.sykmelding.SykmeldingService
import no.nav.syfo.sykmelding.client.SyfoSyketilfelleClient
import no.nav.syfo.sykmelding.db.SykmeldingDb
import no.nav.syfo.sykmelding.pdl.client.PdlClient
import no.nav.syfo.sykmelding.pdl.service.PdlPersonService
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL
import java.util.concurrent.TimeUnit

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.dinesykmeldte-kafka")
val objectMapper: ObjectMapper = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
}

fun main() {
    val env = Environment()
    DefaultExports.initialize()
    val applicationState = ApplicationState()
    val applicationEngine = createApplicationEngine(
        env,
        applicationState
    )
    val applicationServer = ApplicationServer(applicationEngine, applicationState)
    applicationServer.start()
    val config: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        HttpResponseValidator {
            handleResponseException { exception ->
                when (exception) {
                    is SocketTimeoutException -> throw ServiceUnavailableException(exception.message)
                }
            }
        }
    }
    val httpClient = HttpClient(Apache, config)
    val accessTokenClient = AccessTokenClient(env.aadAccessTokenUrl, env.clientId, env.clientSecret, httpClient)
    val pdlClient = PdlClient(
        httpClient,
        env.pdlGraphqlPath,
        PdlClient::class.java.getResource("/graphql/getPerson.graphql").readText().replace(Regex("[\n\t]"), "")
    )
    val pdlPersonService = PdlPersonService(pdlClient, accessTokenClient, env.pdlScope)
    applicationState.ready = true
    val syfoSyketilfelleClient = SyfoSyketilfelleClient(
        syketilfelleEndpointURL = env.syketilfelleEndpointURL,
        accessTokenClient = accessTokenClient,
        syketilfelleScope = env.syketilfelleScope,
        httpClient = httpClient
    )

    val kafkaConsumer = KafkaConsumer(
        KafkaUtils.getAivenKafkaConfig().also {
            it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "none"
            it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = 100
        }.toConsumerConfig("dinesykmeldte-backend", StringDeserializer::class),
        StringDeserializer(),
        StringDeserializer()
    )
    val credentials: GcpDatabaseCredentials = objectMapper.readValue(getFileAsString("/run/secrets/secret"))
    val database = GcpDatabase(credentials, "dinesykmeldte-backend")

    val narmestelederService = NarmestelederService(NarmestelederDb(database),)
    val sykmeldingService = SykmeldingService(SykmeldingDb(database), pdlPersonService, syfoSyketilfelleClient, env.cluster)
    val soknadService = SoknadService(SoknadDb(database))
    val hendelserService = HendelserService(HendelserDb(database))


}


fun getWellKnownTokenX(httpClient: HttpClient, wellKnownUrl: String) =
    runBlocking { httpClient.get<WellKnownTokenX>(wellKnownUrl) }

@JsonIgnoreProperties(ignoreUnknown = true)
data class WellKnownTokenX(
    val token_endpoint: String,
    val jwks_uri: String,
    val issuer: String
)
