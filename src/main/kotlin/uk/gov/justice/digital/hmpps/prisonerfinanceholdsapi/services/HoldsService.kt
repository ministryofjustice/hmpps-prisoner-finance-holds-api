package uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.services

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.HoldRepository
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.entities.HoldEntity
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.enums.SubAccountRef
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.requests.CreateHoldRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.responses.HoldBalanceResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.responses.HoldResponse
import java.util.UUID

@Service
class HoldsService(val holdRepository: HoldRepository) {

  fun createHold(createHoldRequest: CreateHoldRequest): HoldResponse {
    val newHold = HoldEntity(
      id = UUID.randomUUID(),
      prisonNumber = createHoldRequest.prisonNumber,
      legacyHoldNumber = createHoldRequest.legacyHoldNumber,
      subAccountRef = createHoldRequest.subAccountRef,
      createdAt = createHoldRequest.createdAt,
      createdBy = createHoldRequest.createdBy,
      holdFromDate = createHoldRequest.holdFromDate,
      holdUntilDate = createHoldRequest.holdUntilDate,
      isReleased = createHoldRequest.isReleased,
      description = createHoldRequest.description,
      holdType = createHoldRequest.holdType,
      amount = createHoldRequest.amount,
      holdLocation = createHoldRequest.holdLocation,
    )
    val savedHold = holdRepository.save(newHold)
    return HoldResponse.fromEntity(savedHold)
  }

  fun getHoldBalanceForAccount(prisonNumber: String): HoldBalanceResponse {
    val response = holdRepository.getHoldBalanceForAccount(prisonNumber)
    return HoldBalanceResponse(response.balanceDateTime, response.amount)
  }

  fun getHoldBalanceForSubAccount(prisonNumber: String, subAccountRef: SubAccountRef): HoldBalanceResponse {
    val response = holdRepository.getHoldBalanceForSubAccount(prisonNumber, subAccountRef)
    return HoldBalanceResponse(response.balanceDateTime, response.amount)
  }
}
