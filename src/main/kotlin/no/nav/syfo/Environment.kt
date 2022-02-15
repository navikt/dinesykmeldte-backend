package no.nav.syfo

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val applicationName: String = getEnvVar("NAIS_APP_NAME", "dinesykmeldte-backend"),
    val tokenXWellKnownUrl: String = getEnvVar("TOKEN_X_WELL_KNOWN_URL"),
    val dineSykmeldteBackendTokenXClientId: String = getEnvVar("TOKEN_X_CLIENT_ID"),
    val allowedOrigin: List<String> = getEnvVar("ALLOWED_ORIGIN").split(","),
    val databaseUsername: String = getEnvVar("NAIS_DATABASE_USERNAME"),
    val databasePassword: String = getEnvVar("NAIS_DATABASE_PASSWORD"),
    val dbHost: String = getEnvVar("NAIS_DATABASE_HOST"),
    val dbPort: String = getEnvVar("NAIS_DATABASE_PORT"),
    val dbName: String = getEnvVar("NAIS_DATABASE_DATABASE"),
    val narmestelederLeesahTopic: String = "teamsykmelding.syfo-narmesteleder-leesah",
    val sendtSykmeldingTopic: String = "teamsykmelding.syfo-sendt-sykmelding",
    val pdlScope: String = getEnvVar("PDL_SCOPE"),
    val pdlGraphqlPath: String = getEnvVar("PDL_GRAPHQL_PATH"),
    val aadAccessTokenUrl: String = getEnvVar("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
    val clientId: String = getEnvVar("AZURE_APP_CLIENT_ID"),
    val clientSecret: String = getEnvVar("AZURE_APP_CLIENT_SECRET"),
    val syketilfelleEndpointURL: String = getEnvVar("SYKETILLFELLE_ENDPOINT_URL"),
    val syketilfelleScope: String = getEnvVar("SYKETILLFELLE_SCOPE"),
    val cluster: String = getEnvVar("NAIS_CLUSTER_NAME"),
    val sykepengesoknadTopic: String = "flex.sykepengesoknad",
    val lestSykmeldingSoknadTopic: String = "teamsykmelding.dinesykmeldte-hendelser",
    val hendelserTopic: String = "teamsykmelding.dinesykmeldte-hendelser-v2",
    val runKafkaConsumer: Boolean = getRunKafkaProperty()
) {
    fun jdbcUrl(): String {
        return "jdbc:postgresql://$dbHost:$dbPort/$dbName"
    }
}

private fun getRunKafkaProperty() = when (getEnvVar("RUN_KAFKA_CONSUMER", "true")) {
    "false" -> false
    else -> true
}

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
