package uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.integration

import jakarta.persistence.EntityManager
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.HoldRepository
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.config.ROLE_PRISONER_FINANCE__HOLDS__RW
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.enums.HoldType
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.enums.SubAccountRef
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.requests.CreateHoldRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.responses.HoldResponse
import uk.gov.justice.hmpps.test.kotlin.auth.JwtAuthorisationHelper
import java.time.Instant

@TestConfiguration
class IntegrationTestHelper(
  private val jwtAuthHelper: JwtAuthorisationHelper,
  private val holdsRepository: HoldRepository,
) {
  lateinit var webTestClient: WebTestClient

  fun setWebClient(webClient: WebTestClient) {
    webTestClient = webClient
  }

  internal fun setAuthorisation(
    username: String? = "AUTH_ADM",
    roles: List<String> = listOf(),
    scopes: List<String> = listOf("read"),
  ): (HttpHeaders) -> Unit = jwtAuthHelper.setAuthorisationHeader(username = username, scope = scopes, roles = roles)

  fun createHold(
    prisonNumber: String,
    holdNumber: Long,
    subAccountRef: SubAccountRef,
    amount: Long,
    holdFromDate: Instant,
    holdUntilDate: Instant,
    isReleased: Boolean,
  ) {
    val createHoldRequest1 = CreateHoldRequest(
      prisonNumber = prisonNumber,
      legacyHoldNumber = holdNumber,
      subAccountRef = subAccountRef,
      createdAt = Instant.now(),
      createdBy = "TEST",
      holdFromDate = holdFromDate,
      holdUntilDate = holdUntilDate,
      isReleased = isReleased,
      description = "Damages to cell",
      holdType = HoldType.HOA,
      amount = amount,
      holdLocation = "LEI",
    )

    webTestClient.post().uri("/holds")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__HOLDS__RW)))
      .bodyValue(createHoldRequest1)
      .exchange()
      .expectStatus()
      .isCreated
      .expectBody<HoldResponse>()
      .returnResult()
      .responseBody!!
  }

  @Autowired
  lateinit var entityManager: EntityManager

  @Transactional(rollbackFor = [Exception::class, Error::class])
  fun clearDB() {
    entityManager.clear()
    entityManager.flush()

    holdsRepository.deleteAllInBatch()
  }
}
