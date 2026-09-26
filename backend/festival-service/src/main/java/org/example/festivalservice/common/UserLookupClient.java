package org.example.festivalservice.common;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Festival-Service → Auth-Service 내부 조회: userId 목록으로 이름·닉네임·이메일을 가져온다.
 * 운영자 심사 화면에 "누가 신청했는지"를 보여주기 위한 보조 정보라, auth-service가 응답하지 못해도
 * 목록 자체는 내려가야 하므로 실패 시 빈 맵을 돌려주고 로그만 남긴다.
 */
@Component
@RequiredArgsConstructor
public class UserLookupClient {

    private static final Logger log = LoggerFactory.getLogger(UserLookupClient.class);

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UserSummary(Long id, String username, String name, String nickname) {
    }

    //공통 응답 봉투 {success, data, ...} 중 data만 읽는다(ApiResponse는 역직렬화용 생성자가 없다).
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record LookupEnvelope(List<UserSummary> data) {
    }

    //검색 API 응답 봉투 — data가 회원 id 목록이다.
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record IdsEnvelope(List<Long> data) {
    }

    private final RestClient authServiceRestClient;

    @Value("${internal.auth-service.token:CHANGE_ME_IN_ENV}")
    private String internalAuthToken;

    public Map<Long, UserSummary> findByIds(Collection<Long> userIds) {
        List<Long> ids = userIds.stream().distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        try {
            LookupEnvelope response = authServiceRestClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/internal/v1/users").queryParam("ids", ids).build())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + internalAuthToken)
                    .retrieve()
                    .body(LookupEnvelope.class);
            if (response == null || response.data() == null) {
                return Map.of();
            }
            return response.data().stream().collect(Collectors.toMap(UserSummary::id, Function.identity()));
        } catch (RestClientException e) {
            log.warn("auth-service 사용자 요약 조회 실패 — 신청자 정보 없이 목록을 반환한다. ids={}", ids, e);
            return Map.of();
        }
    }

    /**
     * 닉네임·이메일 검색어로 회원 id 목록을 조회한다(최대 500개, auth-service 기준).
     * 어드민 페스티벌 검색에서 "주최자 이름으로 찾기"에 쓰며, 실패하면 빈 목록을 돌려줘
     * 페스티벌명 검색은 그대로 동작하게 한다.
     */
    public List<Long> searchIds(String keyword, String role) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        try {
            IdsEnvelope response = authServiceRestClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/internal/v1/users/search").queryParam("keyword", keyword.trim());
                        if (role != null) {
                            uriBuilder.queryParam("role", role);
                        }
                        return uriBuilder.build();
                    })
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + internalAuthToken)
                    .retrieve()
                    .body(IdsEnvelope.class);
            if (response == null || response.data() == null) {
                return List.of();
            }
            return response.data();
        } catch (RestClientException e) {
            log.warn("auth-service 사용자 검색 실패 — 주최자 검색 없이 진행한다. keyword={}", keyword, e);
            return List.of();
        }
    }
}