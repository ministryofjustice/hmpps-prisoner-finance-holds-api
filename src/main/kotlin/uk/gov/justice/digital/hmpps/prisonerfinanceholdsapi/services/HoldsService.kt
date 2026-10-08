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
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.requests.ReleaseHoldRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.responses.HoldBalanceResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.responses.HoldResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.responses.PagedResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.responses.ReleasedHoldResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.utils.toPageResponse
import java.time.Instant
import java.util.UUID

const val HOLD_ACCOUNT_CODE = "2199"

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
      prisonSubAccountId = holdMigrationRequest.prisonSubAccountId,
      prisonerSubAccountId = holdMigrationRequest.prisonerSubAccountId,
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
      prisonSubAccountId = createHoldRequest.prisonSubAccountId,
      prisonerSubAccountId = createHoldRequest.prisonerSubAccountId,
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

  fun releaseHoldById(holdId: UUID, releaseHoldRequest: ReleaseHoldRequest, idempotencyKey: UUID): ReleasedHoldResponse {
    val holdToRelease = holdRepository.findHoldEntityById(holdId)
      ?: throw CustomException(status = HttpStatus.NOT_FOUND, message = "Hold not found")

    if (!holdToRelease.isReleased) {
      val prisonerSubAccount = generalLedgerApiClient.findSubAccount(
        parentReference = holdToRelease.prisonNumber,
        subAccountReference = holdToRelease.subAccountRef.toString(),
      )

      if (prisonerSubAccount == null) {
        throw CustomException(status = HttpStatus.NOT_FOUND, message = "Prisoner subaccount not found")
      }

      val prisonSubAccount = generalLedgerApiClient.findSubAccount(
        parentReference = holdToRelease.holdLocation,
        subAccountReference = "${HOLD_ACCOUNT_CODE}:${holdToRelease.holdType.getReleaseType()}",
      )

      if (prisonSubAccount == null) {
        throw CustomException(status = HttpStatus.NOT_FOUND, message = "Prison subaccount not found")
      }

      try {
        val releaseTransactionId = generalLedgerApiClient.postTransaction(
          CreateTransactionRequest(
            reference = "",
            description = "Remove Hold",
            timestamp = releaseHoldRequest.releaseDateTime,
            amount = holdToRelease.amount,
            entrySequence = 1,
            postings = listOf(
              CreatePostingRequest(
                subAccountId = prisonerSubAccount.id,
                type = CreatePostingRequest.Type.CR,
                amount = holdToRelease.amount,
                entrySequence = 1,
              ),
              CreatePostingRequest(
                subAccountId = prisonSubAccount.id,
                type = CreatePostingRequest.Type.DR,
                amount = holdToRelease.amount,
                entrySequence = 2,
              ),
            ),
            legacyTransactionId = releaseHoldRequest.legacyTransactionId,
          ),
          idempotencyKey = idempotencyKey,
        )

        holdToRelease.releasedTransactionId = releaseTransactionId
      } catch (e: Exception) {
        throw CustomException(status = HttpStatus.BAD_REQUEST, message = "Release transaction failed")
      }

      holdToRelease.isReleased = true
      holdToRelease.releasedAt = releaseHoldRequest.releaseDateTime

      holdRepository.save(holdToRelease)
    }

    return ReleasedHoldResponse(
      id = holdId,
      prisonNumber = holdToRelease.prisonNumber,
      subAccountRef = holdToRelease.subAccountRef,
      amountReleased = holdToRelease.amount,
      releasedAt = holdToRelease.releasedAt!!,
      releasedTransactionId = holdToRelease.releasedTransactionId!!,
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
