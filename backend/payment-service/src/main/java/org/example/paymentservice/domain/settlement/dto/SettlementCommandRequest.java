package org.example.paymentservice.domain.settlement.dto;

import java.time.Instant;

public record SettlementCommandRequest(Instant paidAt, String paymentReference, String memo) {
    // DTO 이름이 바뀌어도 이미 저장된 멱등 fingerprint의 입력 문자열은 유지한다.
    public String fingerprintValue() {
        return "Command[paidAt=" + paidAt + ", paymentReference=" + paymentReference + ", memo=" + memo + "]";
    }
}
