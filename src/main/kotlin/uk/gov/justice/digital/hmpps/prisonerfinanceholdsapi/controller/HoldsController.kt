package uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.requests.CreateHoldRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.models.responses.HoldResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceholdsapi.services.HoldsService


@RestController

class HoldsController (val holdsService: HoldsService){


  @PostMapping("/holds")
  fun postHolds(@RequestBody createHoldRequest: CreateHoldRequest):ResponseEntity<HoldResponse> {

    val createdHoldResponse = holdsService.createHold(createHoldRequest)
    return ResponseEntity.status(201).body(createdHoldResponse)
  }

}