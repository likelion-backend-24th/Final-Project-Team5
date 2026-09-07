package org.example.paymentservice.infrastructure.portone.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/**
 * PortOne V2 "결제 단건 조회"(GET /payments/{paymentId}?storeId=...) 응답.
 *
 * 2026-09-07 실제 테스트 채널 결제(PAID·FAILED·VIRTUAL_ACCOUNT_ISSUED)로 검증 완료.
 * status별 유니언 스키마라 paidAt/failure/method처럼 상태 전용 필드는 해당 상태가
 * 아니면 null로 온다. CANCELLED 응답은 아직 실제로 확인하지 못했으니, 취소(Story9)
 * 구현 전에 cancellations 관련 필드는 다시 검증해야 한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PortOnePaymentResponse(
        String id,
        String status,
        String transactionId,
        String storeId,
        Channel channel,
        Method method,
        Amount amount,
        String currency,
        String orderName,
        Instant requestedAt,
        Instant updatedAt,
        Instant statusChangedAt,
        Instant paidAt,
        Instant failedAt,
        Failure failure,
        String pgTxId
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Channel(String id, String key, String type, String name, String pgProvider) {
    }

    // VIRTUAL_ACCOUNT_ISSUED일 때만 bank/accountNumber/expiredAt 등이 채워짐.
    // expiredAt = 입금 기한 — 가상계좌는 예매 홀드 시간을 이 값 기준으로 별도 연장해야 한다
    // (10분 고정 홀드 정책의 예외, 2026-09-07 팀 결정).
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Method(
            String type,
            String bank,
            String accountNumber,
            String accountType,
            String remitterName,
            Instant expiredAt,
            Instant issuedAt
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Amount(long total, long taxFree, long vat, long supply, long discount, long paid, long cancelled, long cancelledTaxFree) {
    }

    // FailedPayment 상태일 때만 채워짐(실제 확인: PAY_PROCESS_CANCELED 등)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Failure(String reason, String pgCode, String pgMessage) {
    }
}
