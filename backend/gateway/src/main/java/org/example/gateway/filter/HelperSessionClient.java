package org.example.gateway.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * HELPER 보호 API마다 계정 상태와 세션 버전을 확인해 활성화·해지 이전 세션을 즉시 차단한다.
 * Auth 응답 실패나 timeout 때 유효성을 보장할 수 없으므로 접근을 거부한다.
 */
@Component
public class HelperSessionClient {

    private final WebClient client;

    public HelperSessionClient(
            @Value("${AUTH_SERVICE_URL:http://localhost:8081}") String baseUrl,
            @Value("${INTERNAL_AUTH_TOKEN:CHANGE_ME_IN_ENV}") String internalToken
    ) {
        client = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + internalToken)
                .build();
    }

    public Mono<Boolean> isValid(long userId, long festivalId, long version) {
        return client.get()
                .uri(builder -> builder.path("/internal/v1/helper-accounts/session")
                        .queryParam("userId", userId)
                        .queryParam("festivalId", festivalId)
                        .queryParam("version", version)
                        .build())
                .retrieve()
                .bodyToMono(SessionResponse.class)
                .map(result -> result.success() && Boolean.TRUE.equals(result.data()))
                .timeout(Duration.ofSeconds(3))
                .onErrorReturn(false)
                .defaultIfEmpty(false);
    }

    private record SessionResponse(
            boolean success,
            Boolean data
    ) {
    }
}
