package uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.config.ROLE_PRISONER_FINANCE__HOLDS__RW
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.config.TAG_MIGRATION
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.requests.CreateHoldMigrationRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.responses.HoldResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.services.HoldsService
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse

@Tag(name = TAG_MIGRATION)
@RestController
class MigrationController(val holdsService: HoldsService) {

  @Operation(
    summary = "Migrate a legacy hold",
    description = "Migrates a legacy hold from NOMIS.",
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "201",
        description = "Hold Migrated",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = HoldResponse::class))],
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
  @PostMapping("/migrate/holds")
  fun postHold(
    @Valid @RequestBody createHoldMigrationRequest: CreateHoldMigrationRequest,
  ): ResponseEntity<HoldResponse> {
    val migratedHold = holdsService.migrateHold(createHoldMigrationRequest)

    return ResponseEntity.status(201).body(HoldResponse.fromEntity(migratedHold))
  }
}
