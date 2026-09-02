package org.example.festivalservice.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Festival-Service → Auth-Service 내부 동기 호출용 RestClient. connect 1s/read 2s(API 계약). */
@Configuration
public class AuthServiceClientConfig {

    @Bean
    public RestClient authServiceRestClient(
            @Value("${auth-service.base-url:http://localhost:8081}") String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(1000);
        requestFactory.setReadTimeout(2000);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
