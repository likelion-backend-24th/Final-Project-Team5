package org.example.paymentservice.domain.payment;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * PortOne V2 결제 연동 실전 가이드 4.5절의 결제 상태 모델.
 * FAILED 이후 같은 paymentId로 재시도해 PAID가 되는 경우만 예외적으로 허용한다.
 */
public enum PaymentStatus {
    READY,
    PENDING,
    VIRTUAL_ACCOUNT_ISSUED,
    PAID,
    FAILED,
    EXPIRED,
    PARTIAL_CANCELLED,
    CANCELLED;

    private static final Map<PaymentStatus, Set<PaymentStatus>> ALLOWED_TRANSITIONS = Map.of(
            READY, EnumSet.of(PENDING, VIRTUAL_ACCOUNT_ISSUED, PAID, FAILED, EXPIRED),
            PENDING, EnumSet.of(VIRTUAL_ACCOUNT_ISSUED, PAID, FAILED, EXPIRED),
            VIRTUAL_ACCOUNT_ISSUED, EnumSet.of(PAID, FAILED, EXPIRED),
            FAILED, EnumSet.of(PAID),
            EXPIRED, EnumSet.noneOf(PaymentStatus.class),
            PAID, EnumSet.of(PARTIAL_CANCELLED, CANCELLED),
            PARTIAL_CANCELLED, EnumSet.of(PARTIAL_CANCELLED, CANCELLED),
            CANCELLED, EnumSet.noneOf(PaymentStatus.class)
    );

    public boolean canTransitionTo(PaymentStatus next) {
        return ALLOWED_TRANSITIONS.get(this).contains(next);
    }
}
