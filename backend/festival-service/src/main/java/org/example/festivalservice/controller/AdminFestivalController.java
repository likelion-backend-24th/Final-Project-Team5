package org.example.festivalservice.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.festivalservice.common.dto.ApiResponse;
import org.example.festivalservice.domain.festival.FestivalCancellationRequestResponseDto;
import org.example.festivalservice.domain.festival.FestivalCancellationService;
import org.example.festivalservice.domain.festival.FestivalResponseDto;
import org.example.festivalservice.domain.festival.FestivalReviewRequestDto;
import org.example.festivalservice.domain.festival.FestivalService;
import org.example.festivalservice.domain.festival.FestivalStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/festivals")
@RequiredArgsConstructor
public class AdminFestivalController {

    private final FestivalService festivalService;
    private final FestivalCancellationService festivalCancellationService;

    //운영자가 심사 대기 중인 페스티벌 목록을 조회한다
    @GetMapping
    public ResponseEntity<ApiResponse<List<FestivalResponseDto>>> listPendingFestivals(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(ApiResponse.success("심사 대기 중인 페스티벌 목록 조회",festivalService.listPendingFestivals(role)));
    }

    //운영자가 대기 중인 페스티벌을 공개·반려 처리한다
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<FestivalResponseDto>> reviewFestival(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody FestivalReviewRequestDto request) {
        return ResponseEntity.ok(ApiResponse.success("심사 대기 중인 페스티벌 상태 변경 성공",festivalService.reviewFestival(id, role, request)));
    }

    //운영자가 승인 대기 중인 행사 취소 요청 목록을 조회한다(approved=true면 환불 배치가 이미 진행 중)
    @GetMapping("/cancellation-requests")
    public ResponseEntity<ApiResponse<List<FestivalCancellationRequestResponseDto>>> listCancellationRequests(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(ApiResponse.success("취소 승인 요청",
                festivalCancellationService.listCancellationRequests(role)));
    }

    //운영자가 행사 취소를 승인한다 — 승인 시각은 최초 1회만 기록되고 환불 배치가 이를 기준으로 전액 환불을 시작한다
    @PostMapping("/{id}/approve-cancellation")
    public ResponseEntity<ApiResponse<FestivalStatus>> approveCancellation(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(ApiResponse.success("전액 환불 승인",
                festivalCancellationService.approveCancellation(id, userId, role)));
    }

    //운영자가 행사 취소 요청을 반려한다 — 요청 전 상태(공개/종료)로 되돌리며, 이미 승인된 요청은 반려할 수 없다
    @PostMapping("/{id}/reject-cancellation")
    public ResponseEntity<ApiResponse<FestivalStatus>> rejectCancellation(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(ApiResponse.success("취소 요청 반려",
                festivalCancellationService.rejectCancellation(id, role)));
    }
}
