package org.example.festivalservice.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.festivalservice.common.dto.ApiResponse;
import org.example.festivalservice.domain.booth.BoothImageUploadResponseDto;
import org.example.festivalservice.domain.booth.BoothImageUploadService;
import org.example.festivalservice.domain.booth.BoothRequestDto;
import org.example.festivalservice.domain.booth.BoothResponseDto;
import org.example.festivalservice.domain.booth.BoothService;
import org.example.festivalservice.domain.booth.BoothStatusUpdateRequestDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** STOREHOST(부스 운영자) 전용 — Gateway가 로그인은 걸러주고, 역할 체크는 서비스 계층에서 한다. */
@RestController
@RequestMapping("/api/store/booths")
@RequiredArgsConstructor
public class StoreBoothController {

    private final BoothService boothService;
    private final BoothImageUploadService boothImageUploadService;

    //부스 등록 전 대표 이미지 1장을 먼저 업로드하고 URL을 받는다.
    @PostMapping("/images")
    public ResponseEntity<ApiResponse<BoothImageUploadResponseDto>> uploadImage(
            @RequestHeader("X-User-Role") String role,
            @RequestParam(value = "image", required = false) MultipartFile image
    ) {
        return ResponseEntity.ok(ApiResponse.success("부스 이미지 업로드 성공", boothImageUploadService.upload(role, image)));
    }

    //부스 개설 — 역할 체크만 한다(어느 페스티벌 소유인지, 승인 상태인지는 검증하지 않음)
    @PostMapping
    public ResponseEntity<ApiResponse<BoothResponseDto>> createBooth(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody BoothRequestDto request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("부스 개설 성공", boothService.createBooth(userId, role, request)));
    }

    //본인이 개설한 부스 목록(상태 무관)
    @GetMapping
    public ResponseEntity<ApiResponse<List<BoothResponseDto>>> listMyBooths(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role
    ) {
        return ResponseEntity.ok(ApiResponse.success("내 부스 목록 조회", boothService.listMyBooths(userId, role)));
    }

    //본인 부스 상세(상태 무관)
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BoothResponseDto>> getMyBoothDetail(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role
    ) {
        return ResponseEntity.ok(ApiResponse.success("내 부스 상세 조회", boothService.getMyBoothDetail(id, userId, role)));
    }

    //부스 상태 변경(대기/운영중/마감)
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<BoothResponseDto>> changeStatus(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody BoothStatusUpdateRequestDto request
    ) {
        return ResponseEntity.ok(ApiResponse.success("부스 상태 변경 성공", boothService.changeStatus(id, userId, role, request)));
    }
}
