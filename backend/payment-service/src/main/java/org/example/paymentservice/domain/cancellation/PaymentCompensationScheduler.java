package org.example.paymentservice.domain.cancellation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.paymentservice.domain.payment.Payment;
import org.example.paymentservice.domain.payment.PaymentRepository;
import org.example.paymentservice.domain.payment.PaymentStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * 예매 확정이 거절된 결제의 자동 전액 환불(보상) 재시도 배치(실전 가이드 11.5·12.6).
 * 결제 요청 스레드에서 환불하지 못했으면(PortOne 타임아웃 등) 같은 멱등키로 다시 시도해, 돈만 빠져나간 결제가 남지 않게 한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "payment-compensation.scheduling-enabled", matchIfMissing = true)
public class PaymentCompensationScheduler {

    //아직 전액 취소되지 않은 결제만 다시 시도한다 — CANCELLED면 보상이 끝난 것이다.
    private static final Set<PaymentStatus> NOT_YET_REFUNDED = Set.of(PaymentStatus.PAID, PaymentStatus.PARTIAL_CANCELLED);
    //방금 거절돼 요청 스레드가 환불 중인 결제와 겹치지 않도록 조금 지난 결제만 집는다.
    private static final Duration GRACE = Duration.ofMinutes(1);

    private final PaymentRepository payments;
    private final PaymentCancellationService cancellations;

    @Scheduled(fixedDelayString = "${payment-compensation.delay-ms:60000}")
    public void run() {
        for (Payment payment : payments.findByStatusInAndReservationRejectedAtBefore(NOT_YET_REFUNDED, Instant.now().minus(GRACE))) {
            try {
                if (cancellations.compensate(payment)) {
                    log.info("확정 거절 결제 자동 환불 완료: paymentId={}", payment.getPaymentId());
                }
            } catch (RuntimeException e) {
                log.warn("확정 거절 결제 자동 환불 재시도 필요: paymentId={}", payment.getPaymentId(), e);
            }
        }
    }
}
