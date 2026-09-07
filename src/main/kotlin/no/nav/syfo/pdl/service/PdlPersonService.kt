package no.nav.syfo.pdl.service

import kotlinx.coroutines.CancellationException
import no.nav.syfo.azuread.AccessTokenClient
import no.nav.syfo.azuread.AccessTokenRequestFailedException
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.client.model.GetPersonResponse
import no.nav.syfo.pdl.exceptions.NameNotFoundInPdlException
import no.nav.syfo.pdl.exceptions.PdlPersonoppslagFailedException
import no.nav.syfo.pdl.exceptions.PdlRequestFailedException
import no.nav.syfo.pdl.exceptions.PdlResponseIncompleteException
import no.nav.syfo.pdl.model.Navn
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.util.logger

class PdlPersonService(
    private val pdlClient: PdlClient,
    private val accessTokenClient: AccessTokenClient,
    private val pdlScope: String,
) {
    private val personoppslagLogger = PdlPersonoppslagLogger(logger())

    suspend fun getPerson(fnr: String): PdlPerson {
        val accessToken = getAccessToken()
        val response = getPersonResponse(fnr, accessToken)
        return handlePersonResponse(response)
    }

    private suspend fun getAccessToken(): String =
        try {
            accessTokenClient.getAccessToken(pdlScope)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            val failure = exception.toAccessTokenFailure() ?: throw exception
            val errorMessage = "Klarte ikke å hente tilgangstoken for PDL"
            personoppslagLogger.failed(
                errorCode = PdlPersonoppslagErrorCode.PDL_ACCESS_TOKEN_FAILED,
                retryable = failure.retryable,
                upstreamStatus =
                    (exception as? AccessTokenRequestFailedException)?.statusCode,
                message = errorMessage,
                causeType = exception.javaClass.simpleName,
            )
            throw PdlPersonoppslagFailedException(
                message = errorMessage,
                retryable = failure.retryable,
                cause = exception,
            )
        }

    private suspend fun getPersonResponse(
        fnr: String,
        accessToken: String,
    ): GetPersonResponse =
        try {
            pdlClient.getPerson(fnr = fnr, token = accessToken)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            val failure = exception.toPdlRequestFailure() ?: throw exception
            personoppslagLogger.failed(
                errorCode = failure.errorCode,
                retryable = failure.retryable,
                upstreamStatus = (exception as? PdlRequestFailedException)?.statusCode,
                causeType = exception.javaClass.simpleName,
            )
            throw if (exception is PdlPersonoppslagFailedException) {
                exception
            } else {
                PdlPersonoppslagFailedException(
                    message = "Klarte ikke å hente personopplysninger fra PDL",
                    retryable = failure.retryable,
                    cause = exception,
                )
            }
        }

    private fun handlePersonResponse(response: GetPersonResponse): PdlPerson {
        val person = response.toPersonOrNull() ?: handleMissingPerson(response)
        val errors = response.errors.orEmpty()
        if (errors.isNotEmpty()) {
            personoppslagLogger.partialResponse(errors)
        }
        return person
    }

    private fun handleMissingPerson(response: GetPersonResponse): Nothing {
        val errors = response.errors.orEmpty()
        val errorCode =
            if (errors.isEmpty()) {
                PdlPersonoppslagErrorCode.PDL_RESPONSE_INCOMPLETE
            } else {
                errors.toPdlPersonoppslagErrorCode()
            }
        when {
            errors.isEmpty() ->
                personoppslagLogger.failed(
                    errorCode = errorCode,
                    retryable = errorCode.isRetryableGraphQlResponse(),
                    message = "PDL-responsen manglet nødvendige personopplysninger",
                )
            errorCode == PdlPersonoppslagErrorCode.PDL_NOT_FOUND ->
                personoppslagLogger.notFound(errors)
            else ->
                personoppslagLogger.failed(
                    pdlErrors = errors,
                    retryable = errorCode.isRetryableGraphQlResponse(),
                )
        }

        val incompleteResponse = PdlResponseIncompleteException("Fant ikke navn i PDL-respons")
        throw when {
            // Preserve the existing Kafka skip path for a response without a name.
            response.data != null -> NameNotFoundInPdlException("Fant ikke navn i PDL")
            errors.isEmpty() -> incompleteResponse
            errorCode == PdlPersonoppslagErrorCode.PDL_NOT_FOUND ->
                NameNotFoundInPdlException("Fant ikke person i PDL")
            else ->
                PdlPersonoppslagFailedException(
                    message = "PDL returnerte feil ved personoppslag",
                    retryable = errorCode.isRetryableGraphQlResponse(),
                    cause = incompleteResponse,
                )
        }
    }
}

private fun GetPersonResponse.toPersonOrNull(): PdlPerson? {
    val navn = data?.person?.navn?.firstOrNull() ?: return null
    return PdlPerson(
        navn =
            Navn(
                fornavn = navn.fornavn,
                mellomnavn = navn.mellomnavn,
                etternavn = navn.etternavn,
            ),
    )
}
