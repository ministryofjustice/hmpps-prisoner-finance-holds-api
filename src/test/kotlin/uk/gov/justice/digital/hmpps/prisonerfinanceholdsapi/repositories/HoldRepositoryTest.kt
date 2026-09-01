package uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.repositories

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.HoldRepository
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.entities.HoldEntity
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.enums.HoldType
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.enums.SubAccountRef
import java.time.Instant

@DataJpaTest
@Import(RepoTestHelper::class)
class HoldRepositoryTest @Autowired constructor(
  val holdRepository: HoldRepository,
  private val repoTestHelper: RepoTestHelper,
) {
  val prisonNumber = "A12345BC"

  @BeforeEach
  fun setup() {
    repoTestHelper.clearDb()
  }

  private fun createHoldEntity(prisonNumber: String, holdNumber: Long, subAccountRef: SubAccountRef, isReleased: Boolean, amount: Long) {
    val hold = HoldEntity(
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

    holdRepository.save(hold)
  }

  @Nested
  inner class GetHoldBalanceForAccount {
    @Test
    fun `should return hold balance for prisonNumber`() {
      createHoldEntity(
        prisonNumber = prisonNumber,
        holdNumber = 1234,
        subAccountRef = SubAccountRef.SPENDS,
        isReleased = false,
        amount = 500,
      )

      val response = holdRepository.getHoldBalanceForAccount(prisonNumber)

      assertThat(response.amount).isEqualTo(500)
    }

    @Test
    fun `should return hold balance for prisonNumber and ignore released holds`() {
      createHoldEntity(
        prisonNumber = prisonNumber,
        holdNumber = 1234,
        subAccountRef = SubAccountRef.SPENDS,
        isReleased = false,
        amount = 500,
      )

      createHoldEntity(
        prisonNumber = prisonNumber,
        holdNumber = 12345,
        subAccountRef = SubAccountRef.SPENDS,
        isReleased = true,
        amount = 600,
      )

      val response = holdRepository.getHoldBalanceForAccount(prisonNumber)

      assertThat(response.amount).isEqualTo(500)
    }

    @Test
    fun `should return hold balance 0 when there are no holds`() {
      val response = holdRepository.getHoldBalanceForAccount(prisonNumber)
      assertThat(response.amount).isEqualTo(0)
    }
  }

  @Nested
  inner class GetHoldBalanceForSubAccount {
    @Test
    fun `should return hold balance for prisonNumber sub account`() {
      createHoldEntity(
        prisonNumber = prisonNumber,
        holdNumber = 1234,
        subAccountRef = SubAccountRef.SPENDS,
        isReleased = false,
        amount = 500,
      )

      createHoldEntity(
        prisonNumber = prisonNumber,
        holdNumber = 1274,
        subAccountRef = SubAccountRef.CASH,
        isReleased = false,
        amount = 200,
      )

      val response = holdRepository.getHoldBalanceForSubAccount(prisonNumber, SubAccountRef.SPENDS)

      assertThat(response.amount).isEqualTo(500)
    }

    @Test
    fun `should return hold balance for prisonNumber sub account and ignore released holds`() {
      createHoldEntity(
        prisonNumber = prisonNumber,
        holdNumber = 1234,
        subAccountRef = SubAccountRef.SPENDS,
        isReleased = false,
        amount = 500,
      )

      createHoldEntity(
        prisonNumber = prisonNumber,
        holdNumber = 12345,
        subAccountRef = SubAccountRef.SPENDS,
        isReleased = true,
        amount = 600,
      )

      val response = holdRepository.getHoldBalanceForSubAccount(prisonNumber, SubAccountRef.SPENDS)

      assertThat(response.amount).isEqualTo(500)
    }

    @Test
    fun `should return hold balance 0 when there are no holds in the sub account`() {
      createHoldEntity(
        prisonNumber = prisonNumber,
        holdNumber = 1234,
        subAccountRef = SubAccountRef.CASH,
        isReleased = false,
        amount = 500,
      )

      val response = holdRepository.getHoldBalanceForSubAccount(prisonNumber, SubAccountRef.SPENDS)
      assertThat(response.amount).isEqualTo(0)
    }
  }
}
