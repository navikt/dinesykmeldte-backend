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
import no.nav.syfo.application.leaderelection.LeaderElection
import no.nav.syfo.azuread.AccessTokenClient
import no.nav.syfo.common.delete.DeleteDataDb
import no.nav.syfo.common.delete.DeleteDataService
import no.nav.syfo.common.kafka.CommonKafkaService
import no.nav.syfo.hendelser.HendelserService
import no.nav.syfo.hendelser.db.HendelserDb
import no.nav.syfo.kafka.KafkaUtils
import no.nav.syfo.kafka.createKafkaProducer
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.minesykmeldte.MineSykmeldteService
import no.nav.syfo.minesykmeldte.db.MineSykmeldteDb
import no.nav.syfo.narmesteleder.NarmestelederService
import no.nav.syfo.narmesteleder.db.NarmestelederDb
import no.nav.syfo.narmesteleder.kafka.NLResponseProducer
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.soknad.SoknadService
import no.nav.syfo.soknad.db.SoknadDb
import no.nav.syfo.syketilfelle.client.SyfoSyketilfelleClient
import no.nav.syfo.sykmelding.SykmeldingService
import no.nav.syfo.sykmelding.db.SykmeldingDb
import no.nav.syfo.synchendelse.SyncHendelse
import no.nav.syfo.util.AuthConfiguration
import no.nav.syfo.util.getWellKnownTokenX
import no.nav.syfo.virksomhet.api.VirksomhetService
import no.nav.syfo.virksomhet.db.VirksomhetDb
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.koin.core.scope.Scope
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
            servicesModule(),
            commonKafkaConsumer(),
            hendelseKafkaProducer(),
        )
    }
}

fun hendelseKafkaProducer() = module {
    single { createKafkaProducer<SyncHendelse>("dinesykmeldte-sync-hendelse-producer") }
}

private fun servicesModule() = module {
    single { AccessTokenClient(env().aadAccessTokenUrl, env().clientId, env().clientSecret, get()) }
    single { PdlClient(get(), env().pdlGraphqlPath) }
    single { PdlPersonService(get<PdlClient>(), get(), get<Environment>().pdlScope) }
    single { SoknadService(SoknadDb(get()), env().cluster) }
    single { HendelserService(HendelserDb(get())) }
    single {
        SyfoSyketilfelleClient(env().syketilfelleEndpointURL, get(), env().syketilfelleScope, get())
    }
    single { SykmeldingService(SykmeldingDb(get()), get(), get(), get<Environment>().cluster) }
    single { VirksomhetService(VirksomhetDb(get())) }
    single {
        val nlResponseProducer =
            NLResponseProducer(
                createKafkaProducer("syfo-narmesteleder-producer"),
                env().nlResponseTopic
            )
        NarmestelederService(NarmestelederDb(get()), nlResponseProducer)
    }
    single { MineSykmeldteService(MineSykmeldteDb(get()), get(), env().syncTopic) }
    single { LeaderElection(get(), env().electorPath) }
    single { DeleteDataService(DeleteDataDb(get()), get()) }
}

private fun Scope.env() = get<Environment>()

private fun commonKafkaConsumer() = module {
    single {
        val kafkaConsumer =
            KafkaConsumer(
                KafkaUtils.getKafkaConfig("dinesykmeldte-backend-consumer")
                    .also {
                        it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
                        it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = 100
                    }
                    .toConsumerConfig("dinesykmeldte-backend", StringDeserializer::class),
                StringDeserializer(),
                StringDeserializer(),
            )
        CommonKafkaService(kafkaConsumer, get(), get(), get(), get(), get(), get())
    }
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
