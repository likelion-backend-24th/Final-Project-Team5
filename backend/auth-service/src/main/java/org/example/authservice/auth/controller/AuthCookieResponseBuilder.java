package org.example.authservice.auth.controller;

import org.example.authservice.auth.dto.TokenResponse;
import org.example.authservice.common.dto.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;


@Component
public class AuthCookieResponseBuilder {

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    //Refresh Token을 HttpOnly 쿠키로 응답에 실어주는 역할
    //(Access Token은 body에, Refresh Token은 쿠키에 분리해서 내려줌)
    public ResponseEntity<ApiResponse<TokenResponse>> buildWithCookie(TokenResponse response,String message) {
        ResponseCookie cookie = ResponseCookie.from("refreshToken", response.getRefreshToken())
                .httpOnly(true)
                .secure(true) //Https/localhost 만 전송 나중에 고려
                .sameSite("Strict")  //다른 사이트 요청엔 쿠키 미전송
                .path("/") //모든 경로에 쿠키전송
                .maxAge(Duration.ofMillis(refreshTokenExpiration))
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.success(message, response));
    }

    // 로그아웃 시 - 쿠키를 즉시 만료시켜서 삭제
    public ResponseEntity<ApiResponse<Void>> buildWithCookieDeleted(String message) {
        ResponseCookie deleteCookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(0)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, deleteCookie.toString())
                .body(ApiResponse.success(message, null));
    }

    @Value("${app.frontend-url}")
    private String frontendUrl;

    // 소셜 로그인 성공 시 RefreshToken은 쿠키로 실어주고, 프론트 홈페이지로 302 리다이렉트
    public ResponseEntity<Void> buildRedirectWithCookie(TokenResponse response) {
        ResponseCookie cookie = ResponseCookie.from("refreshToken", response.getRefreshToken())
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ofMillis(refreshTokenExpiration))
                .build();

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .header(HttpHeaders.LOCATION, frontendUrl)
                .build();
    }

    // 소셜 로그인 콜백이 실패했을 때(탈퇴·정지 계정 등) — API 에러 JSON을 그대로 보여주는 대신
    // 로그인 화면으로 돌려보내 프론트가 익숙한 에러 문구로 안내하게 한다.
    public ResponseEntity<Void> buildLoginErrorRedirect(String errorCode) {
        String location = frontendUrl + "/login?error=" + URLEncoder.encode(errorCode, StandardCharsets.UTF_8);

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, location)
                .build();
    }

    // 이미 비밀번호로 쓰이던 이메일로 소셜 로그인이 들어왔을 때 — 로그인을 완료하지 않고(쿠키 없음)
    // 전환 동의 화면으로 보낸다. 동의는 이 토큰을 그대로 들고 /api/auth/oauth/confirm-link를 호출해야 한다.
    public ResponseEntity<Void> buildLinkConfirmRedirect(String pendingLinkToken, String email) {
        String location = frontendUrl + "/oauth/link-confirm"
                + "?token=" + URLEncoder.encode(pendingLinkToken, StandardCharsets.UTF_8)
                + "&email=" + URLEncoder.encode(email, StandardCharsets.UTF_8);

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, location)
                .build();
    }
}
