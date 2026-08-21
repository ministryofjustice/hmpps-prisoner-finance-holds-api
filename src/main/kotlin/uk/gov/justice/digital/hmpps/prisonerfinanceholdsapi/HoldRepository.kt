package uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi

import org.springframework.data.jpa.repository.JpaRepository
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.entities.HoldEntity
import java.util.UUID

interface HoldRepository : JpaRepository<HoldEntity, UUID>
