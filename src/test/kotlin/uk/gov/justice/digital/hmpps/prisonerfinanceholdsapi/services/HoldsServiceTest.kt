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

      val holdEntityCaptor = argumentCaptor<HoldEntity>()

      whenever { holdRepository.save(holdEntityCaptor.capture()) }.thenReturn(holdEntity)

      holdsService.createHold(createHoldRequest, idempotencyKey)

      val savedHoldEntity = holdEntityCaptor.firstValue

      assertThat(savedHoldEntity.prisonSubAccountId).isEqualTo(createHoldRequest.prisonSubAccountId)
      assertThat(savedHoldEntity.prisonerSubAccountId).isEqualTo(createHoldRequest.prisonerSubAccountId)

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
        prisonerSubAccountId = UUID.randomUUID(),
        prisonSubAccountId = UUID.randomUUID(),
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

      assertThat(migratedHoldEntity.prisonSubAccountId).isEqualTo(migrationRequest.prisonSubAccountId)
      assertThat(migratedHoldEntity.prisonerSubAccountId).isEqualTo(migrationRequest.prisonerSubAccountId)

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
        prisonSubAccountId = UUID.randomUUID(),
        prisonerSubAccountId = UUID.randomUUID(),
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

      assertThat(migratedHoldEntity.prisonSubAccountId).isEqualTo(migrationRequest.prisonSubAccountId)
      assertThat(migratedHoldEntity.prisonerSubAccountId).isEqualTo(migrationRequest.prisonerSubAccountId)
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
        prisonSubAccountId = UUID.randomUUID(),
        prisonerSubAccountId = UUID.randomUUID(),
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

      assertThat(migratedHoldEntity.prisonSubAccountId).isEqualTo(migrationRequest.prisonSubAccountId)
      assertThat(migratedHoldEntity.prisonerSubAccountId).isEqualTo(migrationRequest.prisonerSubAccountId)
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
        prisonerSubAccountId = UUID.randomUUID(),
        prisonSubAccountId = UUID.randomUUID(),
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
        prisonerSubAccountId = UUID.randomUUID(),
        prisonSubAccountId = UUID.randomUUID(),
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

      assertThat(capturedPageable.pageNumber).isEqualTo(pageNumber - 1) // zero indexed
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

    val releaseDateTime = Instant.now()
    val prisonSubAccountUUID = UUID.randomUUID()
    val prisonerSubAccountUUID = UUID.randomUUID()
    val legacyTransactionId = 12345L
    val glTransactionUUID = UUID.randomUUID()
    val holdUUID = UUID.randomUUID()
    val idempotencyKey = UUID.randomUUID()

    lateinit var releaseHoldRequest: ReleaseHoldRequest
    lateinit var releasedHoldEntity: HoldEntity

    lateinit var releaseHoldResponse: ReleasedHoldResponse
    lateinit var transactionReq: CreateTransactionRequest

    val unreleasedHoldEntity = HoldEntity(
      id = holdUUID,
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

    val prisonerParentAccountId = UUID.randomUUID()
    val prisonerSubAccountResponse = SubAccountResponse(
      id = prisonerSubAccountUUID,
      reference = "SPENDS",
      parentAccountId = prisonerParentAccountId,
      createdBy = "JOHN_USER",
      createdAt = Instant.now(),
    )

    @Test
    fun `should throw a 404 if the prisoner subaccount cannot be found`() {
      whenever {
        generalLedgerApiClient.findSubAccount(
          any(),
          any(),
        )
      }.thenReturn(null)

      whenever { holdRepository.findHoldEntityById(holdUUID) }.thenReturn(unreleasedHoldEntity)

      val exception = assertThrows<CustomException> {
        holdsService.releaseHoldById(holdUUID, releaseHoldRequest, idempotencyKey)
      }

      assertThat(exception.status).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `should throw a 404 if the prison subaccount cannot be found`() {
      whenever {
        generalLedgerApiClient.findSubAccount(
          any(),
          any(),
        )
      }.thenReturn(prisonerSubAccountResponse)
        .thenReturn(null)

      whenever { holdRepository.findHoldEntityById(holdUUID) }.thenReturn(unreleasedHoldEntity)

      val exception = assertThrows<CustomException> {
        holdsService.releaseHoldById(holdUUID, releaseHoldRequest, idempotencyKey)
      }

      assertThat(exception.status).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @ParameterizedTest
    @EnumSource(HoldType::class)
    fun `should map prison subaccount`(holdType: HoldType) {
      val testUnreleasedHoldEntity = unreleasedHoldEntity.copy(holdType = holdType)

      whenever { holdRepository.findHoldEntityById(holdUUID) }.thenReturn(testUnreleasedHoldEntity)

      whenever {
        generalLedgerApiClient.findSubAccount(
          unreleasedHoldEntity.prisonNumber,
          unreleasedHoldEntity.subAccountRef.toString(),
        )
      }.thenReturn(prisonerSubAccountResponse)

      var expectedHoldType = "2199:WHR"

      if (holdType == HoldType.HOA) {
        expectedHoldType = "2199:HOR"
      }

      whenever {
        generalLedgerApiClient.findSubAccount(
          "LEI",
          expectedHoldType,
        )
      }.thenReturn(null)

      holdsService.releaseHoldById(holdUUID, releaseHoldRequest, idempotencyKey)
    }

    @BeforeEach
    fun setup() {
      releaseHoldRequest = ReleaseHoldRequest(
        releaseDateTime = releaseDateTime,
        legacyTransactionId = legacyTransactionId,
      )

      val unreleasedHoldEntity = HoldEntity(
        id = holdUUID,
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

      releasedHoldEntity = unreleasedHoldEntity.copy(
        isReleased = true,
        releasedTransactionId = glTransactionUUID,
        releasedAt = releaseHoldRequest.releaseDateTime,
      )

//      val transactionReqCaptor = argumentCaptor<CreateTransactionRequest>()
//      whenever {
//        generalLedgerApiClient.postTransaction(
//          transactionReqCaptor.capture(),
//          any(),
//        )
//      }.thenReturn(glTransactionUUID)

      val prisonParentAccountId = UUID.randomUUID()
      val prisonSubAccountResponse = SubAccountResponse(
        id = prisonSubAccountUUID,
        reference = "2199:HOR",
        parentAccountId = prisonParentAccountId,
        createdBy = "JOHN_USER",
        createdAt = Instant.now(),
      )
      whenever {
        generalLedgerApiClient.findSubAccount(
          parentReference = unreleasedHoldEntity.prisonNumber,
          subAccountReference = prisonerSubAccountResponse.reference,
        )
      }.thenReturn(prisonerSubAccountResponse)

      whenever {
        generalLedgerApiClient.findSubAccount(
          parentReference = unreleasedHoldEntity.holdLocation,
          subAccountReference = prisonSubAccountResponse.reference,
        )
      }.thenReturn(prisonerSubAccountResponse)

      whenever { holdRepository.findHoldEntityById(holdUUID) }.thenReturn(unreleasedHoldEntity)

      whenever { holdRepository.save(any<HoldEntity>()) }.thenReturn(releasedHoldEntity)

      releaseHoldResponse = holdsService.releaseHoldById(holdUUID, releaseHoldRequest, idempotencyKey)

//      transactionReq = transactionReqCaptor.firstValue
    }

//    @Test
//    fun `should call GL to create transaction and save to the repository`() {
//      verify(generalLedgerApiClient, times(1))
//        .postTransaction(
//          any(),
//          eq(idempotencyKey),
//        )
//      verify(holdRepository, times(1)).save(any())
//    }

    @Test
    fun `release hold response is constructed as expected`() {
      assertThat(releaseHoldResponse.releasedAt).isEqualTo(releasedHoldEntity.releasedAt)
      assertThat(releaseHoldResponse.id).isEqualTo(releasedHoldEntity.id)
      assertThat(releaseHoldResponse.amountReleased).isEqualTo(releasedHoldEntity.amount)
//      assertThat(releaseHoldResponse.releasedTransactionId).isEqualTo(glTransactionUUID)
    }

    @Test
    fun `sub-account is called for prisoner and prison`() {
      verify(generalLedgerApiClient, times(2)).findSubAccount(any(), any())
    }

//    @Test
//    fun `transaction request is constructed as expected`() {
//      assertThat(transactionReq.amount).isEqualTo(releaseHoldResponse.amountReleased)
//      assertThat(transactionReq.description).isEqualTo("Remove Hold")
//      assertThat(transactionReq.reference).isEqualTo("")
//      assertThat(transactionReq.entrySequence).isEqualTo(1)
//      assertThat(transactionReq.timestamp).isEqualTo(releaseDateTime)
//      assertThat(transactionReq.legacyTransactionId).isEqualTo(releaseHoldRequest.legacyTransactionId)
//    }

//    @Test
//    fun `transaction requests postings are constructed as expected`() {
//      val debitPosting = transactionReq.postings.first { it.type == CreatePostingRequest.Type.DR }
//      assertThat(debitPosting.subAccountId).isEqualTo(prisonSubAccountUUID)
//      assertThat(debitPosting.entrySequence).isEqualTo(1)
//      assertThat(debitPosting.amount).isEqualTo(releasedHoldEntity.amount)
//
//      val creditPosting = transactionReq.postings.first { it.type == CreatePostingRequest.Type.CR }
//      assertThat(creditPosting.subAccountId).isEqualTo(prisonerSubAccountUUID)
//      assertThat(creditPosting.entrySequence).isEqualTo(2)
//      assertThat(creditPosting.amount).isEqualTo(releasedHoldEntity.amount)
//    }
  }
}
