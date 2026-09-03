package org.example.gateway.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationGlobalFilterTest {

    private static final String SECRET = "test-secret-key-for-jwt-filter-unit-tests-0123456789";

    private final SecretKey secretKey = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    private JwtAuthenticationGlobalFilter filter;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationGlobalFilter(SECRET);
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    @Test
    void allowsPublicPathWithoutToken() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/festivals/3").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void stripsClientSuppliedIdentityHeadersOnPublicPath() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/festivals/3")
                .header(JwtAuthenticationGlobalFilter.USER_ID_HEADER, "999")
                .header(JwtAuthenticationGlobalFilter.USER_ROLE_HEADER, "ADMIN")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        var captor = org.mockito.ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        HttpHeaders forwardedHeaders = captor.getValue().getRequest().getHeaders();
        assertThat(forwardedHeaders.getFirst(JwtAuthenticationGlobalFilter.USER_ID_HEADER)).isNull();
        assertThat(forwardedHeaders.getFirst(JwtAuthenticationGlobalFilter.USER_ROLE_HEADER)).isNull();
    }

    @Test
    void rejectsProtectedPathWithoutToken() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/host-applications").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
    }

    @Test
    void rejectsProtectedPathWithInvalidToken() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/host-applications")
                .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void forwardsUserIdAndRoleHeadersForValidToken() {
        String token = Jwts.builder()
                .subject("user42@test.com")
                .claim("userId", 42L)
                .claim("role", "HOST")
                .signWith(secretKey)
                .compact();

        MockServerHttpRequest request = MockServerHttpRequest.post("/api/host-applications")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        var captor = org.mockito.ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        HttpHeaders forwardedHeaders = captor.getValue().getRequest().getHeaders();
        assertThat(forwardedHeaders.getFirst(JwtAuthenticationGlobalFilter.USER_ID_HEADER)).isEqualTo("42");
        assertThat(forwardedHeaders.getFirst(JwtAuthenticationGlobalFilter.USER_ROLE_HEADER)).isEqualTo("HOST");
    }
}
