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
import io.ktor.client.engine.apache.Apache
import io.ktor.client.engine.apache.ApacheEngineConfig
import io.ktor.client.features.HttpResponseValidator
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.get
import io.ktor.network.sockets.SocketTimeoutException
import io.prometheus.client.hotspot.DefaultExports
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.ApplicationServer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.createApplicationEngine
import no.nav.syfo.application.database.Database
import no.nav.syfo.azuread.AccessTokenClient
import no.nav.syfo.common.CommonKafkaService
import no.nav.syfo.common.delete.DeleteDataDb
import no.nav.syfo.common.delete.DeleteDataService
import no.nav.syfo.common.exception.ServiceUnavailableException
import no.nav.syfo.hendelser.HendelserService
import no.nav.syfo.hendelser.db.HendelserDb
import no.nav.syfo.kafka.aiven.KafkaUtils
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.minesykmeldte.MineSykmeldteService
import no.nav.syfo.minesykmeldte.db.MineSykmeldteDb
import no.nav.syfo.narmesteleder.NarmestelederService
import no.nav.syfo.narmesteleder.db.NarmestelederDb
import no.nav.syfo.narmesteleder.kafka.NLResponseProducer
import no.nav.syfo.narmesteleder.kafka.model.NlResponseKafkaMessage
import no.nav.syfo.soknad.SoknadService
import no.nav.syfo.soknad.db.SoknadDb
import no.nav.syfo.sykmelding.SykmeldingService
import no.nav.syfo.sykmelding.client.SyfoSyketilfelleClient
import no.nav.syfo.sykmelding.db.SykmeldingDb
import no.nav.syfo.sykmelding.pdl.client.PdlClient
import no.nav.syfo.sykmelding.pdl.service.PdlPersonService
import no.nav.syfo.util.JacksonKafkaSerializer
import no.nav.syfo.virksomhet.api.VirksomhetService
import no.nav.syfo.virksomhet.db.VirksomhetDb
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlin.time.ExperimentalTime

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.dinesykmeldte-backend")

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
    val syfoSyketilfelleClient = SyfoSyketilfelleClient(
        syketilfelleEndpointURL = env.syketilfelleEndpointURL,
        accessTokenClient = accessTokenClient,
        syketilfelleScope = env.syketilfelleScope,
        httpClient = httpClient
    )

    val wellKnownTokenX = getWellKnownTokenX(httpClient, env.tokenXWellKnownUrl)
    val jwkProviderTokenX = JwkProviderBuilder(URL(wellKnownTokenX.jwks_uri))
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    val kafkaConsumer = KafkaConsumer(
        KafkaUtils.getAivenKafkaConfig().also {
            it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "none"
            it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = 100
        }.toConsumerConfig("dinesykmeldte-backend", StringDeserializer::class),
        StringDeserializer(),
        StringDeserializer()
    )

    val kafkaProducer = KafkaProducer<String, NlResponseKafkaMessage>(
        KafkaUtils
            .getAivenKafkaConfig()
            .toProducerConfig("${env.applicationName}-producer", JacksonKafkaSerializer::class, StringSerializer::class)
    )
    val nlResponseProducer = NLResponseProducer(kafkaProducer, env.nlResponseTopic)

    val narmestelederService = NarmestelederService(NarmestelederDb(database), nlResponseProducer)
    val sykmeldingService = SykmeldingService(SykmeldingDb(database), pdlPersonService, syfoSyketilfelleClient, env.cluster)
    val soknadService = SoknadService(SoknadDb(database))
    val hendelserService = HendelserService(HendelserDb(database))

    val commonKafkaService = CommonKafkaService(
        kafkaConsumer,
        applicationState,
        env,
        narmestelederService,
        sykmeldingService,
        soknadService,
        hendelserService
    )

    val applicationEngine = createApplicationEngine(
        env,
        jwkProviderTokenX,
        wellKnownTokenX.issuer,
        applicationState,
        MineSykmeldteService(MineSykmeldteDb(database)),
        VirksomhetService(VirksomhetDb(database)),
        narmestelederService
    )
    val applicationServer = ApplicationServer(applicationEngine, applicationState)
    applicationServer.start()
    applicationState.ready = true

    commonKafkaService.startConsumer()

    DeleteDataService(DeleteDataDb(database), applicationState).start()
}

fun getWellKnownTokenX(httpClient: HttpClient, wellKnownUrl: String) =
    runBlocking { httpClient.get<WellKnownTokenX>(wellKnownUrl) }

@JsonIgnoreProperties(ignoreUnknown = true)
data class WellKnownTokenX(
    val token_endpoint: String,
    val jwks_uri: String,
    val issuer: String
)
