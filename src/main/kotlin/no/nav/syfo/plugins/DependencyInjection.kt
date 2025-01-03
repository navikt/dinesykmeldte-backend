package no.nav.syfo.plugins

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.apache.Apache
import io.ktor.client.engine.apache.ApacheEngineConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import java.net.URI
import java.time.Duration
import java.util.concurrent.TimeUnit
import no.nav.syfo.Environment
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.database.Database
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.kafka.aiven.KafkaUtils
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.minesykmeldte.MineSykmeldteService
import no.nav.syfo.minesykmeldte.db.MineSykmeldteDb
import no.nav.syfo.narmesteleder.NarmestelederService
import no.nav.syfo.narmesteleder.db.NarmestelederDb
import no.nav.syfo.narmesteleder.kafka.NLResponseProducer
import no.nav.syfo.util.AuthConfiguration
import no.nav.syfo.util.JacksonKafkaSerializer
import no.nav.syfo.util.getWellKnownTokenX
import no.nav.syfo.virksomhet.api.VirksomhetService
import no.nav.syfo.virksomhet.db.VirksomhetDb
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.serialization.StringSerializer
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun Application.configureDependencies() {
    install(Koin) {
        slf4jLogger()

        modules(
            applicationStateModule(),
            environmentModule(),
            httpClient(),
            authModule(),
            databaseModule(),
            narmestelederService(),
            mineSykmeldteService(),
            virksomhetService(),
        )
    }
}

private fun virksomhetService() = module { single { VirksomhetService(VirksomhetDb(get())) } }

private fun narmestelederService() = module {
    single {
        val environment: Environment = get()
        val nlResponseProducer =
            NLResponseProducer(createKafkaProducer(environment), environment.nlResponseTopic)
        NarmestelederService(NarmestelederDb(get()), nlResponseProducer)
    }
}

private fun mineSykmeldteService() = module {
    single { MineSykmeldteService(MineSykmeldteDb(get())) }
}

private fun databaseModule() = module { single<DatabaseInterface> { Database(get()) } }

private fun httpClient() = module {
    single {
        val config: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
            install(ContentNegotiation) {
                jackson {
                    registerKotlinModule()
                    registerModule(JavaTimeModule())
                    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                }
            }
        }
        HttpClient(Apache, config)
    }
}

private fun environmentModule() = module { single { Environment() } }

private fun authModule() = module {
    single {
        val env: Environment = get()
        val httpClient: HttpClient = get()

        val wellKnownTokenX = getWellKnownTokenX(httpClient, env.tokenXWellKnownUrl)
        val jwkProviderTokenX =
            JwkProviderBuilder(URI.create(wellKnownTokenX.jwks_uri).toURL())
                .cached(10, Duration.ofHours(24))
                .rateLimited(10, 1, TimeUnit.MINUTES)
                .build()

        val tokenXIssuer: String = wellKnownTokenX.issuer

        AuthConfiguration(
            jwkProviderTokenX = jwkProviderTokenX,
            tokenXIssuer = tokenXIssuer,
            clientIdTokenX = env.dineSykmeldteBackendTokenXClientId,
        )
    }
}

private fun applicationStateModule() = module { single { ApplicationState() } }

fun <T> createKafkaProducer(env: Environment): KafkaProducer<String, T> =
    KafkaProducer(
        KafkaUtils.getAivenKafkaConfig("syfo-narmesteleder-producer")
            .toProducerConfig(
                "${env.applicationName}-producer",
                JacksonKafkaSerializer::class,
                StringSerializer::class,
            ),
    )
