package org.example.authservice.auth.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.authservice.auth.dto.SignupRequest;
import org.example.authservice.auth.service.AuthService;
import org.example.authservice.common.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name="인증", description = "회원가입, 로그인, 토큰 재발급, 로그아웃,소셜 로그인 API")
public class AuthController {

    private final AuthService authService;

    //회원가입
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<Void>> signup(@Valid @RequestBody SignupRequest signupRequest){
        authService.signup(signupRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(null));

    }
}
