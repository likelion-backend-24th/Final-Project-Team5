package org.example.authservice.user.controller;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.example.authservice.common.dto.ApiResponse;
import org.example.authservice.user.dto.AdminHostResponse;
import org.example.authservice.user.service.AdminHostService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 화면의 주최자 계정 목록 조회를 Auth Service로 연결한다.
 */
@RestController
@RequestMapping("/api/admin/hosts")
@RequiredArgsConstructor
public class AdminHostController {

    private final AdminHostService adminHostService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminHostResponse>>> listHosts(
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(ApiResponse.success("주최자 목록 조회 성공", adminHostService.listHosts(role)));
    }
}
