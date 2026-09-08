package org.example.reservationservice.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.reservationservice.common.dto.ApiResponse;
import org.example.reservationservice.domain.ReservationCreateRequestDto;
import org.example.reservationservice.domain.ReservationQrResponseDto;
import org.example.reservationservice.domain.ReservationResponseDto;
import org.example.reservationservice.domain.ReservationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    //참가자가 티켓 예매를 신청한다
    @PostMapping
    public ResponseEntity<ApiResponse<ReservationResponseDto>> createReservation(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody ReservationCreateRequestDto request
    ) {
        ReservationResponseDto response = reservationService.createReservation(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("예매 신청 성공", response));
    }

    //참가자 본인의 예매 목록을 조회한다
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<ReservationResponseDto>>> listMyReservations(
            @RequestHeader("X-User-Id") Long userId
    ) {
        return ResponseEntity.ok(ApiResponse.success("내 예매 목록 조회", reservationService.listMyReservations(userId)));
    }

    //참가자 본인의 예매 상세를 조회한다
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ReservationResponseDto>> getMyReservationDetail(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId
    ) {
        return ResponseEntity.ok(ApiResponse.success("내 예매 상세 조회", reservationService.getMyReservationDetail(id, userId)));
    }

    //참가자 본인의 확정된 예매에 대해 입장용 QR을 발급받는다
    @GetMapping("/{id}/qr")
    public ResponseEntity<ApiResponse<ReservationQrResponseDto>> getQr(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId
    ) {
        return ResponseEntity.ok(ApiResponse.success("QR 발급 성공", reservationService.getQrForReservation(id, userId)));
    }

    //참가자 본인이 결제대기 중인 예매를 직접 취소한다
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelMyReservation(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId
    ) {
        reservationService.cancelMyReservation(id, userId);
        return ResponseEntity.ok(ApiResponse.success("예매 취소 성공", null));
    }
}
