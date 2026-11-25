package uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.services

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.clients.GeneralLedgerApiClient
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.config.CustomException
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.entities.HoldEntity
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.enums.HoldType
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.enums.SubAccountRef
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.generalledger.CreatePostingRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.generalledger.CreateTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.generalledger.SubAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.requests.ReleaseHoldRequest
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class GeneralLedgerServiceTest {

  @Mock
  private lateinit var generalLedgerApiClient: GeneralLedgerApiClient

  @InjectMocks
  private lateinit var generalLedgerService: GeneralLedgerService

  @Nested
  inner class ResolveAccount {

    val accountUUID = UUID.randomUUID()
    val subAccountUUID = UUID.randomUUID()
    val subAccountResponse = SubAccountResponse(
      id = subAccountUUID,
      reference = "CASH",
      parentAccountId = accountUUID,
      createdBy = "JOHN_USER",
      createdAt = Instant.now(),
    )

    @Test
    fun `should return sub account if exists`() {
      val accountRef = "A1234AA"
      val subAccountRef = "CASH"

      whenever { generalLedgerApiClient.findSubAccount(accountRef, subAccountRef) }.thenReturn(subAccountResponse)

      val result = generalLedgerService.resolveSubAccount(accountRef, subAccountRef)

      assertThat(result).isEqualTo(subAccountResponse)
    }

    @Test
    fun `should throw a 404 if the prisoner subaccount cannot be found`() {
      whenever {
        generalLedgerApiClient.findSubAccount(
          any(),
          any(),
        )
      }.thenReturn(null)

      val exception = assertThrows<CustomException> {
        generalLedgerService.resolveSubAccount("LEI", "2199:HOA")
      }

      assertThat(exception.status).isEqualTo(HttpStatus.NOT_FOUND)
    }
  }

  @Nested
  inner class PostReleaseHoldTransaction {

    val updatedBy = "UPDATED_USER"
    val releaseDateTime: Instant = Instant.now()
    val idempotencyKey: UUID = UUID.randomUUID()
    val holdId: UUID = UUID.randomUUID()

    val prisonNumber = "A12345BC"
    val prisonCode = "LEI"
    val unreleasedHoldEntity = HoldEntity(
      id = holdId,
      prisonNumber = prisonNumber,
      legacyHoldNumber = 1,
      subAccountRef = SubAccountRef.SPENDS,
      createdAt = Instant.now(),
      createdBy = "",
      holdFromDate = Instant.now(),
      holdUntilDate = Instant.now().plusSeconds(1),
      isReleased = false,
      description = "",
      holdType = HoldType.HOA,
      amount = 1,
      holdLocation = prisonCode,
      releasedAt = null,
      holdTransactionId = UUID.randomUUID(),
      releasedTransactionId = null,
    )

    val releaseHoldType = unreleasedHoldEntity.holdType.getReleaseType()

    val prisonerSubAccountRef = "SPENDS"

    val prisonerSubAccountResponse = SubAccountResponse(
      id = UUID.randomUUID(),
      reference = prisonerSubAccountRef,
      parentAccountId = UUID.randomUUID(),
      createdBy = "JOHN_USER",
      createdAt = Instant
        .now(),
    )

    val prisonSubAccountResponse = SubAccountResponse(
      id = UUID.randomUUID(),
      reference = "2199:$releaseHoldType",
      parentAccountId = UUID.randomUUID(),
      createdBy = "JOHN_USER",
      createdAt = Instant
        .now(),
    )

    val releaseHoldRequest = ReleaseHoldRequest(releaseDateTime, legacyTransactionId = 12345L, updatedBy)
    val glTransactionId = UUID.randomUUID()

    @Nested
    inner class SuccessfulCases {
      @BeforeEach
      fun setup() {
        whenever(
          generalLedgerApiClient.findSubAccount(
            any(),
            any(),
          ),
        )
          .thenReturn(prisonerSubAccountResponse)
          .thenReturn(prisonSubAccountResponse)

        whenever {
          generalLedgerApiClient.postTransaction(
            request = any(),
            idempotencyKey = any(),
          )
        }.thenReturn(glTransactionId)
      }

      @Test
      fun `should try to resolve the prisoner sub account`() {
        generalLedgerService.createReleaseHoldTransaction(unreleasedHoldEntity, releaseHoldRequest, idempotencyKey)

        verify(generalLedgerApiClient, times(1)).findSubAccount(
          parentReference = eq(prisonNumber),
          subAccountReference = eq(prisonerSubAccountRef),
        )
      }

      @ParameterizedTest
      @EnumSource(HoldType::class)
      fun `should resolve the correct prison sub account`(holdType: HoldType) {
        val hold = unreleasedHoldEntity.copy(holdType = holdType)
        generalLedgerService.createReleaseHoldTransaction(hold, releaseHoldRequest, idempotencyKey)

        verify(generalLedgerApiClient, times(1)).findSubAccount(
          parentReference = eq(prisonCode),
          subAccountReference = eq("2199:${holdType.getReleaseType()}"),
        )
      }

      @ParameterizedTest
      @EnumSource(HoldType::class)
      fun `should construct the transaction correctly and post it`(holdType: HoldType) {
        val hold = unreleasedHoldEntity.copy(holdType = holdType)
        val response = generalLedgerService.createReleaseHoldTransaction(hold, releaseHoldRequest, idempotencyKey)

        val expectedGLCreateTransactionRequest = CreateTransactionRequest(
          reference = "",
          description = "Remove Hold",
          timestamp = releaseHoldRequest.releaseDateTime,
          amount = hold.amount,
          entrySequence = 1,
          postings = listOf(
            CreatePostingRequest(
              subAccountId = prisonSubAccountResponse.id,
              type = CreatePostingRequest.Type.DR,
              amount = hold.amount,
              entrySequence = 1,
            ),
            CreatePostingRequest(
              subAccountId = prisonerSubAccountResponse.id,
              type = CreatePostingRequest.Type.CR,
              amount = hold.amount,
              entrySequence = 2,
            ),

          ),
          legacyTransactionId = releaseHoldRequest.legacyTransactionId,
        )

        val postTransactionCaptor = argumentCaptor<CreateTransactionRequest>()
        val idempotencyCaptor = argumentCaptor<UUID>()

        verify(generalLedgerApiClient, times(1)).postTransaction(postTransactionCaptor.capture(), idempotencyCaptor.capture())

        val postingTransaction = postTransactionCaptor.firstValue
        val idempotency = idempotencyCaptor.firstValue

        assertThat(postingTransaction).isEqualTo(expectedGLCreateTransactionRequest)
        assertThat(idempotency).isEqualTo(idempotencyKey)

        assertThat(response).isEqualTo(glTransactionId)
      }
    }

    @Nested
    inner class ErrorCases {

      @Test
      fun `should throw exception if findSubAccount fails`() {
        whenever(generalLedgerApiClient.findSubAccount(any(), any())).thenReturn(null)

        assertThrows<CustomException> {
          generalLedgerService.createReleaseHoldTransaction(unreleasedHoldEntity, releaseHoldRequest, idempotencyKey)
        }
      }
    }

    @Test
    fun `should throw exception if postTransaction fails`() {
      whenever(
        generalLedgerApiClient.findSubAccount(
          any(),
          any(),
        ),
      )
        .thenReturn(prisonerSubAccountResponse)
        .thenReturn(prisonSubAccountResponse)

      whenever(generalLedgerApiClient.postTransaction(any(), any())).thenAnswer {
        throw CustomException("Internal Server Error", HttpStatus.INTERNAL_SERVER_ERROR)
      }

      assertThrows<CustomException> {
        generalLedgerService.createReleaseHoldTransaction(unreleasedHoldEntity, releaseHoldRequest, idempotencyKey)
      }
    }
  }
}
