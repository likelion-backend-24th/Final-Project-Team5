package org.example.festivalservice.infrastructure.kakao;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Festival-Service → 카카오 로컬 REST API(키워드 장소 검색) 호출용 RestClient.
 * 지도 JavaScript 키와는 다른 REST API 키를 쓴다 — 카카오맵 제품이 활성화된 그 앱의 REST 키여야 한다.
 * 값은 .env의 KAKAO_MAP_REST_API_KEY로만 주입한다(서버 전용, 브라우저에 노출하지 않는다).
 */
@Configuration
public class KakaoLocalClientConfig {

    @Bean
    public RestClient kakaoLocalRestClient(
            @Value("${kakao.local.base-url:https://dapi.kakao.com}") String baseUrl,
            @Value("${kakao.local.rest-api-key:CHANGE_ME_IN_ENV}") String restApiKey) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(5));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader("Authorization", "KakaoAK " + restApiKey)
                .build();
    }
}
