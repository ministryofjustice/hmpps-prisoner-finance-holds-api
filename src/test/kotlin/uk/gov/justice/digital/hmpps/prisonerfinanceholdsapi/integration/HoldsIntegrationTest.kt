package uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.config.ROLE_PRISONER_FINANCE__HOLDS__RO
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.config.ROLE_PRISONER_FINANCE__HOLDS__RW
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.enums.HoldType
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.enums.SubAccountRef
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.requests.CreateHoldRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.responses.HoldResponse
import java.time.Instant

class HoldsIntegrationTest : IntegrationTestBase() {

  @Nested
  inner class PostHolds {

    @Test
    fun `should create a hold and return 201 created with the created hold`() {
      val threeDaysInSeconds = 259200L

      val createHoldRequest = CreateHoldRequest(
        prisonNumber = "A12345BC",
        legacyHoldNumber = 12345678,
        subAccountRef = SubAccountRef.CASH,
        createdAt = Instant.now(),
        createdBy = "TEST",
        holdFromDate = Instant.now(),
        holdUntilDate = Instant.now().plusSeconds(threeDaysInSeconds),
        isReleased = false,
        description = "Damages to cell",
        holdType = HoldType.HOA,
        amount = 1000L,
      )

      val responseBody = webTestClient.post().uri("/holds")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__HOLDS__RW)))
        .bodyValue(createHoldRequest)
        .exchange()
        .expectStatus()
        .isCreated
        .expectBody<HoldResponse>()
        .returnResult()
        .responseBody!!

      assertThat(responseBody.legacyHoldNumber).isEqualTo(createHoldRequest.legacyHoldNumber)
      assertThat(responseBody.prisonNumber).isEqualTo(createHoldRequest.prisonNumber)
      assertThat(responseBody.subAccountRef).isEqualTo(SubAccountRef.CASH)
      assertThat(responseBody.holdType).isEqualTo(createHoldRequest.holdType)
      assertThat(responseBody.amount).isEqualTo(createHoldRequest.amount)
      assertThat(responseBody.description).isEqualTo(createHoldRequest.description)
      assertThat(responseBody.isReleased).isEqualTo(createHoldRequest.isReleased)
      assertThat(responseBody.holdFromDate).isEqualTo(createHoldRequest.holdFromDate)
      assertThat(responseBody.holdUntilDate).isEqualTo(createHoldRequest.holdUntilDate)
      assertThat(responseBody.createdAt).isEqualTo(createHoldRequest.createdAt)
      assertThat(responseBody.createdBy).isEqualTo(createHoldRequest.createdBy)
      assertThat(responseBody.id).isNotNull()
    }

    @Test
    fun `should return 403 forbidden when user does not have the correct role`() {
      val threeDaysInSeconds = 259200L

      val createHoldRequest = CreateHoldRequest(
        prisonNumber = "A12345BC",
        legacyHoldNumber = 12345678,
        subAccountRef = SubAccountRef.CASH,
        createdAt = Instant.now(),
        createdBy = "TEST",
        holdFromDate = Instant.now(),
        holdUntilDate = Instant.now().plusSeconds(threeDaysInSeconds),
        isReleased = false,
        description = "Damages to cell",
        holdType = HoldType.HOA,
        amount = 1000L,
      )

      webTestClient.post().uri("/holds")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__HOLDS__RO)))
        .bodyValue(createHoldRequest)
        .exchange()
        .expectStatus()
        .isForbidden
    }
  }
}
