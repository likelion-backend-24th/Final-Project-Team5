package org.example.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Verifies the caller's JWT for every route except the public allowlist below, and forwards the
 * verified identity to downstream services as trusted headers. Any client-supplied copies of
 * those headers are always stripped first so a downstream service can trust them unconditionally.
 */
@Component
public class JwtAuthenticationGlobalFilter implements GlobalFilter, Ordered {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ROLE_HEADER = "X-User-Role";
    public static final String FESTIVAL_ID_HEADER = "X-Festival-Id";

    private static final String USER_ID_CLAIM = "userId";
    private static final String ROLE_CLAIM = "role";
    private static final String FESTIVAL_ID_CLAIM = "festivalId";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String HELPER_ROLE = "HELPER";

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/**",
            "/api/festivals/**",
            "/api/ticket-types/**",
            // PortOne 웹훅은 사용자 JWT 대신 자체 서명(webhook-signature 헤더)으로 인증한다(실전 가이드 12.2).
            "/api/v1/webhooks/**"
    );

    /**
     * 도우미(HELPER)는 주최자가 현장 입장 검증만 맡기려고 발급한 임시 계정이라, 나머지 동작은 전부 막는다.
     * 서비스마다 역할 검사를 흩뿌리면 빠뜨리기 쉬우므로 "기본 차단 + 여기 적힌 것만 허용"으로 한곳에서 통제한다.
     * (페스티벌 정보 조회 /api/festivals/**는 애초에 공개 라우트라 위 PUBLIC_PATHS에서 이미 통과한다.)
     */
    private static final List<HelperAllowedRoute> HELPER_ALLOWED_ROUTES = List.of(
            // 로그인한 도우미가 자기 담당 페스티벌 ID를 확인하는 용도
            new HelperAllowedRoute(HttpMethod.GET, "/api/users/me"),
            // QR 스캔 검증
            new HelperAllowedRoute(HttpMethod.POST, "/api/organizer/reservations/verify"),
            // QR 스캔이 안 될 때 쓰는 입장 코드 검증
            new HelperAllowedRoute(HttpMethod.POST, "/api/organizer/reservations/verify-code"),
            // 총 티켓 수 대비 입장 인원 확인
            new HelperAllowedRoute(HttpMethod.GET, "/api/organizer/reservations/check-in-stats")
    );

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final SecretKey secretKey;

    public JwtAuthenticationGlobalFilter(@Value("${jwt.secret}") String secret) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpRequest strippedRequest = request.mutate()
                .headers(headers -> {
                    headers.remove(USER_ID_HEADER);
                    headers.remove(USER_ROLE_HEADER);
                    headers.remove(FESTIVAL_ID_HEADER);
                })
                .build();

        if (isPublic(request.getURI().getPath())) {
            return chain.filter(exchange.mutate().request(strippedRequest).build());
        }

        String token = resolveToken(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        if (token == null) {
            return unauthorized(exchange);
        }

        try {
            Claims claims = Jwts.parser().verifyWith(secretKey).build()
                    .parseSignedClaims(token)
                    .getPayload();

            String role = claims.get(ROLE_CLAIM, String.class);
            if (HELPER_ROLE.equals(role) && !isHelperAllowed(request)) {
                return forbidden(exchange);
            }

            ServerHttpRequest.Builder authenticatedRequest = strippedRequest.mutate()
                    .header(USER_ID_HEADER, String.valueOf(claims.get(USER_ID_CLAIM)))
                    .header(USER_ROLE_HEADER, role);

            // HELPER만 값이 있고 나머지 역할은 null이라, 값이 있을 때만 헤더를 붙인다.
            Object festivalId = claims.get(FESTIVAL_ID_CLAIM);
            if (festivalId != null) {
                authenticatedRequest.header(FESTIVAL_ID_HEADER, String.valueOf(festivalId));
            }

            return chain.filter(exchange.mutate().request(authenticatedRequest.build()).build());
        } catch (JwtException | IllegalArgumentException e) {
            return unauthorized(exchange);
        }
    }

    private boolean isPublic(String path) {
        return PUBLIC_PATHS.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private boolean isHelperAllowed(ServerHttpRequest request) {
        String path = request.getURI().getPath();
        HttpMethod method = request.getMethod();
        return HELPER_ALLOWED_ROUTES.stream()
                .anyMatch(route -> route.method().equals(method) && pathMatcher.match(route.path(), path));
    }

    private String resolveToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return authorizationHeader.substring(BEARER_PREFIX.length());
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        return response.setComplete();
    }

    private Mono<Void> forbidden(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        return response.setComplete();
    }

    @Override
    public int getOrder() {
        return -1;
    }

    /** HELPER에게 허용할 경로를 메서드까지 묶어서 지정한다(같은 경로라도 GET만 허용하는 경우가 있어서). */
    private record HelperAllowedRoute(HttpMethod method, String path) {
    }
}
