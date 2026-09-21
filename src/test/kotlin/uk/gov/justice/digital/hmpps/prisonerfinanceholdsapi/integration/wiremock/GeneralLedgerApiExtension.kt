package uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.integration.wiremock

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.matching
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.generalledger.AccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.generalledger.PostingResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.generalledger.SubAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.generalledger.TransactionResponse
import java.time.Instant
import java.util.UUID

class GeneralLedgerApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {

  private val mapper = ObjectMapper().registerModule(JavaTimeModule())

  companion object {
    val generalLedgerApi = GeneralLedgerApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    generalLedgerApi.start()
  }

  override fun afterAll(context: ExtensionContext) {
    generalLedgerApi.stop()
  }

  override fun beforeEach(context: ExtensionContext) {
    generalLedgerApi.resetAll()
  }
}

class GeneralLedgerApiMockServer : WireMockServer(WIREMOCK_PORT) {
  private val mapper = ObjectMapper().registerModule(JavaTimeModule())

  fun stubHealthPing(status: Int) {
    stubFor(
      get("/health/ping").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(if (status == 200) """{"status":"UP"}""" else """{"status":"DOWN"}""")
          .withStatus(status),
      ),
    )
  }

  fun stubPostTransaction(
    creditorSubAccountUuid: String? = null,
    debtorSubAccountUuid: String? = null,
    reference: String? = null,
    returnUUID: UUID = UUID.randomUUID(),
    postings: List<PostingResponse> = emptyList(),
    amount: Long = 1000,
    legacyTransactionId: String? = null,
  ): TransactionResponse {
    val response = TransactionResponse(
      id = returnUUID,
      reference = reference ?: "MOCK_TXN",
      amount = amount,
      createdBy = "MOCK_USER",
      createdAt = Instant.now(),
      description = "Mock Transaction Description",
      timestamp = Instant.now(),
      postings = postings,
    )

    var mapping = post(urlEqualTo("/transactions"))
      .withHeader("Idempotency-Key", matching(".*"))
      .willReturn(
        aResponse()
          .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
          .withStatus(201)
          .withBody(mapper.writeValueAsString(response)),
      )

    if (creditorSubAccountUuid != null) {
      mapping = mapping.withRequestBody(
        matchingJsonPath("$.postings[?(@.type == 'CR' && @.subAccountId == '$creditorSubAccountUuid')]"),
      )
    }
    if (debtorSubAccountUuid != null) {
      mapping = mapping.withRequestBody(
        matchingJsonPath("$.postings[?(@.type == 'DR' && @.subAccountId == '$debtorSubAccountUuid')]"),
      )
    }
    if (legacyTransactionId != null) {
      mapping = mapping.withRequestBody(
        matchingJsonPath("$[?(@.legacyTransactionId == '$legacyTransactionId')]"),
      )
    }
    stubFor(mapping)

    return response
  }

  // POST /transactions
  fun stubPostTransactionReturnsBadRequest() {
    var mapping = post(urlEqualTo("/transactions"))
      .withHeader("Idempotency-Key", matching(".*"))
      .willReturn(
        aResponse()
          .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
          .withStatus(400)
          .withBody(
            """
                {
                  "status": 400,
                  "errorCode": "BadRequest",
                  "userMessage": "Bad Request",
                  "developerMessage": "Bad Request",
                  "moreInfo": "more info"
                }
              """,
          ),
      )

    stubFor(mapping)
  }

  fun stubGetAccount(
    reference: String,
    returnUuid: UUID = UUID.randomUUID(),
    subAccounts: List<SubAccountResponse> = emptyList(),
    scenarioName: String? = null,
    scenarioState: String = STARTED,
    nextState: String = "SECOND_CALL",
  ) {
    val type = if (reference.length > 3) AccountResponse.Type.PRISONER else AccountResponse.Type.PRISON
    val response = AccountResponse(
      id = returnUuid,
      reference = reference,
      createdAt = Instant.now(),
      createdBy = "MOCK_USER",
      subAccounts = subAccounts,
      type = type,
    )

    stubFor(
      get(urlPathEqualTo("/accounts"))
        .apply {
          if (scenarioName != null) {
            inScenario(scenarioName)
              .whenScenarioStateIs(scenarioState)
              .willSetStateTo(nextState)
          }
        }
        .withQueryParam("reference", equalTo(reference))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .withStatus(200)
            .withBody(mapper.writeValueAsString(listOf(response))),
        ),
    )
  }

  fun stubGetAccountNotFound(
    reference: String,
    scenarioName: String? = null,
    scenarioState: String = STARTED,
    nextState: String = "SECOND_CALL",
  ) {
    stubFor(
      get(urlPathEqualTo("/accounts"))
        .apply {
          if (scenarioName != null) {
            inScenario(scenarioName)
              .whenScenarioStateIs(scenarioState)
              .willSetStateTo(nextState)
          }
        }
        .withQueryParam("reference", equalTo(reference))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .withStatus(200)
            .withBody("[]"),
        ),
    )
  }

  fun stubGetSubAccount(
    parentReference: String,
    subAccountReference: String,
    parentAccountId: UUID = UUID.randomUUID(),
    response: List<SubAccountResponse>? = null,
  ) {
    val subAccount = SubAccountResponse(
      id = UUID.randomUUID(),
      parentAccountId = parentAccountId,
      reference = subAccountReference,
      createdAt = Instant.now(),
      createdBy = "MOCK_USER",
    )

    stubFor(
      get(urlPathEqualTo("/sub-accounts"))
        .withQueryParam("reference", equalTo(subAccountReference))
        .withQueryParam("accountReference", equalTo(parentReference))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .withStatus(200)
            .withBody(
              mapper.writeValueAsString(
                response ?: listOf(subAccount),
              ),
            ),
        ),
    )
  }

  // GET /sub-accounts
  fun stubGetSubAccountNotFound(parentReference: String, subAccountReference: String) {
    stubFor(
      get(urlPathEqualTo("/sub-accounts"))
        .withQueryParam("reference", equalTo(subAccountReference))
        .withQueryParam("accountReference", equalTo(parentReference))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .withStatus(200)
            .withBody("[]"),
        ),
    )
  }

  companion object {
    private const val WIREMOCK_PORT = 8091
  }
}
