package uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.enums.HoldType
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.enums.SubAccountRef
import java.time.Instant
import java.util.UUID

@Entity
@Table(
  name = "holds",
)
data class HoldEntity(

  @Id
  val id: UUID = UUID.randomUUID(),

  @Column(name = "prison_number", nullable = false, unique = false)
  val prisonNumber: String,

  @Column(name = "legacy_hold_number", nullable = false, unique = true)
  val legacyHoldNumber: Long,

  @Enumerated(EnumType.STRING)
  @Column(name = "sub_account_ref", nullable = false)
  val subAccountRef: SubAccountRef,

  @Column(name = "created_at", nullable = false, unique = false)
  val createdAt: Instant,

  @Column(name = "created_by", nullable = false, unique = false)
  val createdBy: String,

  @Column(name = "hold_from_date", nullable = false, unique = false)
  val holdFromDate: Instant,

  @Column(name = "hold_until_date", nullable = true, unique = false)
  val holdUntilDate: Instant? = null,

  @Column(name = "is_released", nullable = false, unique = false)
  val isReleased: Boolean = false,

  @Column(name = "description", nullable = true, unique = false)
  val description: String? = null,

  @Enumerated(EnumType.STRING)
  @Column(name = "hold_type", nullable = false, unique = false)
  val holdType: HoldType,

  @Column(name = "amount", nullable = false, unique = false)
  val amount: Long,
)
