package org.example.authservice.admin.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.authservice.admin.dto.AdminUserResponse;
import org.example.authservice.admin.dto.AdminUserSuspendRequest;
import org.example.authservice.admin.service.AdminUserService;
import org.example.authservice.common.dto.ApiResponse;
import org.example.authservice.common.dto.Meta;
import org.example.authservice.user.entity.AccountStatus;
import org.example.authservice.user.entity.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    // 회원 목록 조회
    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminUserResponse>>> getUsers(
            @RequestHeader("X-User-Role") String role,
            @RequestParam(required = false) String keyword,
            @RequestParam(value = "role", required = false) Role roleFilter,
            @RequestParam(value = "status", required = false) AccountStatus statusFilter,
            @PageableDefault(size = 10) Pageable pageable) {

        Page<AdminUserResponse> page = adminUserService.getUsers(role, keyword, roleFilter, statusFilter, pageable);
        return ResponseEntity.ok(ApiResponse.success("회원 목록 조회 성공", page.getContent(), Meta.of(page)));
    }

    // 회원 정지
    @PatchMapping("/{userId}/suspend")
    public ResponseEntity<ApiResponse<Void>> suspendUser(
            @RequestHeader("X-User-Role") String role,
            @RequestHeader("X-User-Id") Long adminId,
            @PathVariable Long userId,
            @Valid @RequestBody AdminUserSuspendRequest request) {

        adminUserService.suspendUser(role, adminId, userId, request.getReason());
        return ResponseEntity.ok(ApiResponse.success("회원이 정지되었습니다.", null));
    }

    // 회원 정지 해제
    @PatchMapping("/{userId}/unsuspend")
    public ResponseEntity<ApiResponse<Void>> unsuspendUser(
            @RequestHeader("X-User-Role") String role,
            @PathVariable Long userId) {

        adminUserService.unsuspendUser(role, userId);
        return ResponseEntity.ok(ApiResponse.success("회원 정지가 해제되었습니다.", null));
    }
}