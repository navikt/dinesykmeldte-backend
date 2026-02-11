package no.nav.syfo

import no.nav.syfo.texas.TexasEnvironment

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val applicationName: String = getEnvVar("NAIS_APP_NAME", "dinesykmeldte-backend"),
    val tokenXWellKnownUrl: String = getEnvVar("TOKEN_X_WELL_KNOWN_URL"),
    val dineSykmeldteBackendTokenXClientId: String = getEnvVar("TOKEN_X_CLIENT_ID"),
    val cluster: String = getEnvVar("NAIS_CLUSTER_NAME"),
    val nlResponseTopic: String = "teamsykmelding.syfo-narmesteleder",
    val pdlGraphqlPath: String = getEnvVar("PDL_GRAPHQL_PATH"),
    val pdlScope: String = getEnvVar("PDL_SCOPE"),
    val clientId: String = getEnvVar("AZURE_APP_CLIENT_ID"),
    val clientSecret: String = getEnvVar("AZURE_APP_CLIENT_SECRET"),
    val aadAccessTokenUrl: String = getEnvVar("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
    val narmestelederLeesahTopic: String = "teamsykmelding.syfo-narmesteleder-leesah",
    val sendtSykmeldingTopic: String = "teamsykmelding.syfo-sendt-sykmelding",
    val sykepengesoknadTopic: String = "flex.sykepengesoknad",
    val hendelserTopic: String = "team-esyfo.dinesykmeldte-hendelser-v2",
    val syketilfelleEndpointURL: String =
        getEnvVar("SYKETILLFELLE_ENDPOINT_URL", "http://flex-syketilfelle.flex"),
    val syketilfelleScope: String = getEnvVar("SYKETILLFELLE_SCOPE"),
    val electorPath: String = getEnvVar("ELECTOR_PATH"),
    val dbUrl: String = getEnvVar("NAIS_DATABASE_JDBC_URL"),
    val texas: TexasEnvironment = TexasEnvironment.createFromEnvVars(),
) {
    companion object {
        val authserver = getEnvVar("AUTH_SERVER", "localhost:6969")
        val dbserver = getEnvVar("DB_SERVER", "localhost:5432")

        fun createLocal() =
            Environment(
                cluster = "local",
                tokenXWellKnownUrl = "http://$authserver/tokenx/.well-known/openid-configuration",
                dineSykmeldteBackendTokenXClientId = "dinesykmeldte-backend",
                pdlGraphqlPath = "http://localhost:8080/graphql",
                pdlScope = "pdl-api://default",
                clientId = "clientId",
                clientSecret = "clientSecret",
                aadAccessTokenUrl = "http://localhost:8080/token",
                electorPath = "dinesykmeldte-backend-local",
                dbUrl = "jdbc:postgresql://$dbserver/dinesykmeldte-backend_dev?user=username&password=password&ssl=false",
                syketilfelleScope = "syketilfelle-backend",
                texas = TexasEnvironment.createForLocal(),
            )
    }
}

fun getEnvVar(
    varName: String,
    defaultValue: String? = null,
) = System.getenv(varName)
    ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")

fun isLocalEnv(): Boolean = getEnvVar("NAIS_CLUSTER_NAME", "local") == "local"
