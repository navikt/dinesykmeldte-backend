package no.nav.syfo.pdl.service

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.OutputStreamAppender
import com.fasterxml.jackson.databind.JsonNode
import io.kotest.core.spec.style.FunSpec
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.withContext
import net.logstash.logback.encoder.LogstashEncoder
import no.nav.syfo.azuread.AccessTokenClient
import no.nav.syfo.azuread.AccessTokenRequestFailedException
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.exceptions.NameNotFoundInPdlException
import no.nav.syfo.pdl.exceptions.PdlPersonoppslagFailedException
import no.nav.syfo.pdl.exceptions.PdlRequestFailedException
import no.nav.syfo.pdl.exceptions.PdlResponseIncompleteException
import no.nav.syfo.util.HttpClientTest
import no.nav.syfo.util.ResponseData
import no.nav.syfo.util.objectMapper
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PdlStructuredLoggingTest :
    FunSpec({
        val fnrCanary = "12345678910"
        val tokenCanary = "TOKEN_CANARY"
        val graphqlDataNameCanary = "GRAPHQL_DATA_NAME_CANARY"
        val graphqlDataIdentCanary = "GRAPHQL_DATA_IDENT_CANARY"
        val localContextCanary = "SYKMELDING_ID_CANARY"
        val httpBodyCanary = "HTTP_BODY_CANARY"
        val endpointCanary = "graphql-endpoint-canary"
        val traceIdCanary = "0123456789abcdef0123456789abcdef"
        val accessTokenClient = mockk<AccessTokenClient>()
        val httpClient = HttpClientTest()
        val pdlPersonService =
            PdlPersonService(
                pdlClient = PdlClient(httpClient.httpClient, endpointCanary),
                accessTokenClient = accessTokenClient,
                pdlScope = "scope",
            )

        beforeEach {
            coEvery { accessTokenClient.getAccessToken(any()) } returns tokenCanary
        }

        test("serialiserer én terminal ERROR med dokumenterte PDL-feildetaljer") {
            httpClient.respond {
                ResponseData(
                    httpStatusCode = HttpStatusCode.OK,
                    content =
                        pdlResponseWithDataAndError(
                            code = "unauthorized",
                            dataName = graphqlDataNameCanary,
                            dataIdent = graphqlDataIdentCanary,
                            localContext = localContextCanary,
                            hasUsableName = false,
                        ),
                )
            }

            MDC.put("trace_id", traceIdCanary)
            val logs =
                try {
                    withContext(MDCContext()) {
                        capturePdlLogs {
                            assertFailsWith<PdlPersonoppslagFailedException> {
                                pdlPersonService.getPerson(fnrCanary)
                            }
                        }
                    }
                } finally {
                    MDC.remove("trace_id")
                }

            assertEquals(1, logs.size)
            val log = logs.single()
            assertEquals("ERROR", log["level"].asText())
            assertEquals(PDL_PERSONOPPSLAG_FAILED, log["event_type"].asText())
            assertEquals("PDL_UNAUTHORIZED", log["error_code"].asText())
            assertFalse(log["retryable"].asBoolean())
            assertEquals(HENT_PERSONOPPLYSNINGER, log["operation"].asText())
            assertEquals(traceIdCanary, log["trace_id"].asText())
            assertTrue(log["trace_id"].asText().matches(Regex("^[a-f0-9]{32}$")))
            assertEquals("Klarte ikke å fullføre personoppslag i PDL", log["message"].asText())
            assertFalse(log.has("upstream_status"))

            val pdlError = log["pdl_errors"].single()
            assertEquals("DIAGNOSTIC_MESSAGE_CANARY", pdlError["message"].asText())
            assertEquals("hentPerson", pdlError["path"].single().asText())
            assertEquals("unauthorized", pdlError["extensions"]["code"].asText())
            assertEquals("CURRENT_PDL_ERROR_ID", pdlError["extensions"]["id"].asText())
            assertEquals(
                "FUTURE_NESTED_VALUE",
                pdlError["extensions"]["future_metadata"]["nested"].asText(),
            )
            assertEquals("FUTURE_TOP_LEVEL_VALUE", pdlError["future_top_level"].asText())
            assertEquals("DETAIL_TYPE_CANARY", pdlError["extensions"]["details"]["type"].asText())
            assertEquals("DETAIL_CAUSE_CANARY", pdlError["extensions"]["details"]["cause"].asText())
            assertEquals(
                "DETAIL_POLICY_CANARY",
                pdlError["extensions"]["details"]["policy"].asText(),
            )

            assertPrivateCanariesAbsent(
                log = log,
                privateCanaries =
                    listOf(
                        fnrCanary,
                        tokenCanary,
                        graphqlDataNameCanary,
                        graphqlDataIdentCanary,
                        "DATA_ETTERNAVN_CANARY",
                        localContextCanary,
                        endpointCanary,
                    ),
            )
        }

        test("logger brukbar partial response som WARN, ikke terminal ERROR") {
            httpClient.respond {
                ResponseData(
                    httpStatusCode = HttpStatusCode.OK,
                    content =
                        pdlResponseWithDataAndError(
                            code = "server_error",
                            dataName = graphqlDataNameCanary,
                            dataIdent = graphqlDataIdentCanary,
                            localContext = localContextCanary,
                            hasUsableName = true,
                        ),
                )
            }

            val logs = capturePdlLogs { pdlPersonService.getPerson(fnrCanary) }

            assertEquals(1, logs.size)
            val log = logs.single()
            assertEquals("WARN", log["level"].asText())
            assertEquals(PDL_PERSONOPPSLAG_PARTIAL_RESPONSE, log["event_type"].asText())
            assertEquals("PDL_SERVER_ERROR", log["error_code"].asText())
            assertEquals(HENT_PERSONOPPLYSNINGER, log["operation"].asText())
            assertEquals(
                "PDL returnerte feil sammen med brukbare personopplysninger",
                log["message"].asText(),
            )
            assertEquals(
                "DIAGNOSTIC_MESSAGE_CANARY",
                log["pdl_errors"].single()["message"].asText(),
            )
            assertPrivateCanariesAbsent(
                log,
                listOf(fnrCanary, tokenCanary, graphqlDataNameCanary, graphqlDataIdentCanary),
            )
        }

        test("mapper ukjent runtimekode til en kodeeid feilkode") {
            httpClient.respond {
                ResponseData(
                    httpStatusCode = HttpStatusCode.OK,
                    content =
                        pdlResponseWithDataAndError(
                            code = "future_runtime_code",
                            dataName = graphqlDataNameCanary,
                            dataIdent = graphqlDataIdentCanary,
                            localContext = localContextCanary,
                            hasUsableName = false,
                        ),
                )
            }

            val logs =
                capturePdlLogs {
                    assertFailsWith<PdlPersonoppslagFailedException> {
                        pdlPersonService.getPerson(fnrCanary)
                    }
                }

            assertEquals(1, logs.size)
            assertEquals("PDL_UNKNOWN_ERROR", logs.single()["error_code"].asText())
            assertEquals(
                "future_runtime_code",
                logs.single()["pdl_errors"].single()["extensions"]["code"].asText(),
            )
        }

        test("logger not_found én gang som WARN") {
            httpClient.respond {
                ResponseData(
                    httpStatusCode = HttpStatusCode.OK,
                    content =
                        pdlResponseWithDataAndError(
                            code = "not_found",
                            dataName = graphqlDataNameCanary,
                            dataIdent = graphqlDataIdentCanary,
                            localContext = localContextCanary,
                            hasUsableName = false,
                        ),
                )
            }

            val logs =
                capturePdlLogs {
                    assertFailsWith<NameNotFoundInPdlException> {
                        pdlPersonService.getPerson(fnrCanary)
                    }
                }

            assertEquals(1, logs.size)
            val log = logs.single()
            assertEquals("WARN", log["level"].asText())
            assertEquals("pdl_personoppslag_not_found", log["event_type"].asText())
            assertEquals("PDL_NOT_FOUND", log["error_code"].asText())
            assertEquals(HENT_PERSONOPPLYSNINGER, log["operation"].asText())
            assertEquals("not_found", log["pdl_errors"].single()["extensions"]["code"].asText())
            assertFalse(log.has("stack_trace"))
            assertPrivateCanariesAbsent(
                log,
                listOf(fnrCanary, tokenCanary, graphqlDataNameCanary, graphqlDataIdentCanary),
            )
        }

        test("logger HTTP-feil én gang uten responsinnhold") {
            httpClient.respond {
                ResponseData(
                    httpStatusCode = HttpStatusCode.InternalServerError,
                    content = """{"data":"$httpBodyCanary"}""",
                )
            }

            val logs =
                capturePdlLogs {
                    assertFailsWith<PdlRequestFailedException> {
                        pdlPersonService.getPerson(fnrCanary)
                    }
                }

            assertEquals(1, logs.size)
            val log = logs.single()
            assertEquals("ERROR", log["level"].asText())
            assertEquals(PDL_PERSONOPPSLAG_FAILED, log["event_type"].asText())
            assertEquals("PDL_HTTP_ERROR", log["error_code"].asText())
            assertTrue(log["retryable"].asBoolean())
            assertEquals(HENT_PERSONOPPLYSNINGER, log["operation"].asText())
            assertEquals(500, log["upstream_status"].asInt())
            assertFalse(log.has("pdl_errors"))
            assertPrivateCanariesAbsent(
                log,
                listOf(httpBodyCanary, fnrCanary, tokenCanary, endpointCanary),
            )
        }

        test("klassifiserer tokenhenting uten å logge vilkårlig exception") {
            val tokenExceptionCanary = "TOKEN_EXCEPTION_CANARY"
            coEvery { accessTokenClient.getAccessToken(any()) } throws
                IOException(tokenExceptionCanary)

            val logs =
                capturePdlLogs {
                    assertFailsWith<PdlPersonoppslagFailedException> {
                        pdlPersonService.getPerson(fnrCanary)
                    }
                }

            assertEquals(1, logs.size)
            val log = logs.single()
            assertEquals("ERROR", log["level"].asText())
            assertEquals(PDL_PERSONOPPSLAG_FAILED, log["event_type"].asText())
            assertEquals("PDL_ACCESS_TOKEN_FAILED", log["error_code"].asText())
            assertTrue(log["retryable"].asBoolean())
            assertEquals("IOException", log["cause_type"].asText())
            assertEquals(HENT_PERSONOPPLYSNINGER, log["operation"].asText())
            assertEquals("Klarte ikke å hente tilgangstoken for PDL", log["message"].asText())
            assertFalse(log.has("pdl_errors"))
            assertPrivateCanariesAbsent(log, listOf(tokenExceptionCanary, fnrCanary, tokenCanary))
        }

        test("lar uventet kodefeil fra tokenhenting gå til generell feilhåndtering") {
            val unexpected = IllegalStateException("UNEXPECTED_TOKEN_CODE_ERROR_CANARY")
            coEvery { accessTokenClient.getAccessToken(any()) } throws unexpected

            lateinit var thrown: IllegalStateException
            val logs =
                capturePdlLogs {
                    thrown =
                        assertFailsWith<IllegalStateException> {
                            pdlPersonService.getPerson(fnrCanary)
                        }
                }

            assertSame(unexpected, thrown)
            assertTrue(logs.isEmpty())
        }

        test("klassifiserer permanent HTTP-feil fra tokenhenting uten retry") {
            coEvery { accessTokenClient.getAccessToken(any()) } throws
                AccessTokenRequestFailedException(401)

            lateinit var thrown: PdlPersonoppslagFailedException
            val logs =
                capturePdlLogs {
                    thrown =
                        assertFailsWith<PdlPersonoppslagFailedException> {
                            pdlPersonService.getPerson(fnrCanary)
                        }
                }

            assertFalse(thrown.retryable)
            assertEquals(1, logs.size)
            val log = logs.single()
            assertEquals("PDL_ACCESS_TOKEN_FAILED", log["error_code"].asText())
            assertFalse(log["retryable"].asBoolean())
            assertEquals(401, log["upstream_status"].asInt())
            assertEquals("AccessTokenRequestFailedException", log["cause_type"].asText())
            assertPrivateCanariesAbsent(log, listOf(fnrCanary, tokenCanary))
        }

        test("klassifiserer ugyldig PDL-JSON som permanent uten å logge responsen") {
            val malformedResponseCanary = "MALFORMED_RESPONSE_CANARY"
            httpClient.respond {
                ResponseData(
                    httpStatusCode = HttpStatusCode.OK,
                    content = """{"$malformedResponseCanary":"""",
                )
            }

            lateinit var thrown: PdlPersonoppslagFailedException
            val logs =
                capturePdlLogs {
                    thrown =
                        assertFailsWith<PdlPersonoppslagFailedException> {
                            pdlPersonService.getPerson(fnrCanary)
                        }
                }

            assertFalse(thrown.retryable)
            assertEquals(1, logs.size)
            val log = logs.single()
            assertEquals("PDL_REQUEST_FAILED", log["error_code"].asText())
            assertFalse(log["retryable"].asBoolean())
            assertEquals("JsonConvertException", log["cause_type"].asText())
            assertPrivateCanariesAbsent(
                log,
                listOf(malformedResponseCanary, fnrCanary, tokenCanary),
            )
        }

        test("klassifiserer manglende navn uten errors som permanent") {
            httpClient.respond {
                ResponseData(
                    httpStatusCode = HttpStatusCode.OK,
                    content = """{"data":{"person":null}}""",
                )
            }

            val logs =
                capturePdlLogs {
                    assertFailsWith<PdlResponseIncompleteException> {
                        pdlPersonService.getPerson(fnrCanary)
                    }
                }

            assertEquals(1, logs.size)
            val log = logs.single()
            assertEquals(PDL_PERSONOPPSLAG_FAILED, log["event_type"].asText())
            assertEquals("PDL_RESPONSE_INCOMPLETE", log["error_code"].asText())
            assertFalse(log["retryable"].asBoolean())
            assertFalse(log.has("pdl_errors"))
            assertPrivateCanariesAbsent(log, listOf(fnrCanary, tokenCanary))
        }

        test("lar uventet kodefeil gå til generell feilhåndtering") {
            val unexpected = IllegalStateException("UNEXPECTED_CODE_ERROR_CANARY")
            val pdlClient = mockk<PdlClient>()
            coEvery { pdlClient.getPerson(any(), any()) } throws unexpected
            val service = PdlPersonService(pdlClient, accessTokenClient, "scope")

            lateinit var thrown: IllegalStateException
            val logs =
                capturePdlLogs {
                    thrown =
                        assertFailsWith<IllegalStateException> {
                            service.getPerson(fnrCanary)
                        }
                }

            assertSame(unexpected, thrown)
            assertTrue(logs.isEmpty())
        }

        test("logger ikke vellykket personoppslag") {
            httpClient.respond {
                ResponseData(
                    httpStatusCode = HttpStatusCode.OK,
                    content = getTestData(),
                )
            }

            val logs = capturePdlLogs { pdlPersonService.getPerson(fnrCanary) }

            assertTrue(logs.isEmpty())
        }

        test("propagerer cancellation uten å logge") {
            coEvery { accessTokenClient.getAccessToken(any()) } throws
                CancellationException("cancel")

            val logs =
                capturePdlLogs {
                    assertFailsWith<CancellationException> {
                        pdlPersonService.getPerson(fnrCanary)
                    }
                }

            assertTrue(logs.isEmpty())
        }

        test("mapper PDL-koder deterministisk til lukket error_code-katalog") {
            val documentedMappings =
                mapOf(
                    "unauthenticated" to PdlPersonoppslagErrorCode.PDL_UNAUTHENTICATED,
                    "unauthorized" to PdlPersonoppslagErrorCode.PDL_UNAUTHORIZED,
                    "not_found" to PdlPersonoppslagErrorCode.PDL_NOT_FOUND,
                    "bad_request" to PdlPersonoppslagErrorCode.PDL_BAD_REQUEST,
                    "server_error" to PdlPersonoppslagErrorCode.PDL_SERVER_ERROR,
                )

            documentedMappings.forEach { (runtimeCode, expectedCode) ->
                assertEquals(
                    expectedCode,
                    listOf(responseError(runtimeCode)).toPdlPersonoppslagErrorCode(),
                )
            }
            assertEquals(
                PdlPersonoppslagErrorCode.PDL_GRAPHQL_ERROR,
                listOf(
                    objectMapper.readTree("""{"message":"General GraphQL error"}"""),
                ).toPdlPersonoppslagErrorCode(),
            )
            assertEquals(
                PdlPersonoppslagErrorCode.PDL_UNKNOWN_ERROR,
                listOf(responseError("future_runtime_code")).toPdlPersonoppslagErrorCode(),
            )

            val multipleErrors =
                listOf(responseError("server_error"), responseError("unauthorized"))
            assertEquals(
                PdlPersonoppslagErrorCode.PDL_MULTIPLE_ERRORS,
                multipleErrors.toPdlPersonoppslagErrorCode(),
            )
            assertEquals(
                PdlPersonoppslagErrorCode.PDL_MULTIPLE_ERRORS,
                multipleErrors.reversed().toPdlPersonoppslagErrorCode(),
            )
            assertEquals(
                PdlPersonoppslagErrorCode.PDL_UNAUTHORIZED,
                listOf(responseError("unauthorized"), responseError("unauthorized"))
                    .toPdlPersonoppslagErrorCode(),
            )
            assertEquals(
                PdlPersonoppslagErrorCode.PDL_UNKNOWN_ERROR,
                listOf(objectMapper.readTree("""{"extensions":{"code":42}}"""))
                    .toPdlPersonoppslagErrorCode(),
            )

            val retryPolicy =
                mapOf(
                    PdlPersonoppslagErrorCode.PDL_UNAUTHENTICATED to false,
                    PdlPersonoppslagErrorCode.PDL_UNAUTHORIZED to false,
                    PdlPersonoppslagErrorCode.PDL_NOT_FOUND to false,
                    PdlPersonoppslagErrorCode.PDL_BAD_REQUEST to false,
                    PdlPersonoppslagErrorCode.PDL_SERVER_ERROR to true,
                    PdlPersonoppslagErrorCode.PDL_GRAPHQL_ERROR to false,
                    PdlPersonoppslagErrorCode.PDL_UNKNOWN_ERROR to false,
                    PdlPersonoppslagErrorCode.PDL_MULTIPLE_ERRORS to false,
                    PdlPersonoppslagErrorCode.PDL_RESPONSE_INCOMPLETE to false,
                )
            retryPolicy.forEach { (errorCode, expectedRetryable) ->
                assertEquals(expectedRetryable, errorCode.isRetryableGraphQlResponse())
            }
            assertEquals(
                setOf(
                    PdlPersonoppslagErrorCode.PDL_ACCESS_TOKEN_FAILED,
                    PdlPersonoppslagErrorCode.PDL_HTTP_ERROR,
                    PdlPersonoppslagErrorCode.PDL_REQUEST_FAILED,
                ),
                PdlPersonoppslagErrorCode.entries.toSet() - retryPolicy.keys,
            )

            assertTrue(PDL_PERSONOPPSLAG_FAILED.matches(Regex("^[a-z][a-z0-9_.-]{0,79}$")))
            assertTrue(
                PDL_PERSONOPPSLAG_PARTIAL_RESPONSE.matches(Regex("^[a-z][a-z0-9_.-]{0,79}$")),
            )
            assertTrue(PDL_PERSONOPPSLAG_NOT_FOUND.matches(Regex("^[a-z][a-z0-9_.-]{0,79}$")))
            assertTrue(HENT_PERSONOPPLYSNINGER.matches(Regex("^[a-z][a-z0-9_.-]{0,79}$")))
            PdlPersonoppslagErrorCode.entries.forEach { errorCode ->
                assertTrue(errorCode.name.matches(Regex("^[A-Z][A-Z0-9_]{1,79}$")))
            }
        }

        test("retryer bare transiente HTTP-statuser") {
            listOf(408, 425, 429, 500, 503, 599).forEach { statusCode ->
                assertTrue(PdlRequestFailedException(statusCode).retryable)
            }
            listOf(400, 401, 403, 404, 422, 600).forEach { statusCode ->
                assertFalse(PdlRequestFailedException(statusCode).retryable)
            }
        }
    })

