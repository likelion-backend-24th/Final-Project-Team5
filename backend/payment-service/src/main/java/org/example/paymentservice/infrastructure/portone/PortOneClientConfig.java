package org.example.paymentservice.infrastructure.portone;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Payment-Service → PortOne REST API 호출용 RestClient. connect 1s/read 2s(API 계약). */
@Configuration
public class PortOneClientConfig {

    @Bean
    public RestClient portOneRestClient(
            @Value("${portone.api-base-url}") String baseUrl,
            @Value("${portone.api-secret}") String apiSecret) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(1000);
        requestFactory.setReadTimeout(2000);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                // PG사 Secret이 아니라 PortOne V2 API Secret이며, "PortOne " 접두사를 반드시 붙인다.
                .defaultHeader(HttpHeaders.AUTHORIZATION, "PortOne " + apiSecret)
                .build();
    }
}
