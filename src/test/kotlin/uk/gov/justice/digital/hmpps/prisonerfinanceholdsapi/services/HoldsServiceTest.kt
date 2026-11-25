package uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.services

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
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

  @Mock
  private lateinit var generalLedgerService: GeneralLedgerService

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
    holdType: HoldType = HoldType.HOA,
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
    holdType = holdType,
    amount = amount,
    holdLocation = "LEI",
    holdTransactionId = holdTransactionId,
    releasedTransactionId = releasedTransactionId,
  )

  @Nested
  inner class CreateHold {
    val idempotencyKey = UUID.randomUUID()

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
    )

    val holdEntity = createHoldEntity(
      prisonNumber = createHoldRequest.prisonNumber,
      holdNumber = createHoldRequest.legacyHoldNumber,
      subAccountRef = createHoldRequest.subAccountRef,
      isReleased = createHoldRequest.isReleased,
      amount = createHoldRequest.amount,
      createdAt = createHoldRequest.createdAt,
    )

    @Test
    fun `should call GL service to create the transaction and create hold`() {
      whenever { holdRepository.save(any<HoldEntity>()) }.thenReturn(holdEntity)

      whenever { generalLedgerService.postCreateHoldTransaction(createHoldRequest, idempotencyKey) }.thenReturn(UUID.randomUUID())

      holdsService.createHold(createHoldRequest, idempotencyKey)

      verify(generalLedgerService, times(1))
        .postCreateHoldTransaction(createHoldRequest, idempotencyKey)

      verify(holdRepository, times(1)).save(any())
    }

    @Test
    fun `should not call general ledger when the hold already exists`() {
      whenever { holdRepository.getHoldEntityByLegacyHoldNumber(createHoldRequest.legacyHoldNumber) }.thenReturn(holdEntity)

      holdsService.createHold(createHoldRequest, idempotencyKey)

      verify(generalLedgerService, times(0))
        .postCreateHoldTransaction(any(), any())

      verify(holdRepository, times(0)).save(any<HoldEntity>())
    }

    @Test
    fun `should handle unique constrain violation and return the existing hold when the hold already exists`() {
      // Unique constraint violation is expected during race conditions
      whenever { holdRepository.getHoldEntityByLegacyHoldNumber(createHoldRequest.legacyHoldNumber) }
        .thenReturn(null)
        .thenReturn(holdEntity)

      whenever { generalLedgerService.postCreateHoldTransaction(createHoldRequest, idempotencyKey) }.thenReturn(UUID.randomUUID())

      whenever { holdRepository.save(any<HoldEntity>()) }.thenThrow(
        DataIntegrityViolationException("DataIntegrityViolationException for constraint uc_holds_legacy_hold_number"),
      )

      val response = holdsService.createHold(createHoldRequest, idempotencyKey)

      verify(generalLedgerService, times(1))
        .postCreateHoldTransaction(createHoldRequest, idempotencyKey)

      verify(holdRepository, times(1)).save(any())

      assertThat(response.id).isEqualTo(holdEntity.id)
    }

    @Test
    fun `should throw exception when create transaction fails and does not save entity`() {
      whenever { holdRepository.getHoldEntityByLegacyHoldNumber(createHoldRequest.legacyHoldNumber) }
        .thenReturn(null)

      whenever(generalLedgerService.postCreateHoldTransaction(any(), any()))
        .thenAnswer { throw CustomException("Subaccount not found", HttpStatus.NOT_FOUND) }

      val exception = assertThrows<CustomException> {
        holdsService.createHold(createHoldRequest, idempotencyKey)
      }

      assertThat(exception.message).contains("Subaccount not found")
      assertThat(exception.status).isEqualTo(HttpStatus.NOT_FOUND)

      verify(holdRepository, never()).save(any<HoldEntity>())
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
    fun `should calculate HOA balance from the returned holds`() {
      whenever { holdRepository.findByPrisonNumberAndIsReleasedFalse(prisonNumber) }
        .thenReturn(
          listOf(
            createHoldEntity(
              prisonNumber = prisonNumber,
              holdNumber = 1234,
              subAccountRef = SubAccountRef.SPENDS,
              isReleased = false,
              amount = 500,
              holdType = HoldType.HOA,
            ),
            createHoldEntity(
              prisonNumber = prisonNumber,
              holdNumber = 1234,
              subAccountRef = SubAccountRef.SPENDS,
              isReleased = false,
              amount = 500,
              holdType = HoldType.HOA,
            ),
            createHoldEntity(
              prisonNumber = prisonNumber,
              holdNumber = 1234,
              subAccountRef = SubAccountRef.SPENDS,
              isReleased = false,
              amount = 200,
              holdType = HoldType.WHF,
            ),
          ),
        )

      val response = holdsService.getHoldBalanceForAccount(prisonNumber)

      assertThat(response.hoa).isEqualTo(1000)
    }

    @Test
    fun `should calculate WHF balance from the returned holds`() {
      whenever { holdRepository.findByPrisonNumberAndIsReleasedFalse(prisonNumber) }
        .thenReturn(
          listOf(
            createHoldEntity(
              prisonNumber = prisonNumber,
              holdNumber = 1234,
              subAccountRef = SubAccountRef.SPENDS,
              isReleased = false,
              amount = 500,
              holdType = HoldType.HOA,
            ),
            createHoldEntity(
              prisonNumber = prisonNumber,
              holdNumber = 1234,
              subAccountRef = SubAccountRef.SPENDS,
              isReleased = false,
              amount = 500,
              holdType = HoldType.WHF,
            ),
            createHoldEntity(
              prisonNumber = prisonNumber,
              holdNumber = 1234,
              subAccountRef = SubAccountRef.SPENDS,
              isReleased = false,
              amount = 200,
              holdType = HoldType.WHF,
            ),
          ),
        )

      val response = holdsService.getHoldBalanceForAccount(prisonNumber)

      assertThat(response.whf).isEqualTo(700)
    }

    @Test
    fun `should return hold balance 0 when there are no holds`() {
      whenever { holdRepository.findByPrisonNumberAndIsReleasedFalse(prisonNumber) }
        .thenReturn(
          emptyList(),
        )
      val response = holdsService.getHoldBalanceForAccount(prisonNumber)
      assertThat(response.amount).isEqualTo(0)
      assertThat(response.hoa).isEqualTo(0)
      assertThat(response.whf).isEqualTo(0)
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
    fun `should calculate HOA balance from the returned holds`() {
      whenever { holdRepository.findByPrisonNumberAndSubAccountRefAndIsReleasedFalse(prisonNumber, SubAccountRef.SPENDS) }
        .thenReturn(
          listOf(
            createHoldEntity(
              prisonNumber = prisonNumber,
              holdNumber = 1234,
              subAccountRef = SubAccountRef.SPENDS,
              isReleased = false,
              amount = 500,
              holdType = HoldType.HOA,
            ),
            createHoldEntity(
              prisonNumber = prisonNumber,
              holdNumber = 1234,
              subAccountRef = SubAccountRef.SPENDS,
              isReleased = false,
              amount = 500,
              holdType = HoldType.HOA,
            ),
            createHoldEntity(
              prisonNumber = prisonNumber,
              holdNumber = 1234,
              subAccountRef = SubAccountRef.SPENDS,
              isReleased = false,
              amount = 200,
              holdType = HoldType.WHF,
            ),
          ),
        )

      val response = holdsService.getHoldBalanceForSubAccount(prisonNumber, SubAccountRef.SPENDS)

      assertThat(response.hoa).isEqualTo(1000)
    }

    @Test
    fun `should calculate WHF balance from the returned holds`() {
      whenever { holdRepository.findByPrisonNumberAndSubAccountRefAndIsReleasedFalse(prisonNumber, SubAccountRef.SPENDS) }
        .thenReturn(
          listOf(
            createHoldEntity(
              prisonNumber = prisonNumber,
              holdNumber = 1234,
              subAccountRef = SubAccountRef.SPENDS,
              isReleased = false,
              amount = 500,
              holdType = HoldType.HOA,
            ),
            createHoldEntity(
              prisonNumber = prisonNumber,
              holdNumber = 1234,
              subAccountRef = SubAccountRef.SPENDS,
              isReleased = false,
              amount = 500,
              holdType = HoldType.WHF,
            ),
            createHoldEntity(
              prisonNumber = prisonNumber,
              holdNumber = 1234,
              subAccountRef = SubAccountRef.SPENDS,
              isReleased = false,
              amount = 200,
              holdType = HoldType.WHF,
            ),
          ),
        )

      val response = holdsService.getHoldBalanceForSubAccount(prisonNumber, SubAccountRef.SPENDS)

      assertThat(response.whf).isEqualTo(700)
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
      assertThat(response.hoa).isEqualTo(0)
      assertThat(response.whf).isEqualTo(0)
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

      whenever { holdRepository.findByPrisonNumberAndIsReleasedFalse(eq(prisonNumber), any(), eq(null)) }.thenReturn(
        pagedRepoResponse,
      )

      val response = holdsService.getActiveHolds(prisonNumber, pageNumber, pageSize)

      val pageableCaptor = argumentCaptor<Pageable>()

      verify(holdRepository, times(1))
        .findByPrisonNumberAndIsReleasedFalse(eq(prisonNumber), pageableCaptor.capture(), eq(null))

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

    @ParameterizedTest
    @CsvSource("HOA", "WHF")
    fun `Should call repository with hold type`(holdType: HoldType) {
      val holdsTypeCaptor = argumentCaptor<HoldType>()

      whenever { holdRepository.findByPrisonNumberAndIsReleasedFalse(eq(prisonNumber), any(), any()) }.thenReturn(
        PageImpl(emptyList()),
      )

      holdsService.getActiveHolds(prisonNumber, 1, 25, holdType)

      verify(holdRepository, times(1)).findByPrisonNumberAndIsReleasedFalse(
        eq(prisonNumber),
        any(),
        holdsTypeCaptor.capture(),
      )

      assertThat(holdsTypeCaptor.firstValue).isEqualTo(holdType)
    }
  }

  @Nested
  inner class ReleaseHolds {

    val updatedBy = "UPDATED_USER"
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
      updatedBy = updatedBy,
    )

    val releasedHoldEntity = unreleasedHoldEntity.copy(
      isReleased = true,
      releasedTransactionId = glTransactionId,
      releasedAt = releaseHoldRequest.releaseDateTime,
    )

    val entityCaptor = argumentCaptor<HoldEntity>()

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
        releasedTransactionId = glTransactionId,
        updatedBy = updatedBy,
      )

      assertThat(actualReleaseHoldResponse).isEqualTo(expectedReleasedHoldResponse)
      verify(generalLedgerApiClient, never()).postTransaction(any(), any())
    }

    @Test
    fun `should save updated entity and return response`() {
      whenever { holdRepository.findHoldEntityById(holdId) }.thenReturn(unreleasedHoldEntity)

      whenever { generalLedgerService.postReleaseHoldTransaction(unreleasedHoldEntity, releaseHoldRequest, idempotencyKey) }.thenReturn(glTransactionId)

      whenever { holdRepository.save(entityCaptor.capture()) }.thenAnswer { it.arguments[0] }

      holdsService.releaseHoldById(holdId, releaseHoldRequest, idempotencyKey)

      val capturedEntity = entityCaptor.firstValue
      assertThat(capturedEntity.releasedAt).isEqualTo(releaseHoldRequest.releaseDateTime)
      assertThat(capturedEntity.releasedTransactionId).isEqualTo(glTransactionId)
      assertThat(capturedEntity.isReleased).isTrue()
      assertThat(capturedEntity.updatedBy).isEqualTo(updatedBy)
    }
  }

  @Nested
  inner class MergeHolds {
    private val survivedPrisonNumber = "A123AA"
    private val mergedPrisonNumber = "X999ZX"

    @Test
    fun `should merge holds by prisonNumber`() {
      val mockHolds = listOf(
        HoldEntity(
          prisonNumber = mergedPrisonNumber,
          legacyHoldNumber = 123,
          subAccountRef = SubAccountRef.SPENDS,
          createdAt = Instant.now(),
          createdBy = "TEST",
          holdFromDate = Instant.now(),
          holdUntilDate = null,
          isReleased = false,
          description = "a description",
          holdType = HoldType.HOA,
          amount = 123,
          holdLocation = "LEI",
          releasedAt = Instant.now(),
          holdTransactionId = null,
          releasedTransactionId = null,
        ),
        HoldEntity(
          prisonNumber = mergedPrisonNumber,
          legacyHoldNumber = 5322,
          subAccountRef = SubAccountRef.SPENDS,
          createdAt = Instant.now(),
          createdBy = "TEST",
          holdFromDate = Instant.now(),
          holdUntilDate = null,
          isReleased = false,
          description = "a description",
          holdType = HoldType.HOA,
          amount = 123,
          holdLocation = "LEI",
          releasedAt = Instant.now(),
          holdTransactionId = null,
          releasedTransactionId = null,
        ),
      )

      whenever {
        holdRepository.findHoldEntityByPrisonNumber(mergedPrisonNumber)
      }.thenReturn(mockHolds)

      val entityUpdateCaptor = argumentCaptor<List<HoldEntity>>()

      whenever { holdRepository.saveAll(entityUpdateCaptor.capture()) }.thenAnswer { it.arguments[0] }

      holdsService.mergeHolds(survivedPrisonNumber, mergedPrisonNumber)

      entityUpdateCaptor.firstValue.withIndex().forEach { (i, hold) ->
        assertThat(mockHolds[i].holdTransactionId).isEqualTo(hold.holdTransactionId)
        assertThat(hold.prisonNumber).isEqualTo(survivedPrisonNumber)
        assertThat(hold.updatedBy).isEqualTo("DPS-Merge-Event")
      }
    }
  }
}