private suspend fun capturePdlLogs(block: suspend () -> Unit): List<JsonNode> {
    val loggerContext = LoggerFactory.getILoggerFactory() as LoggerContext
    val outputStream = ByteArrayOutputStream()
    val encoder =
        LogstashEncoder().apply {
            context = loggerContext
            start()
        }
    val appender =
        OutputStreamAppender<ILoggingEvent>().apply {
            context = loggerContext
            name = "pdl-contract-${UUID.randomUUID()}"
            this.encoder = encoder
            setOutputStream(outputStream)
            start()
        }
    val pdlLogger = loggerContext.getLogger(PdlPersonService::class.java)
    pdlLogger.addAppender(appender)

    try {
        block()
    } finally {
        pdlLogger.detachAppender(appender)
        appender.stop()
        encoder.stop()
    }

    return outputStream
        .toString(Charsets.UTF_8)
        .lineSequence()
        .filter(String::isNotBlank)
        .map(objectMapper::readTree)
        .toList()
}

private fun assertPrivateCanariesAbsent(
    log: JsonNode,
    privateCanaries: List<String>,
) {
    val serializedLog = log.toString()
    privateCanaries.forEach { privateCanary ->
        assertFalse(serializedLog.contains(privateCanary))
    }
    listOf(
        "data",
        "query",
        "variables",
        "fnr",
        "token",
        "callId",
        "sykmeldingId",
    ).forEach { forbiddenField -> assertFalse(log.has(forbiddenField)) }
}

