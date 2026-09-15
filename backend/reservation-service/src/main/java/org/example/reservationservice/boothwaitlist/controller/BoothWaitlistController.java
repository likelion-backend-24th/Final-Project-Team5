package org.example.reservationservice.boothwaitlist.controller;

import lombok.RequiredArgsConstructor;
import org.example.reservationservice.boothwaitlist.dto.BoothWaitlistResponseDto;
import org.example.reservationservice.boothwaitlist.service.BoothWaitlistService;
import org.example.reservationservice.common.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 부스 대기 신청 — Gateway가 로그인은 걸러주고, 대상 부스가 속한 페스티벌 티켓 보유 여부는 여기서 확인한다. */
@RestController
@RequestMapping("/api/booth-waitlists")
@RequiredArgsConstructor
public class BoothWaitlistController {

    private final BoothWaitlistService boothWaitlistService;

    //대기 신청
    @PostMapping("/{boothId}")
    public ResponseEntity<ApiResponse<BoothWaitlistResponseDto>> requestWaitlist(
            @PathVariable Long boothId,
            @RequestHeader("X-User-Id") Long userId
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("대기 신청 성공", boothWaitlistService.requestWaitlist(userId, boothId)));
    }

    //내 대기번호 조회
    @GetMapping("/{boothId}/me")
    public ResponseEntity<ApiResponse<BoothWaitlistResponseDto>> getMyWaitlist(
            @PathVariable Long boothId,
            @RequestHeader("X-User-Id") Long userId
    ) {
        return ResponseEntity.ok(ApiResponse.success("내 대기번호 조회", boothWaitlistService.getMyWaitlist(userId, boothId)));
    }
}
