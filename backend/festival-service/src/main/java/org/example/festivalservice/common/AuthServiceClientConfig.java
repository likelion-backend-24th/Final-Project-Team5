package org.example.festivalservice.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Festival-Service → Auth-Service 내부 동기 호출용 RestClient. connect 1s / read 5s.
 * read는 원래 2s였는데, 1GB 서버에서 Role 부여 응답이 2초를 넘겨 신청이 APPROVAL_PENDING에 멈추는 일이
 * 실제로 있었다(멈춘 건은 배치가 재시도하지만, 애초에 첫 시도에서 끝나는 편이 낫다).
 */
@Configuration
public class AuthServiceClientConfig {

    @Bean
    public RestClient authServiceRestClient(
            @Value("${auth-service.base-url:http://localhost:8081}") String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(1000);
        requestFactory.setReadTimeout(5000);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
