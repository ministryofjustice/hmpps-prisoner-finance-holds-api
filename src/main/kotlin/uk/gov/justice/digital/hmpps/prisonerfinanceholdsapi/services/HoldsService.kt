package uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.services

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.HoldRepository
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.clients.GeneralLedgerApiClient
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.config.CustomException
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.entities.HoldEntity
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.enums.SubAccountRef
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.generalledger.CreatePostingRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.generalledger.CreateTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.requests.CreateHoldMigrationRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.requests.CreateHoldRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.responses.HoldBalanceResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.responses.HoldResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.responses.PagedResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.responses.ReleasedHoldResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.utils.toPageResponse
import java.time.Instant
import java.util.UUID

@Service
class HoldsService(
  val holdRepository: HoldRepository,
  val generalLedgerApiClient: GeneralLedgerApiClient,
) {

  private fun saveHoldTransactionToGL(createHoldRequest: CreateHoldRequest, idempotencyKey: UUID): UUID {
    val transactionReq = CreateTransactionRequest(
      reference = "", // not set for holds
      description = createHoldRequest.description ?: "",
      timestamp = createHoldRequest.createdAt,
      amount = createHoldRequest.amount,
      entrySequence = 1,
      postings = listOf(
        CreatePostingRequest(
          type = CreatePostingRequest.Type.DR,
          subAccountId = createHoldRequest.prisonerSubAccountId,
          amount = createHoldRequest.amount,
          entrySequence = 1,
        ),
        CreatePostingRequest(
          type = CreatePostingRequest.Type.CR,
          subAccountId = createHoldRequest.prisonSubAccountId,
          amount = createHoldRequest.amount,
          entrySequence = 2,
        ),
      ),
      legacyTransactionId = createHoldRequest.holdLegacyTransactionId,
    )

    return generalLedgerApiClient.postTransaction(
      transactionReq,
      idempotencyKey,
      transactionReq.legacyTransactionId,
    )
  }

  fun migrateHold(holdMigrationRequest: CreateHoldMigrationRequest): HoldEntity {
    val migratedHold = HoldEntity(
      id = UUID.randomUUID(),
      prisonNumber = holdMigrationRequest.prisonNumber,
      legacyHoldNumber = holdMigrationRequest.legacyHoldNumber,
      subAccountRef = holdMigrationRequest.subAccountRef,
      createdAt = holdMigrationRequest.createdAt,
      createdBy = holdMigrationRequest.createdBy,
      holdFromDate = holdMigrationRequest.holdFromDate,
      holdUntilDate = holdMigrationRequest.holdUntilDate,
      isReleased = holdMigrationRequest.isReleased,
      description = holdMigrationRequest.description,
      holdType = holdMigrationRequest.holdType,
      amount = holdMigrationRequest.amount,
      holdLocation = holdMigrationRequest.holdLocation,
      holdTransactionId = holdMigrationRequest.holdTransactionId,
      releasedTransactionId = holdMigrationRequest.releasedTransactionId,
    )

    return saveOrGetExistingHoldEntity(migratedHold, migratedHold.legacyHoldNumber)
  }

  private fun saveOrGetExistingHoldEntity(holdEntity: HoldEntity, legacyHoldNumber: Long): HoldEntity {
    try {
        return holdRepository.save(holdEntity)
    } catch (e: Exception) {
      val isDuplicateHold = e.message?.contains("uc_holds_legacy_hold_number") == true
      if (e is DataIntegrityViolationException && isDuplicateHold) {
        val holdEntity = holdRepository.getHoldEntityByLegacyHoldNumber(legacyHoldNumber)
          ?: throw Exception("Unexpected hold not found after duplicate data integrity violation")
        return holdEntity
      }
      throw e
    }
  }

  fun createHold(createHoldRequest: CreateHoldRequest, idempotencyKey: UUID): HoldEntity {
    val existingHold = holdRepository.getHoldEntityByLegacyHoldNumber(createHoldRequest.legacyHoldNumber)
    if (existingHold != null) {
      return existingHold
    }

    val transactionGLId = saveHoldTransactionToGL(createHoldRequest, idempotencyKey)

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
      holdTransactionId = transactionGLId,
    )

    return saveOrGetExistingHoldEntity(newHold, createHoldRequest.legacyHoldNumber)
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

  fun getActiveHolds(prisonNumber: String, pageNumber: Int, pageSize: Int): PagedResponse<HoldResponse> {
    val zeroIndexedPage: Int = pageNumber - 1

    val pagedRequest = PageRequest.of(
      zeroIndexedPage,
      pageSize,
      Sort.by(
        Sort.Order.desc("createdAt"),
        Sort.Order.desc("id"),
      ),
    )

    return holdRepository.findByPrisonNumberAndIsReleasedFalse(prisonNumber, pagedRequest)
      .toPageResponse { content ->
        content.map { HoldResponse.fromEntity(it) }
      }
  }
}
