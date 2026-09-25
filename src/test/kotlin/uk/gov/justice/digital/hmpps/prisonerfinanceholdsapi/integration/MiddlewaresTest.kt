package uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.integration

import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.config.ROLE_PRISONER_FINANCE__HOLDS__RW

@Import(IntegrationTestHelpers::class)
class MiddlewaresTest : IntegrationTestBase() {

  @Test
  fun `Should return 405 method not allowed when calling an endpoint with a method that's not allowed`() {
    webTestClient.put().uri("/holds")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__HOLDS__RW)))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isEqualTo(405)
  }
}
