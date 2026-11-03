package no.nav.syfo.pdl.cache

import no.nav.syfo.getEnvVar

// Not a data class: credentials must never appear in generated string representations.
class ValkeyConfig(
    val host: String,
    val port: Int,
    val username: String?,
    val password: String?,
    val tls: Boolean = true,
) {
    override fun toString() = "ValkeyConfig(redacted)"

    companion object {
        fun fromEnvironment() =
            ValkeyConfig(
                host = getEnvVar("VALKEY_HOST_DINESYKMELDTE_BACKEND_CACHE"),
                port = getEnvVar("VALKEY_PORT_DINESYKMELDTE_BACKEND_CACHE").toInt(),
                username = getEnvVar("VALKEY_USERNAME_DINESYKMELDTE_BACKEND_CACHE"),
                password = getEnvVar("VALKEY_PASSWORD_DINESYKMELDTE_BACKEND_CACHE"),
            )

        fun local() =
            ValkeyConfig(
                host = getEnvVar("VALKEY_LOCAL_HOST", "localhost"),
                port = 6379,
                username = null,
                password = null,
                tls = false,
            )
    }
}
