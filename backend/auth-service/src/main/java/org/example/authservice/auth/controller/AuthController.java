package org.example.authservice.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.authservice.auth.dto.LoginRequest;
import org.example.authservice.auth.dto.SignupRequest;
import org.example.authservice.auth.dto.TokenResponse;
import org.example.authservice.auth.dto.emailverification.ResetPasswordRequest;
import org.example.authservice.auth.service.AuthService;
import org.example.authservice.common.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name="인증", description = "회원가입, 로그인, 토큰 재발급, 로그아웃,소셜 로그인 API")
public class AuthController {

    private final AuthService authService;
    private final AuthCookieResponseBuilder authCookieResponseBuilder;

    //회원가입
    @Operation(summary = "회원가입", description = "이름, 이메일(username), 비밀번호, 닉네임으로 회원가입 합니다.")
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<Void>> signup(@Valid @RequestBody SignupRequest signupRequest){
        authService.signup(signupRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("회원가입 성공",null));
    }

    //로그인
    @Operation(summary = "로그인",description = "이메일(username)과 비밀번호로 로그인하고 JWT 토큰을 발급받습니다, 기존 소셜 계정과 이메일이 같으면 자동 연동됩니다.")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest loginRequest){
        TokenResponse response = authService.login(loginRequest);
        return authCookieResponseBuilder.buildWithCookie(response,"로그인 성공");
    }

    // 토큰 재발급
    @Operation(summary = "토큰 재발급", description = "쿠키의 Refresh Token으로 Access/Refresh Token을 재발급합니다. Rotation 및 재사용 탐지가 적용됩니다.")
    @PostMapping("/reissue")
    public ResponseEntity<ApiResponse<TokenResponse>> reissue(
            @CookieValue("refreshToken") String refreshToken) {
        TokenResponse response = authService.reissue(refreshToken);
        return authCookieResponseBuilder.buildWithCookie(response,"토큰 재발급 성공");
    }

    //로그아웃
    @Operation(summary = "로그아웃", description = "현재 세션의 Refresh Token을 무효화하고, 쿠키를 삭제합니다.")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(value = "refreshToken", required = false) String refreshToken) {
        authService.logout(refreshToken);
        return authCookieResponseBuilder.buildWithCookieDeleted("로그아웃 성공");
    }

    //비밀번호 재설정
    @Operation(summary = "비밀번호 재설정", description = "이메일 인증 완료 후 비밀번호를 재설정합니다. (로그인 없이 이메일 인증만으로 진행)")
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.getUsername(), request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.success("비밀번호가 재설정되었습니다.", null));
    }

    // Kakao 로그인 콜백
    @Operation(summary = "카카오 로그인 콜백", description = "카카오가 발급한 인가 코드(code)를 받아 로그인 처리합니다. 최초 로그인 시 자동 회원가입됩니다.")
    @GetMapping("/kakao/callback")
    public ResponseEntity<Void> kakaoLoginCallback(@RequestParam("code") String code) {
        TokenResponse response = authService.kakaoLogin(code);
        return authCookieResponseBuilder.buildRedirectWithCookie(response);
    }

    // Google 로그인 콜백
    @Operation(summary = "구글 로그인 콜백", description = "구글이 발급한 인가 코드(code)를 받아 로그인 처리합니다. 최초 로그인 시 자동 회원가입되며, 기존 일반 가입 계정과 이메일이 같으면 자동 연동됩니다.")
    @GetMapping("/google/callback")
    public ResponseEntity<Void> googleLoginCallback(@RequestParam("code") String code) {
        TokenResponse response = authService.googleLogin(code);
        return authCookieResponseBuilder.buildRedirectWithCookie(response);
    }
}
