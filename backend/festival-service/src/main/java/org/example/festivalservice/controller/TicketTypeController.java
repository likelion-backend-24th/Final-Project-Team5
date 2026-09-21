package org.example.festivalservice.controller;

import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.RequiredArgsConstructor;
import org.example.festivalservice.common.dto.ApiResponse;
import org.example.festivalservice.common.exception.ApiException;
import org.example.festivalservice.domain.festival.FestivalErrorCode;
import org.example.festivalservice.domain.tickettype.TicketTypeDeductRequestDto;
import org.example.festivalservice.domain.tickettype.TicketTypeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class TicketTypeController {

    private final TicketTypeService ticketTypeService;

    @Value("${internal.auth-token:CHANGE_ME_IN_ENV}")
    private String internalAuthToken;

    //Reservation-Service → Festival-Service 내부 호출: 예매 신청 시 재고를 원자적으로 차감
    @PatchMapping("/internal/v1/ticket-types/{id}/stock")
    public ResponseEntity<ApiResponse<?>> deductStock(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", defaultValue = "") String authorization,
            @Valid @RequestBody TicketTypeDeductRequestDto request) {
        verifyInternalToken(authorization);
        ticketTypeService.deductStock(id, request.quantity());
        return ResponseEntity.ok(ApiResponse.success(null,"티켓 차감 성공"));
    }

    //Reservation-Service → Festival-Service 내부 호출: 결제 실패·취소 시 차감했던 재고를 복구
    @PatchMapping("/internal/v1/ticket-types/{id}/stock/restore")
    public ResponseEntity<ApiResponse<?>> restoreStock(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", defaultValue = "") String authorization,
            @Valid @RequestBody TicketTypeDeductRequestDto request) {
        verifyInternalToken(authorization);
        ticketTypeService.restoreStock(id, request.quantity());
        return ResponseEntity.ok(ApiResponse.success(null,"티켓 복구 성공"));
    }

    //내부망에서도 임의의 재고 변경을 막고 토큰 비교 시간으로 값을 추측하지 못하게 한다.
    private void verifyInternalToken(String authorization) {
        byte[] expected = ("Bearer " + internalAuthToken).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, authorization.getBytes(StandardCharsets.UTF_8))) {
            throw new ApiException(FestivalErrorCode.INVALID_INTERNAL_TOKEN);
        }
    }
}
