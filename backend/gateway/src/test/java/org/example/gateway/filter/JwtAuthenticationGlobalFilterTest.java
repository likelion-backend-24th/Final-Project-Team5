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
    void allowsWebhookPathWithoutToken() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/webhooks/portone").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
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

    @Test
    void doesNotForwardFestivalIdHeaderWhenClaimIsAbsent() {
        MockServerWebExchange exchange = exchangeWithToken(
                MockServerHttpRequest.post("/api/host-applications"), "HOST", null);

        filter.filter(exchange, chain).block();

        assertThat(forwardedHeaders().getFirst(JwtAuthenticationGlobalFilter.FESTIVAL_ID_HEADER)).isNull();
    }

    @Test
    void stripsClientSuppliedFestivalIdHeader() {
        MockServerWebExchange exchange = exchangeWithToken(
                MockServerHttpRequest.post("/api/host-applications")
                        .header(JwtAuthenticationGlobalFilter.FESTIVAL_ID_HEADER, "999"),
                "HOST", null);

        filter.filter(exchange, chain).block();

        assertThat(forwardedHeaders().getFirst(JwtAuthenticationGlobalFilter.FESTIVAL_ID_HEADER)).isNull();
    }

    @Test
    void forwardsFestivalIdHeaderForHelperToken() {
        MockServerWebExchange exchange = exchangeWithToken(
                MockServerHttpRequest.post("/api/organizer/reservations/verify"), "HELPER", 7L);

        filter.filter(exchange, chain).block();

        HttpHeaders headers = forwardedHeaders();
        assertThat(headers.getFirst(JwtAuthenticationGlobalFilter.USER_ROLE_HEADER)).isEqualTo("HELPER");
        assertThat(headers.getFirst(JwtAuthenticationGlobalFilter.FESTIVAL_ID_HEADER)).isEqualTo("7");
    }

    @Test
    void allowsHelperOnCheckInRoutes() {
        MockServerWebExchange verifyByCode = exchangeWithToken(
                MockServerHttpRequest.post("/api/organizer/reservations/verify-code"), "HELPER", 7L);
        MockServerWebExchange stats = exchangeWithToken(
                MockServerHttpRequest.get("/api/organizer/reservations/check-in-stats?festivalId=7"), "HELPER", 7L);
        MockServerWebExchange myInfo = exchangeWithToken(
                MockServerHttpRequest.get("/api/users/me"), "HELPER", 7L);

        filter.filter(verifyByCode, chain).block();
        filter.filter(stats, chain).block();
        filter.filter(myInfo, chain).block();

        assertThat(verifyByCode.getResponse().getStatusCode()).isNull();
        assertThat(stats.getResponse().getStatusCode()).isNull();
        assertThat(myInfo.getResponse().getStatusCode()).isNull();
    }

    @Test
    void blocksHelperOnEverythingOutsideTheAllowlist() {
        MockServerWebExchange reservation = exchangeWithToken(
                MockServerHttpRequest.post("/api/reservations"), "HELPER", 7L);
        MockServerWebExchange payment = exchangeWithToken(
                MockServerHttpRequest.post("/api/payments/prepare"), "HELPER", 7L);
        MockServerWebExchange hostFestival = exchangeWithToken(
                MockServerHttpRequest.post("/api/host/festivals"), "HELPER", 7L);

        filter.filter(reservation, chain).block();
        filter.filter(payment, chain).block();
        filter.filter(hostFestival, chain).block();

        assertThat(reservation.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(payment.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(hostFestival.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // 도우미는 비밀번호를 바꿀 수 없다 — 같은 /api/users/me 경로라도 GET만 허용되고 나머지 메서드는 막힌다.
    @Test
    void blocksHelperFromChangingPasswordOrLeaving() {
        MockServerWebExchange passwordChange = exchangeWithToken(
                MockServerHttpRequest.patch("/api/users/me/password"), "HELPER", 7L);
        MockServerWebExchange withdraw = exchangeWithToken(
                MockServerHttpRequest.delete("/api/users/me"), "HELPER", 7L);

        filter.filter(passwordChange, chain).block();
        filter.filter(withdraw, chain).block();

        assertThat(passwordChange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(withdraw.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // 도우미 제한은 HELPER에만 적용되고 참가자·주최자 흐름은 그대로여야 한다.
    @Test
    void doesNotRestrictOtherRoles() {
        MockServerWebExchange userReservation = exchangeWithToken(
                MockServerHttpRequest.post("/api/reservations"), "USER", null);

        filter.filter(userReservation, chain).block();

        assertThat(userReservation.getResponse().getStatusCode()).isNull();
    }

    private MockServerWebExchange exchangeWithToken(MockServerHttpRequest.BaseBuilder<?> builder,
                                                    String role, Long festivalId) {
        String token = Jwts.builder()
                .subject("scanner@test.com")
                .claim("userId", 42L)
                .claim("role", role)
                .claim("festivalId", festivalId)
                .signWith(secretKey)
                .compact();
        return MockServerWebExchange.from(
                builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token).build());
    }

    private HttpHeaders forwardedHeaders() {
        var captor = org.mockito.ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        return captor.getValue().getRequest().getHeaders();
    }
}
