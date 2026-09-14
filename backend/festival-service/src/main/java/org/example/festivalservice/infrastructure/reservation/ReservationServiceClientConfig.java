package org.example.festivalservice.infrastructure.reservation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Festival-Service → Reservation-Service 호출용 RestClient. connect 1s/read 2s.
 * reservation-service의 FestivalServiceClientConfig와 동일한 설정(JdkClientHttpRequestFactory 기반)을 사용한다.
 */
@Configuration
public class ReservationServiceClientConfig {

    @Bean
    public RestClient reservationServiceRestClient(
            @Value("${reservation-service.base-url:http://localhost:8083}") String baseUrl) {
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