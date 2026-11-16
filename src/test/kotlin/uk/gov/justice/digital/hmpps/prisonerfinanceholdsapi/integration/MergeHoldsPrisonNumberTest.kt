package uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.config.ROLE_PRISONER_FINANCE__HOLDS__RW
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.enums.HoldType
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.enums.SubAccountRef
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.responses.HoldResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.responses.PagedResponse
import java.time.Instant

class MergeHoldsPrisonNumberTest : SqsIntegrationTestBase() {
  @Nested
  inner class MergePrisonNumber {
    val survivingPrisonNumber = "A9971EC"
    val mergedPrisonNumber = "B9971XZ"

    @Test
    fun `Should merge all holds of a prisonNUmber to another`() {
      integrationTestHelpers.createHold(
        prisonNumber = mergedPrisonNumber,
        holdNumber = 123,
        subAccountRef = SubAccountRef.SPENDS,
        amount = 10,
        holdFromDate = Instant.now(),
        holdUntilDate = Instant.now().plusSeconds(240),
        isReleased = false,
        holdType = HoldType.HOA,
      )

      val responseBeforeMerge = webTestClient.get().uri("/holds/$mergedPrisonNumber")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__HOLDS__RW)))
        .exchange()
        .expectStatus()
        .isOk
        .expectBody<PagedResponse<HoldResponse>>()
        .returnResult()
        .responseBody!!

      assertThat(responseBeforeMerge.content).hasSize(1)
      assertThat(responseBeforeMerge.content.first().prisonNumber).isEqualTo(mergedPrisonNumber)

      val responseSurvivorBeforeMerge = webTestClient.get().uri("/holds/$survivingPrisonNumber")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__HOLDS__RW)))
        .exchange()
        .expectStatus()
        .isOk
        .expectBody<PagedResponse<HoldResponse>>()
        .returnResult()
        .responseBody!!

      assertThat(responseSurvivorBeforeMerge.content).hasSize(0)

      publishMergeEvent(mergedPrisonNumber, survivingPrisonNumber)

      waitUntilEmpty(
        hmppsQueueService = hmppsQueueService,
      )

      val responseAfterMerge = webTestClient.get().uri("/holds/$mergedPrisonNumber")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__HOLDS__RW)))
        .exchange()
        .expectStatus()
        .isOk
        .expectBody<PagedResponse<HoldResponse>>()
        .returnResult()
        .responseBody!!

      assertThat(responseAfterMerge.content).hasSize(0)

      val responseSurvivorAfterMerge = webTestClient.get().uri("/holds/$survivingPrisonNumber")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__HOLDS__RW)))
        .exchange()
        .expectStatus()
        .isOk
        .expectBody<PagedResponse<HoldResponse>>()
        .returnResult()
        .responseBody!!

      assertThat(responseSurvivorAfterMerge.content).hasSize(1)
      assertThat(responseSurvivorAfterMerge.content.first().prisonNumber).isEqualTo(survivingPrisonNumber)
    }
  }
}
