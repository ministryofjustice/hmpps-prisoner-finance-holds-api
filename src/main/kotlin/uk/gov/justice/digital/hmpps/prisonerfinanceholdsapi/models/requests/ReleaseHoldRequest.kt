package uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.requests

import java.time.Instant
import java.util.UUID

data class ReleaseHoldRequest(
  val releaseDateTime: Instant,
  val legacyTransactionId: Long? = null,
  val prisonSubAccountId: UUID,
  val prisonerSubAccountId: UUID,
)
