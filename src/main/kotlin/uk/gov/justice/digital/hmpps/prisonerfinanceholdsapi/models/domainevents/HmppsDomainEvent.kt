package uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.domainevents

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant
import java.util.UUID

@JsonIgnoreProperties(ignoreUnknown = true)
open class HmppsDomainEvent(
  @JsonProperty("eventType") open val eventType: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Event(
  @JsonProperty("Message")
  val message: String,

  @JsonProperty("MessageId")
  val messageId: UUID,

  @JsonProperty("Timestamp")
  val timestamp: Instant,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class HmppsMergeEvent(
  @JsonProperty("eventType")
  override val eventType: String,
  @JsonProperty("additionalInformation")
  val additionalInformation: AdditionalInformation,
) : HmppsDomainEvent(eventType)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AdditionalInformation(
  @JsonProperty("nomsNumber")
  val nomsNumber: String,
  @JsonProperty("removedNomsNumber")
  val removedNomsNumber: String,
  @JsonProperty("reason")
  val reason: String? = null,
)

const val PRISONER_ACCOUNT_MERGED = "prison-offender-events.prisoner.merged"
