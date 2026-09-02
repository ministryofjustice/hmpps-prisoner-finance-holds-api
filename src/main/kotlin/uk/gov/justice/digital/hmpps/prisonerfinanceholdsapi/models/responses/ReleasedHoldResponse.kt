package uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.responses

import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.enums.SubAccountRef
import java.time.Instant
import java.util.UUID

data class ReleasedHoldResponse(
  val id: UUID,
  val prisonNumber: String,
  val subAccountRef: SubAccountRef,
  val amountReleased: Long,
  val releasedAt: Instant,
)
