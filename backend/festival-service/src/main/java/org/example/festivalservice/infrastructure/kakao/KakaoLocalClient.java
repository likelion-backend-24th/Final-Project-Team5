package org.example.festivalservice.infrastructure.kakao;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 카카오 로컬 "키워드로 장소 검색" REST 호출(SDK 미사용). 실패해도 예외를 던지지 않고 빈 결과를 돌려준다 —
 * 여러 페스티벌을 순회하며 호출하는 배치 작업에서, 한 건의 검색 실패가 나머지를 막으면 안 되기 때문이다.
 */
@Component
@RequiredArgsConstructor
public class KakaoLocalClient {

    private static final Logger log = LoggerFactory.getLogger(KakaoLocalClient.class);

    public record KeywordResult(String addressName, String roadAddressName, double latitude, double longitude) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KeywordSearchResponse(List<Document> documents) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Document(
            @JsonProperty("address_name") String addressName,
            @JsonProperty("road_address_name") String roadAddressName,
            String x,
            String y) {
    }

    private final RestClient kakaoLocalRestClient;

    public Optional<KeywordResult> searchKeyword(String query) {
        try {
            KeywordSearchResponse response = kakaoLocalRestClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/v2/local/search/keyword.json")
                            .queryParam("query", query)
                            .queryParam("size", 1)
                            .build())
                    .retrieve()
                    .body(KeywordSearchResponse.class);

            if (response == null || response.documents() == null || response.documents().isEmpty()) {
                return Optional.empty();
            }
            Document doc = response.documents().get(0);
            return Optional.of(new KeywordResult(
                    doc.addressName(), doc.roadAddressName(),
                    Double.parseDouble(doc.y()), Double.parseDouble(doc.x())));
        } catch (RestClientException | NumberFormatException | NullPointerException e) {
            log.warn("카카오 장소 검색 실패. query={}", query, e);
            return Optional.empty();
        }
    }
}
