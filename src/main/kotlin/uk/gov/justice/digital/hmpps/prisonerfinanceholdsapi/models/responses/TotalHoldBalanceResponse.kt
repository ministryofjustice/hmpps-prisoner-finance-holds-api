package uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.responses
import java.time.Instant

data class TotalHoldBalanceResponse(
  val balanceDateTime: Instant,
  val amount: Long,
  val hoa: Long,
  val whf: Long,
)
