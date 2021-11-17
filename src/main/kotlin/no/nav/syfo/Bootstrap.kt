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
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.get
import io.prometheus.client.hotspot.DefaultExports
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.ApplicationServer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.createApplicationEngine
import no.nav.syfo.application.database.Database
import no.nav.syfo.azuread.AccessTokenClient
import no.nav.syfo.hendelser.HendelserService
import no.nav.syfo.hendelser.db.HendelserDb
import no.nav.syfo.hendelser.kafka.model.DineSykmeldteHendelse
import no.nav.syfo.kafka.aiven.KafkaUtils
import no.nav.syfo.kafka.felles.SykepengesoknadDTO
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.minesykmeldte.MineSykmeldteService
import no.nav.syfo.minesykmeldte.db.MineSykmeldteDb
import no.nav.syfo.narmesteleder.NarmestelederService
import no.nav.syfo.narmesteleder.db.NarmestelederDb
import no.nav.syfo.narmesteleder.kafka.model.NarmestelederLeesahKafkaMessage
import no.nav.syfo.soknad.SoknadService
import no.nav.syfo.soknad.db.SoknadDb
import no.nav.syfo.sykmelding.SykmeldingService
import no.nav.syfo.sykmelding.client.SyfoSyketilfelleClient
import no.nav.syfo.sykmelding.db.SykmeldingDb
import no.nav.syfo.sykmelding.kafka.model.SendtSykmeldingKafkaMessage
import no.nav.syfo.sykmelding.pdl.client.PdlClient
import no.nav.syfo.sykmelding.pdl.service.PdlPersonService
import no.nav.syfo.util.JacksonKafkaDeserializer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL
import java.util.concurrent.TimeUnit

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.dinesykmeldte-backend")

val objectMapper: ObjectMapper = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
}

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

    val kafkaConsumerNarmesteleder = KafkaConsumer(
        KafkaUtils.getAivenKafkaConfig().also {
            it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "none"
            it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = 10
            it[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = false
        }.toConsumerConfig("dinesykmeldte-backend", JacksonKafkaDeserializer::class),
        StringDeserializer(),
        JacksonKafkaDeserializer(NarmestelederLeesahKafkaMessage::class)
    )
    val narmestelederService = NarmestelederService(kafkaConsumerNarmesteleder, NarmestelederDb(database), applicationState, env.narmestelederLeesahTopic)

    val kafkaConsumerSykmelding = KafkaConsumer(
        KafkaUtils.getAivenKafkaConfig().also {
            it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "none"
            it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = 1
            it[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = false
        }.toConsumerConfig("dinesykmeldte-backend", JacksonKafkaDeserializer::class),
        StringDeserializer(),
        JacksonKafkaDeserializer(SendtSykmeldingKafkaMessage::class)
    )
    val sykmeldingService = SykmeldingService(kafkaConsumerSykmelding, SykmeldingDb(database), applicationState, env.sendtSykmeldingTopic, pdlPersonService, syfoSyketilfelleClient, env.cluster)

    val kafkaConsumerSoknad = KafkaConsumer(
        KafkaUtils.getAivenKafkaConfig().also {
            it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "none"
            it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = 1
            it[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = false
        }.toConsumerConfig("dinesykmeldte-backend", JacksonKafkaDeserializer::class),
        StringDeserializer(),
        JacksonKafkaDeserializer(SykepengesoknadDTO::class)
    )
    val soknadService = SoknadService(kafkaConsumerSoknad, SoknadDb(database), applicationState, env.sykepengesoknadTopic)

    val kafkaConsumerHendelser = KafkaConsumer(
        KafkaUtils.getAivenKafkaConfig().also {
            it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
            it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = 10
            it[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = false
        }.toConsumerConfig("dinesykmeldte-backend", JacksonKafkaDeserializer::class),
        StringDeserializer(),
        JacksonKafkaDeserializer(DineSykmeldteHendelse::class)
    )
    val hendelserService = HendelserService(kafkaConsumerHendelser, HendelserDb(database), applicationState, env.hendelserTopic)

    val applicationEngine = createApplicationEngine(
        env,
        jwkProviderTokenX,
        wellKnownTokenX.issuer,
        applicationState,
        MineSykmeldteService(MineSykmeldteDb(database))
    )
    val applicationServer = ApplicationServer(applicationEngine, applicationState)
    applicationServer.start()
    applicationState.ready = true

    narmestelederService.startConsumer()
    sykmeldingService.startConsumer()
    soknadService.startConsumer()
    hendelserService.startConsumer()
}

fun getWellKnownTokenX(httpClient: HttpClient, wellKnownUrl: String) =
    runBlocking { httpClient.get<WellKnownTokenX>(wellKnownUrl) }

@JsonIgnoreProperties(ignoreUnknown = true)
data class WellKnownTokenX(
    val token_endpoint: String,
    val jwks_uri: String,
    val issuer: String
)
