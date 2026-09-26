package org.example.festivalservice.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.example.festivalservice.common.dto.ApiResponse;
import org.example.festivalservice.common.dto.Meta;
import org.example.festivalservice.domain.festival.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/festivals")
@RequiredArgsConstructor
public class AdminFestivalController {

    private final FestivalService festivalService;
    private final FestivalCancellationService festivalCancellationService;
    private final FestivalCoordinateBackfillService festivalCoordinateBackfillService;
    private final FestivalOperationService festivalOperationService;

    //운영자 페스티벌 심사 목록 — 상태 묶음(ALL/PENDING/APPROVED/REJECTED)·검색·정렬·페이징
    @GetMapping
    public ResponseEntity<ApiResponse<List<FestivalResponseDto>>> searchFestivalsForAdmin(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<FestivalResponseDto> page = festivalService.searchFestivalsForAdmin(role, status, keyword, pageable);
        return ResponseEntity.ok(ApiResponse.success("운영자 페스티벌 심사 목록 조회", page.getContent(), Meta.of(page)));
    }

    //운영자 주최자 목록용 — 주최자 id별 등록 페스티벌 개수(상태 무관, 최대 100명)
    @GetMapping("/host-counts")
    public ResponseEntity<ApiResponse<Map<Long, Long>>> countFestivalsByHosts(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role,
            @RequestParam("hostIds") List<Long> hostIds) {
        return ResponseEntity.ok(ApiResponse.success("주최자별 페스티벌 개수 조회",
                festivalService.countFestivalsByHosts(role, hostIds)));
    }

    //운영자 운영 현황 — 공개된 적 있는 페스티벌의 운영 상태(ALL/SCHEDULED/ONGOING/CLOSED/CANCELLED)·판매 현황
    @GetMapping("/operations")
    public ResponseEntity<ApiResponse<List<FestivalOperationResponseDto>>> searchOperations(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role,
            @RequestParam(defaultValue = "ALL") String operationStatus,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 10, sort = "startAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<FestivalOperationResponseDto> page =
                festivalOperationService.searchOperations(role, operationStatus, keyword, pageable);
        return ResponseEntity.ok(ApiResponse.success("운영자 페스티벌 운영 현황 조회", page.getContent(), Meta.of(page)));
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

    //운영자 행사 취소 목록 — PENDING(대기)·REFUNDING(환불 진행 중)·CANCELLED(취소 완료)·REJECTED(반려)
//대기 목록에는 승인 전 영향 미리보기(판매 티켓 수·예상 환불 금액)가 붙는다
    @GetMapping("/cancellation-requests")
    public ResponseEntity<ApiResponse<List<FestivalCancellationRequestResponseDto>>> listCancellationRequests(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role,
            @RequestParam(defaultValue = "PENDING") String status) {
        return ResponseEntity.ok(ApiResponse.success("행사 취소 목록 조회",
                festivalCancellationService.listCancellationRequests(role, status)));
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
                festivalCancellationService.rejectCancellation(id, userId, role)));
    }

    //운영자가 좌표 없이 등록된 페스티벌들의 좌표를 카카오 장소 검색으로 일괄 채운다(실행할 때마다 남아있는
    //null 좌표만 대상으로 하므로 여러 번 실행해도 안전하다).
    @PostMapping("/backfill-coordinates")
    public ResponseEntity<ApiResponse<List<CoordinateBackfillResultDto>>> backfillCoordinates(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(ApiResponse.success("좌표 백필 실행 결과",
                festivalCoordinateBackfillService.backfillMissingCoordinates(role)));
    }
}
