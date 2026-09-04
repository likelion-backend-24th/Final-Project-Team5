package org.example.authservice.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.authservice.auth.dto.LoginRequest;
import org.example.authservice.auth.dto.SignupRequest;
import org.example.authservice.auth.dto.TokenResponse;
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
    @Operation(summary = "로그인",description = "이메일(username)과 비밀번호로 로그인하고 JWT 토큰을 발급받습니다.")
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
}
