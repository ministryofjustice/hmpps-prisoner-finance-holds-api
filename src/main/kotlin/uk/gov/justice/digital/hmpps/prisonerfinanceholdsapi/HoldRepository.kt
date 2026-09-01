package uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.entities.HoldEntity
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.enums.SubAccountRef
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.responses.IHoldBalanceResponse
import java.util.UUID

@Repository
interface HoldRepository : JpaRepository<HoldEntity, UUID> {
  @Query(
    """
      SELECT 
            /* SUM is null if there is no record hence it needs coalesce */
            COALESCE(SUM(amount), 0) as amount, 
            CURRENT_TIMESTAMP as balanceDateTime 
      FROM HoldEntity h
      WHERE h.prisonNumber = :prisonNumber AND h.isReleased = false
    """,
  )
  fun getHoldBalanceForAccount(prisonNumber: String): IHoldBalanceResponse

  @Query(
    """
      SELECT 
            /* SUM is null if there is no record hence it needs coalesce */
            COALESCE(SUM(amount), 0) as amount, 
            CURRENT_TIMESTAMP as balanceDateTime 
      FROM HoldEntity h
      WHERE h.prisonNumber = :prisonNumber AND h.isReleased = false and h.subAccountRef = :subAccountRef
    """,
  )
  fun getHoldBalanceForSubAccount(prisonNumber: String, subAccountRef: SubAccountRef): IHoldBalanceResponse
}
