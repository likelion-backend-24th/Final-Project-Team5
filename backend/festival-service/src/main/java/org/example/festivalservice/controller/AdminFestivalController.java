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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/festivals")
@RequiredArgsConstructor
public class AdminFestivalController {

    private final FestivalCancellationService cancellationService;
    private final FestivalService festivalService;

    //운영자가 심사 대기 중인 페스티벌 목록을 조회한다
    @GetMapping
    public ResponseEntity<ApiResponse<List<FestivalResponseDto>>> listPendingFestivals(
        @RequestHeader("X-User-Id") Long userId,
        @RequestHeader("X-User-Role") String role
    ) {
        return ResponseEntity.ok(
            ApiResponse.success("심사 대기 중인 페스티벌 목록 조회", festivalService.listPendingFestivals(role))
        );
    }

    //운영자가 대기 중인 페스티벌을 공개·반려 처리한다
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<FestivalResponseDto>> reviewFestival(
        @PathVariable Long id,
        @RequestHeader("X-User-Id") Long userId,
        @RequestHeader("X-User-Role") String role,
        @Valid @RequestBody FestivalReviewRequestDto request
    ) {
        return ResponseEntity.ok(
            ApiResponse.success(
                "심사 대기 중인 페스티벌 상태 변경 성공",
                festivalService.reviewFestival(id, role, request)
            )
        );
    }

    // 환불 배치는 승인된 요청만 처리하므로 승인 여부를 먼저 확인한다.
    @GetMapping("/cancellation-requests")
    public ResponseEntity<ApiResponse<List<FestivalCancellationRequestResponseDto>>> cancellationRequests(
        @RequestHeader("X-User-Role") String role
    ) {
        return ResponseEntity.ok(
            ApiResponse.success("취소 승인 요청", cancellationService.listCancellationRequests(role))
        );
    }

    // 환불 배치가 동일 요청을 재시도할 수 있도록 승인 결과를 서비스에서 보존한다.
    @PostMapping("/{id}/approve-cancellation")
    public ResponseEntity<ApiResponse<FestivalStatus>> approveCancellation(
        @PathVariable Long id,
        @RequestHeader("X-User-Role") String role,
        @RequestHeader("X-User-Id") Long actor
    ) {
        return ResponseEntity.ok(
            ApiResponse.success("전액 환불 승인", cancellationService.approveCancellation(id, actor, role))
        );
    }
}
