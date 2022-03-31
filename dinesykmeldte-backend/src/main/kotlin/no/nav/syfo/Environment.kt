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
    val cluster: String = getEnvVar("NAIS_CLUSTER_NAME"),
    val nlResponseTopic: String = "teamsykmelding.syfo-narmesteleder"
) {
    fun jdbcUrl(): String {
        return "jdbc:postgresql://$dbHost:$dbPort/$dbName"
    }
}

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
