package org.example.reservationservice.infrastructure.festival;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Reservation-Service → Festival-Service 호출용 RestClient. connect 1s/read 2s. */
@Configuration
public class FestivalServiceClientConfig {

    @Bean
    public RestClient festivalServiceRestClient(
            @Value("${festival-service.base-url:http://localhost:8082}") String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(1000);
        requestFactory.setReadTimeout(2000);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
