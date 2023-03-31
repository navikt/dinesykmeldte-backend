package no.nav.syfo

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val applicationName: String = getEnvVar("NAIS_APP_NAME", "dinesykmeldte-kafka"),
    val aadAccessTokenUrl: String = getEnvVar("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
    val clientId: String = getEnvVar("AZURE_APP_CLIENT_ID"),
    val clientSecret: String = getEnvVar("AZURE_APP_CLIENT_SECRET"),
    val cluster: String = getEnvVar("NAIS_CLUSTER_NAME"),
    val pdlScope: String = getEnvVar("PDL_SCOPE"),
    val pdlGraphqlPath: String = getEnvVar("PDL_GRAPHQL_PATH"),
    val syketilfelleEndpointURL: String = getEnvVar("SYKETILLFELLE_ENDPOINT_URL", "http://flex-syketilfelle.flex"),
    val syketilfelleScope: String = getEnvVar("SYKETILLFELLE_SCOPE"),
    val narmestelederLeesahTopic: String = "teamsykmelding.syfo-narmesteleder-leesah",
    val sendtSykmeldingTopic: String = "teamsykmelding.syfo-sendt-sykmelding",
    val sykepengesoknadTopic: String = "flex.sykepengesoknad",
    val hendelserTopic: String = "teamsykmelding.dinesykmeldte-hendelser-v2",
    val databaseUsername: String = getEnvVar("NAIS_DATABASE_DINESYKMELDTE_KAFKA_USER_USERNAME"),
    val databasePassword: String = getEnvVar("NAIS_DATABASE_DINESYKMELDTE_KAFKA_USER_PASSWORD"),
    val dbHost: String = getEnvVar("NAIS_DATABASE_DINESYKMELDTE_KAFKA_USER_HOST"),
    val dbPort: String = getEnvVar("NAIS_DATABASE_DINESYKMELDTE_KAFKA_USER_PORT"),
    val dbName: String = getEnvVar("NAIS_DATABASE_DINESYKMELDTE_KAFKA_USER_DATABASE"),
    val cloudSqlInstance: String = getEnvVar("CLOUD_SQL_INSTANCE"),
    val electorPath: String = getEnvVar("ELECTOR_PATH"),
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")

fun getFileAsString(filePath: String) = String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8)
