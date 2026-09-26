package org.example.authservice.user.controller;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.example.authservice.common.dto.ApiResponse;
import org.example.authservice.common.dto.Meta;
import org.example.authservice.user.dto.AdminHostResponse;
import org.example.authservice.user.entity.AccountStatus;
import org.example.authservice.user.service.AdminHostService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
            @RequestHeader("X-User-Role") String role,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) AccountStatus status,
            @PageableDefault(size = 10) Pageable pageable) {

        Page<AdminHostResponse> page = adminHostService.listHosts(role, keyword, status, pageable);
        return ResponseEntity.ok(ApiResponse.success("주최자 목록 조회 성공", page.getContent(), Meta.of(page)));
    }
}
