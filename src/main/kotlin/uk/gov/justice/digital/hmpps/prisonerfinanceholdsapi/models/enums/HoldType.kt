package uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.enums

enum class HoldType {
  HOA,
  WHF,
  ;

  fun getReleaseType(): String = when (this) {
    HOA -> "HOR"
    WHF -> "WHR"
  }
}
