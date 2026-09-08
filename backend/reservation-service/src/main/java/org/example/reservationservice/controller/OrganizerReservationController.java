package org.example.reservationservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.reservationservice.common.dto.ApiResponse;
import org.example.reservationservice.common.exception.ApiException;
import org.example.reservationservice.domain.CheckInStatsResponseDto;
import org.example.reservationservice.domain.ReservationErrorCode;
import org.example.reservationservice.domain.ReservationService;
import org.example.reservationservice.domain.ReservationVerifyByCodeRequestDto;
import org.example.reservationservice.domain.ReservationVerifyRequestDto;
import org.example.reservationservice.domain.ReservationVerifyResponseDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 현장 입장 검증 API. 주최자(HOST) 본인과, 주최자가 발급한 도우미(HELPER) 계정이 함께 쓴다.
 * 도우미는 계정 발급 시 배정된 페스티벌 하나만 다룰 수 있고, 그 정보는 Gateway가 JWT에서 꺼내
 * X-Festival-Id로 전달한다(클라이언트가 임의로 넣은 값은 Gateway에서 제거된다).
 */
@RestController
@RequestMapping("/api/organizer/reservations")
@RequiredArgsConstructor
public class OrganizerReservationController {

    private static final List<String> CHECK_IN_ROLES = List.of("HOST", "HELPER");

    private final ReservationService reservationService;

    //스캔한 QR(qrToken)을 검증하고 입장 처리한다
    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<ReservationVerifyResponseDto>> verify(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role,
            @RequestHeader(value = "X-Festival-Id", required = false) Long festivalId,
            @Valid @RequestBody ReservationVerifyRequestDto request
    ) {
        verifyCheckInRole(role);
        return ResponseEntity.ok(ApiResponse.success("입장 처리 완료",
                reservationService.verifyAndCheckIn(userId, role, festivalId, request)));
    }

    //QR 스캔이 안 될 때 입장 코드(2-4-4)를 손으로 입력해 검증하고 입장 처리한다
    @PostMapping("/verify-code")
    public ResponseEntity<ApiResponse<ReservationVerifyResponseDto>> verifyByCode(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role,
            @RequestHeader(value = "X-Festival-Id", required = false) Long festivalId,
            @Valid @RequestBody ReservationVerifyByCodeRequestDto request
    ) {
        verifyCheckInRole(role);
        return ResponseEntity.ok(ApiResponse.success("입장 처리 완료",
                reservationService.verifyAndCheckInByCode(userId, role, festivalId, request)));
    }

    //총 티켓 수 대비 현재 입장 인원
    @GetMapping("/check-in-stats")
    public ResponseEntity<ApiResponse<CheckInStatsResponseDto>> getCheckInStats(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role,
            @RequestHeader(value = "X-Festival-Id", required = false) Long scannerFestivalId,
            @RequestParam Long festivalId
    ) {
        verifyCheckInRole(role);
        return ResponseEntity.ok(ApiResponse.success("입장 현황 조회 성공",
                reservationService.getCheckInStats(festivalId, userId, role, scannerFestivalId)));
    }

    private void verifyCheckInRole(String role) {
        if (!CHECK_IN_ROLES.contains(role)) {
            throw new ApiException(ReservationErrorCode.FORBIDDEN_NOT_ORGANIZER);
        }
    }
}
