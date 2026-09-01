package uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.repositories

import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.ContainersConfig
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.HoldRepository

@Import(ContainersConfig::class)
@TestConfiguration
class RepoTestHelper(
  private val entityManager: TestEntityManager,
  private val holdRepository: HoldRepository,
) {

  fun persist(entity: Any) {
    entityManager.persist(entity)
  }

  fun clearDb() {
    holdRepository.deleteAll()
    entityManager.clear()
  }
}
