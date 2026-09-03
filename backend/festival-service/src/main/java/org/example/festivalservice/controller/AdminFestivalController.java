package org.example.festivalservice.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.festivalservice.common.ApiResponse;
import org.example.festivalservice.domain.festival.FestivalResponseDto;
import org.example.festivalservice.domain.festival.FestivalReviewRequestDto;
import org.example.festivalservice.domain.festival.FestivalService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/festivals")
@RequiredArgsConstructor
public class AdminFestivalController {

    private final FestivalService festivalService;

    //운영자가 심사 대기 중인 페스티벌 목록을 조회한다
    @GetMapping
    public ResponseEntity<ApiResponse<List<FestivalResponseDto>>> listPendingFestivals(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(ApiResponse.success(festivalService.listPendingFestivals(role),"심사 대기 중인 페스티벌 목록 조회"));
    }

    //운영자가 대기 중인 페스티벌을 공개·반려 처리한다
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<FestivalResponseDto>> reviewFestival(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody FestivalReviewRequestDto request) {
        return ResponseEntity.ok(ApiResponse.success(festivalService.reviewFestival(id, role, request),"심사 대기 중인 페스티벌 상태 변경 성공"));
    }
}
