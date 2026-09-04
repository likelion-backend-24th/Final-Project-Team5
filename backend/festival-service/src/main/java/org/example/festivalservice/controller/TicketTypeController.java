package org.example.festivalservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.festivalservice.common.dto.ApiResponse;
import org.example.festivalservice.domain.tickettype.TicketTypeDeductRequestDto;
import org.example.festivalservice.domain.tickettype.TicketTypeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class TicketTypeController {

    private final TicketTypeService ticketTypeService;

    //Reservation-Service → Festival-Service 내부 호출: 예매 신청 시 재고를 원자적으로 차감
    @PatchMapping("/internal/v1/ticket-types/{id}/stock")
    public ResponseEntity<ApiResponse<?>> deductStock(
            @PathVariable Long id,
            @Valid @RequestBody TicketTypeDeductRequestDto request) {
        ticketTypeService.deductStock(id, request.quantity());
        return ResponseEntity.ok(ApiResponse.success(null,"티켓 차감 성공"));
    }

    //Reservation-Service → Festival-Service 내부 호출: 결제 실패·취소 시 차감했던 재고를 복구
    @PatchMapping("/internal/v1/ticket-types/{id}/stock/restore")
    public ResponseEntity<ApiResponse<?>> restoreStock(
            @PathVariable Long id,
            @Valid @RequestBody TicketTypeDeductRequestDto request) {
        ticketTypeService.restoreStock(id, request.quantity());
        return ResponseEntity.ok(ApiResponse.success(null,"티켓 복구 성공"));
    }
}
