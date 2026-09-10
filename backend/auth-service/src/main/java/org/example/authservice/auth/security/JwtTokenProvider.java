package org.example.authservice.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    // 실제 서명/검증할때 쓸 암호화 키 객체 필드
    private SecretKey key;

    // 스프링이 앱 시작 하고 딱 한번 secret를 바이트 배열로 변환!!
    // 바이트 배열을 Hs256 알고리즘에 쓸 수 있는 SecretKey 객체로 변환
    @PostConstruct
    public void init(){
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
    }

    // AccessToken 생성
    // festivalId는 HELPER(도우미) 계정만 값이 있고 나머지 역할은 null이다 — Gateway가 X-Festival-Id로 전달해
    // reservation-service가 "이 도우미가 담당하는 페스티벌"을 별도 조회 없이 판별한다.
    public String generateAccessToken(Long userId, String username, String role, Long festivalId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenExpiration);

        return Jwts.builder()
                .subject(username)
                .claim("userId", userId) // Gateway가 X-User-Id로 전달할 회원 숫자 ID
                .claim("role", role) // 페이로드에 role 필드 추가!!
                .claim("festivalId", festivalId) // HELPER가 담당하는 페스티벌(그 외 역할은 null)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    // Refresh Token 생성 (role 미포함 - 재발급 시 DB에서 최신 role 재조회)
    public String generateRefreshToken(String username) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + refreshTokenExpiration);

        return Jwts.builder()
                .subject(username)
                .id(UUID.randomUUID().toString()) // jti - 매번 고유한 값이라 같은 초에 발급돼도 토큰이 달라짐
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }


    // 토큰에서 username(이메일) 추출
    public String getUsernameFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    // 토큰에서 role 추출
    public String getRoleFromToken(String token) {
        return parseClaims(token).get("role", String.class);
    }

    // 토큰 유효성 검증 -
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // 공통 파싱 로직- 실제로 토큰을 해독하는 로직!!
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token) // 서명/검증/만료시간 체크 자동
                .getPayload();
    }
}
