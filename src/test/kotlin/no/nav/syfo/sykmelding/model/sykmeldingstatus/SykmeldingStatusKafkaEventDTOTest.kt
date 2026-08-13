package no.nav.syfo.sykmelding.model.sykmeldingstatus

import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.core.spec.style.FunSpec
import no.nav.syfo.util.objectMapper
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEqualTo

class SykmeldingStatusKafkaEventDTOTest :
    FunSpec(
        {
            test("deserializes brukerSvar as a scalar value") {
                val event =
                    objectMapper.readValue<SykmeldingStatusKafkaEventDTO>(
                        SCALAR_BRUKER_SVAR,
                    )

                event.brukerSvar?.asText() shouldBeEqualTo "JA"
            }

            test("deserializes brukerSvar with unknown object properties") {
                val event =
                    objectMapper.readValue<SykmeldingStatusKafkaEventDTO>(
                        OBJECT_BRUKER_SVAR,
                    )

                event.brukerSvar?.isObject shouldBe true
                event.brukerSvar?.get("unknownProperty")?.asText() shouldBeEqualTo "value"
                event.brukerSvar
                    ?.get(
                        "nested",
                    )?.get("anotherUnknownProperty")
                    ?.asInt() shouldBeEqualTo
                    42
            }
        },
    )

private const val SCALAR_BRUKER_SVAR =
    """
    {
      "sykmeldingId": "sykmelding-id",
      "timestamp": "2026-08-13T10:00:00Z",
      "statusEvent": "SENDT",
      "brukerSvar": "JA"
    }
    """

private const val OBJECT_BRUKER_SVAR =
    """
    {
      "sykmeldingId": "sykmelding-id",
      "timestamp": "2026-08-13T10:00:00Z",
      "statusEvent": "SENDT",
      "brukerSvar": {
        "unknownProperty": "value",
        "nested": {
          "anotherUnknownProperty": 42
        }
      }
    }
    """
