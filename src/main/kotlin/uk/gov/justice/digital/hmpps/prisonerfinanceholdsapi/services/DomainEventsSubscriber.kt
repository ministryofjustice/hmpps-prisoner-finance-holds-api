package uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.services

import com.fasterxml.jackson.databind.ObjectMapper
import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.domainevents.Event
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.domainevents.HmppsDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.domainevents.HmppsMergeEvent
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.domainevents.PRISONER_ACCOUNT_MERGED

@Service
class DomainEventsSubscriber(
  @Autowired private val objectMapper: ObjectMapper,
  @Autowired private val holdsService: HoldsService,
) {
  @SqsListener(
    "domainevents",
    factory = "hmppsQueueContainerFactoryProxy",
    maxMessagesPerPoll = "1",
    maxConcurrentMessages = "1",
  )
  fun handleEvents(requestJson: String?) {
    try {
      log.info("Received event: $requestJson")
      val event = objectMapper.readValue(requestJson, Event::class.java)
      val domainEvent = objectMapper.readValue(event.message, HmppsDomainEvent::class.java)

      when (domainEvent.eventType) {
        PRISONER_ACCOUNT_MERGED -> {
          val prisonerMerged = objectMapper.readValue(event.message, HmppsMergeEvent::class.java)

          log.info("Merged event: $prisonerMerged")

          holdsService.mergeHolds(
            survivedPrisonNumber = prisonerMerged.additionalInformation.nomsNumber,
            mergedPrisonNumber = prisonerMerged.additionalInformation.removedNomsNumber,
          )
        }

        else -> {
          log.warn("Ignored unexpected event type: ${domainEvent.eventType}")
        }
      }
    } catch (e: Exception) {
      log.error("Failed to process domain event. Message will be retried. Payload: $requestJson", e)
      throw e
    }
  }

  companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
