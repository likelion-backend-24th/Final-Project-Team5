package org.example.festivalservice.infrastructure.gemini;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Festival-Service → Google Gemini API 호출용 RestClient. connect 2s / read 20s.
 * 생성형 응답은 수 초가 걸리는 게 정상이라 내부 서비스 호출(1~5s)보다 read 여유를 크게 둔다.
 * API 키는 헤더(x-goog-api-key)로 매 요청에 붙이며, 값은 .env의 GEMINI_API_KEY로만 주입한다.
 */
@Configuration
public class GeminiClientConfig {

    @Bean
    public RestClient geminiRestClient(
            @Value("${gemini.base-url:https://generativelanguage.googleapis.com}") String baseUrl,
            @Value("${gemini.api-key:CHANGE_ME_IN_ENV}") String apiKey) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(20));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader("x-goog-api-key", apiKey)
                .build();
    }
}
