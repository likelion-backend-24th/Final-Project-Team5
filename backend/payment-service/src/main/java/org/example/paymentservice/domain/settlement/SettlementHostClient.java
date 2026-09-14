package org.example.paymentservice.domain.settlement;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;

@Component
public class SettlementHostClient {
    private final RestClient client;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SettlementHostClient.class);
    @JsonIgnoreProperties(ignoreUnknown = true) public record Host(Long id, String name) { }
    @JsonIgnoreProperties(ignoreUnknown = true) public record Envelope(List<Host> data) { }
    public SettlementHostClient(@Value("${auth-service.base-url:http://localhost:8081}") String url,
                                @Value("${internal.auth-token}") String token) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        client = RestClient.builder().baseUrl(url).requestFactory(factory).defaultHeader("Authorization", "Bearer " + token).build();
    }
    public Map<Long, String> names(Collection<Long> ids) {
        var unique = ids.stream().filter(Objects::nonNull).distinct().toList();
        var result = new HashMap<Long, String>();
        for (int offset = 0; offset < unique.size(); offset += 200) {
            var batch = unique.subList(offset, Math.min(offset + 200, unique.size()));
            try {
                var response = client.get().uri(b -> b.path("/internal/v1/users").queryParam("ids", batch).build()).retrieve().body(Envelope.class);
                if (response != null && response.data() != null) for (var host : response.data())
                    if (host.id() != null && host.name() != null && !host.name().isBlank()) result.put(host.id(), host.name());
            } catch (org.springframework.web.client.RestClientException e) {
                log.warn("정산 주최자 이름 조회를 다음 조회 주기에 재시도합니다.");
            }
        }
        return result;
    }
}
