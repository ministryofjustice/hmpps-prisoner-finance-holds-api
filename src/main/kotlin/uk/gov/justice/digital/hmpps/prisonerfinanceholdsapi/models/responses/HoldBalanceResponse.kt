package uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.responses
import java.time.Instant

interface IHoldBalanceResponse {
  val balanceDateTime: Instant
  val amount: Long
}

data class HoldBalanceResponse(
  override val balanceDateTime: Instant,
  override val amount: Long,
) : IHoldBalanceResponse
