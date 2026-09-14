package org.example.paymentservice.domain.settlement;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import java.net.http.HttpClient;
import java.time.*;
import java.util.*;

@Component
public class FestivalSettlementClient {
    private final RestClient client;
    public record Context(Long festivalId, Long hostUserId, String name, Instant eligibleAt, String status) { }
    public FestivalSettlementClient(@Value("${festival-service.base-url:http://localhost:8082}") String url,
                                    @Value("${internal.auth-token}") String token) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        client = RestClient.builder().baseUrl(url).requestFactory(factory)
                .defaultHeader("Authorization", "Bearer " + token).build();
    }
    public List<Context> candidates(int page) {
        return Arrays.asList(Objects.requireNonNull(client.get().uri("/internal/v1/festivals/settlement-candidates?page={page}", page)
                .retrieve().body(Context[].class)));
    }
    public Context context(Long id) {
        return Objects.requireNonNull(client.get().uri("/internal/v1/festivals/{id}/settlement-context", id)
                .retrieve().body(Context.class));
    }
    public record RefundCandidate(Long festivalId, Long hostUserId, Long initiatedBy, String reason) { }
    public List<RefundCandidate> refundCandidates() {
        return Arrays.asList(Objects.requireNonNull(client.get().uri("/internal/v1/festivals/refund-candidates")
                .retrieve().body(RefundCandidate[].class)));
    }
    public void completeCancellation(Long id) {
        client.post().uri("/internal/v1/festivals/{id}/complete-cancellation", id).retrieve().toBodilessEntity();
    }
}
