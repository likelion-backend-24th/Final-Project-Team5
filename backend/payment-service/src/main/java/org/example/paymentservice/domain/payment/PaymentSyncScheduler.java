package org.example.paymentservice.domain.payment;

import lombok.extern.slf4j.Slf4j;
import org.example.paymentservice.common.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 웹훅이 오지 않은 결제의 결과 재조회 배치(실전 가이드 12.6 복구 작업).
 * 브라우저가 완료 API를 부르기 전에 닫히고 웹훅까지 유실되면, PG에서는 승인됐는데 우리 DB는 READY로 남는다.
 * WebhookRetryScheduler는 "받은" 웹훅만 다시 처리하므로, 받지 못한 경우는 여기서 PortOne을 직접 다시 조회해
 * 완료 API·웹훅과 같은 동기화 경로(PaymentService#syncPayment)로 확정·실패·보상 환불을 마무리한다.
 * 승인은 됐는데 예매 확정 응답을 못 받은(Timeout) 결제도 같은 경로로 확정을 다시 요청한다(예매 확정은 같은 paymentId면 멱등).
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "payment-sync.scheduling-enabled", matchIfMissing = true)
public class PaymentSyncScheduler {

    //결과를 아직 모르는 상태 — 결제창을 열었거나 가상계좌만 발급된 결제
    private static final Set<PaymentStatus> AWAITING_RESULT =
            Set.of(PaymentStatus.READY, PaymentStatus.PENDING, PaymentStatus.VIRTUAL_ACCOUNT_ISSUED);
    //PortOne이 아직 이 결제를 모르거나(결제창 미완료) 결과가 나지 않은 상태 — 다음 주기에 다시 본다
    private static final Set<String> NOT_DECIDED_YET =
            Set.of(PaymentErrorCode.PAYMENT_NOT_YET_PROCESSED.name(), PaymentErrorCode.UNEXPECTED_PAYMENT_STATUS.name());
    private static final int BATCH_SIZE = 100;

    private final PaymentRepository payments;
    private final PaymentService paymentService;
    private final Duration minAge;
    private final Duration lookback;

    public PaymentSyncScheduler(PaymentRepository payments,
                                PaymentService paymentService,
                                @Value("${payment-sync.min-age-minutes:10}") long minAgeMinutes,
                                @Value("${payment-sync.lookback-hours:24}") long lookbackHours) {
        this.payments = payments;
        this.paymentService = paymentService;
        //결제창이 열려 있는 동안(예매 홀드 10분)은 완료 API·웹훅이 처리하도록 비켜 둔다.
        this.minAge = Duration.ofMinutes(minAgeMinutes);
        //오래된 결제를 끝없이 다시 조회하지 않도록 최근 결제만 본다.
        this.lookback = Duration.ofHours(lookbackHours);
    }

    @Scheduled(fixedDelayString = "${payment-sync.delay-ms:300000}", initialDelayString = "${payment-sync.initial-delay-ms:60000}")
    public void run() {
        LocalDateTime now = LocalDateTime.now();
        List<Payment> targets;
        try {
            targets = payments.findSyncTargets(AWAITING_RESULT, PaymentStatus.PAID,
                    now.minus(lookback), now.minus(minAge), PageRequest.of(0, BATCH_SIZE));
        } catch (RuntimeException e) {
            log.warn("결제 결과 재조회 대상 조회 실패 — 다음 주기에 다시 시도한다.", e);
            return;
        }

        for (Payment payment : targets) {
            try {
                paymentService.syncPayment(payment.getPaymentId());
            } catch (ApiException e) {
                String code = e.getErrorCode().name();
                if (NOT_DECIDED_YET.contains(code)) {
                    log.debug("결제 결과가 아직 없음: paymentId={}, code={}", payment.getPaymentId(), code);
                } else if (PaymentErrorCode.RESERVATION_ALREADY_FINALIZED.name().equals(code)) {
                    //승인됐지만 예매가 이미 만료·취소돼 확정이 거절된 결제 — syncPayment가 자동 환불(보상)까지 넘겼다.
                    log.info("재조회로 찾은 승인 결제의 예매 확정이 거절돼 자동 환불로 넘김: paymentId={}", payment.getPaymentId());
                } else {
                    log.warn("결제 결과 재조회 처리 실패: paymentId={}, code={}", payment.getPaymentId(), code);
                }
            } catch (RuntimeException e) {
                //한 건의 PortOne·예매 서비스 장애가 나머지 결제의 복구를 막지 않도록 계속 진행한다.
                log.warn("결제 결과 재조회 실패(다음 주기에 재시도): paymentId={}", payment.getPaymentId(), e);
            }
        }
    }
}
