package org.example.reservationservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.reservationservice.common.exception.ApiException;
import org.example.reservationservice.domain.ReservationCancelRequestDto;
import org.example.reservationservice.domain.ReservationConfirmRequestDto;
import org.example.reservationservice.domain.ReservationErrorCode;
import org.example.reservationservice.domain.ReservationForPaymentResponseDto;
import org.example.reservationservice.domain.ReservationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Gateway를 거치지 않는 내부 전용 API — Payment-Service가 관계별 환경변수 Bearer Token으로만
 * 인증해 호출한다. 응답은 팀 공통 ApiResponse 봉투를 씌우지 않고 DTO를 그대로 반환한다
 * (Payment-Service의 ReservationServiceClient가 그렇게 파싱하도록 이미 구현돼 있음).
 */
@RestController
@RequestMapping("/internal/v1/reservations")
@RequiredArgsConstructor
public class InternalReservationController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final ReservationService reservationService;

    @Value("${internal.auth-token:CHANGE_ME_IN_ENV}")
    private String internalAuthToken;

    //Payment-Service → Reservation-Service: 결제 시작 전 예매 정보 조회(getReservationForPayment)
    @GetMapping("/{id}")
    public ResponseEntity<ReservationForPaymentResponseDto> getReservationForPayment(
            @PathVariable Long id,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization
    ) {
        verifyInternalToken(authorization);
        return ResponseEntity.ok(reservationService.getReservationForPayment(id));
    }

    //Payment-Service → Reservation-Service: 결제 성공 확정
    @PatchMapping("/{id}/confirm")
    public ResponseEntity<Void> confirm(
            @PathVariable Long id,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @Valid @RequestBody ReservationConfirmRequestDto request
    ) {
        verifyInternalToken(authorization);
        reservationService.confirmReservation(id, request);
        return ResponseEntity.ok().build();
    }

    //Payment-Service → Reservation-Service: 결제 실패·취소 시 예매 취소 + 재고 복구
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<Void> cancel(
            @PathVariable Long id,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @Valid @RequestBody ReservationCancelRequestDto request
    ) {
        verifyInternalToken(authorization);
        reservationService.cancelReservation(id, request);
        return ResponseEntity.ok().build();
    }

    private void verifyInternalToken(String authorization) {
        if (!authorization.equals(BEARER_PREFIX + internalAuthToken)) {
            throw new ApiException(ReservationErrorCode.INVALID_INTERNAL_TOKEN);
        }
    }
}
