package uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.services

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.HoldRepository
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.entities.HoldEntity
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.requests.CreateHoldRequest
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
    try {
      val savedHold = holdRepository.save(newHold)
      return HoldResponse.fromEntity(savedHold)
    } catch (e: Exception) {
      val isDuplicateHold = e.message?.contains("uc_holds_legacy_hold_number") == true
      if (e is DataIntegrityViolationException && isDuplicateHold) {
        val previouslyCreatedHold = holdRepository.getHoldEntityByLegacyHoldNumber(createHoldRequest.legacyHoldNumber)
        return HoldResponse.fromEntity(previouslyCreatedHold!!)
      }
      throw e
    }
  }
}
