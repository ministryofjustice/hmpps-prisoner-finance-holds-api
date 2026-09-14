package uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.entities.HoldEntity
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.enums.SubAccountRef
import java.util.UUID

@Repository
interface HoldRepository : JpaRepository<HoldEntity, UUID> {
  fun getHoldEntityByLegacyHoldNumber(legacyHoldNumber: Long): HoldEntity?

  fun findByPrisonNumberAndSubAccountRefAndIsReleasedFalse(prisonNumber: String, subAccountRef: SubAccountRef): List<HoldEntity>

  //  If pageable is passed, will return entities in a page rather than a raw list
  fun findByPrisonNumberAndIsReleasedFalse(prisonNumber: String): List<HoldEntity>
  fun findByPrisonNumberAndIsReleasedFalse(prisonNumber: String, pageable: Pageable): Page<HoldEntity>

  fun findHoldEntityById(holdId: UUID): HoldEntity?
}
