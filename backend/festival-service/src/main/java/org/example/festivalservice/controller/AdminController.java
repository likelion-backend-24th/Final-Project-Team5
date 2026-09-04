package org.example.festivalservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.festivalservice.common.ApiResponse;
import org.example.festivalservice.domain.hostapplication.HostApplicationResponseDto;
import org.example.festivalservice.domain.hostapplication.HostApplicationService;
import org.example.festivalservice.domain.hostapplication.HostApplicationSetHostRequestDto;
import org.example.festivalservice.domain.hostapplication.HostApplicationStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/host-applications")
public class AdminController {
    private final HostApplicationService hostApplicationService;

    //운영자가 심사 대기 중인 주최자 신청 목록을 조회한다
    @GetMapping
    public ResponseEntity<ApiResponse<List<HostApplicationResponseDto>>> listHostApplications(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role){
        return ResponseEntity.ok(ApiResponse.success(hostApplicationService.getListHostApplications(role),"주최자 신청 목록 조회"));
    }

    //운영자가 주최자 신청을 승인·반려하고, 승인 시 주최자 권한을 부여한다
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<HostApplicationResponseDto>> reviewHostApplication(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody HostApplicationSetHostRequestDto dto){
        HostApplicationResponseDto response = hostApplicationService.review(id, role, dto);
        HttpStatus httpStatus = response.status() == HostApplicationStatus.APPROVAL_PENDING
                ? HttpStatus.ACCEPTED
                : HttpStatus.OK;
        return ResponseEntity.status(httpStatus).body(ApiResponse.success(response, "주최자 권한 부여 성공"));
    }
}
