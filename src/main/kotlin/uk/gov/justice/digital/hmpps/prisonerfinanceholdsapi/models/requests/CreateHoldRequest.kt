package uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.requests

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.enums.HoldType
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.enums.SubAccountRef
import java.time.Instant

data class CreateHoldRequest(
  @field:NotNull
  @field:Schema(description = "The prison number as seen in services like DPS", example = "A1999EC", required = true)
  val prisonNumber: String,

  @field:NotNull
  @field:Schema(description = "The hold number as seen in NOMIS", example = "12345789", required = true)
  val legacyHoldNumber: Long,

  @field:NotNull
  @field:Schema(description = "The sub account reference as seen in Prisoner Finance", example = "CASH", required = true)
  val subAccountRef: SubAccountRef,

  @field:NotNull
  @field:Schema(description = "The time the Hold was created", example = "2026-08-21T15:05:29.426237729Z", required = true)
  val createdAt: Instant,

  @field:Schema(description = "The person who created the Hold", example = "2026-08-21T15:05:29.426237729Z", required = true)
  val createdBy: String,

  @field:NotNull
  @field:Schema(description = "The date that the hold is active from", example = "2026-08-21T15:05:29.426237729Z", required = true)
  val holdFromDate: Instant,

  @field:Schema(description = "The date that the hold is expected to be removed", example = "2026-08-21T15:05:29.426237729Z", required = false)
  val holdUntilDate: Instant? = null,

  @field:NotNull
  @field:Schema(description = "A boolean indicating whether the hold has been released", example = "false", required = true)
  val isReleased: Boolean = false,

  @field:Schema(description = "A description of the reason for the hold", example = "Damages to cell", required = false)
  val description: String? = null,

  @field:NotNull
  @field:Schema(description = "The type of transaction that initiated the hold", example = "WHF", required = true)
  val holdType: HoldType,

  @field:NotNull
  @field:Schema(description = "The amount to hold from the sub account in pence", example = "1023", required = true)
  val amount: Long,

  @field:NotNull
  @field:Schema(description = "The location of the hold as a prison code", example = "LEI", required = true)
  val holdLocation: String,
)
