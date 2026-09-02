package uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.services

import org.springframework.http.HttpStatus
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.HoldRepository
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.config.CustomException
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.entities.HoldEntity
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.enums.SubAccountRef
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.requests.CreateHoldRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.responses.HoldBalanceResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.responses.HoldResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.responses.ReleasedHoldResponse
import java.time.Instant
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

  fun getHoldBalanceForAccount(prisonNumber: String): HoldBalanceResponse {
    val amount = holdRepository.findByPrisonNumberAndIsReleasedFalse(
      prisonNumber = prisonNumber,
    ).sumOf { it.amount }
    return HoldBalanceResponse(Instant.now(), amount)
  }

  fun getHoldBalanceForSubAccount(prisonNumber: String, subAccountRef: SubAccountRef): HoldBalanceResponse {
    val amount = holdRepository.findByPrisonNumberAndSubAccountRefAndIsReleasedFalse(
      prisonNumber = prisonNumber,
      subAccountRef = subAccountRef,
    ).sumOf { it.amount }
    return HoldBalanceResponse(Instant.now(), amount)
  }

  fun releaseHoldById(holdId: UUID, releaseTime: Instant): ReleasedHoldResponse {
    val holdToRelease = holdRepository.findHoldEntityById(holdId)
      ?: throw CustomException(status = HttpStatus.NOT_FOUND, message = "Hold not found")

    if (!holdToRelease.isReleased) {
      holdToRelease.isReleased = true
      holdToRelease.releasedAt = releaseTime
      holdRepository.save(holdToRelease)
    }

    return ReleasedHoldResponse(
      id = holdId,
      prisonNumber = holdToRelease.prisonNumber,
      subAccountRef = holdToRelease.subAccountRef,
      amountReleased = holdToRelease.amount,
      releasedAt = holdToRelease.releasedAt!!,
    )
  }
}
