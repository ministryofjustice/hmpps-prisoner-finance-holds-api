package uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.sqs

import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.entities.HoldEntity
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.enums.HoldType
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.enums.SubAccountRef
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.services.sqs.PayloadDataClass
import java.time.Instant

data class VerifyHoldTransaction (
  val prisonNumber: String,
  val subAccountRef: SubAccountRef,
  val amount: Long,
  val holdType: HoldType,
  val holdFromDate: Instant,
  val createdAt: Instant
): PayloadDataClass
{
  companion object {
    fun fromEntity(holdEntity: HoldEntity) = VerifyHoldTransaction(
      prisonNumber = holdEntity.prisonNumber,
      subAccountRef = holdEntity.subAccountRef,
      amount = holdEntity.amount,
      holdType = holdEntity.holdType,
      holdFromDate = holdEntity.holdFromDate,
      createdAt = holdEntity.createdAt
    )
  }
}