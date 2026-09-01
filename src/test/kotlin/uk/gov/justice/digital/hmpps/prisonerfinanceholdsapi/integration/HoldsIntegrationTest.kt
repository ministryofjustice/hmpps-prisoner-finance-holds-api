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
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.responses.HoldBalanceResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.responses.HoldResponse
import java.time.Instant

class HoldsIntegrationTest : IntegrationTestBase() {

  @BeforeEach
  fun setup() {
    integrationTestHelper.clearDB()
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

    @Test
    fun `should return 409 when the legacy hold number already exists`() {
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

      webTestClient.post().uri("/holds")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__HOLDS__RW)))
        .bodyValue(createHoldRequest)
        .exchange()
        .expectStatus()
        .isCreated

      webTestClient.post().uri("/holds")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__HOLDS__RW)))
        .bodyValue(createHoldRequest)
        .exchange()
        .expectStatus().isEqualTo(409)
    }
  }

  @Nested
  inner class GetAccountHoldsBalance {

    @Test
    fun `should return holds balance for account`() {
      val prisonNumber = "A12345BC"

      val threeDaysInSeconds = 259200L

      integrationTestHelper.createHold(
        prisonNumber = prisonNumber,
        holdNumber = 12345678,
        subAccountRef = SubAccountRef.CASH,
        amount = 500L,
        holdFromDate = Instant.now(),
        holdUntilDate = Instant.now().plusSeconds(threeDaysInSeconds),
        isReleased = false,
      )

      integrationTestHelper.createHold(
        prisonNumber = prisonNumber,
        holdNumber = 12345679,
        subAccountRef = SubAccountRef.SPENDS,
        amount = 600L,
        holdFromDate = Instant.now(),
        holdUntilDate = Instant.now().plusSeconds(threeDaysInSeconds),
        isReleased = false,
      )

      val result = webTestClient.get().uri("/holds/$prisonNumber/balance")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__HOLDS__RO)))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus()
        .isOk
        .expectBody<HoldBalanceResponse>()
        .returnResult()
        .responseBody!!

      assertThat(result.amount).isEqualTo(1100)
    }

    @Test
    fun `should return holds balance for account and not include any released holds`() {
      val prisonNumber = "A12345BC"

      val threeDaysInSeconds = 259200L

      integrationTestHelper.createHold(
        prisonNumber = prisonNumber,
        holdNumber = 12345678,
        subAccountRef = SubAccountRef.CASH,
        amount = 600L,
        holdFromDate = Instant.now(),
        holdUntilDate = Instant.now().plusSeconds(threeDaysInSeconds),
        isReleased = false,
      )

      integrationTestHelper.createHold(
        prisonNumber = prisonNumber,
        holdNumber = 12345679,
        subAccountRef = SubAccountRef.SPENDS,
        amount = 600L,
        holdFromDate = Instant.now(),
        holdUntilDate = Instant.now().plusSeconds(threeDaysInSeconds),
        isReleased = false,
      )

      integrationTestHelper.createHold(
        prisonNumber = prisonNumber,
        holdNumber = 1234599,
        subAccountRef = SubAccountRef.SPENDS,
        amount = 222L,
        holdFromDate = Instant.now(),
        holdUntilDate = Instant.now().plusSeconds(threeDaysInSeconds),
        isReleased = true,
      )

      val result = webTestClient.get().uri("/holds/$prisonNumber/balance")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__HOLDS__RO)))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus()
        .isOk
        .expectBody<HoldBalanceResponse>()
        .returnResult()
        .responseBody!!

      assertThat(result.amount).isEqualTo(1200)
    }

    @Test
    fun `should return zero hold balance for account when there are no holds`() {
      val prisonNumber = "A12345BC"

      val result = webTestClient.get().uri("/holds/$prisonNumber/balance")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__HOLDS__RO)))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus()
        .isOk
        .expectBody<HoldBalanceResponse>()
        .returnResult()
        .responseBody!!

      assertThat(result.amount).isEqualTo(0)
    }

    @Test
    fun `should return 400 BAD REQUEST when NULL byte is passed in the prison number`() {
      val prisonNumber = "A12345BC\\0"

      webTestClient.get().uri("/holds/$prisonNumber/balance")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__HOLDS__RO)))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus()
        .isBadRequest
    }

    @Test
    fun `should return 403 forbidden when user does not have the correct role`() {
      val prisonNumber = "A12345BC"

      webTestClient.get().uri("/holds/$prisonNumber/balance")
        .headers(setAuthorisation(roles = listOf("ROLE__WRONG_ROLE")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus()
        .isForbidden
    }
  }

  @Nested
  inner class GetSubAccountHoldsBalance {

    @Test
    fun `should return holds balance for the sub account`() {
      val prisonNumber = "A12345BC"

      val threeDaysInSeconds = 259200L

      integrationTestHelper.createHold(
        prisonNumber = prisonNumber,
        holdNumber = 12345678,
        subAccountRef = SubAccountRef.CASH,
        amount = 500L,
        holdFromDate = Instant.now(),
        holdUntilDate = Instant.now().plusSeconds(threeDaysInSeconds),
        isReleased = false,
      )

      integrationTestHelper.createHold(
        prisonNumber = prisonNumber,
        holdNumber = 12345679,
        subAccountRef = SubAccountRef.SPENDS,
        amount = 600L,
        holdFromDate = Instant.now(),
        holdUntilDate = Instant.now().plusSeconds(threeDaysInSeconds),
        isReleased = false,
      )

      integrationTestHelper.createHold(
        prisonNumber = prisonNumber,
        holdNumber = 12345689,
        subAccountRef = SubAccountRef.SPENDS,
        amount = 200L,
        holdFromDate = Instant.now(),
        holdUntilDate = Instant.now().plusSeconds(threeDaysInSeconds),
        isReleased = false,
      )

      val result = webTestClient.get().uri("/holds/$prisonNumber/balance/SPENDS")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__HOLDS__RO)))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus()
        .isOk
        .expectBody<HoldBalanceResponse>()
        .returnResult()
        .responseBody!!

      assertThat(result.amount).isEqualTo(800)
    }

    @Test
    fun `should return holds balance for the sub account and not include any released holds`() {
      val prisonNumber = "A12345BC"

      val threeDaysInSeconds = 259200L

      integrationTestHelper.createHold(
        prisonNumber = prisonNumber,
        holdNumber = 12345678,
        subAccountRef = SubAccountRef.SPENDS,
        amount = 300L,
        holdFromDate = Instant.now(),
        holdUntilDate = Instant.now().plusSeconds(threeDaysInSeconds),
        isReleased = false,
      )

      integrationTestHelper.createHold(
        prisonNumber = prisonNumber,
        holdNumber = 12345679,
        subAccountRef = SubAccountRef.SPENDS,
        amount = 600L,
        holdFromDate = Instant.now(),
        holdUntilDate = Instant.now().plusSeconds(threeDaysInSeconds),
        isReleased = false,
      )

      integrationTestHelper.createHold(
        prisonNumber = prisonNumber,
        holdNumber = 1234599,
        subAccountRef = SubAccountRef.SPENDS,
        amount = 222L,
        holdFromDate = Instant.now(),
        holdUntilDate = Instant.now().plusSeconds(threeDaysInSeconds),
        isReleased = true,
      )

      val result = webTestClient.get().uri("/holds/$prisonNumber/balance/SPENDS")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__HOLDS__RO)))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus()
        .isOk
        .expectBody<HoldBalanceResponse>()
        .returnResult()
        .responseBody!!

      assertThat(result.amount).isEqualTo(900)
    }

    @Test
    fun `should return zero hold balance for the sub account when there are no holds`() {
      val prisonNumber = "A12345BC"

      val result = webTestClient.get().uri("/holds/$prisonNumber/balance/SPENDS")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__HOLDS__RO)))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus()
        .isOk
        .expectBody<HoldBalanceResponse>()
        .returnResult()
        .responseBody!!

      assertThat(result.amount).isEqualTo(0)
    }

    @Test
    fun `should return 400 BAD REQUEST when NULL byte is passed in the prison number`() {
      val prisonNumber = "A12345BC\\0"

      webTestClient.get().uri("/holds/$prisonNumber/balance/SPENDS")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__HOLDS__RO)))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus()
        .isBadRequest
    }

    @Test
    fun `should return 400 BAD REQUEST when NULL byte is passed in the subAccount ref`() {
      val prisonNumber = "A12345BC"

      webTestClient.get().uri("/holds/$prisonNumber/balance/SPENDS\\0")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__HOLDS__RO)))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus()
        .isBadRequest
    }

    @Test
    fun `should return 400 BAD REQUEST when subAccountRef is invalid`() {
      val prisonNumber = "A12345BC"

      webTestClient.get().uri("/holds/$prisonNumber/balance/INVALID")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__HOLDS__RO)))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus()
        .isBadRequest
    }

    @Test
    fun `should return 403 forbidden when user does not have the correct role`() {
      val prisonNumber = "A12345BC"

      webTestClient.get().uri("/holds/$prisonNumber/balance/CASH")
        .headers(setAuthorisation(roles = listOf("ROLE__WRONG_ROLE")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus()
        .isForbidden
    }
  }
}
