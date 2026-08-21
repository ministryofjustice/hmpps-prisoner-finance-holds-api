package uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.requests

import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.enums.HoldType
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.enums.SubAccountType
import java.time.Instant

data class CreateHoldRequest (
  val prisonNumber: String,

  val legacyHoldNumber: Long,

  val subAccountCode: SubAccountType,

  val createdAt: Instant,

  val createdBy: String,

  val holdFromDate : Instant,

  val holdUntilDate :Instant? = null,

  val isReleased : Boolean = false,

  val description: String? = null,

  val holdType: HoldType,

  val amount: Long
)