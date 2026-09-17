// org/example/reservationservice/reservation/controller/InternalSeatController.java
package org.example.reservationservice.reservation.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.reservationservice.common.exception.ApiException;
import org.example.reservationservice.reservation.exception.ReservationErrorCode;
import org.example.reservationservice.seat.dto.SeatGenerationRequest;
import org.example.reservationservice.seat.service.SeatGenerationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Gateway를 거치지 않는 내부 전용 API — Festival-Service가 페스티벌 승인 시 호출한다.
 * InternalReservationController와 같은 Bearer Token 인증 패턴을 쓴다.
 */
@RestController
@RequestMapping("/internal/v1/seats")
@RequiredArgsConstructor
public class InternalSeatController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final SeatGenerationService seatGenerationService;

    @Value("${internal.reservation-service.token:CHANGE_ME_IN_ENV}")
    private String internalAuthToken;

    //Festival-Service → Reservation-Service: 페스티벌 승인 시 SEATED 티켓타입의 좌석을 생성한다
    @PostMapping
    public ResponseEntity<Void> generateSeats(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @Valid @RequestBody SeatGenerationRequest request
    ) {
        verifyInternalToken(authorization);
        seatGenerationService.generateSeats(request);
        return ResponseEntity.ok().build();
    }

    private void verifyInternalToken(String authorization) {
        if (!authorization.equals(BEARER_PREFIX + internalAuthToken)) {
            throw new ApiException(ReservationErrorCode.INVALID_INTERNAL_TOKEN);
        }
    }
}