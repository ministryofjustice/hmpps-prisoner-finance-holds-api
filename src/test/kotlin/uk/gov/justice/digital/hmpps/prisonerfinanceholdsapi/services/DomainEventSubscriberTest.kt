package uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Spy
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.times
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.services.helpers.mockLogger
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class DomainEventSubscriberTest {

  @Mock
  private lateinit var holdsService: HoldsService

  @Spy
  private var objectMapper: ObjectMapper = jacksonObjectMapper().findAndRegisterModules()

  @InjectMocks
  private lateinit var domainEventSubscriber: DomainEventsSubscriber

  fun makeMergeEvent(removedPrisonNumber: String, survivedPrisonNumber: String): String {
    val eventType = "prison-offender-events.prisoner.merged"
    val payload =
      """{"eventType": "$eventType",
     "additionalInformation": {"nomsNumber" : "$survivedPrisonNumber",
     "removedNomsNumber" : "$removedPrisonNumber",
     "reason" : "test"}
  }"""
        .replace("\"", "\\\"").replace(Regex("\\s+"), " ")

    return """
    {
        "Type": "Notification",
        "MessageId": "5b90ee7d-67bc-5959-a4d8-b7d420180853",
        "Message":"$payload",
        "Timestamp": "2021-09-01T09:18:28.725Z",
        "MessageAttributes": {
            "eventType": {
                "Type": "String",
                "Value": "$eventType"
            }
        }
    }
  """
  }

  @Test
  fun `should throw exception when JSON is malformed to ensure message goes to DLQ`() {
    val malformedJson = "{ \"invalid\": \"json\" " // Missing closing brace

    assertThrows<Exception> {
      domainEventSubscriber.handleEvents(malformedJson)
    }
  }

  @Test
  fun `Log errors when we receive an event of the wrong type`() {
    val eventType = "wrong-event-type"
    val otherEventType = """
      {
      "Message": "{\"eventType\": \"$eventType\"}",
      "MessageId": "${UUID.randomUUID()}",
      "Timestamp": "2021-09-01T09:18:28.725Z"
      }
    """.trimIndent()

    val logger = mockLogger()

    domainEventSubscriber.handleEvents(otherEventType)

    assert(
      logger.events.any {
        it.formattedMessage.contains("Ignored unexpected event type: $eventType")
      },
    )
  }

  @Test
  fun `should call holds service when merge event is sent`() {
    val removedPrisonNumber = "A1234BC"
    val survivedPrisonNumber = "Z9874EF"

    doNothing().whenever(holdsService).mergeHolds(
      survivedPrisonNumber = survivedPrisonNumber,
      mergedPrisonNumber = removedPrisonNumber,
    )

    val mergeEventPayload = makeMergeEvent(
      removedPrisonNumber = removedPrisonNumber,
      survivedPrisonNumber = survivedPrisonNumber,
    )

    domainEventSubscriber.handleEvents(mergeEventPayload)

    verify(holdsService, times(1)).mergeHolds(
      survivedPrisonNumber = survivedPrisonNumber,
      mergedPrisonNumber = removedPrisonNumber,
    )
  }
}
