package uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.envers.RevisionEntity
import org.hibernate.envers.RevisionNumber
import org.hibernate.envers.RevisionTimestamp

@Entity
@Table(name = "revinfo")
@RevisionEntity
open class AuditRevisionEntity {

  @field:Id
  @field:GeneratedValue(strategy = GenerationType.IDENTITY)
  @field:RevisionNumber
  @field:Column(name = "rev")
  open var id: Long? = null

  @field:RevisionTimestamp
  @field:Column(name = "revtstmp", nullable = false)
  open var timestamp: Long = 0
}
