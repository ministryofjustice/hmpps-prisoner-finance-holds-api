package uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.responses

import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.entities.HoldEntity
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.enums.HoldType
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.enums.SubAccountRef
import java.time.Instant
import java.util.UUID

data class HoldResponse(
  val id: UUID,

  val prisonNumber: String,

  val legacyHoldNumber: Long,

  val subAccountRef: SubAccountRef,

  val createdAt: Instant,

  val createdBy: String,

  val holdFromDate: Instant,

  val holdUntilDate: Instant? = null,

  val isReleased: Boolean = false,

  val description: String? = null,

  val holdType: HoldType,

  val amount: Long,

  val holdLocation: String,
) {
  companion object {

    fun fromEntity(holdEntity: HoldEntity): HoldResponse = HoldResponse(
      id = holdEntity.id,
      prisonNumber = holdEntity.prisonNumber,
      legacyHoldNumber = holdEntity.legacyHoldNumber,
      subAccountRef = holdEntity.subAccountRef,
      createdAt = holdEntity.createdAt,
      createdBy = holdEntity.createdBy,
      holdFromDate = holdEntity.holdFromDate,
      holdUntilDate = holdEntity.holdUntilDate,
      isReleased = holdEntity.isReleased,
      description = holdEntity.description,
      holdType = holdEntity.holdType,
      amount = holdEntity.amount,
      holdLocation = holdEntity.holdLocation,
    )
  }
}
