package org.example.reservationservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.reservationservice.common.dto.ApiResponse;
import org.example.reservationservice.common.exception.ApiException;
import org.example.reservationservice.domain.ReservationErrorCode;
import org.example.reservationservice.domain.ReservationService;
import org.example.reservationservice.domain.ReservationVerifyRequestDto;
import org.example.reservationservice.domain.ReservationVerifyResponseDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 주최자가 현장에서 참가자의 QR을 스캔해 입장 검증할 때 쓰는 API. */
@RestController
@RequestMapping("/api/organizer/reservations")
@RequiredArgsConstructor
public class OrganizerReservationController {

    private static final String HOST_ROLE = "HOST";

    private final ReservationService reservationService;

    //주최자가 스캔한 QR(qrToken)을 검증하고 입장 처리한다
    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<ReservationVerifyResponseDto>> verify(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody ReservationVerifyRequestDto request
    ) {
        if (!HOST_ROLE.equals(role)) {
            throw new ApiException(ReservationErrorCode.FORBIDDEN_NOT_ORGANIZER);
        }
        return ResponseEntity.ok(ApiResponse.success("입장 처리 완료", reservationService.verifyAndCheckIn(userId, request)));
    }
}
