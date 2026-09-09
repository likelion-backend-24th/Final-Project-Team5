package org.example.paymentservice.infrastructure.portone;

import lombok.RequiredArgsConstructor;
import org.example.paymentservice.infrastructure.portone.dto.PortOneCancelRequest;
import org.example.paymentservice.infrastructure.portone.dto.PortOneCancelResponse;
import org.example.paymentservice.infrastructure.portone.dto.PortOnePaymentResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * PortOne V2 결제 조회 REST API 호출만 담당한다. 응답을 내부 도메인 상태로 매핑하고
 * 검증하는 책임은 여기가 아니라 상위 결제 검증·동기화 서비스(Task 7-4)에 있다.
 */
@Component
@RequiredArgsConstructor
public class PortOnePaymentClient {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final RestClient portOneRestClient;

    @Value("${portone.store-id}")
    private String storeId;

    // 브라우저 결과·웹훅 본문을 신뢰하지 않고, 항상 이 조회로 최신 결제 상태를 다시 확인한다.
    // storeId 쿼리 파라미터가 없으면 PortOne이 결제를 찾지 못해 PAYMENT_NOT_FOUND(404)를 반환한다
    // (실제 테스트 결제로 확인함 — 이 API Secret이 여러 팀의 store를 포괄하는 계정이라서 그렇다).
    public PortOnePaymentResponse getPayment(String paymentId) {
        return portOneRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/payments/{paymentId}")
                        .queryParam("storeId", storeId)
                        .build(paymentId))
                .retrieve()
                .body(PortOnePaymentResponse.class);
    }

    /**
     * 전체·부분 취소 요청(가이드 9장). amount가 null이면 전액 취소, 값이 있으면 그 금액만 부분 취소한다.
     *
     * 같은 논리 요청의 재시도가 중복 취소가 되지 않도록 Idempotency-Key를 그대로 전달한다
     * (가이드 5.4 — 다른 금액·사유의 요청에는 새 키를 써야 한다).
     */
    public PortOneCancelResponse cancelPayment(String paymentId, Long amount, String reason, String idempotencyKey) {
        return portOneRestClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/payments/{paymentId}/cancel")
                        .build(paymentId))
                .header(IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                .body(new PortOneCancelRequest(storeId, amount, reason))
                .retrieve()
                .body(PortOneCancelResponse.class);
    }
}