private fun responseError(code: String): JsonNode =
    objectMapper.readTree("""{"extensions":{"code":"$code"}}""")

private fun pdlResponseWithDataAndError(
    code: String,
    dataName: String,
    dataIdent: String,
    localContext: String,
    hasUsableName: Boolean,
): String {
    val person =
        if (hasUsableName) {
            """
            {
              "navn": [
                {
                  "fornavn": "$dataName",
                  "mellomnavn": null,
                  "etternavn": "DATA_ETTERNAVN_CANARY"
                }
              ]
            }
            """.trimIndent()
        } else {
            "null"
        }
    return """
        {
          "errors": [
            {
              "message": "DIAGNOSTIC_MESSAGE_CANARY",
              "locations": [{"line": 2, "column": 3}],
              "path": ["hentPerson"],
              "extensions": {
                "code": "$code",
                "id": "CURRENT_PDL_ERROR_ID",
                "details": {
                  "type": "DETAIL_TYPE_CANARY",
                  "cause": "DETAIL_CAUSE_CANARY",
                  "policy": "DETAIL_POLICY_CANARY"
                },
                "future_metadata": {
                  "nested": "FUTURE_NESTED_VALUE"
                },
                "classification": "ExecutionAborted"
              },
              "future_top_level": "FUTURE_TOP_LEVEL_VALUE"
            }
          ],
          "data": {
            "person": $person,
            "identer": {
              "identer": [
                {
                  "ident": "$dataIdent",
                  "gruppe": "AKTORID"
                }
              ]
            },
            "local_context": "$localContext"
          }
        }
        """.trimIndent()
}
