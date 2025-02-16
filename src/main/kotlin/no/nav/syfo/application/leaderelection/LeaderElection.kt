package no.nav.syfo.application.leaderelection

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import java.net.InetAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.syfo.util.logger

class LeaderElection(
    private val httpClient: HttpClient,
    private val electorPath: String,
) {
    private val log = logger()

    suspend fun isLeader(): Boolean {
        val hostname: String = withContext(Dispatchers.IO) { InetAddress.getLocalHost() }.hostName

        try {
            val leader = httpClient.get(getHttpPath(electorPath)).body<Leader>()
            return leader.name == hostname
        } catch (e: Exception) {
            val message = "Kall mot elector feiler"
            log.error(message)
            throw RuntimeException(message)
        }
    }

    private fun getHttpPath(url: String): String =
        when (url.startsWith("http://")) {
            true -> url
            else -> "http://$url"
        }

    private data class Leader(val name: String)
}
