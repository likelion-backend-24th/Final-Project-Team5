package org.example.festivalservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.festivalservice.common.ApiResponse;
import org.example.festivalservice.domain.hostapplication.HostApplicationResponseDto;
import org.example.festivalservice.domain.hostapplication.HostApplicationService;
import org.example.festivalservice.domain.hostapplication.HostApplicationSubmitRequestDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/host-applications")
@RequiredArgsConstructor
public class HostApplicationController {

    private final HostApplicationService hostApplicationService;

    //회원이 페스티벌 주최자가 되기 위한 신청을 제출한다
    @PostMapping
    public ResponseEntity<ApiResponse<HostApplicationResponseDto>> submit(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody HostApplicationSubmitRequestDto request
    ) {
        HostApplicationResponseDto response = hostApplicationService.submit(userId, role, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response,"주최자 신청 제출 성공"));
    }

    //본인의 주최 신청 상태·반려사유를 조회한다
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<HostApplicationResponseDto>> getMy(
            @RequestHeader("X-User-Id") Long userId
    ) {
        HostApplicationResponseDto response = hostApplicationService.getMy(userId);
        return ResponseEntity.ok(ApiResponse.success(response,"주최자 신청 상태·반려사유 조회"));
    }
}
