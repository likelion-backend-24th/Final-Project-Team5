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

    private static final String ROLE_CLAIM = "role";
    private static final String BEARER_PREFIX = "Bearer ";

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/**",
            "/api/festivals/**",
            "/api/ticket-types/**"
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

            ServerHttpRequest authenticatedRequest = strippedRequest.mutate()
                    .header(USER_ID_HEADER, claims.getSubject())
                    .header(USER_ROLE_HEADER, claims.get(ROLE_CLAIM, String.class))
                    .build();
            return chain.filter(exchange.mutate().request(authenticatedRequest).build());
        } catch (JwtException | IllegalArgumentException e) {
            return unauthorized(exchange);
        }
    }

    private boolean isPublic(String path) {
        return PUBLIC_PATHS.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
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

    @Override
    public int getOrder() {
        return -1;
    }
}
