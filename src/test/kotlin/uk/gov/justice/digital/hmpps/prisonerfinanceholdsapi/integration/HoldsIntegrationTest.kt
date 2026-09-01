package uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
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

  @BeforeEach
  fun setup() {
    integrationTestHelpers.clearDB()
  }

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
        holdLocation = "LEI",
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
      assertThat(responseBody.holdLocation).isEqualTo(createHoldRequest.holdLocation)
    }

    @Test
    fun `should return 201 when the legacy hold number already exists`() {
      val threeDaysInSeconds = 259200L
      val legacyHoldNumber = 12345678L

      val createHoldRequest = CreateHoldRequest(
        prisonNumber = "A12345BC",
        legacyHoldNumber = legacyHoldNumber,
        subAccountRef = SubAccountRef.CASH,
        createdAt = Instant.now(),
        createdBy = "TEST",
        holdFromDate = Instant.now(),
        holdUntilDate = Instant.now().plusSeconds(threeDaysInSeconds),
        isReleased = false,
        description = "Damages to cell",
        holdType = HoldType.HOA,
        amount = 1000L,
        holdLocation = "LEI",
      )

      val createdHold = webTestClient.post().uri("/holds")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__HOLDS__RW)))
        .bodyValue(createHoldRequest)
        .exchange()
        .expectStatus()
        .isCreated
        .expectBody<HoldResponse>()
        .returnResult()
        .responseBody!!

      val duplicate = webTestClient.post().uri("/holds")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__HOLDS__RW)))
        .bodyValue(createHoldRequest)
        .exchange()
        .expectStatus().isEqualTo(201)
        .expectBody<HoldResponse>()
        .returnResult()
        .responseBody!!

      assertThat(createdHold).isEqualTo(duplicate)
    }

    @Test
    fun `should return 400 bad request when request is invalid`() {
      val createHoldRequestJson = """ {
        "prisonNumber": 12345,
        "legacyHoldNumber": "12345678",
        "subAccountRef": "CASH",
        "createdAt": "TEST",
        "createdBy": 20260824,
        "holdFromDate": 20260824,
        "holdUntilDate": 20260824,
        "isReleased": "false",
        "description": 123456,
        "holdType": "HOA",
        "amount": "1000",
        "holdLocation": "LEI"
      }"""

      webTestClient.post().uri("/holds")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__HOLDS__RO)))
        .header("Content-Type", "application/json")
        .bodyValue(createHoldRequestJson)
        .exchange()
        .expectStatus()
        .isBadRequest
    }

    @Test
    fun `should return a 400 if hold is applied to a sub-account that a prisoner should not have`() {
      val createHoldRequestJson = """{
        "prisonNumber": "A12345BC",
        "legacyHoldNumber": 12345678,
        "subAccountRef": "CANT",
        "createdAt": "2026-08-24T12:27:56Z",
        "createdBy": "TEST",
        "holdFromDate": "2026-08-24T12:27:56Z",
        "holdUntilDate": "2026-08-27T12:27:56Z",
        "isReleased": false,
        "description": "Damages to cell",
        "holdType": "HOA",
        "amount": 1000,
        "holdLocation": "LEI"
      }"""

      webTestClient.post().uri("/holds")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__HOLDS__RO)))
        .header("Content-Type", "application/json")
        .bodyValue(createHoldRequestJson)
        .exchange()
        .expectStatus()
        .isBadRequest
    }

    @Test
    fun `should return a 400 if hold type is invalid`() {
      val createHoldRequestJson = """{
        "prisonNumber": "A12345BC",
        "legacyHoldNumber": 12345678,
        "subAccountRef": "CASH",
        "createdAt": "2026-08-24T12:27:56Z",
        "createdBy": "TEST",
        "holdFromDate": "2026-08-24T12:27:56Z",
        "holdUntilDate": "2026-08-27T12:27:56Z",
        "isReleased": false,
        "description": "Damages to cell",
        "holdType": "ATOF",
        "amount": 1000, 
        "holdLocation": "LEI"
      }"""

      webTestClient.post().uri("/holds")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__HOLDS__RO)))
        .header("Content-Type", "application/json")
        .bodyValue(createHoldRequestJson)
        .exchange()
        .expectStatus()
        .isBadRequest
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
        holdLocation = "LEI",
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
