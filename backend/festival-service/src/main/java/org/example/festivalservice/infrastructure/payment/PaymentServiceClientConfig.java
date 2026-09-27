package org.example.festivalservice.infrastructure.payment;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Festival-Service → Payment-Service 내부 호출용 RestClient. 운영자 화면 보조 정보라 타임아웃을 짧게 둔다.
 */
@Configuration
public class PaymentServiceClientConfig {

    @Bean
    public RestClient paymentServiceRestClient(
            @Value("${payment-service.base-url:http://localhost:8084}") String baseUrl) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(2));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}