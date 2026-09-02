package uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.ValidationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.config.ROLE_PRISONER_FINANCE__HOLDS__RO
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.config.ROLE_PRISONER_FINANCE__HOLDS__RW
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.config.TAG_HOLDS
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.enums.SubAccountRef
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.requests.CreateHoldRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.responses.HoldBalanceResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.responses.HoldResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.services.HoldsService
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse

@Tag(name = TAG_HOLDS)
@RestController
class HoldsController(val holdsService: HoldsService) {

  @Operation(
    summary = "Create a new hold",
    description = "Creates a new hold for a prisoner's sub-account.",
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "201",
        description = "Hold Created",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = HoldResponse::class))],
      ),
      ApiResponse(
        responseCode = "400",
        description = "Bad Request",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized - requires a valid OAuth2 token",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Forbidden - requires an appropriate role",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "409",
        description = "Conflict - Legacy Hold Number already exists",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "500",
        description = "Internal Server Error - An unexpected error occurred.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  @SecurityRequirement(name = "bearer-jwt", scopes = [ROLE_PRISONER_FINANCE__HOLDS__RW])
  @PreAuthorize("hasAnyAuthority('$ROLE_PRISONER_FINANCE__HOLDS__RW')")
  @PostMapping("/holds")
  fun postHold(@Valid @RequestBody createHoldRequest: CreateHoldRequest): ResponseEntity<HoldResponse> {
    val createdHoldResponse = holdsService.createHold(createHoldRequest)
    return ResponseEntity.status(201).body(createdHoldResponse)
  }

  @Operation(
    summary = "Get the account hold balance.",
    description = "Get the account hold balance.",
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Overall hold balance of the account for all sub accounts",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = HoldBalanceResponse::class))],
      ),
      ApiResponse(
        responseCode = "400",
        description = "Bad Request",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized - requires a valid OAuth2 token",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Forbidden - requires an appropriate role",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "500",
        description = "Internal Server Error - An unexpected error occurred.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  @SecurityRequirement(name = "bearer-jwt", scopes = [ROLE_PRISONER_FINANCE__HOLDS__RO, ROLE_PRISONER_FINANCE__HOLDS__RW])
  @PreAuthorize("hasAnyAuthority('$ROLE_PRISONER_FINANCE__HOLDS__RW', '$ROLE_PRISONER_FINANCE__HOLDS__RO')")
  @GetMapping("/holds/{prisonNumber}/balance")
  fun getAccountHoldBalance(@PathVariable prisonNumber: String): ResponseEntity<HoldBalanceResponse> {
    val holdBalanceResponse = holdsService.getHoldBalanceForAccount(prisonNumber)

    return ResponseEntity.status(HttpStatus.OK).body(holdBalanceResponse)
  }

  @Operation(
    summary = "Get the sub account hold balance.",
    description = "Get the sub account hold balance.",
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Hold balance of the sub account",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = HoldBalanceResponse::class))],
      ),
      ApiResponse(
        responseCode = "400",
        description = "Bad Request",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized - requires a valid OAuth2 token",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Forbidden - requires an appropriate role",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "500",
        description = "Internal Server Error - An unexpected error occurred.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  @SecurityRequirement(name = "bearer-jwt", scopes = [ROLE_PRISONER_FINANCE__HOLDS__RO, ROLE_PRISONER_FINANCE__HOLDS__RW])
  @PreAuthorize("hasAnyAuthority('$ROLE_PRISONER_FINANCE__HOLDS__RW', '$ROLE_PRISONER_FINANCE__HOLDS__RO')")
  @GetMapping("/holds/{prisonNumber}/balance/{subAccountRef}")
  fun getSubAccountHoldBalance(
    @PathVariable prisonNumber: String,
    @PathVariable subAccountRef: String,
  ): ResponseEntity<HoldBalanceResponse> {
    val matchingSubAccountRef = SubAccountRef.entries.find {
      it.name.equals(subAccountRef, ignoreCase = true)
    } ?: throw ValidationException("Invalid sub account ref")

    val holdBalanceResponse = holdsService.getHoldBalanceForSubAccount(prisonNumber, matchingSubAccountRef)

    return ResponseEntity.status(HttpStatus.OK).body(holdBalanceResponse)
  }
}
