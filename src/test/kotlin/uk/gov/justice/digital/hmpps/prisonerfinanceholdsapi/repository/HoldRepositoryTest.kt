package uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.repository

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.ContainersConfig
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.HoldRepository
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.entities.HoldEntity
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.enums.HoldType
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.enums.SubAccountRef
import java.time.Instant
import java.util.UUID

@DataJpaTest
@Import(ContainersConfig::class)
class HoldRepositoryTest @Autowired constructor(
  val entityManager: TestEntityManager,
  val holdRepository: HoldRepository,
) {

  var legacyHoldNumber: Long = 12345678

  val prisonNumber = "A1234AA"

  fun createHold(holdType: HoldType) {
    entityManager.persist(
      HoldEntity(
        holdType = holdType,
        prisonNumber = prisonNumber,
        isReleased = false,
        legacyHoldNumber = legacyHoldNumber,
        holdFromDate = Instant.now(),
        amount = 1000L,
        description = "Damages to cell",
        id = UUID.randomUUID(),
        subAccountRef = SubAccountRef.CASH,
        createdAt = Instant.now(),
        createdBy = "USER",
        holdUntilDate = null,
        holdLocation = "LEI",
        releasedAt = null,
        holdTransactionId = UUID.randomUUID(),
        releasedTransactionId = null,
        updatedBy = null,
      ),
    )

    legacyHoldNumber++
  }

  @Nested
  inner class FindByPrisonNumberAndIsReleasedFalse {

    val page = PageRequest.of(0, 10)

    @Test
    fun `should return all active holds for a prison number if holds type is null`() {
      repeat(2) {
        createHold(HoldType.HOA)
      }

      repeat(3) {
        createHold(HoldType.WHF)
      }

      val result = holdRepository.findByPrisonNumberAndIsReleasedFalse(prisonNumber = prisonNumber, pageable = page, holdType = null)

      assert(result.content.size == 5)
    }

    @ParameterizedTest
    @EnumSource(HoldType::class)
    fun `should return all active holds for the given hold type`(holdType: HoldType) {
      val countOfWHF = (1..5).random()
      val countOfHOA = (6..10).random()

      val expectedCount = when (holdType) {
        HoldType.WHF -> countOfWHF
        HoldType.HOA -> countOfHOA
      }

      repeat(countOfWHF) {
        createHold(HoldType.WHF)
      }

      repeat(countOfHOA) {
        createHold(HoldType.HOA)
      }

      val result = holdRepository.findByPrisonNumberAndIsReleasedFalse(
        prisonNumber = prisonNumber,
        pageable = page,
        holdType = holdType,
      )

      assert(result.content.size == expectedCount)
    }
  }
}
