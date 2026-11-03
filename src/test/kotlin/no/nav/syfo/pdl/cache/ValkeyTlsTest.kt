package no.nav.syfo.pdl.cache

import io.kotest.core.spec.style.FunSpec
import java.io.EOFException
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.security.cert.CertificateException
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValkeyTlsTest :
    FunSpec({
        test("production pool verifies TLS hostnames even with a trusted certificate") {
            val directory = Files.createTempDirectory("valkey-tls-test")
            try {
                val store = directory.resolve("server.p12")
                val password = "synthetic-test-only"
                val keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString()
                val process =
                    ProcessBuilder(
                        keytool,
                        "-genkeypair",
                        "-alias",
                        "server",
                        "-keyalg",
                        "EC",
                        "-dname",
                        "CN=localhost",
                        "-ext",
                        "SAN=dns:localhost",
                        "-validity",
                        "1",
                        "-storetype",
                        "PKCS12",
                        "-keystore",
                        store.toString(),
                        "-storepass",
                        password,
                        "-keypass",
                        password,
                        "-noprompt",
                    ).redirectErrorStream(true).start()
                // Discard bounded keytool output; never include private material in test failures.
                val finished = process.waitFor(15, TimeUnit.SECONDS)
                if (!finished) process.destroyForcibly()
                assertTrue(finished)
                process.inputStream.readAllBytes()
                assertEquals(0, process.exitValue())
                val keys =
                    KeyStore.getInstance("PKCS12").apply {
                        Files.newInputStream(store).use { load(it, password.toCharArray()) }
                    }
                val keyManagers =
                    KeyManagerFactory.getInstance(
                        KeyManagerFactory.getDefaultAlgorithm(),
                    )
                keyManagers.init(keys, password.toCharArray())
                val trust =
                    TrustManagerFactory.getInstance(
                        TrustManagerFactory.getDefaultAlgorithm(),
                    )
                trust.init(keys)
                val context = SSLContext.getInstance("TLS")
                context.init(keyManagers.keyManagers, trust.trustManagers, null)

                listOf("localhost", "127.0.0.1").forEach { host ->
                    val server =
                        context.serverSocketFactory.createServerSocket(
                            0,
                        ) as SSLServerSocket
                    server.soTimeout = 2_000
                    server.use {
                        val worker =
                            thread(name = "valkey-tls-fixture") {
                                try {
                                    (server.accept() as SSLSocket).use { socket ->
                                        socket.soTimeout = 2_000
                                        socket.startHandshake()
                                        respondToValkey(socket)
                                    }
                                } catch (_: Exception) {
                                    // The mismatching client aborts the TLS handshake.
                                }
                            }
                        try {
                            val config = ValkeyConfig(host, server.localPort, null, null)
                            val pool = ValkeyIdenterCache.createPool(config, context.socketFactory)
                            pool.use {
                                if (host == "localhost") {
                                    pool.resource.use { assertEquals("PONG", it.ping()) }
                                } else {
                                    val failure =
                                        assertFailsWith<Exception> {
                                            pool.resource.use { it.ping() }
                                        }
                                    val causes = generateSequence<Throwable>(failure) { it.cause }
                                    assertTrue(causes.any { it is CertificateException })
                                }
                            }
                        } finally {
                            worker.join(3_000)
                            assertFalse(worker.isAlive)
                        }
                    }
                }
            } finally {
                Files.walk(directory).use { paths ->
                    paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
                }
            }
        }
    })

private fun respondToValkey(socket: SSLSocket) {
    val input = socket.inputStream.bufferedReader(Charsets.UTF_8)
    val output = socket.outputStream
    while (true) {
        val count = input.readLine()?.removePrefix("*")?.toInt() ?: return
        val command =
            (1..count).map {
                val length = input.readLine()?.removePrefix("$")?.toInt() ?: throw EOFException()
                val chars = CharArray(length)
                var offset = 0
                while (offset < length) {
                    val read = input.read(chars, offset, length - offset)
                    if (read < 0) throw EOFException()
                    offset += read
                }
                input.readLine()
                String(chars)
            }
        val response = if (command.first() == "PING") "+PONG\r\n" else "+OK\r\n"
        output.write(response.toByteArray())
        output.flush()
    }
}
