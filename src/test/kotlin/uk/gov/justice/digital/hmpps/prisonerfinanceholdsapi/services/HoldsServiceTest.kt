package uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.services

import org.assertj.core.api.Assertions.assertThat
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
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.HoldRepository
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.clients.GeneralLedgerApiClient
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.config.CustomException
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.entities.HoldEntity
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.enums.HoldType
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.enums.SubAccountRef
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.generalledger.CreatePostingRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.generalledger.CreateTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.generalledger.SubAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.requests.CreateHoldMigrationRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.requests.CreateHoldRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.requests.ReleaseHoldRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.responses.ReleasedHoldResponse
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class HoldsServiceTest {

  @Mock
  private lateinit var holdRepository: HoldRepository

  @Mock
  private lateinit var generalLedgerApiClient: GeneralLedgerApiClient

  @InjectMocks
  private lateinit var holdsService: HoldsService
  val prisonNumber = "A12345BC"

  private fun createHoldEntity(
    prisonNumber: String,
    holdNumber: Long,
    subAccountRef: SubAccountRef,
    isReleased: Boolean,
    amount: Long,
    holdTransactionId: UUID? = null,
    releasedTransactionId: UUID? = null,
    createdAt: Instant = Instant.now(),
  ) = HoldEntity(
    prisonNumber = prisonNumber,
    legacyHoldNumber = holdNumber,
    subAccountRef = subAccountRef,
    createdAt = createdAt,
    createdBy = "",
    holdFromDate = Instant.now(),
    holdUntilDate = Instant.now().plusSeconds(1),
    isReleased = isReleased,
    description = "",
    holdType = HoldType.HOA,
    amount = amount,
    holdLocation = "LEI",
    holdTransactionId = holdTransactionId,
    releasedTransactionId = releasedTransactionId,
  )

  @Nested
  inner class CreateHold {
    val idempotencyKey = UUID.randomUUID()
    val transactionGLId = UUID.randomUUID()
    val prisonerCashAccountUUID = UUID.randomUUID()
    val prisonHoldAccountUUID = UUID.randomUUID()

    @Test
    fun `should call GL service to create the transaction and create hold`() {
      val createHoldRequest = CreateHoldRequest(
        prisonNumber = prisonNumber,
        legacyHoldNumber = 1234,
        subAccountRef = SubAccountRef.CASH,
        createdAt = Instant.now(),
        createdBy = "TEST",
        holdFromDate = Instant.now(),
        holdUntilDate = Instant.now().plusSeconds(1),
        isReleased = false,
        description = "",
        holdType = HoldType.HOA,
        amount = 100,
        holdLocation = "LEI",
        prisonSubAccountId = prisonHoldAccountUUID,
        prisonerSubAccountId = prisonerCashAccountUUID,
      )

      val holdEntity = createHoldEntity(
        prisonNumber = createHoldRequest.prisonNumber,
        holdNumber = createHoldRequest.legacyHoldNumber,
        subAccountRef = createHoldRequest.subAccountRef,
        isReleased = createHoldRequest.isReleased,
        amount = createHoldRequest.amount,
        createdAt = createHoldRequest.createdAt,
      )

      val transactionReqCaptor = argumentCaptor<CreateTransactionRequest>()
      whenever {
        generalLedgerApiClient.postTransaction(
          transactionReqCaptor.capture(),
          any(),
        )
      }.thenReturn(transactionGLId)

      whenever { holdRepository.save(any<HoldEntity>()) }.thenReturn(holdEntity)

      holdsService.createHold(createHoldRequest, idempotencyKey)

      verify(generalLedgerApiClient, times(1))
        .postTransaction(
          any(),
          eq(idempotencyKey),
        )
      verify(holdRepository, times(1)).save(any())

      val transactionReq = transactionReqCaptor.firstValue

      assertThat(transactionReq.amount).isEqualTo(createHoldRequest.amount)
      assertThat(transactionReq.description).isEqualTo(createHoldRequest.description)
      assertThat(transactionReq.reference).isEqualTo("")
      assertThat(transactionReq.entrySequence).isEqualTo(1)
      assertThat(transactionReq.timestamp).isEqualTo(createHoldRequest.createdAt)
      assertThat(transactionReq.legacyTransactionId).isEqualTo(createHoldRequest.holdLegacyTransactionId)

      val debitPosting = transactionReq.postings.first { it.type == CreatePostingRequest.Type.DR }
      assertThat(debitPosting.subAccountId).isEqualTo(prisonerCashAccountUUID)
      assertThat(debitPosting.entrySequence).isEqualTo(1)
      assertThat(debitPosting.amount).isEqualTo(createHoldRequest.amount)

      val creditPosting = transactionReq.postings.first { it.type == CreatePostingRequest.Type.CR }
      assertThat(creditPosting.subAccountId).isEqualTo(prisonHoldAccountUUID)
      assertThat(creditPosting.entrySequence).isEqualTo(2)
      assertThat(creditPosting.amount).isEqualTo(createHoldRequest.amount)
    }

    @Test
    fun `should not call general ledger when the hold already exists`() {
      val createHoldRequest = CreateHoldRequest(
        prisonNumber = prisonNumber,
        legacyHoldNumber = 1234,
        subAccountRef = SubAccountRef.CASH,
        createdAt = Instant.now(),
        createdBy = "TEST",
        holdFromDate = Instant.now(),
        holdUntilDate = Instant.now().plusSeconds(1),
        isReleased = false,
        description = "",
        holdType = HoldType.HOA,
        amount = 100,
        holdLocation = "LEI",
        prisonSubAccountId = prisonHoldAccountUUID,
        prisonerSubAccountId = prisonerCashAccountUUID,
      )

      val holdEntity = createHoldEntity(
        prisonNumber = createHoldRequest.prisonNumber,
        holdNumber = createHoldRequest.legacyHoldNumber,
        subAccountRef = createHoldRequest.subAccountRef,
        isReleased = createHoldRequest.isReleased,
        amount = createHoldRequest.amount,
        holdTransactionId = transactionGLId,
        createdAt = createHoldRequest.createdAt,
      )

      whenever { holdRepository.getHoldEntityByLegacyHoldNumber(createHoldRequest.legacyHoldNumber) }.thenReturn(holdEntity)

      holdsService.createHold(createHoldRequest, idempotencyKey)

      verify(generalLedgerApiClient, times(0))
        .postTransaction(any(), any())
      verify(holdRepository, times(0)).save(any())
      verify(holdRepository, times(1)).getHoldEntityByLegacyHoldNumber(createHoldRequest.legacyHoldNumber)
    }

    @Test
    fun `should handle unique constrain violation and return the existing hold when the hold already exists`() {
      // Unique constraint violation is expected during race conditions

      val createHoldRequest = CreateHoldRequest(
        prisonNumber = prisonNumber,
        legacyHoldNumber = 1234,
        subAccountRef = SubAccountRef.CASH,
        createdAt = Instant.now(),
        createdBy = "TEST",
        holdFromDate = Instant.now(),
        holdUntilDate = Instant.now().plusSeconds(1),
        isReleased = false,
        description = "",
        holdType = HoldType.HOA,
        amount = 100,
        holdLocation = "LEI",
        prisonSubAccountId = prisonHoldAccountUUID,
        prisonerSubAccountId = prisonerCashAccountUUID,
      )

      val holdEntity = createHoldEntity(
        prisonNumber = createHoldRequest.prisonNumber,
        holdNumber = createHoldRequest.legacyHoldNumber,
        subAccountRef = createHoldRequest.subAccountRef,
        isReleased = createHoldRequest.isReleased,
        amount = createHoldRequest.amount,
        createdAt = createHoldRequest.createdAt,
      )

      whenever { holdRepository.getHoldEntityByLegacyHoldNumber(createHoldRequest.legacyHoldNumber) }
        .thenReturn(null)
        .thenReturn(holdEntity)

      whenever {
        generalLedgerApiClient.postTransaction(
          any(),
          any(),
        )
      }.thenReturn(transactionGLId, idempotencyKey)

      whenever { holdRepository.save(any<HoldEntity>()) }.thenThrow(
        DataIntegrityViolationException("DataIntegrityViolationException for constraint uc_holds_legacy_hold_number"),
      )

      val response = holdsService.createHold(createHoldRequest, idempotencyKey)

      verify(generalLedgerApiClient, times(1))
        .postTransaction(
          any(),
          eq(idempotencyKey),
        )
      verify(holdRepository, times(1)).save(any())

      assertThat(response.id).isEqualTo(holdEntity.id)
    }
  }

  @Nested
  inner class MigrateHold {

    @Test
    fun `should create a hold with no transaction mappings`() {
      val migrationRequest = CreateHoldMigrationRequest(
        prisonNumber = prisonNumber,
        legacyHoldNumber = 1234,
        subAccountRef = SubAccountRef.CASH,
        createdAt = Instant.now(),
        createdBy = "TEST",
        holdFromDate = Instant.now(),
        holdUntilDate = Instant.now().plusSeconds(1),
        isReleased = false,
        description = "",
        holdType = HoldType.HOA,
        amount = 100,
        holdLocation = "LEI",
      )

      val holdEntity = createHoldEntity(
        prisonNumber = migrationRequest.prisonNumber,
        holdNumber = migrationRequest.legacyHoldNumber,
        subAccountRef = migrationRequest.subAccountRef,
        isReleased = migrationRequest.isReleased,
        amount = migrationRequest.amount,
        createdAt = migrationRequest.createdAt,
      )

      whenever { holdRepository.save(any<HoldEntity>()) }.thenReturn(holdEntity)

      holdsService.migrateHold(migrationRequest)

      verify(holdRepository, times(1)).save(any())
    }

    @Test
    fun `should create a hold with holdTransactionId mapping`() {
      val migrationRequest = CreateHoldMigrationRequest(
        prisonNumber = prisonNumber,
        legacyHoldNumber = 1234,
        subAccountRef = SubAccountRef.CASH,
        createdAt = Instant.now(),
        createdBy = "TEST",
        holdFromDate = Instant.now(),
        holdUntilDate = Instant.now().plusSeconds(1),
        isReleased = false,
        description = "",
        holdType = HoldType.HOA,
        amount = 100,
        holdLocation = "LEI",
        holdTransactionId = UUID.randomUUID(),
        releasedTransactionId = null,
      )

      val holdEntity = createHoldEntity(
        prisonNumber = migrationRequest.prisonNumber,
        holdNumber = migrationRequest.legacyHoldNumber,
        subAccountRef = migrationRequest.subAccountRef,
        isReleased = migrationRequest.isReleased,
        amount = migrationRequest.amount,
        createdAt = migrationRequest.createdAt,
      )

      val holdEntityCaptor = argumentCaptor<HoldEntity>()

      whenever { holdRepository.save(holdEntityCaptor.capture()) }.thenReturn(holdEntity)

      holdsService.migrateHold(migrationRequest)

      val migratedHoldEntity = holdEntityCaptor.firstValue

      assertThat(migratedHoldEntity.holdTransactionId).isEqualTo(migrationRequest.holdTransactionId)

      verify(holdRepository, times(1)).save(any())
    }

    @Test
    fun `should create a hold with releaseTransactionId mapping`() {
      val migrationRequest = CreateHoldMigrationRequest(
        prisonNumber = prisonNumber,
        legacyHoldNumber = 1234,
        subAccountRef = SubAccountRef.CASH,
        createdAt = Instant.now(),
        createdBy = "TEST",
        holdFromDate = Instant.now(),
        holdUntilDate = Instant.now().plusSeconds(1),
        isReleased = true,
        description = "",
        holdType = HoldType.HOA,
        amount = 100,
        holdLocation = "LEI",
        holdTransactionId = UUID.randomUUID(),
        releasedTransactionId = UUID.randomUUID(),
      )

      val holdEntity = createHoldEntity(
        prisonNumber = migrationRequest.prisonNumber,
        holdNumber = migrationRequest.legacyHoldNumber,
        subAccountRef = migrationRequest.subAccountRef,
        isReleased = migrationRequest.isReleased,
        amount = migrationRequest.amount,
        createdAt = migrationRequest.createdAt,
      )

      val holdEntityCaptor = argumentCaptor<HoldEntity>()

      whenever { holdRepository.save(holdEntityCaptor.capture()) }.thenReturn(holdEntity)

      holdsService.migrateHold(migrationRequest)

      val migratedHoldEntity = holdEntityCaptor.firstValue

      assertThat(migratedHoldEntity.holdTransactionId).isEqualTo(migrationRequest.holdTransactionId)
      assertThat(migratedHoldEntity.releasedTransactionId).isEqualTo(migrationRequest.releasedTransactionId)

      verify(holdRepository, times(1)).save(any())
    }

    @Test
    fun `should handle unique constrain violation and return the existing hold when the hold already exists`() {
      // Unique constraint violation is expected during race conditions
      val createHoldMigrationReq = CreateHoldMigrationRequest(
        prisonNumber = prisonNumber,
        legacyHoldNumber = 1234,
        subAccountRef = SubAccountRef.CASH,
        createdAt = Instant.now(),
        createdBy = "TEST",
        holdFromDate = Instant.now(),
        holdUntilDate = Instant.now().plusSeconds(1),
        isReleased = false,
        description = "",
        holdType = HoldType.HOA,
        amount = 100,
        holdLocation = "LEI",
        holdTransactionId = UUID.randomUUID(),
        releasedTransactionId = UUID.randomUUID(),
      )

      val holdEntity = createHoldEntity(
        prisonNumber = createHoldMigrationReq.prisonNumber,
        holdNumber = createHoldMigrationReq.legacyHoldNumber,
        subAccountRef = createHoldMigrationReq.subAccountRef,
        isReleased = createHoldMigrationReq.isReleased,
        amount = createHoldMigrationReq.amount,
        createdAt = createHoldMigrationReq.createdAt,
        holdTransactionId = createHoldMigrationReq.holdTransactionId,
        releasedTransactionId = createHoldMigrationReq.releasedTransactionId,
      )

      whenever { holdRepository.save(any<HoldEntity>()) }.thenThrow(
        DataIntegrityViolationException("DataIntegrityViolationException for constraint uc_holds_legacy_hold_number"),
      )

      whenever { holdRepository.getHoldEntityByLegacyHoldNumber(createHoldMigrationReq.legacyHoldNumber) }
        .thenReturn(holdEntity)

      val response = holdsService.migrateHold(createHoldMigrationReq)

      verify(holdRepository, times(1)).save(any())

      assertThat(response.id).isEqualTo(holdEntity.id)
    }
  }

  @Nested
  inner class GetHoldBalanceForAccount {
    @Test
    fun `should return hold balance for prisonNumber`() {
      whenever { holdRepository.findByPrisonNumberAndIsReleasedFalse(prisonNumber) }
        .thenReturn(
          listOf(
            createHoldEntity(
              prisonNumber = prisonNumber,
              holdNumber = 1234,
              subAccountRef = SubAccountRef.SPENDS,
              isReleased = false,
              amount = 500,
            ),
          ),
        )

      val response = holdsService.getHoldBalanceForAccount(prisonNumber)

      assertThat(response.amount).isEqualTo(500)
    }

    @Test
    fun `should return hold balance 0 when there are no holds`() {
      whenever { holdRepository.findByPrisonNumberAndIsReleasedFalse(prisonNumber) }
        .thenReturn(
          emptyList(),
        )
      val response = holdsService.getHoldBalanceForAccount(prisonNumber)
      assertThat(response.amount).isEqualTo(0)
    }
  }

  @Nested
  inner class GetHoldBalanceForSubAccount {
    @Test
    fun `should return hold balance for prisonNumber sub account`() {
      whenever {
        holdRepository.findByPrisonNumberAndSubAccountRefAndIsReleasedFalse(
          prisonNumber,
          SubAccountRef.SPENDS,
        )
      }
        .thenReturn(
          listOf(
            createHoldEntity(
              prisonNumber = prisonNumber,
              holdNumber = 1234,
              subAccountRef = SubAccountRef.SPENDS,
              isReleased = false,
              amount = 400,
            ),

            createHoldEntity(
              prisonNumber = prisonNumber,
              holdNumber = 1274,
              subAccountRef = SubAccountRef.SPENDS,
              isReleased = false,
              amount = 250,
            ),
          ),
        )

      val response = holdsService.getHoldBalanceForSubAccount(prisonNumber, SubAccountRef.SPENDS)

      assertThat(response.amount).isEqualTo(650)
    }

    @Test
    fun `should return hold balance 0 when there are no holds in the sub account`() {
      whenever {
        holdRepository.findByPrisonNumberAndSubAccountRefAndIsReleasedFalse(
          prisonNumber,
          SubAccountRef.SPENDS,
        )
      }
        .thenReturn(
          emptyList(),
        )

      val response = holdsService.getHoldBalanceForSubAccount(prisonNumber, SubAccountRef.SPENDS)
      assertThat(response.amount).isEqualTo(0)
    }
  }

  @Nested
  inner class GetHolds {
    @Test
    fun `Should call repository and getHolds`() {
      val holdEntity = HoldEntity(
        prisonNumber = prisonNumber,
        legacyHoldNumber = 1,
        subAccountRef = SubAccountRef.CASH,
        createdAt = Instant.now(),
        createdBy = "",
        holdFromDate = Instant.now(),
        holdUntilDate = Instant.now().plusSeconds(1),
        isReleased = false,
        description = "",
        holdType = HoldType.HOA,
        amount = 1,
        holdLocation = "LEI",
        releasedAt = null,
        holdTransactionId = UUID.randomUUID(),
        releasedTransactionId = UUID.randomUUID(),
      )

      val pageNumber = 1
      val pageSize = 10
      val pagedRepoResponse = PageImpl(listOf(holdEntity))

      whenever { holdRepository.findByPrisonNumberAndIsReleasedFalse(eq(prisonNumber), any()) }.thenReturn(
        pagedRepoResponse,
      )

      val response = holdsService.getActiveHolds(prisonNumber, pageNumber, pageSize)

      val pageableCaptor = argumentCaptor<Pageable>()

      verify(holdRepository, times(1))
        .findByPrisonNumberAndIsReleasedFalse(eq(prisonNumber), pageableCaptor.capture())

      val capturedPageable = pageableCaptor.firstValue

      assertThat(capturedPageable.pageNumber).isEqualTo(0) // zero indexed
      assertThat(capturedPageable.pageSize).isEqualTo(pageSize)

      assertThat(response.content).hasSize(1)
      assertThat(response.pageNumber).isEqualTo(pageNumber)

      val holdResponse = response.content[0]
      assertThat(holdResponse.prisonNumber).isEqualTo(holdEntity.prisonNumber)
      assertThat(holdResponse.amount).isEqualTo(holdEntity.amount)
      assertThat(holdResponse.holdLocation).isEqualTo(holdEntity.holdLocation)
      assertThat(holdResponse.holdType).isEqualTo(holdEntity.holdType)
      assertThat(holdResponse.subAccountRef).isEqualTo(holdEntity.subAccountRef)
      assertThat(holdResponse.description).isEqualTo(holdEntity.description)
      assertThat(holdResponse.id).isEqualTo(holdEntity.id)
      assertThat(holdResponse.createdAt).isEqualTo(holdEntity.createdAt)
      assertThat(holdResponse.createdBy).isEqualTo(holdEntity.createdBy)
      assertThat(holdResponse.holdFromDate).isEqualTo(holdEntity.holdFromDate)
      assertThat(holdResponse.isReleased).isEqualTo(holdEntity.isReleased)
      assertThat(holdResponse.legacyHoldNumber).isEqualTo(holdEntity.legacyHoldNumber)
    }
  }

  @Nested
  inner class ReleaseHolds {

    val prisonSubAccountId: UUID = UUID.randomUUID()
    val prisonerSubAccountId: UUID = UUID.randomUUID()
    val glTransactionId: UUID = UUID.randomUUID()
    val holdId: UUID = UUID.randomUUID()
    val idempotencyKey: UUID = UUID.randomUUID()
    val releaseDateTime: Instant = Instant.now()
    val legacyTransactionId = 12345L

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
      holdLocation = "LEI",
      releasedAt = null,
      holdTransactionId = UUID.randomUUID(),
      releasedTransactionId = null,
    )

    val releaseHoldRequest = ReleaseHoldRequest(
      releaseDateTime = releaseDateTime,
      legacyTransactionId = legacyTransactionId,
    )

    val releasedHoldEntity = unreleasedHoldEntity.copy(
      isReleased = true,
      releasedTransactionId = glTransactionId,
      releasedAt = releaseHoldRequest.releaseDateTime,
    )

    val prisonerParentAccountId: UUID = UUID.randomUUID()
    val prisonerSubAccountResponse = SubAccountResponse(
      id = prisonerSubAccountId,
      reference = "SPENDS",
      parentAccountId = prisonerParentAccountId,
      createdBy = "JOHN_USER",
      createdAt = Instant.now(),
    )

    val prisonParentAccountId: UUID = UUID.randomUUID()
    val prisonSubAccountResponse = SubAccountResponse(
      id = prisonSubAccountId,
      reference = "2199:HOR",
      parentAccountId = prisonParentAccountId,
      createdBy = "JOHN_USER",
      createdAt = Instant.now(),
    )

    @Test
    fun `should throw a 404 if the prisoner subaccount cannot be found`() {
      whenever { holdRepository.findHoldEntityById(holdId) }.thenReturn(unreleasedHoldEntity)

      whenever {
        generalLedgerApiClient.findSubAccount(
          any(),
          any(),
        )
      }.thenReturn(null)

      val exception = assertThrows<CustomException> {
        holdsService.releaseHoldById(holdId, releaseHoldRequest, idempotencyKey)
      }

      assertThat(exception.status).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `should throw a 404 if the prison subaccount cannot be found`() {
      whenever { holdRepository.findHoldEntityById(holdId) }.thenReturn(unreleasedHoldEntity)

      whenever {
        generalLedgerApiClient.findSubAccount(
          any(),
          any(),
        )
      }.thenReturn(prisonerSubAccountResponse)
        .thenReturn(null)

      val exception = assertThrows<CustomException> {
        holdsService.releaseHoldById(holdId, releaseHoldRequest, idempotencyKey)
      }

      assertThat(exception.status).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @ParameterizedTest
    @EnumSource(HoldType::class)
    fun `should map prison subaccount`(holdType: HoldType) {
      val testUnreleasedHoldEntity = unreleasedHoldEntity.copy(holdType = holdType)

      whenever { holdRepository.findHoldEntityById(holdId) }.thenReturn(testUnreleasedHoldEntity)

      whenever {
        generalLedgerApiClient.findSubAccount(
          unreleasedHoldEntity.prisonNumber,
          unreleasedHoldEntity.subAccountRef.toString(),
        )
      }.thenReturn(prisonerSubAccountResponse)

      whenever {
        generalLedgerApiClient.postTransaction(
          any(),
          any(),
        )
      }.thenReturn(UUID.randomUUID())

      val expectedHoldType = "2199:${holdType.getReleaseType()}"

      whenever {
        generalLedgerApiClient.findSubAccount(
          testUnreleasedHoldEntity.holdLocation,
          expectedHoldType,
        )
      }.thenReturn(prisonSubAccountResponse)

      holdsService.releaseHoldById(holdId, releaseHoldRequest, idempotencyKey)
    }

    @Test
    fun `should call create transaction for the release`() {
      whenever { holdRepository.findHoldEntityById(holdId) }.thenReturn(unreleasedHoldEntity)

      whenever {
        generalLedgerApiClient.findSubAccount(
          unreleasedHoldEntity.prisonNumber,
          unreleasedHoldEntity.subAccountRef.toString(),
        )
      }.thenReturn(prisonerSubAccountResponse)

      whenever {
        generalLedgerApiClient.findSubAccount(
          parentReference = unreleasedHoldEntity.holdLocation,
          subAccountReference = "2199:HOR",
        )
      }.thenReturn(prisonSubAccountResponse)

      whenever {
        generalLedgerApiClient.postTransaction(
          request = any(),
          idempotencyKey = any(),
        )
      }.thenReturn(UUID.randomUUID())

      whenever { holdRepository.save(any<HoldEntity>()) }.thenReturn(releasedHoldEntity)

      holdsService.releaseHoldById(holdId, releaseHoldRequest, idempotencyKey)
    }

    @Test
    fun `should throw exception when create transaction fails and does not save updated entity`() {
      whenever { holdRepository.findHoldEntityById(holdId) }.thenReturn(unreleasedHoldEntity)

      whenever {
        generalLedgerApiClient.findSubAccount(
          unreleasedHoldEntity.prisonNumber,
          unreleasedHoldEntity.subAccountRef.toString(),
        )
      }.thenReturn(prisonerSubAccountResponse)

      whenever {
        generalLedgerApiClient.findSubAccount(
          parentReference = unreleasedHoldEntity.holdLocation,
          subAccountReference = "2199:HOR",
        )
      }.thenReturn(prisonSubAccountResponse)

      whenever {
        generalLedgerApiClient.postTransaction(
          request = any(),
          idempotencyKey = any(),
        )
      }.thenThrow(
        IllegalStateException("GL API returned null body for transaction"),
      )

      val exception = assertThrows<Exception> {
        holdsService.releaseHoldById(holdId, releaseHoldRequest, idempotencyKey)
      }

      assertThat(exception.message).contains("GL API returned null body for transaction")

      verify(holdRepository, never()).save(any<HoldEntity>())
    }

    @Test
    fun `sub-account is called for prisoner and prison`() {
      whenever { holdRepository.findHoldEntityById(holdId) }.thenReturn(unreleasedHoldEntity)

      whenever {
        generalLedgerApiClient.findSubAccount(
          parentReference = unreleasedHoldEntity.prisonNumber,
          subAccountReference = prisonerSubAccountResponse.reference,
        )
      }.thenReturn(prisonerSubAccountResponse)

      whenever {
        generalLedgerApiClient.findSubAccount(
          parentReference = unreleasedHoldEntity.holdLocation,
          subAccountReference = "2199:HOR",
        )
      }.thenReturn(prisonSubAccountResponse)

      whenever {
        generalLedgerApiClient.postTransaction(
          any(),
          any(),
        )
      }.thenReturn(UUID.randomUUID())

      whenever { holdRepository.save(any<HoldEntity>()) }.thenReturn(releasedHoldEntity)

      holdsService.releaseHoldById(holdId, releaseHoldRequest, idempotencyKey)

      verify(generalLedgerApiClient, times(2)).findSubAccount(any(), any())
    }

    @Test
    fun `transaction request is constructed as expected`() {
      whenever { holdRepository.findHoldEntityById(holdId) }.thenReturn(unreleasedHoldEntity)

      whenever {
        generalLedgerApiClient.findSubAccount(
          parentReference = unreleasedHoldEntity.prisonNumber,
          subAccountReference = prisonerSubAccountResponse.reference,
        )
      }.thenReturn(prisonerSubAccountResponse)

      whenever {
        generalLedgerApiClient.findSubAccount(
          parentReference = unreleasedHoldEntity.holdLocation,
          subAccountReference = "2199:HOR",
        )
      }.thenReturn(prisonSubAccountResponse)

      val expectedGLCreateTransactionRequest = CreateTransactionRequest(
        reference = "",
        description = "Remove Hold",
        timestamp = releaseHoldRequest.releaseDateTime,
        amount = releasedHoldEntity.amount,
        entrySequence = 1,
        postings = listOf(
          CreatePostingRequest(
            subAccountId = prisonerSubAccountResponse.id,
            type = CreatePostingRequest.Type.CR,
            amount = releasedHoldEntity.amount,
            entrySequence = 1,
          ),
          CreatePostingRequest(
            subAccountId = prisonSubAccountResponse.id,
            type = CreatePostingRequest.Type.DR,
            amount = releasedHoldEntity.amount,
            entrySequence = 2,
          ),
        ),
        legacyTransactionId = releaseHoldRequest.legacyTransactionId,
      )

      val postTransactionCaptor = argumentCaptor<CreateTransactionRequest>()
      val idempotencyCaptor = argumentCaptor<UUID>()

      whenever {
        generalLedgerApiClient.postTransaction(
          request = postTransactionCaptor.capture(),
          idempotencyKey = idempotencyCaptor.capture(),
        )
      }.thenReturn(UUID.randomUUID())

      whenever { holdRepository.save(any<HoldEntity>()) }.thenReturn(releasedHoldEntity)

      holdsService.releaseHoldById(holdId, releaseHoldRequest, idempotencyKey)

      val postingTransaction = postTransactionCaptor.firstValue
      val idempotency = idempotencyCaptor.firstValue

      assertThat(postingTransaction).isEqualTo(expectedGLCreateTransactionRequest)
      assertThat(idempotency).isEqualTo(idempotencyKey)
    }

    @Test
    fun `response from release hold is constructed as expected`() {
      whenever { holdRepository.findHoldEntityById(holdId) }.thenReturn(unreleasedHoldEntity)

      whenever {
        generalLedgerApiClient.findSubAccount(
          parentReference = unreleasedHoldEntity.prisonNumber,
          subAccountReference = prisonerSubAccountResponse.reference,
        )
      }.thenReturn(prisonerSubAccountResponse)

      whenever {
        generalLedgerApiClient.findSubAccount(
          parentReference = unreleasedHoldEntity.holdLocation,
          subAccountReference = "2199:HOR",
        )
      }.thenReturn(prisonSubAccountResponse)

      whenever {
        generalLedgerApiClient.postTransaction(
          request = any(),
          idempotencyKey = any(),
        )
      }.thenReturn(glTransactionId)

      whenever { holdRepository.save(any<HoldEntity>()) }.thenReturn(releasedHoldEntity)

      val actualReleaseHoldResponse = holdsService.releaseHoldById(holdId, releaseHoldRequest, idempotencyKey)

      val expectedReleasedHoldResponse = ReleasedHoldResponse(
        id = releasedHoldEntity.id,
        prisonNumber = releasedHoldEntity.prisonNumber,
        subAccountRef = releasedHoldEntity.subAccountRef,
        amountReleased = releasedHoldEntity.amount,
        releasedAt = releaseHoldRequest.releaseDateTime,
        releasedTransactionId = glTransactionId,
      )

      assertThat(actualReleaseHoldResponse).isEqualTo(expectedReleasedHoldResponse)
    }

    @Test
    fun `should return existing hold if already released`() {
      whenever { holdRepository.findHoldEntityById(holdId) }.thenReturn(releasedHoldEntity)

      val actualReleaseHoldResponse = holdsService.releaseHoldById(holdId, releaseHoldRequest, idempotencyKey)

      val expectedReleasedHoldResponse = ReleasedHoldResponse(
        id = releasedHoldEntity.id,
        prisonNumber = releasedHoldEntity.prisonNumber,
        subAccountRef = releasedHoldEntity.subAccountRef,
        amountReleased = releasedHoldEntity.amount,
        releasedAt = releaseHoldRequest.releaseDateTime,
        releasedTransactionId = releasedHoldEntity.releasedTransactionId!!,
      )

      assertThat(actualReleaseHoldResponse).isEqualTo(expectedReleasedHoldResponse)
      verify(generalLedgerApiClient, never()).postTransaction(any(), any())
    }
  }
}
