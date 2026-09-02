package uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.entities.HoldEntity
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.enums.SubAccountRef
import java.util.UUID

@Repository
interface HoldRepository : JpaRepository<HoldEntity, UUID> {
  fun getHoldEntityByLegacyHoldNumber(legacyHoldNumber: Long): HoldEntity?

  fun findByPrisonNumberAndSubAccountRefAndIsReleasedFalse(prisonNumber: String, subAccountRef: SubAccountRef): List<HoldEntity>

  fun findByPrisonNumberAndIsReleasedFalse(prisonNumber: String): List<HoldEntity>
  fun findHoldEntityById(holdId: UUID): HoldEntity?
}
