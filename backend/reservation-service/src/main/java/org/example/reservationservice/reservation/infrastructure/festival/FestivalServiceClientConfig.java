package org.example.reservationservice.infrastructure.festival;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Reservation-Service → Festival-Service 호출용 RestClient. connect 1s/read 2s.
 * SimpleClientHttpRequestFactory(JDK HttpURLConnection 기반)는 PATCH 메서드를 지원하지 않아
 * deductStock/restoreStock 호출이 항상 ProtocolException으로 실패했다 — JdkClientHttpRequestFactory
 * (java.net.http.HttpClient 기반)로 교체해 PATCH를 지원한다.
 */
@Configuration
public class FestivalServiceClientConfig {

    @Bean
    public RestClient festivalServiceRestClient(
            @Value("${festival-service.base-url:http://localhost:8082}") String baseUrl) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(1))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(2));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
