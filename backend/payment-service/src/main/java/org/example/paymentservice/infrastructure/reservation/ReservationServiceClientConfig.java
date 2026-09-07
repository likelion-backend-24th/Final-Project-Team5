package org.example.paymentservice.infrastructure.reservation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Payment-Service → Reservation-Service 내부 호출용 RestClient. connect 1s/read 2s(API 계약). */
@Configuration
public class ReservationServiceClientConfig {

    @Bean
    public RestClient reservationServiceRestClient(
            @Value("${reservation-service.base-url}") String baseUrl,
            @Value("${internal.auth-token}") String internalAuthToken) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(1000);
        requestFactory.setReadTimeout(2000);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + internalAuthToken)
                .build();
    }
}
