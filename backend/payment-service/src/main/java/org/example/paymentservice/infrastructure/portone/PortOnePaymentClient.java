package org.example.paymentservice.infrastructure.portone;

import lombok.RequiredArgsConstructor;
import org.example.paymentservice.infrastructure.portone.dto.PortOnePaymentResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * PortOne V2 결제 조회 REST API 호출만 담당한다. 응답을 내부 도메인 상태로 매핑하고
 * 검증하는 책임은 여기가 아니라 상위 결제 검증·동기화 서비스(Task 7-4)에 있다.
 */
@Component
@RequiredArgsConstructor
public class PortOnePaymentClient {

    private final RestClient portOneRestClient;

    // 브라우저 결과·웹훅 본문을 신뢰하지 않고, 항상 이 조회로 최신 결제 상태를 다시 확인한다.
    public PortOnePaymentResponse getPayment(String paymentId) {
        return portOneRestClient.get()
                .uri("/payments/{paymentId}", paymentId)
                .retrieve()
                .body(PortOnePaymentResponse.class);
    }
}
