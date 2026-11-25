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
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.enums.HoldType
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.enums.SubAccountRef
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.generalledger.CreatePostingRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.generalledger.CreateTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.requests.CreateHoldMigrationRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.requests.CreateHoldRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.requests.ReleaseHoldRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.responses.HoldResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.responses.PagedResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.responses.ReleasedHoldResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.responses.TotalHoldBalanceResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.utils.toPageResponse
import java.time.Instant
import java.util.UUID

const val HOLD_ACCOUNT_CODE = "2199"

@Service
class HoldsService(
  val holdRepository: HoldRepository,
  val generalLedgerApiClient: GeneralLedgerApiClient,
  val generalLedgerService: GeneralLedgerService,
) {

  private fun saveCreateHoldTransactionToGL(
    createHoldRequest: CreateHoldRequest,
    idempotencyKey: UUID,
    prisonSubaccountId: UUID,
    prisonerSubaccountId: UUID,
  ): UUID {
    val transactionReq = CreateTransactionRequest(
      reference = "", // not set for holds
      description = createHoldRequest.description ?: "",
      timestamp = createHoldRequest.createdAt,
      amount = createHoldRequest.amount,
      entrySequence = 1,
      postings = listOf(
        CreatePostingRequest(
          type = CreatePostingRequest.Type.DR,
          subAccountId = prisonerSubaccountId,
          amount = createHoldRequest.amount,
          entrySequence = 1,
        ),
        CreatePostingRequest(
          type = CreatePostingRequest.Type.CR,
          subAccountId = prisonSubaccountId,
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

    val prisonerSubaccount = generalLedgerApiClient.findSubAccount(
      parentReference = createHoldRequest.prisonNumber,
      subAccountReference = createHoldRequest.subAccountRef.toString(),
    )

    if (prisonerSubaccount == null) {
      throw CustomException("Prisoner subaccount not found", HttpStatus.NOT_FOUND)
    }

    val prisonSubaccount = generalLedgerApiClient.findSubAccount(
      parentReference = createHoldRequest.holdLocation,
      subAccountReference = "2199:${createHoldRequest.holdType}",
    )

    if (prisonSubaccount == null) {
      throw CustomException("Prison subaccount not found", HttpStatus.NOT_FOUND)
    }

    val transactionGLId = saveCreateHoldTransactionToGL(
      createHoldRequest = createHoldRequest,
      idempotencyKey = idempotencyKey,
      prisonSubaccountId = prisonSubaccount.id,
      prisonerSubaccountId = prisonerSubaccount.id,
    )

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

  fun getHoldBalanceForAccount(prisonNumber: String): TotalHoldBalanceResponse {
    val holds = holdRepository.findByPrisonNumberAndIsReleasedFalse(
      prisonNumber = prisonNumber,
    )

    return calculateHoldBalances(holds)
  }

  fun getHoldBalanceForSubAccount(prisonNumber: String, subAccountRef: SubAccountRef): TotalHoldBalanceResponse {
    val holds = holdRepository.findByPrisonNumberAndSubAccountRefAndIsReleasedFalse(
      prisonNumber = prisonNumber,
      subAccountRef = subAccountRef,
    )

    return calculateHoldBalances(holds)
  }

  private fun calculateHoldBalances(holds: List<HoldEntity>): TotalHoldBalanceResponse {
    val (hoa, whf) = holds.partition { it.holdType == HoldType.HOA }

    val hoaBalance = hoa.sumOf { it.amount }
    val whfBalance = whf.sumOf { it.amount }
    val totalBalance = hoaBalance + whfBalance

    return TotalHoldBalanceResponse(balanceDateTime = Instant.now(), amount = totalBalance, hoa = hoaBalance, whf = whfBalance)
  }

  fun releaseHoldById(holdId: UUID, releaseHoldRequest: ReleaseHoldRequest, idempotencyKey: UUID): ReleasedHoldResponse {
    val holdToRelease = holdRepository.findHoldEntityById(holdId)
      ?: throw CustomException(status = HttpStatus.NOT_FOUND, message = "Hold not found")

    if (holdToRelease.isReleased) {
      return ReleasedHoldResponse(
        id = holdId,
        prisonNumber = holdToRelease.prisonNumber,
        subAccountRef = holdToRelease.subAccountRef,
        amountReleased = holdToRelease.amount,
        releasedAt = holdToRelease.releasedAt!!,
        releasedTransactionId = holdToRelease.releasedTransactionId!!,
        updatedBy = holdToRelease.updatedBy,
      )
    }

    val releaseTransactionUUID = generalLedgerService.createReleaseHoldTransaction(holdToRelease, releaseHoldRequest, idempotencyKey)

    holdToRelease.releasedTransactionId = releaseTransactionUUID
    holdToRelease.isReleased = true
    holdToRelease.releasedAt = releaseHoldRequest.releaseDateTime
    holdToRelease.updatedBy = releaseHoldRequest.updatedBy

    holdRepository.save(holdToRelease)

    return ReleasedHoldResponse(
      id = holdId,
      prisonNumber = holdToRelease.prisonNumber,
      subAccountRef = holdToRelease.subAccountRef,
      amountReleased = holdToRelease.amount,
      releasedAt = holdToRelease.releasedAt!!,
      releasedTransactionId = releaseTransactionUUID!!,
      updatedBy = holdToRelease.updatedBy,
    )
  }

  fun getActiveHolds(prisonNumber: String, pageNumber: Int, pageSize: Int, holdType: HoldType? = null): PagedResponse<HoldResponse> {
    val zeroIndexedPage: Int = pageNumber - 1

    val pagedRequest = PageRequest.of(
      zeroIndexedPage,
      pageSize,
      Sort.by(
        Sort.Order.desc("createdAt"),
        Sort.Order.desc("id"),
      ),
    )

    return holdRepository.findByPrisonNumberAndIsReleasedFalse(prisonNumber, pagedRequest, holdType)
      .toPageResponse { content ->
        content.map { HoldResponse.fromEntity(it) }
      }
  }

  fun mergeHolds(survivedPrisonNumber: String, mergedPrisonNumber: String) {
    val holds = holdRepository.findHoldEntityByPrisonNumber(prisonNumber = mergedPrisonNumber)

    holds.forEach { hold ->
      hold.prisonNumber = survivedPrisonNumber
      hold.updatedBy = "DPS-Merge-Event"
    }
    holdRepository.saveAll(holds)
  }
}
