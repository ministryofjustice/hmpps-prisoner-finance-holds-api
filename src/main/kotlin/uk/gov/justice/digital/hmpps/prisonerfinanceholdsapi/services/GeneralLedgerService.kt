package uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.services

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.clients.GeneralLedgerApiClient
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.config.CustomException
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.entities.HoldEntity
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.generalledger.CreatePostingRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.generalledger.CreateTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.generalledger.SubAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.requests.ReleaseHoldRequest
import java.util.UUID

@Service
class GeneralLedgerService(val generalLedgerApiClient: GeneralLedgerApiClient) {

  fun resolveSubAccount(accountRef: String, subAccountRef: String): SubAccountResponse {
    val subAccountResponse = generalLedgerApiClient.findSubAccount(accountRef, subAccountRef)
      ?: throw CustomException("Sub account not found for: $accountRef:$subAccountRef", HttpStatus.NOT_FOUND)

    return subAccountResponse
  }

  fun createReleaseHoldTransaction(unreleasedHoldEntity: HoldEntity, releaseHoldRequest: ReleaseHoldRequest, idempotencyKey: UUID): UUID {
    val prisonerSubAccount = resolveSubAccount(unreleasedHoldEntity.prisonNumber, unreleasedHoldEntity.subAccountRef.toString())
    val prisonSubAccount = resolveSubAccount(unreleasedHoldEntity.holdLocation, "${HOLD_ACCOUNT_CODE}:${unreleasedHoldEntity.holdType.getReleaseType()}")

    val releaseTransactionRequest = CreateTransactionRequest(
      reference = "",
      description = "Remove Hold",
      timestamp = releaseHoldRequest.releaseDateTime,
      amount = unreleasedHoldEntity.amount,
      entrySequence = 1,
      postings = listOf(
        CreatePostingRequest(
          subAccountId = prisonSubAccount.id,
          type = CreatePostingRequest.Type.DR,
          amount = unreleasedHoldEntity.amount,
          entrySequence = 1,
        ),
        CreatePostingRequest(
          subAccountId = prisonerSubAccount.id,
          type = CreatePostingRequest.Type.CR,
          amount = unreleasedHoldEntity.amount,
          entrySequence = 2,
        ),
      ),
      legacyTransactionId = releaseHoldRequest.legacyTransactionId,
    )

    val generalLedgerTransactionId = generalLedgerApiClient.postTransaction(releaseTransactionRequest, idempotencyKey)

    return generalLedgerTransactionId
  }
}
