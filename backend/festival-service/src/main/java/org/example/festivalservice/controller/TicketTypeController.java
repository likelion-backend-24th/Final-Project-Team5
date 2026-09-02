package org.example.festivalservice.controller;

import lombok.RequiredArgsConstructor;
import org.example.festivalservice.common.ApiResponse;
import org.example.festivalservice.domain.tickettype.TicketTypeDeductRequestDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class TicketTypeController {

    @PatchMapping("/internal/v1/ticket-types/{id}/stock")
    public ResponseEntity<ApiResponse<?>> deductStock(
            @PathVariable Long id,
            @RequestBody TicketTypeDeductRequestDto request) {
        return null;
    }
}
