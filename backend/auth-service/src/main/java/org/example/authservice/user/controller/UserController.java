package org.example.authservice.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.authservice.common.dto.ApiResponse;
import org.example.authservice.user.dto.UserResponse;
import org.example.authservice.user.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "회원", description = "내 정보 조회, 닉네임 수정, 비빌번호 수정, 회원 탈퇴 API")
public class UserController {
    private final UserService userService;

    @Operation(summary = "내 정보 조회", description = "로그인한 사용자 본인의 정보를 조회합니다." )
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getMyInfo(@RequestHeader("X-User-Id") Long userId) {
        UserResponse response = userService.getMyInfo(userId);
        return ResponseEntity.ok(ApiResponse.success("내 정보 조회 성공", response ));
    }
}
