package org.example.authservice.auth.controller;

import org.example.authservice.auth.dto.TokenResponse;
import org.example.authservice.common.dto.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.Duration;


@Component
public class AuthCookieResponseBuilder {

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    //Refresh Token을 HttpOnly 쿠키로 응답에 실어주는 역할
    //(Access Token은 body에, Refresh Token은 쿠키에 분리해서 내려줌)
    public ResponseEntity<ApiResponse<TokenResponse>> buildWithCookie(TokenResponse response) {
        ResponseCookie cookie = ResponseCookie.from("refreshToken", response.getRefreshToken())
                .httpOnly(true)
                .secure(true) //Https/localhost 만 전송 나중에 고려
                .sameSite("Strict")  //다른 사이트 요청엔 쿠키 미전송
                .path("/") //모든 경로에 쿠키전송
                .maxAge(Duration.ofMillis(refreshTokenExpiration))
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.success(response,"Access Token/Refresh Token 전송 성공"));
    }

    // 로그아웃 시 - 쿠키를 즉시 만료시켜서 삭제
    public ResponseEntity<ApiResponse<Void>> buildWithCookieDeleted() {
        ResponseCookie deleteCookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(0)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, deleteCookie.toString())
                .body(ApiResponse.success(null,"로그아웃 성공"));
    }
}
