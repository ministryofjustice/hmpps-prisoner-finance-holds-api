package uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.integration

import jakarta.persistence.EntityManager
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.HoldRepository

@TestConfiguration
class IntegrationTestHelpers(private val holdsRepository: HoldRepository) {

  @Autowired
  lateinit var entityManager: EntityManager

  @Transactional(rollbackFor = [Exception::class, Error::class])
  fun clearDB() {
    entityManager.clear()
    entityManager.flush()

    holdsRepository.deleteAllInBatch()
  }
}
