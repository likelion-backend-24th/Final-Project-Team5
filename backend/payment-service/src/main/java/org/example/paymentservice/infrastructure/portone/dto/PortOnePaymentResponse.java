package org.example.paymentservice.infrastructure.portone.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;

/**
 * PortOne V2 "결제 단건 조회"(GET /payments/{paymentId}?storeId=...) 응답.
 *
 * 2026-09-07 실제 테스트 채널 결제(PAID·FAILED·VIRTUAL_ACCOUNT_ISSUED)로 검증 완료.
 * status별 유니언 스키마라 paidAt/failure/method처럼 상태 전용 필드는 해당 상태가
 * 아니면 null로 온다.
 *
 * cancellations는 Story 9(환불)에서 추가했다. 아직 실제 취소 응답으로 검증하지 못했으므로
 * ignoreUnknown에 기대고, 대사 로직은 개별 필드보다 amount.cancelled(누적 취소액)를 우선 신뢰한다.
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
        String pgTxId,
        //전체·부분 취소 이력. 가이드 9.4 — 웹훅 본문 하나만 믿지 말고 이 목록 전체로 로컬을 맞춘다.
        List<Cancellation> cancellations
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

    // 취소 1건. status는 PortOne CancellationStatus(REQUESTED/PENDING/SUCCEEDED/FAILED)와 같은 문자열.
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Cancellation(
            String id,
            String status,
            String reason,
            long totalAmount,
            Instant requestedAt,
            Instant cancelledAt
    ) {
    }
}
