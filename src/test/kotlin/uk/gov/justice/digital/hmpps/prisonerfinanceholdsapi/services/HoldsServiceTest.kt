package uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.services

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.HoldRepository
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.entities.HoldEntity
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.enums.HoldType
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.enums.SubAccountRef
import java.time.Instant

@ExtendWith(MockitoExtension::class)
class HoldsServiceTest {

  @Mock
  private lateinit var holdRepository: HoldRepository

  @InjectMocks
  private lateinit var holdsService: HoldsService

  val prisonNumber = "A12345BC"

  private fun createHoldEntity(
    prisonNumber: String,
    holdNumber: Long,
    subAccountRef: SubAccountRef,
    isReleased: Boolean,
    amount: Long,
  ) = HoldEntity(
    prisonNumber = prisonNumber,
    legacyHoldNumber = holdNumber,
    subAccountRef = subAccountRef,
    createdAt = Instant.now(),
    createdBy = "",
    holdFromDate = Instant.now(),
    holdUntilDate = Instant.now().plusSeconds(1),
    isReleased = isReleased,
    description = "",
    holdType = HoldType.HOA,
    amount = amount,
    holdLocation = "LEI",
  )

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
      whenever { holdRepository.findByPrisonNumberAndSubAccountRefAndIsReleasedFalse(prisonNumber, SubAccountRef.SPENDS) }
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
      whenever { holdRepository.findByPrisonNumberAndSubAccountRefAndIsReleasedFalse(prisonNumber, SubAccountRef.SPENDS) }
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
      )

      val pageNumber = 1
      val pageSize = 10
      val pagedRepoResponse = PageImpl(listOf(holdEntity))

      whenever { holdRepository.findByPrisonNumber(eq(prisonNumber), any()) }.thenReturn(
        pagedRepoResponse,
      )

      val response = holdsService.getHolds(prisonNumber, pageNumber, pageSize)

      val pageableCaptor = argumentCaptor<Pageable>()

      verify(holdRepository, times(1))
        .findByPrisonNumber(eq(prisonNumber), pageableCaptor.capture())

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
}
