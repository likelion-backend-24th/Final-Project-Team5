package org.example.authservice.helper.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.authservice.auth.controller.AuthCookieResponseBuilder;
import org.example.authservice.auth.dto.TokenResponse;
import org.example.authservice.common.dto.ApiResponse;
import org.example.authservice.helper.dto.AcceptHelperInvitationRequest;
import org.example.authservice.helper.dto.HelperInvitationInfo;
import org.example.authservice.helper.service.HelperAccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/helper-invitations")
@RequiredArgsConstructor
public class HelperInvitationController {

    private final HelperAccountService helperAccountService;
    private final AuthCookieResponseBuilder cookieResponseBuilder;

    @GetMapping("/{token}")
    public ResponseEntity<ApiResponse<HelperInvitationInfo>> inspect(@PathVariable String token) {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .header("Referrer-Policy", "no-referrer")
                .body(ApiResponse.success("도우미 초대 조회 성공", helperAccountService.inspect(token)));
    }

    @PostMapping("/{token}/accept")
    public ResponseEntity<ApiResponse<TokenResponse>> accept(
            @PathVariable String token,
            @Valid @RequestBody AcceptHelperInvitationRequest request,
            @CookieValue(value = "refreshToken", required = false) String previousRefreshToken
    ) {
        return cookieResponseBuilder.buildWithCookie(
                helperAccountService.accept(token, request, previousRefreshToken), "도우미 계정 활성화 성공");
    }
}
