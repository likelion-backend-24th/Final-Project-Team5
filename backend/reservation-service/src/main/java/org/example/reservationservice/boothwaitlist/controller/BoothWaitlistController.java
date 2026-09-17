package org.example.reservationservice.boothwaitlist.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.reservationservice.boothwaitlist.dto.BoothWaitlistQueueStatusResponseDto;
import org.example.reservationservice.boothwaitlist.dto.BoothWaitlistResponseDto;
import org.example.reservationservice.boothwaitlist.dto.MyActiveBoothWaitlistResponseDto;
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

    //내가 신청한 모든 부스의 대기 현황 — 챗봇 위젯이 주기적으로 폴링해 "내 차례"를 알아낼 때 쓴다.
    @GetMapping("/me/active")
    public ResponseEntity<ApiResponse<List<MyActiveBoothWaitlistResponseDto>>> getMyActiveWaitlists(
            @RequestHeader("X-User-Id") Long userId
    ) {
        return ResponseEntity.ok(ApiResponse.success("내 대기 현황 조회", boothWaitlistService.getMyActiveWaitlists(userId)));
    }

    //STOREHOST가 본인 부스의 대기열 현황(호출 번호·대기 인원)을 조회한다.
    @GetMapping("/booths/{boothId}/queue-status")
    public ResponseEntity<ApiResponse<BoothWaitlistQueueStatusResponseDto>> getQueueStatus(
            @PathVariable Long boothId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role
    ) {
        return ResponseEntity.ok(ApiResponse.success("대기열 현황 조회", boothWaitlistService.getQueueStatus(userId, role, boothId)));
    }

    //STOREHOST가 다음 대기 순번을 호출한다.
    @PostMapping("/booths/{boothId}/call-next")
    public ResponseEntity<ApiResponse<BoothWaitlistQueueStatusResponseDto>> callNext(
            @PathVariable Long boothId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role
    ) {
        return ResponseEntity.ok(ApiResponse.success("다음 순번 호출 성공", boothWaitlistService.callNext(userId, role, boothId)));
    }
}
