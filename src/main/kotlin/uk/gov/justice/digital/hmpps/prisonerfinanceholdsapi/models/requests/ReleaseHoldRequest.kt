package uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.requests

import java.time.Instant

data class ReleaseHoldRequest(
  val releaseDateTime: Instant,
)
