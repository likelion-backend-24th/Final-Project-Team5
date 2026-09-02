package org.example.festivalservice.controller;

import lombok.RequiredArgsConstructor;
import org.example.festivalservice.common.ApiResponse;
import org.example.festivalservice.domain.hostapplication.HostApplicationSubmitRequestDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/host-applications")
public class AdminController {

    //운영자가 심사 대기 중인 주최 신청 목록을 조회한다
    @GetMapping
    public ResponseEntity<ApiResponse<List<HostApplicationSubmitRequestDto>>> listHostApplications(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role){
        return null;
    }

    //운영자가 주최 신청을 승인·반려하고, 승인 시 주최자 권한을 부여한다
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<?>> reviewHostApplication(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role){
        return null;
    }
}
