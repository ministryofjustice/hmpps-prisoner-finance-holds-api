package uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.responses
import java.time.Instant

data class HoldBalanceResponse(
  val balanceDateTime: Instant,
  val amount: Long,
)
