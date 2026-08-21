package uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.enums

import com.fasterxml.jackson.annotation.JsonValue

enum class SubAccountType(
    @JsonValue val code: Int,
    val reference: SubAccountRef
) {
  CASH(2101, SubAccountRef.CASH),
  SPENDS(2102, SubAccountRef.SPENDS),
  SAVINGS(2103, SubAccountRef.SAVINGS);
}

enum class SubAccountRef{
  CASH, SPENDS, SAVINGS

}