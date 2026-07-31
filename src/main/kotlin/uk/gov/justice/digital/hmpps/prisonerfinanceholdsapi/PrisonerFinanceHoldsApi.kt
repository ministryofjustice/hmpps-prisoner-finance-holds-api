package uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class PrisonerFinanceHoldsApi

fun main(args: Array<String>) {
  runApplication<PrisonerFinanceHoldsApi>(*args)
}
