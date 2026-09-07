package org.example.authservice.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.authservice.common.dto.ApiResponse;
import org.example.authservice.user.dto.NicknameUpdateRequest;
import org.example.authservice.user.dto.PasswordUpdateRequest;
import org.example.authservice.user.dto.UserResponse;
import org.example.authservice.user.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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

    @Operation(summary = "닉네임 수정", description = "로그인한 사용자 본인의 닉네임을 수정합니다.")
    @PatchMapping("/me/nickname")
    public ResponseEntity<ApiResponse<Void>> updateNickname(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody NicknameUpdateRequest nicknameUpdateRequest){
        userService.updateNickname(userId,nicknameUpdateRequest.getNickname());
        return ResponseEntity.ok(ApiResponse.success("닉네임이 변경되었습니다.",null));
    }

    // 비밀번호 변경
    @Operation(summary = "비밀번호 변경", description = "로그인한 사용자 본인의 비밀번호를 변경합니다. 변경 시 기존 로그인 세션은 모두 무효화됩니다.")
    @PatchMapping("/me/password")
    public ResponseEntity<ApiResponse<Void>> updatePassword(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody PasswordUpdateRequest request) {
        userService.updatePassword(userId, request.getCurrentPassword(), request.getNewPassword(), request.getNewPasswordConfirm());
        return ResponseEntity.ok(ApiResponse.success("비밀번호가 변경되었습니다.", null));
    }
}
