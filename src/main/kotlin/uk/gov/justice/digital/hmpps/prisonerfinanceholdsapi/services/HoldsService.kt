package uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.services

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.HoldRepository
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.clients.GeneralLedgerApiClient
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.config.CustomException
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.entities.HoldEntity
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.enums.SubAccountRef
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.generalledger.CreatePostingRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.generalledger.CreateTransactionRequest
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

  @Transactional(rollbackFor = [Exception::class, Error::class])
  fun sendTransactionToGLAndUpdateHold(holdEntity: HoldEntity, createHoldRequest: CreateHoldRequest): HoldResponse {
    var savedHold = holdRepository.save(holdEntity)
    if (holdEntity.holdTransactionId == null) {
      val transactionReq = CreateTransactionRequest(
        reference = "", // not set for holds
        description = holdEntity.description ?: "",
        timestamp = holdEntity.createdAt,
        amount = holdEntity.amount,
        entrySequence = 1,
        postings = listOf(
          CreatePostingRequest(
            type = CreatePostingRequest.Type.DR,
            subAccountId = createHoldRequest.prisonerSubAccountId,
            amount = holdEntity.amount,
            entrySequence = 1,
          ),
          CreatePostingRequest(
            type = CreatePostingRequest.Type.CR,
            subAccountId = createHoldRequest.prisonSubAccountId,
            amount = holdEntity.amount,
            entrySequence = 2,
          ),
        ),
        legacyTransactionId = createHoldRequest.holdLegacyTransactionId,
      )

      val idempotencyKey = UUID.randomUUID() // TODO add idempotency key service

      val transactionGLId = generalLedgerApiClient.postTransaction(
        transactionReq,
        idempotencyKey,
        transactionReq.legacyTransactionId,
      )
      savedHold.holdTransactionId = transactionGLId
      savedHold = holdRepository.saveAndFlush(savedHold)
    }

    return HoldResponse.fromEntity(savedHold)
  }

  fun createHoldOld(createHoldRequest: CreateHoldRequest): HoldResponse {
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
      return sendTransactionToGLAndUpdateHold(newHold, createHoldRequest)
    } catch (e: Exception) {
      val isDuplicateHold = e.message?.contains("uc_holds_legacy_hold_number") == true
      if (e is DataIntegrityViolationException && isDuplicateHold) {
        val previouslyCreatedHold = holdRepository.getHoldEntityByLegacyHoldNumber(createHoldRequest.legacyHoldNumber)!!
        return sendTransactionToGLAndUpdateHold(previouslyCreatedHold, createHoldRequest)
      }

      throw e
    }
  }

  fun createHold(createHoldRequest: CreateHoldRequest): HoldResponse {
    val holdEntity = holdRepository.getHoldEntityByLegacyHoldNumber(createHoldRequest.legacyHoldNumber)
    if (holdEntity != null) {
      return HoldResponse.fromEntity(holdEntity)
    }

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

    val idempotencyKey = UUID.randomUUID() // TODO add idempotency key service

    val transactionGLId = generalLedgerApiClient.postTransaction(
      transactionReq,
      idempotencyKey,
      transactionReq.legacyTransactionId,
    )

    val hold = HoldEntity(
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

    return HoldResponse.fromEntity(
      holdRepository.save(hold)
    )
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
