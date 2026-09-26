package org.example.festivalservice.infrastructure.payment;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Festival-Service → Payment-Service 내부 조회: 행사 취소 승인 전 예상 환불 정보를 가져온다.
 * 운영자 화면 보조 정보라 payment-service가 응답하지 못해도 취소 목록은 내려가야 하므로,
 * 실패하면 빈 맵을 돌려주고 로그만 남긴다(UserLookupClient와 같은 방식).
 */
@Component
@RequiredArgsConstructor
public class PaymentServiceClient {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceClient.class);

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RefundPreview(Long festivalId, long refundTargetPaymentCount,
                                long expectedRefundAmount, long unresolvedPaymentCount) {
    }

    private final RestClient paymentServiceRestClient;

    @Value("${internal.payment-service.token:CHANGE_ME_IN_ENV}")
    private String internalAuthToken;

    //페스티벌 id → 예상 환불 정보. 응답은 봉투 없는 배열이다.
    public Map<Long, RefundPreview> refundPreview(Collection<Long> festivalIds) {
        List<Long> ids = festivalIds.stream().distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        try {
            RefundPreview[] response = paymentServiceRestClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/internal/v1/payments/refund-preview")
                            .queryParam("festivalIds", ids).build())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + internalAuthToken)
                    .retrieve()
                    .body(RefundPreview[].class);
            if (response == null) {
                return Map.of();
            }
            Map<Long, RefundPreview> result = new HashMap<>();
            for (RefundPreview preview : response) {
                result.put(preview.festivalId(), preview);
            }
            return result;
        } catch (RestClientException e) {
            log.warn("payment-service 환불 미리보기 조회 실패 — 금액 없이 목록을 반환한다. festivalIds={}", ids, e);
            return Map.of();
        }
    }
}