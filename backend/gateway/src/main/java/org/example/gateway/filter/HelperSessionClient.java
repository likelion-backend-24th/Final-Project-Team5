package org.example.gateway.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import java.time.Duration;

@Component
public class HelperSessionClient {
    private final WebClient client;
    public HelperSessionClient(@Value("${AUTH_SERVICE_URL:http://localhost:8081}") String baseUrl,
            @Value("${INTERNAL_AUTH_TOKEN:CHANGE_ME_IN_ENV}") String internalToken) {
        client = WebClient.builder().baseUrl(baseUrl).defaultHeader("Authorization", "Bearer " + internalToken).build();
    }
    public Mono<Boolean> isValid(long userId, long festivalId, long version) {
        return client.get().uri(builder -> builder.path("/internal/v1/helper-accounts/session")
            .queryParam("userId", userId).queryParam("festivalId", festivalId).queryParam("version", version).build())
            .retrieve().bodyToMono(SessionResponse.class).map(result -> result.success() && Boolean.TRUE.equals(result.data()))
            .timeout(Duration.ofSeconds(3)).onErrorReturn(false).defaultIfEmpty(false);
    }
    private record SessionResponse(boolean success, Boolean data) { }
}
